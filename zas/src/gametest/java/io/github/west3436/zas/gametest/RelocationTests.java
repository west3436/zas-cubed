package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.event.BlocksMovedEvent;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneRelocatedEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationOutcome;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import io.github.west3436.zas.persistence.DimensionData;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Moving blocks with their air through {@link Relocations} in a real level (API-065, API-066, API-067,
 * API-069, GEO-046, GEO-047, DATA-020).
 *
 * <p>Each test moves its blocks itself, as a mover mod would: it begins, takes the blocks away, sets
 * them at the destination turned as the destination says, carries the entities, and completes, all
 * in one game tick. The tests run in their own batch, since the first turns the End into a vacuum
 * while it runs; the restart test runs in {@code PersistenceTests}' restart batch, in the Nether.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RelocationTests {
    private static final String BATCH = "relocation";
    private static final SourceId MOVER = SourceId.of("zas_gametest", "mover");
    private static final SourceId CONTROLLER = SourceId.of("zas_gametest", "controller/moved");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    /** Keeps a moved area loaded and its entities ticking; lapses after 300 game ticks. */
    private static final TicketType<ChunkPos> HELD =
            TicketType.create("zas_gametest_moved", Comparator.comparingLong(ChunkPos::toLong), 300);

    private record Seen(ResourceKey<Level> level, ZoneId zone) {}

    private static final List<Seen> BREACHED = new CopyOnWriteArrayList<>();
    private static final List<ZoneRelocatedEvent> RELOCATED = new CopyOnWriteArrayList<>();
    private static final List<BlocksMovedEvent> MOVED = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener(
                (ZoneBreachedEvent e) -> BREACHED.add(new Seen(e.level().dimension(), e.zone())));
        NeoForge.EVENT_BUS.addListener((ZoneRelocatedEvent e) -> RELOCATED.add(e));
        NeoForge.EVENT_BUS.addListener((BlocksMovedEvent e) -> MOVED.add(e));
    }

    private RelocationTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    /** A test's area: chunk-aligned, and clear of every other test's. */
    private static Rooms rooms(GameTestHelper h, ServerLevel level, int index, int y) {
        return new Rooms(h, level, new BlockPos(-24576 + 512 * index, y, -24576));
    }

    private static ServerLevel level(GameTestHelper h, ResourceKey<Level> dimension) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        if (level == null) {
            throw new GameTestAssertException("no " + dimension.location());
        }
        return level;
    }

    /** Holds the chunks around a box loaded, with their entities ticking. */
    private static void hold(ServerLevel level, BoundingBox box) {
        for (int x = box.minX() >> 4; x <= box.maxX() >> 4; x++) {
            for (int z = box.minZ() >> 4; z <= box.maxZ() >> 4; z++) {
                ChunkPos c = new ChunkPos(x, z);
                level.getChunkSource().addRegionTicket(HELD, c, 2, c);
                level.getChunk(x, z);
            }
        }
    }

    /** The box from area corner (x0, 0, z0) to (x1, 4, z1). */
    private static BoundingBox box(Rooms r, int x0, int z0, int x1, int z1) {
        return BoundingBox.fromCorners(r.abs(x0, 0, z0), r.abs(x1, 4, z1));
    }

    /**
     * Moves a box of blocks and the entities in it as a mover mod would, in one game tick: begin, take
     * the blocks away, set them turned at the destination, carry the entities, complete.
     *
     * @return the ticket and the complete's future
     */
    private static Move move(ServerLevel from, BoundingBox box, Destination d) {
        return move(from, box, d, Relocations.begin(from, box, MOVER));
    }

    /** {@link #move(ServerLevel, BoundingBox, Destination)} under a ticket already begun. */
    private static Move move(ServerLevel from, BoundingBox box, Destination d, RelocationTicket ticket) {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
            states.put(p.immutable(), from.getBlockState(p));
        }
        List<Entity> riders = new ArrayList<>(from.getEntities((Entity) null, AABB.of(box), e -> true));
        for (BlockPos p : states.keySet()) {
            from.setBlockAndUpdate(p, Rooms.AIR);
        }
        states.forEach((p, state) -> d.level().setBlockAndUpdate(d.map(p), state.rotate(d.rotation())));
        for (Entity e : riders) {
            Vec3 to = map(d, e.position());
            if (d.level() == from) {
                e.teleportTo(to.x, to.y, to.z);
            } else {
                e.teleportTo(d.level(), to.x, to.y, to.z, Set.of(), e.getYRot(), e.getXRot());
            }
        }
        return new Move(ticket, Relocations.complete(ticket, d));
    }

    private record Move(RelocationTicket ticket, CompletableFuture<RelocationResult> done) {}

    /** Where a destination puts a point, as it puts the block it is in. */
    private static Vec3 map(Destination d, Vec3 p) {
        double x = p.x - (d.pivot().getX() + 0.5);
        double z = p.z - (d.pivot().getZ() + 0.5);
        double rx = switch (d.rotation()) {
            case NONE -> x;
            case CLOCKWISE_90 -> -z;
            case CLOCKWISE_180 -> -x;
            case COUNTERCLOCKWISE_90 -> z;
        };
        double rz = switch (d.rotation()) {
            case NONE -> z;
            case CLOCKWISE_90 -> x;
            case CLOCKWISE_180 -> -z;
            case COUNTERCLOCKWISE_90 -> -x;
        };
        return new Vec3(
                d.target().getX() + 0.5 + rx,
                p.y - d.pivot().getY() + d.target().getY(),
                d.target().getZ() + 0.5 + rz);
    }

    /** Runs atmos ticks in the levels until the future completes. */
    private static <T> T settle(GameTestHelper h, CompletableFuture<T> future, ServerLevel... levels) {
        for (int i = 0; i < 12 && !future.isDone(); i++) {
            for (ServerLevel level : levels) {
                Rooms.service().tickNow(level);
            }
        }
        h.assertTrue(future.isDone(), "the step didn't complete within 12 atmos ticks");
        return future.join();
    }

    /**
     * Fills a sealed room's zone to {@code kPa} with a mix that isn't the ambient's: some carbon
     * dioxide, under its hazard, and the rest oxygen and nitrogen half and half.
     */
    private static ZoneInfo fill(Rooms r, int x, int y, int z, double kPa) {
        GasParcel seed = new GasParcel(Map.of(CO2, 0.01), 293.15);
        r.settle(GasMutations.add(r.level, r.point(x, y, z), seed, MOVER));
        ZoneInfo zone = r.zone(x, y, z);
        double t = zone.mixture().temperature();
        double now = zone.mixture().moles().values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();
        double target = now * kPa / zone.mixture().pressure();
        double co2 = target * 0.012;
        double rest = target - now - co2;
        GasParcel more = new GasParcel(Map.of(CO2, co2, Rooms.O2, rest / 2, Rooms.N2, rest / 2), t);
        r.settle(GasMutations.add(r.level, r.point(x, y, z), more, MOVER));
        ZoneInfo filled = r.zone(x, y, z);
        r.h.assertTrue(
                Math.abs(filled.mixture().pressure() - kPa) < 0.5,
                "the room isn't at " + kPa + " kPa: " + filled.mixture().pressure());
        return filled;
    }

    /** Checks a zone that moved holds what it held before, gas for gas. */
    private static void carried(GameTestHelper h, ZoneInfo before, ZoneInfo after, String what) {
        h.assertValueEqual(after.cellCount(), before.cellCount(), what + ": cells");
        Map<ResourceLocation, Double> was = before.mixture().moles();
        Map<ResourceLocation, Double> is = after.mixture().moles();
        h.assertValueEqual(is.keySet(), was.keySet(), what + ": gases");
        for (Map.Entry<ResourceLocation, Double> e : was.entrySet()) {
            double n = is.get(e.getKey());
            h.assertTrue(
                    Math.abs(n - e.getValue()) <= 1e-9 * Math.max(1, e.getValue()),
                    what + ": " + e.getKey() + " was " + e.getValue() + ", is " + n);
        }
        h.assertTrue(
                Math.abs(after.mixture().temperature() - before.mixture().temperature()) <= 1e-9 * 300,
                what + ": temperature was " + before.mixture().temperature() + ", is "
                        + after.mixture().temperature());
        h.assertFalse(after.breached(), what + ": breached");
        h.assertFalse(after.paused(), what + ": paused");
    }

    private static List<String> pins(Rooms r, ZoneId id) {
        var v = r.dimension().published().zone(id.value());
        if (v == null) {
            throw new GameTestAssertException("zone " + id.value() + " isn't published");
        }
        return v.pins();
    }

    private static boolean breached(ServerLevel level, ZoneId zone) {
        return BREACHED.contains(new Seen(level.dimension(), zone));
    }

    // ---- Tests --------------------------------------------------------------------------

    /**
     * The acceptance test: a sealed room at 150 kPa with a mix that isn't the ambient's, pinned, with
     * a pig inside, is moved 100 blocks, then into the End made a vacuum. Each time it keeps its
     * moles of each gas, its temperature, its cells and its pin; no breach event fires, one
     * relocation event does, naming where it came from; and the pig is neither pushed nor hurt
     * (API-065, API-066, GEO-046).
     */
    @GameTest(template = "empty", timeoutTicks = 12_000, batch = BATCH)
    public static void api_065_sealed_room_moves_with_its_air_and_into_a_vacuum(GameTestHelper h) {
        ServerLevel overworld = h.getLevel();
        ServerLevel end = level(h, Level.END);
        DimensionAmbient vacuum = Ambients.definition(ResourceKey.create(
                        Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum")))
                .orElseThrow(() -> new GameTestAssertException("no vacuum definition"));
        Rooms r = rooms(h, overworld, 0, 100);
        Rooms e = rooms(h, end, 0, 150);
        hold(overworld, box(r, 0, 0, 4, 4));
        hold(overworld, box(r, 100, 0, 104, 4));
        hold(end, box(e, 0, 0, 4, 4));
        UUID[] pigId = new UUID[1];
        float[] health = new float[1];
        ZoneId[] ids = new ZoneId[3];
        Vec3 there = Vec3.atBottomCenterOf(e.abs(2, 1, 2));
        // The pig must be where entities tick, in each level, or it would be neither found nor hurt.
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        overworld.isPositionEntityTicking(r.abs(2, 1, 2))
                                && overworld.isPositionEntityTicking(r.abs(102, 1, 2))
                                && end.isPositionEntityTicking(e.abs(2, 1, 2)),
                        "the areas don't tick entities yet"))
                .thenExecute(() -> {
                    Ambients.overrideDefinitionForTest(Level.END, vacuum);
                    try {
                        h.assertTrue(Ambients.ambient(Level.END).isVacuum(), "the End isn't a vacuum");
                        Rooms.service().tickNow(end);
                        Pig pig = EntityType.PIG.create(overworld);
                        if (pig == null) {
                            throw new GameTestAssertException("no pig");
                        }
                        r.clear(0, 106);
                        e.clear(0, 6);
                        r.shell(0, 4);
                        pig.setNoAi(true);
                        Vec3 stand = Vec3.atBottomCenterOf(r.abs(2, 1, 2));
                        pig.moveTo(stand.x, stand.y, stand.z, 0, 0);
                        overworld.addFreshEntity(pig);
                        pigId[0] = pig.getUUID();
                        health[0] = pig.getHealth();
                        Rooms.withoutHeat(() -> moveTwice(h, r, e, ids));
                        Entity carried = end.getEntity(pigId[0]);
                        h.assertTrue(carried instanceof LivingEntity, "the pig didn't arrive in the End");
                        h.assertTrue(
                                carried.position().distanceTo(there) < 0.01,
                                "the pig arrived at " + carried.position() + ", not " + there);
                    } catch (RuntimeException | AssertionError failed) {
                        Ambients.overrideDefinitionForTest(Level.END, null);
                        throw failed;
                    }
                })
                .thenExecuteAfter(40, () -> {
                    Entity pig = end.getEntity(pigId[0]);
                    try {
                        h.assertTrue(pig instanceof LivingEntity, "the pig is gone from the End");
                        LivingEntity living = (LivingEntity) pig;
                        Vec3 now = living.position();
                        h.assertTrue(
                                Math.abs(now.x - there.x) < 0.01 && Math.abs(now.z - there.z) < 0.01,
                                "the pig was pushed: " + now + ", not " + there);
                        h.assertTrue(living.isAlive(), "the pig died");
                        h.assertValueEqual(living.getHealth(), health[0], "the pig's health");
                        ZoneInfo later = Atmospheres.zone(end, ids[2])
                                .orElseThrow(() -> new GameTestAssertException("the room's zone is gone"));
                        h.assertFalse(later.breached(), "the room is breached");
                        h.assertFalse(breached(end, ids[2]), "a breach event fired in the End");
                    } finally {
                        if (pig != null) {
                            pig.discard();
                        }
                        ZonePins.unpin(end, CONTROLLER);
                        Ambients.overrideDefinitionForTest(Level.END, null);
                    }
                })
                .thenSucceed();
    }

    /** The acceptance test's two moves, checked as each lands. */
    private static void moveTwice(GameTestHelper h, Rooms r, Rooms e, ZoneId[] ids) {
        ServerLevel overworld = r.level;
        ServerLevel end = e.level;
        ZoneInfo start = fill(r, 2, 2, 2, 150);
        ids[0] = start.id();
        h.assertValueEqual(
                r.settle(ZonePins.pin(overworld, r.point(2, 2, 2), CONTROLLER)).outcome(), PinOutcome.ZONE, "the pin");
        BREACHED.clear();
        RELOCATED.clear();

        // 100 blocks along x, in the same level: the zone keeps its id.
        Move first = move(overworld, box(r, 0, 0, 4, 4), Destination.offset(overworld, new Vec3i(100, 0, 0)));
        h.assertTrue(
                Relocations.isMoving(overworld, r.abs(102, 2, 2)), "the destination isn't moving before it arrives");
        RelocationResult moved = settle(h, first.done(), overworld);
        h.assertValueEqual(moved.outcome(), RelocationOutcome.MOVED, "the first move");
        h.assertValueEqual(moved.zones(), Map.of(ids[0], ids[0]), "the first move's zones");
        h.assertTrue(moved.left().isEmpty(), "the first move left zones: " + moved.left());
        h.assertFalse(r.zoned(2, 2, 2), "a zone is left where the room was");
        h.assertFalse(Relocations.isMoving(overworld, r.abs(102, 2, 2)), "still moving after it arrived");
        ZoneInfo hundred = r.zone(102, 2, 2);
        h.assertValueEqual(hundred.id(), ids[0], "the zone's id after the first move");
        carried(h, start, hundred, "after 100 blocks");
        h.assertValueEqual(pins(r, ids[0]), List.of(CONTROLLER.toString()), "the pins after 100 blocks");

        // Into the End, a vacuum: a new id there, and an event naming the old one.
        Move second = move(
                overworld,
                box(r, 100, 0, 104, 4),
                new Destination(end, r.abs(100, 0, 0), e.abs(0, 0, 0), Rotation.NONE));
        RelocationResult arrived = settle(h, second.done(), overworld, end);
        h.assertValueEqual(arrived.outcome(), RelocationOutcome.MOVED, "the second move");
        ZoneInfo inEnd = e.zone(2, 2, 2);
        ids[2] = inEnd.id();
        h.assertValueEqual(arrived.zones(), Map.of(ids[0], ids[2]), "the second move's zones");
        h.assertFalse(r.zoned(102, 2, 2), "a zone is left where the room was before the End");
        carried(h, start, inEnd, "in the End");
        h.assertValueEqual(pins(e, ids[2]), List.of(CONTROLLER.toString()), "the pins in the End");
        h.assertTrue(
                Math.abs(inEnd.mixture().pressure() - 150) < 0.5,
                "the room's pressure in the End: " + inEnd.mixture().pressure());

        h.assertFalse(breached(overworld, ids[0]), "a breach event fired in the overworld");
        h.assertFalse(breached(end, ids[2]), "a breach event fired in the End");
        List<ZoneRelocatedEvent> events = new ArrayList<>(RELOCATED);
        h.assertValueEqual(events.size(), 2, "relocation events");
        ZoneRelocatedEvent last = events.get(1);
        h.assertValueEqual(last.level(), end, "the second event's level");
        h.assertValueEqual(last.zone(), ids[2], "the second event's zone");
        h.assertValueEqual(last.from(), ids[0], "the second event's old zone");
        h.assertValueEqual(last.fromDimension(), Level.OVERWORLD, "the second event's old dimension");
        h.assertValueEqual(last.mover(), MOVER, "the second event's mover");
    }

    /**
     * A room turned a quarter as it moves keeps its gas, and its cells are where the turn puts them
     * (GEO-046).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void geo_046_turned_room_keeps_its_air(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 1, 100);
        r.clear(0, 0, 50, 8);
        r.box(0, 0, 6, 4); // interior x 1 to 5, z 1 to 3
        Rooms.withoutHeat(() -> {
            ZoneInfo before = fill(r, 3, 2, 2, 130);
            Destination d = new Destination(r.level, r.abs(0, 0, 0), r.abs(40, 0, 0), Rotation.CLOCKWISE_90);
            RelocationResult moved =
                    r.settle(move(r.level, box(r, 0, 0, 6, 4), d).done());
            h.assertValueEqual(moved.outcome(), RelocationOutcome.MOVED, "the move");
            // (x, z) turns to (-z, x): interior (5, 3) is at (40 - 3, 5).
            ZoneInfo after = Atmospheres.zoneAt(r.level, d.map(r.abs(5, 2, 3)))
                    .orElseThrow(() -> new GameTestAssertException("no zone where the turn put the room"));
            h.assertValueEqual(d.map(r.abs(5, 2, 3)), r.abs(37, 2, 5), "where the turn puts a cell");
            carried(h, before, after, "the turned room");
            h.assertFalse(r.zoned(3, 2, 2), "a zone is left where the room was");
        });
        h.succeed();
    }

    /**
     * A zone with cells inside and outside the box stays and is listed as left; the zone wholly inside
     * goes. The begin pauses the zones it takes and leaves the rest (GEO-046, API-065).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void geo_046_room_across_the_box_stays(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 2, 100);
        r.clear(0, 0, 60, 6);
        r.shell(0, 4);
        r.shell(6, 10);
        Rooms.withoutHeat(() -> {
            ZoneInfo inside = fill(r, 2, 2, 2, 120);
            ZoneInfo across = fill(r, 8, 2, 2, 120);
            RelocationTicket ticket = Relocations.begin(r.level, box(r, 0, 0, 8, 4), MOVER);
            RelocationResult begun = r.settle(ticket.begun());
            h.assertValueEqual(begun.outcome(), RelocationOutcome.BEGUN, "the begin");
            h.assertValueEqual(begun.zones().keySet(), Set.of(inside.id()), "the zones the begin took");
            h.assertValueEqual(begun.left(), Set.of(across.id()), "the zones the begin left");
            h.assertTrue(r.zone(2, 2, 2).paused(), "the zone that goes isn't paused");
            h.assertFalse(r.zone(8, 2, 2).paused(), "the zone that stays is paused");
            RelocationResult cancelled = r.settle(Relocations.cancel(ticket));
            h.assertValueEqual(cancelled.outcome(), RelocationOutcome.CANCELLED, "the cancel");
        });
        h.succeed();
    }

    /**
     * Cancelling resumes the zones as they were; a ticket that is over answers ENDED. A begin is
     * refused whole: for a zone another move holds, for more cells than the server allows, and for a
     * box in a chunk that isn't loaded (API-065, GEO-047).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void api_065_cancel_and_refusals(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 3, 100);
        r.clear(0, 6);
        r.shell(0, 4);
        Rooms.withoutHeat(() -> {
            ZoneInfo before = fill(r, 2, 2, 2, 110);
            BoundingBox box = box(r, 0, 0, 4, 4);

            RelocationTicket ticket = Relocations.begin(r.level, box, MOVER);
            h.assertTrue(Relocations.isMoving(r.level, r.abs(2, 2, 2)), "a block in the box isn't moving");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(5, 2, 2)), "a block outside the box is moving");
            RelocationTicket again = Relocations.begin(r.level, box, MOVER);
            h.assertValueEqual(r.settle(ticket.begun()).outcome(), RelocationOutcome.BEGUN, "the begin");
            h.assertValueEqual(r.settle(again.begun()).outcome(), RelocationOutcome.BUSY, "a second begin");
            h.assertValueEqual(
                    Relocations.complete(again, Destination.offset(r.level, new Vec3i(10, 0, 0)))
                            .join()
                            .outcome(),
                    RelocationOutcome.ENDED,
                    "completing a refused ticket");

            RelocationResult cancelled = r.settle(Relocations.cancel(ticket));
            h.assertValueEqual(cancelled.outcome(), RelocationOutcome.CANCELLED, "the cancel");
            h.assertValueEqual(cancelled.zones(), Map.of(before.id(), before.id()), "the cancel's zones");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(2, 2, 2)), "still moving after the cancel");
            r.tick();
            ZoneInfo after = r.zone(2, 2, 2);
            r.same(before, after, "after the cancel");
            h.assertFalse(after.paused(), "paused after the cancel");
            h.assertValueEqual(
                    Relocations.cancel(ticket).join().outcome(), RelocationOutcome.ENDED, "cancelling twice");

            int max = ZasServerConfig.RELOCATION_MAX_CELLS.get();
            ZasServerConfig.RELOCATION_MAX_CELLS.set(26);
            try {
                RelocationTicket big = Relocations.begin(r.level, box, MOVER);
                h.assertValueEqual(r.settle(big.begun()).outcome(), RelocationOutcome.TOO_LARGE, "27 cells over 26");
            } finally {
                ZasServerConfig.RELOCATION_MAX_CELLS.set(max);
            }
            h.assertFalse(r.zone(2, 2, 2).paused(), "a refused begin paused the zone");

            BlockPos far = new BlockPos(-28_000_000, 100, -28_000_000);
            h.assertTrue(r.level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "loaded");
            RelocationTicket unloaded =
                    Relocations.begin(r.level, BoundingBox.fromCorners(far, far.offset(4, 4, 4)), MOVER);
            h.assertTrue(unloaded.begun().isDone(), "an unloaded begin didn't answer at once");
            h.assertValueEqual(unloaded.begun().join().outcome(), RelocationOutcome.UNLOADED, "an unloaded begin");
        });
        h.succeed();
    }

    /**
     * A begin that names the destination holds it as moving from the begin, not from the complete,
     * and lets it go when the ticket ends, cancelled or arrived (API-069). The room moves as it does
     * without one.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void api_069_a_named_destination_is_moving_from_the_begin(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 7, 100);
        r.clear(0, 30);
        r.shell(0, 4);
        Rooms.withoutHeat(() -> {
            ZoneInfo before = fill(r, 2, 2, 2, 110);
            BoundingBox box = box(r, 0, 0, 4, 4);
            Destination d = Destination.offset(r.level, new Vec3i(20, 0, 0));

            RelocationTicket cancelled = Relocations.begin(r.level, box, d, MOVER);
            h.assertValueEqual(cancelled.destination(), Optional.of(d), "the ticket's destination");
            h.assertTrue(Relocations.isMoving(r.level, r.abs(2, 2, 2)), "the box isn't moving");
            h.assertTrue(Relocations.isMoving(r.level, r.abs(22, 2, 2)), "the destination isn't moving");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(25, 2, 2)), "a block past the destination is moving");
            h.assertValueEqual(r.settle(cancelled.begun()).outcome(), RelocationOutcome.BEGUN, "the begin");
            h.assertTrue(Relocations.isMoving(r.level, r.abs(22, 2, 2)), "the destination isn't moving once begun");
            h.assertValueEqual(
                    r.settle(Relocations.cancel(cancelled)).outcome(), RelocationOutcome.CANCELLED, "the cancel");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(22, 2, 2)), "the destination is moving after the cancel");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(2, 2, 2)), "the box is moving after the cancel");
            r.tick();

            RelocationTicket ticket = Relocations.begin(r.level, box, d, MOVER);
            h.assertTrue(Relocations.isMoving(r.level, r.abs(22, 2, 2)), "the destination isn't moving");
            RelocationResult moved = r.settle(move(r.level, box, d, ticket).done());
            h.assertValueEqual(moved.outcome(), RelocationOutcome.MOVED, "the move");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(22, 2, 2)), "still moving after it arrived");
            carried(h, before, r.zone(22, 2, 2), "after the move");
            RelocationTicket unnamed = Relocations.begin(r.level, box, MOVER);
            h.assertTrue(unnamed.destination().isEmpty(), "a ticket begun without a destination has one");
            h.assertFalse(Relocations.isMoving(r.level, r.abs(22, 2, 2)), "an unnamed destination is moving");
            r.settle(Relocations.cancel(unnamed));
        });
        h.succeed();
    }

    /** A ticket left open is cancelled after the server's time limit, and its zones go on (GEO-047). */
    @GameTest(template = "empty", timeoutTicks = 120, batch = BATCH)
    public static void geo_047_open_ticket_times_out(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 4, 100);
        hold(r.level, box(r, 0, 0, 4, 4));
        r.clear(0, 6);
        r.shell(0, 4);
        ZoneInfo before = fill(r, 2, 2, 2, 110);
        int limit = ZasServerConfig.RELOCATION_TIMEOUT_SECONDS.get();
        ZasServerConfig.RELOCATION_TIMEOUT_SECONDS.set(1);
        RelocationTicket ticket = Relocations.begin(r.level, box(r, 0, 0, 4, 4), MOVER);
        h.assertValueEqual(r.settle(ticket.begun()).outcome(), RelocationOutcome.BEGUN, "the begin");
        h.runAfterDelay(40, () -> {
            try {
                h.assertFalse(Relocations.isMoving(r.level, r.abs(2, 2, 2)), "still moving after the limit");
                RelocationResult late = Relocations.complete(ticket, Destination.offset(r.level, new Vec3i(10, 0, 0)))
                        .join();
                h.assertValueEqual(late.outcome(), RelocationOutcome.TIMED_OUT, "a complete after the limit");
                r.tick();
                ZoneInfo after = r.zone(2, 2, 2);
                h.assertValueEqual(after.id(), before.id(), "the zone after the limit");
                h.assertFalse(after.paused(), "the zone is paused after the limit");
                h.succeed();
            } finally {
                ZasServerConfig.RELOCATION_TIMEOUT_SECONDS.set(limit);
            }
        });
    }

    /**
     * A move between world coordinates and a far plot of the same level, as Sable's sub-levels are
     * kept at x and z near 20,481,024 with y raised 28, and back, keeps the zone's air both ways
     * (API-065).
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
    public static void api_065_room_goes_to_a_far_plot_and_back(GameTestHelper h) {
        Rooms r = rooms(h, h.getLevel(), 5, 100);
        Rooms plot = new Rooms(h, h.getLevel(), new BlockPos(20_481_024, 128, 20_481_024));
        hold(r.level, box(r, 0, 0, 4, 4));
        hold(r.level, box(plot, 0, 0, 4, 4));
        r.clear(0, 6);
        plot.clear(0, 6);
        r.shell(0, 4);
        Rooms.withoutHeat(() -> {
            ZoneInfo before = fill(r, 2, 2, 2, 140);
            Destination out = new Destination(r.level, r.abs(0, 0, 0), plot.abs(0, 0, 0), Rotation.NONE);
            h.assertValueEqual(plot.abs(0, 0, 0).getY() - r.abs(0, 0, 0).getY(), 28, "the plot's lift");
            RelocationResult there =
                    r.settle(move(r.level, box(r, 0, 0, 4, 4), out).done());
            h.assertValueEqual(there.outcome(), RelocationOutcome.MOVED, "the move to the plot");
            carried(h, before, plot.zone(2, 2, 2), "on the plot");

            Destination back = new Destination(r.level, plot.abs(0, 0, 0), r.abs(0, 0, 0), Rotation.NONE);
            RelocationResult home =
                    r.settle(move(r.level, box(plot, 0, 0, 4, 4), back).done());
            h.assertValueEqual(home.outcome(), RelocationOutcome.MOVED, "the move back");
            h.assertValueEqual(home.zones(), Map.of(before.id(), before.id()), "the zone's id");
            carried(h, before, r.zone(2, 2, 2), "back from the plot");
            h.assertFalse(plot.zoned(2, 2, 2), "a zone is left on the plot");
        });
        h.succeed();
    }

    /**
     * A zone that has left and not yet arrived when its destination stops is saved with the
     * destination and arrives after the restart, its gas neither lost nor doubled; the move's future
     * ends, cancelled (DATA-020).
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "data_restart")
    public static void data_020_zone_on_its_way_survives_a_restart(GameTestHelper h) {
        ServerLevel overworld = h.getLevel();
        ServerLevel nether = level(h, Level.NETHER);
        Rooms r = rooms(h, overworld, 6, 100);
        Rooms n = rooms(h, nether, 6, 100);
        r.clear(0, 6);
        n.clear(0, 6);
        r.shell(0, 4);
        Rooms.withoutHeat(() -> {
            ZoneInfo before = fill(r, 2, 2, 2, 125);
            Move m = move(
                    overworld,
                    box(r, 0, 0, 4, 4),
                    new Destination(nether, r.abs(0, 0, 0), n.abs(0, 0, 0), Rotation.NONE));
            r.tick(); // the zone leaves the overworld, and the Nether's data holds it
            h.assertFalse(r.zoned(2, 2, 2), "the zone didn't leave");
            h.assertFalse(m.done().isDone(), "the move finished before the zone arrived");
            h.assertValueEqual(DimensionData.of(nether).arrivals().size(), 1, "arrivals the Nether holds");

            long[] chunks = {new ChunkPos(n.abs(0, 0, 0)).toLong()};
            Map<Long, byte[]> saved = Rooms.service().stopForTest(nether, chunks);
            h.assertTrue(m.done().isCompletedExceptionally(), "the move's future didn't end at the stop");
            try {
                m.done().join();
            } catch (CancellationException expected) {
                // as documented
            } catch (RuntimeException other) {
                h.assertTrue(other.getCause() instanceof CancellationException, "the stop's exception: " + other);
            }
            DimensionAtmos restarted = Rooms.service().startForTest(nether, saved);
            n.ticks(3);
            ZoneInfo after = n.zone(2, 2, 2);
            carried(h, before, after, "after the restart");
            h.assertTrue(DimensionData.of(nether).arrivals().isEmpty(), "the arrival is still held");
            h.assertTrue(restarted.published().zone(after.id().value()) != null, "the zone isn't published");
            h.assertFalse(r.zoned(2, 2, 2), "the zone is back in the overworld too");
        });
        h.succeed();
    }
}
