package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerMenu;
import io.github.west3436.zas.atmos.airlock.AirlockCycle;
import io.github.west3436.zas.atmos.airlock.AirlockFirelock;
import io.github.west3436.zas.atmos.airlock.SensorStatus;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ConsoleSide;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentMode;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Airlocks (CONT-133 to CONT-142, CONT-152, CONT-153).
 *
 * <p>The doors stand in the wall at x = 4 between two sealed 3 × 3 × 3 rooms, A (x 0–4) and B (x
 * 4–8), at z = 2 (the large airlock at z 2–3), facing east, so a closed one seals its east face
 * towards B and its cells join A, as the firelock's do.
 *
 * <p>The cycle tests use {@link #lock}: room A (x 0–4) with its area air controller and the airlock
 * controller on its north wall; a chamber one block wide and three high at x = 5, z = 2; and room B
 * (x 6–10), or in the vacuum test nothing, past it. The inner airlock is in A's east wall facing
 * west, the outer in B's west wall facing east, so both seal the face away from the chamber and the
 * chamber holds their cells. Its vents are in its floor and ceiling, and its air sensor on its north
 * wall.
 *
 * <p>Each test but the timed ones drives the atmos ticks itself within one game tick, as {@link
 * FirelockTests} does.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirlockTests {
    private static final String BATCH = "zas_atmos_airlock";
    private static final String TIMED = "zas_atmos_airlock_timed";
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation CO2 = io.github.west3436.zas.atmos.controller.ControllerPreset.CO2;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    private AirlockTests() {}

    // ---- Building -----------------------------------------------------------------------------

    /** Rooms A and B, sealed around the air they were built in. */
    private static void rooms(Site s) {
        s.clear();
        s.room(0, 4);
        s.room(4, 8);
    }

    private static void seedRooms(Site s) {
        FirelockTests.seed(s, 2);
        FirelockTests.seed(s, 6);
    }

    /** A 1 by 2 airlock at (x, 1, z), closed or open, facing {@code facing}. */
    private static AirlockBlockEntity airlock(Site s, int x, int z, Direction facing, boolean open) {
        BlockState lower = AtmosBlocks.AIRLOCK
                .get()
                .defaultBlockState()
                .setValue(AirlockBlock.FACING, facing)
                .setValue(AirlockBlock.OPEN, open);
        s.set(x, 1, z, lower);
        s.set(x, 2, z, lower.setValue(AirlockBlock.HALF, DoubleBlockHalf.UPPER));
        return s.entity(x, 1, z, AirlockBlockEntity.class);
    }

    /** The large airlock in the wall at x = 4, z 2–3, facing east: its left column, the origin, at z = 2. */
    private static AirlockBlockEntity largeAirlock(Site s, boolean open) {
        BlockState base = AtmosBlocks.LARGE_AIRLOCK
                .get()
                .defaultBlockState()
                .setValue(AirlockBlock.FACING, Direction.EAST)
                .setValue(AirlockBlock.OPEN, open);
        for (int i = 0; i < 4; i++) {
            boolean right = (i & 1) == 1;
            boolean upper = i >= 2;
            s.set(
                    4,
                    upper ? 2 : 1,
                    right ? 3 : 2,
                    base.setValue(AirlockBlock.SIDE, right ? ConsoleSide.RIGHT : ConsoleSide.LEFT)
                            .setValue(AirlockBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER));
        }
        return s.entity(4, 1, 2, AirlockBlockEntity.class);
    }

    private static BlockState at(Site s, int x, int y, int z) {
        return s.level.getBlockState(s.abs(x, y, z));
    }

    private static boolean open(Site s, int x, int z) {
        return at(s, x, 1, z).getValue(AirlockBlock.OPEN);
    }

    private static boolean bolted(Site s, int x, int z) {
        return at(s, x, 1, z).getValue(AirlockBlock.BOLTED);
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false);
    }

    /** Uses a block by hand, as a right click with an empty hand does. */
    private static InteractionResult use(Site s, Player player, int x, int y, int z) {
        BlockPos pos = s.abs(x, y, z);
        return s.level.getBlockState(pos).useWithoutItem(s.level, player, hit(pos));
    }

    /** Uses an item on a block, as a right click does once the block has passed it on. */
    private static InteractionResult useItem(Site s, Player player, ItemStack stack, int x, int y, int z) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit(s.abs(x, y, z)));
        InteractionResult first = stack.getItem().onItemUseFirst(stack, context);
        return first != InteractionResult.PASS ? first : stack.useOn(context);
    }

    /** Fills the zone at a point with N₂ and O₂ at partial pressures, emptying it first. */
    private static void fill(Site s, int x, int y, int z, double n2, double o2) {
        if (s.zone(x, y, z).mixture().totalMoles() > 0) {
            s.empty(x, y, z);
        }
        ZoneInfo zone = s.zone(x, y, z);
        double rt = GasMixture.GAS_CONSTANT * 293.15;
        Map<ResourceLocation, Double> gas = new HashMap<>();
        gas.put(N2, n2 * zone.volume() / rt);
        if (o2 > 0) {
            gas.put(O2, o2 * zone.volume() / rt);
        }
        s.settle(GasMutations.add(s.level, s.point(x, y, z), new GasParcel(gas, 293.15), Site.SOURCE));
    }

    /** The items lying around the site, by item, counted. */
    private static Map<Item, Integer> drops(Site s) {
        Map<Item, Integer> out = new HashMap<>();
        for (ItemEntity e : s.level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(s.abs(-2, -2, -2).getCenter(), s.abs(18, 8, 8).getCenter()))) {
            out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
        }
        return out;
    }

    private static void run(GameTestHelper h, Site s, Runnable checks) {
        try {
            checks.run();
        } finally {
            BatteryTests.clear(s);
        }
        h.succeed();
    }

    private static String key(Component c) {
        return c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString();
    }

    /** The parts of {@link #lock}. */
    private record Lock(
            AreaAirControllerBlockEntity area,
            AirlockControllerBlockEntity controller,
            AirlockBlockEntity inner,
            AirlockBlockEntity outer,
            AirSensorBlockEntity sensor,
            List<VentBlockEntity> vents) {
        AtmosDevice[] devices() {
            List<AtmosDevice> all = new ArrayList<>(List.of(area, controller, inner, outer, sensor));
            all.addAll(vents);
            return all.toArray(AtmosDevice[]::new);
        }
    }

    /**
     * The airlock described above, all linked. A holds air at 100 kPa; with {@code roomB}, B is a
     * room. B and the chamber keep the air they were sealed around, 1 atm in the Overworld and
     * nearly none in the vacuum test, with a little N₂ added so each is a zone either way.
     *
     * @param roomB whether B is a room; without, the outer airlock opens onto the outside
     * @param ceilingVent whether the chamber has a second vent, in its ceiling
     */
    private static Lock lock(Site s, boolean roomB, boolean ceilingVent) {
        s.clear();
        s.room(0, 4);
        if (roomB) {
            s.room(6, 10);
        } else {
            s.set(6, 0, 2, Site.STONE);
            s.set(6, 3, 2, Site.STONE);
        }
        for (int y = 0; y <= 4; y++) {
            for (int z = 0; z <= 4; z++) {
                s.set(5, y, z, Site.STONE);
            }
        }
        for (int y = 1; y <= 3; y++) {
            s.set(5, y, 2, Site.AIR);
        }
        List<VentBlockEntity> vents = new ArrayList<>();
        BlockState vent = AtmosBlocks.VENT.get().defaultBlockState();
        vents.add(s.place(5, 0, 2, vent.setValue(VentBlock.FACING, Direction.UP), VentBlockEntity.class));
        if (ceilingVent) {
            vents.add(s.place(5, 4, 2, vent.setValue(VentBlock.FACING, Direction.DOWN), VentBlockEntity.class));
        }
        AirlockBlockEntity inner = airlock(s, 4, 2, Direction.WEST, false);
        AirlockBlockEntity outer = airlock(s, 6, 2, Direction.EAST, false);
        AirSensorBlockEntity sensor = s.place(
                5,
                2,
                2,
                AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH),
                AirSensorBlockEntity.class);
        s.set(2, 2, 0, TestParts.battery().defaultBlockState());
        AreaAirControllerBlockEntity area = s.controller(2);
        AirlockControllerBlockEntity controller = s.place(
                3,
                2,
                1,
                AtmosBlocks.AIRLOCK_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(AirlockControllerBlock.FACING, Direction.SOUTH),
                AirlockControllerBlockEntity.class);
        FirelockTests.seed(s, 2);
        s.settle(GasMutations.add(s.level, s.point(5, 2, 2), new GasParcel(Map.of(N2, 0.01), 293.15), Site.SOURCE));
        if (roomB) {
            s.settle(GasMutations.add(s.level, s.point(8, 2, 2), new GasParcel(Map.of(N2, 0.01), 293.15), Site.SOURCE));
        }
        fill(s, 2, 2, 2, 79, 21);
        for (BlockPos p : List.of(inner.getBlockPos(), outer.getBlockPos(), sensor.getBlockPos())) {
            controller.toggleLink(s.level, p);
        }
        for (VentBlockEntity v : vents) {
            controller.toggleLink(s.level, v.getBlockPos());
        }
        Lock lock = new Lock(area, controller, inner, outer, sensor, vents);
        // The area air controller pins its zone and pays for the airlock controller, which pays its vents.
        for (int i = 0; i < 4 && !controller.power().isPowered(); i++) {
            s.cycle(lock.devices());
        }
        s.cycle(lock.devices());
        s.h.assertTrue(controller.power().isPowered(), "the airlock controller has no power");
        s.h.assertTrue(controller.power().source() == area, "the area air controller doesn't power it");
        return lock;
    }

    private static boolean geometryLike(Geometry a, Geometry b) {
        int boundary = Geometry.FLAG_ZONE_BOUNDARY;
        if ((a.flags() & boundary) != (b.flags() & boundary) || a.cellCount() != b.cellCount()) {
            return false;
        }
        for (Face f : Face.values()) {
            if (a.isFaceFull(f) != b.isFaceFull(f)) {
                return false;
            }
        }
        return true;
    }

    // ---- CONT-133, CONT-134: zone boundaries ------------------------------------------------

    /**
     * Every state of both airlocks is in {@code zas:zone_boundary} and seals the face it looks to,
     * and only that face is its boundary, open or closed (GEO-007, GEO-044).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_133_every_airlock_state_has_one_boundary_face(GameTestHelper h) {
        for (Block block : List.of(AtmosBlocks.AIRLOCK.get(), AtmosBlocks.LARGE_AIRLOCK.get())) {
            h.assertTrue(block.defaultBlockState().is(Geometries.ZONE_BOUNDARY), block + " isn't in zas:zone_boundary");
            h.assertFalse(block.defaultBlockState().is(BlockTags.DOORS), block + " is a vanilla door");
            for (BlockState st : block.getStateDefinition().getPossibleStates()) {
                Face facing = Face.valueOf(st.getValue(AirlockBlock.FACING).name());
                Geometry g = Geometries.table().geometry(st);
                h.assertValueEqual(
                        g.flags() & Geometry.FLAG_ZONE_BOUNDARY,
                        Geometry.boundaryFlag(facing),
                        "the boundary of " + st);
                h.assertValueEqual(g.isFaceFull(facing), !st.getValue(AirlockBlock.OPEN), "the leaf face of " + st);
                h.assertValueEqual(g.cellCount(), 1, "the cells of " + st);
            }
        }
        h.succeed();
    }

    /**
     * Between A and B, a 1 by 2 airlock opened and closed by hand passes gas only while open, keeps
     * A and B two zones, with its cells in A, and no toggle changes a zone's cells (CONT-133,
     * CONT-135). Using either block moves both.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_133_an_airlock_passes_gas_only_while_open(GameTestHelper h) {
        Site s = new Site(h, 1330);
        run(h, s, () -> {
            rooms(s);
            AirlockBlockEntity a = airlock(s, 4, 2, Direction.EAST, false);
            seedRooms(s);
            s.cycle(a);
            h.assertValueEqual(s.zone(2, 2, 2).cellCount(), 29, "A's cells, with the airlock's two");
            h.assertValueEqual(s.zone(6, 2, 2).cellCount(), 27, "B's cells");
            s.addKpa(6, 2, 2, N2, 50);
            double a0 = s.zone(2, 2, 2).mixture().pressure();
            double b0 = s.zone(6, 2, 2).mixture().pressure();
            s.cycles(3, a);
            s.close(s.zone(2, 2, 2).mixture().pressure(), a0, 0.05, "A behind the closed airlock");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            use(s, player, 4, 2, 2); // the upper block
            h.assertTrue(open(s, 4, 2), "the hand didn't open it");
            h.assertTrue(at(s, 4, 2, 2).getValue(AirlockBlock.OPEN), "the upper block stayed closed");
            s.cycles(3, a);
            // B's side: opening widens the airlock's cells, which are A's, so A's pressure dips first.
            h.assertTrue(s.zone(6, 2, 2).mixture().pressure() < b0 - 1, "no gas went through the open airlock");
            h.assertFalse(s.zone(2, 2, 2).id().equals(s.zone(6, 2, 2).id()), "the open airlock joined A and B");
            h.assertValueEqual(s.zone(2, 2, 2).cellCount(), 29, "A's cells once open");

            use(s, player, 4, 1, 2);
            h.assertFalse(open(s, 4, 2), "the hand didn't close it");
            s.cycle(a);
            s.addKpa(6, 2, 2, N2, 20);
            double a1 = s.zone(2, 2, 2).mixture().pressure();
            s.cycles(3, a);
            s.close(s.zone(2, 2, 2).mixture().pressure(), a1, 0.05, "A behind the airlock closed again");
            h.assertValueEqual(s.zone(6, 2, 2).cellCount(), 27, "B's cells once closed");
            player.discard();
        });
    }

    /**
     * The large airlock places only where its four spaces are free and it stands on sturdy blocks,
     * keeps A and B two zones while it passes gas open, and breaking any of its blocks removes all
     * four and drops one large airlock (CONT-134).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_134_the_large_airlock_takes_four_spaces_and_drops_one_item(GameTestHelper h) {
        Site s = new Site(h, 1331);
        run(h, s, () -> {
            rooms(s);
            seedRooms(s);
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    s.set(4, y, z, Site.AIR);
                }
            }
            s.set(4, 2, 3, Site.STONE); // one space taken
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(-90); // looking east
            BlockItem item = (BlockItem) AtmosItems.LARGE_AIRLOCK.get();
            BlockPos origin = s.abs(4, 1, 2);
            BlockHitResult click = new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin, false);
            InteractionResult refused = item.place(
                    new BlockPlaceContext(s.level, player, InteractionHand.MAIN_HAND, new ItemStack(item), click));
            h.assertFalse(refused.consumesAction(), "placed with a space taken");
            h.assertTrue(at(s, 4, 1, 2).isAir(), "a block was placed with a space taken");

            s.set(4, 2, 3, Site.AIR);
            ItemStack stack = new ItemStack(item);
            h.assertTrue(
                    item.place(new BlockPlaceContext(s.level, player, InteractionHand.MAIN_HAND, stack, click))
                            .consumesAction(),
                    "it didn't place in four free spaces");
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    BlockState st = at(s, 4, y, z);
                    h.assertTrue(st.is(AtmosBlocks.LARGE_AIRLOCK.get()), "no large airlock at y " + y + ", z " + z);
                    h.assertValueEqual(st.getValue(AirlockBlock.FACING), Direction.EAST, "its facing");
                    h.assertValueEqual(
                            st.getValue(AirlockBlock.SIDE), z == 2 ? ConsoleSide.LEFT : ConsoleSide.RIGHT, "its side");
                }
            }
            AirlockBlockEntity a = s.entity(4, 1, 2, AirlockBlockEntity.class);
            s.cycle(a);
            h.assertValueEqual(s.zone(2, 2, 2).cellCount(), 31, "A's cells, with the airlock's four");
            s.addKpa(6, 2, 2, N2, 50);
            double a0 = s.zone(2, 2, 2).mixture().pressure();
            double b0 = s.zone(6, 2, 2).mixture().pressure();
            s.cycles(3, a);
            s.close(s.zone(2, 2, 2).mixture().pressure(), a0, 0.05, "A behind the closed large airlock");
            use(s, player, 4, 2, 3); // the upper right block
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    h.assertTrue(at(s, 4, y, z).getValue(AirlockBlock.OPEN), "y " + y + ", z " + z + " stayed closed");
                }
            }
            s.cycles(3, a);
            h.assertTrue(s.zone(6, 2, 2).mixture().pressure() < b0 - 1, "no gas went through the open large airlock");
            h.assertFalse(s.zone(2, 2, 2).id().equals(s.zone(6, 2, 2).id()), "the open large airlock joined A and B");

            s.level.destroyBlock(s.abs(4, 2, 3), true);
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    h.assertTrue(at(s, 4, y, z).isAir(), "y " + y + ", z " + z + " stayed after a break");
                }
            }
            h.assertValueEqual(drops(s), Map.of(item, 1), "the drops");
            player.discard();
        });
    }

    // ---- CONT-135: closing itself, in real time -----------------------------------------------

    /** Runs a timed test's step, clearing up if it fails. */
    private static void step(Runnable cleanup, Runnable checks) {
        try {
            checks.run();
        } catch (RuntimeException | Error e) {
            cleanup.run();
            throw e;
        }
    }

    /**
     * An airlock opened by hand closes itself 5 s later; a redstone signal holds it open, and it
     * closes 5 s after the signal ends; and it waits while something stands in its doorway (CONT-135,
     * changed 2026-11-07 by the owner). The airlock stands alone in a wall.
     */
    @GameTest(template = "empty", batch = TIMED, timeoutTicks = 700)
    public static void cont_135_an_airlock_closes_itself_5_s_after_it_opens_or_its_signal_ends(GameTestHelper h) {
        Site s = new Site(h, 1341);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        s.level.setChunkForced(cx, cz, true);
        Runnable cleanup = () -> {
            BatteryTests.clear(s);
            s.level.setChunkForced(cx, cz, false);
        };
        int wait = AirlockBlock.AUTO_CLOSE_TICKS;
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        ArmorStand[] stand = new ArmorStand[1];
        step(cleanup, () -> {
            rooms(s);
            airlock(s, 4, 2, Direction.EAST, false);
            use(s, player, 4, 1, 2);
            h.assertTrue(open(s, 4, 2), "the hand didn't open it");
        });
        h.startSequence()
                .thenExecuteAfter(wait - 5, () -> step(cleanup, () -> h.assertTrue(open(s, 4, 2), "closed early")))
                .thenExecuteAfter(
                        10,
                        () -> step(cleanup, () -> {
                            h.assertFalse(open(s, 4, 2), "still open 5 s after the hand opened it");
                            s.set(4, 1, 1, Blocks.REDSTONE_BLOCK.defaultBlockState()); // in the wall beside it
                            h.assertTrue(open(s, 4, 2), "a redstone signal didn't open it");
                        }))
                .thenExecuteAfter(
                        wait + 10,
                        () -> step(cleanup, () -> {
                            h.assertTrue(open(s, 4, 2), "the signal didn't hold it open");
                            s.set(4, 1, 1, Site.STONE);
                            h.assertFalse(at(s, 4, 1, 2).getValue(AirlockBlock.POWERED), "still powered");
                            h.assertTrue(open(s, 4, 2), "it closed as the signal ended");
                        }))
                .thenExecuteAfter(
                        wait - 5,
                        () -> step(cleanup, () -> h.assertTrue(open(s, 4, 2), "closed early after the signal")))
                .thenExecuteAfter(
                        10,
                        () -> step(cleanup, () -> {
                            h.assertFalse(open(s, 4, 2), "still open 5 s after the signal ended");
                            use(s, player, 4, 1, 2);
                            ArmorStand a = EntityType.ARMOR_STAND.create(s.level);
                            a.moveTo(Vec3.atBottomCenterOf(s.abs(4, 1, 2)));
                            s.level.addFreshEntity(a);
                            stand[0] = a;
                        }))
                .thenExecuteAfter(
                        wait + 10,
                        () -> step(cleanup, () -> {
                            h.assertTrue(open(s, 4, 2), "it closed on what stood in its doorway");
                            stand[0].discard();
                        }))
                .thenExecuteAfter(AirlockBlock.DOORWAY_WAIT_TICKS + 5, () -> {
                    try {
                        h.assertFalse(open(s, 4, 2), "still open once its doorway cleared");
                    } finally {
                        player.discard();
                        cleanup.run();
                    }
                })
                .thenSucceed();
    }

    // ---- CONT-136: bolts ----------------------------------------------------------------------

    /**
     * A bolted airlock of either size shows its bolted state and stays closed against the hand, the
     * crowbar and a redstone signal (CONT-136). Unlinked, the hand is told it's bolted.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_136_a_bolted_airlock_stays_shut(GameTestHelper h) {
        Site s = new Site(h, 1332);
        run(h, s, () -> {
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            for (boolean large : new boolean[] {false, true}) {
                rooms(s);
                AirlockBlockEntity a = large ? largeAirlock(s, false) : airlock(s, 4, 2, Direction.EAST, false);
                String what = large ? "the large airlock" : "the airlock";
                AirlockBlock.setBolted(s.level, a.getBlockPos(), true);
                h.assertTrue(bolted(s, 4, 2), what + " isn't bolted");
                h.assertTrue(at(s, 4, 2, 2).getValue(AirlockBlock.BOLTED), what + "'s upper block isn't bolted");
                use(s, player, 4, 2, 2);
                h.assertFalse(open(s, 4, 2), what + " opened by hand");
                useItem(s, player, new ItemStack(AtmosItems.CROWBAR.get()), 4, 1, 2);
                h.assertFalse(open(s, 4, 2), what + " opened with the crowbar");
                s.set(4, 1, 1, Blocks.REDSTONE_BLOCK.defaultBlockState());
                h.assertTrue(at(s, 4, 1, 2).getValue(AirlockBlock.POWERED), what + " didn't see the signal");
                h.assertFalse(open(s, 4, 2), what + " opened with a redstone signal");
                h.assertTrue(bolted(s, 4, 2), what + " came unbolted");

                AirlockBlock.setBolted(s.level, a.getBlockPos(), false);
                use(s, player, 4, 1, 2);
                h.assertTrue(open(s, 4, 2), what + " unbolted didn't open by hand");
                AirlockBlock.setBolted(s.level, a.getBlockPos(), true);
                h.assertFalse(open(s, 4, 2), "bolting " + what + " didn't close it");
                BatteryTests.clear(s);
            }
            player.discard();
        });
    }

    // ---- CONT-137: firelocks -------------------------------------------------------------------

    /**
     * A firelock used on an open airlock fits to it, open. A controller in A at danger 2 closes it,
     * which closes the airlock and seals the doorway; while it's shut the airlock doesn't open. The
     * hand opens a shut firelock, leaving the airlock closed, but not one locked down, which the
     * crowbar forces. Breaking the airlock drops it and the firelock (CONT-137).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_137_a_firelock_on_an_airlock_closes_it_and_seals_the_doorway(GameTestHelper h) {
        Site s = new Site(h, 1333);
        run(h, s, () -> {
            rooms(s);
            AirlockBlockEntity a = airlock(s, 4, 2, Direction.EAST, true);
            seedRooms(s);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            s.cycles(2, c, a);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack firelock = new ItemStack(AtmosItems.FIRELOCK.get());
            h.assertTrue(useItem(s, player, firelock, 4, 2, 2).consumesAction(), "the firelock didn't fit");
            h.assertTrue(firelock.isEmpty(), "fitting didn't use the firelock");
            h.assertValueEqual(at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK), AirlockFirelock.OPEN, "the firelock");
            h.assertValueEqual(at(s, 4, 2, 2).getValue(AirlockBlock.FIRELOCK), AirlockFirelock.OPEN, "the upper's");
            h.assertTrue(open(s, 4, 2), "fitting closed the airlock");
            s.cycles(2, c, a);
            h.assertTrue(open(s, 4, 2), "the airlock closed with A safe");

            s.addKpa(2, 2, 2, CO2, 20);
            s.addKpa(6, 2, 2, CO2, 20); // both sides alike, so it isn't locked down
            s.cycle(c, a);
            h.assertValueEqual(c.danger(), 2, "A's danger");
            h.assertValueEqual(at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK), AirlockFirelock.CLOSED, "the firelock");
            h.assertFalse(open(s, 4, 2), "the firelock closed but the airlock stayed open");
            h.assertTrue(Geometries.table().geometry(at(s, 4, 1, 2)).isFaceFull(Face.EAST), "the doorway isn't sealed");
            use(s, player, 4, 1, 2);
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK), AirlockFirelock.OPEN, "the hand didn't open it");
            h.assertFalse(open(s, 4, 2), "opening the firelock opened the airlock");
            for (int i = 0; i < 5; i++) {
                s.cycle(c, a);
            }
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK),
                    AirlockFirelock.CLOSED,
                    "not closed again after 5 s");
            s.set(4, 1, 1, Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertFalse(open(s, 4, 2), "a redstone signal opened it through the shut firelock");
            s.set(4, 1, 1, Site.STONE);

            s.addKpa(2, 2, 2, N2, 40); // a 40 kPa difference
            s.cycle(c, a);
            h.assertValueEqual(at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK), AirlockFirelock.LOCKED, "the lockdown");
            use(s, player, 4, 1, 2);
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK), AirlockFirelock.LOCKED, "the hand opened it");
            useItem(s, player, new ItemStack(AtmosItems.CROWBAR.get()), 4, 2, 2);
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(AirlockBlock.FIRELOCK),
                    AirlockFirelock.OPEN,
                    "the crowbar didn't force it");
            h.assertFalse(open(s, 4, 2), "forcing the firelock opened the airlock");

            s.level.destroyBlock(s.abs(4, 2, 2), true);
            h.assertTrue(at(s, 4, 1, 2).isAir(), "the airlock stayed after a break");
            h.assertValueEqual(
                    drops(s),
                    Map.of(AtmosItems.AIRLOCK.get(), 1, AtmosItems.FIRELOCK.get(), 1),
                    "the drops of an airlock with a firelock");
            player.discard();
        });
    }

    /**
     * A firelock fits the large airlock too, over all four blocks; an alarm closes it and the
     * airlock, and breaking the airlock drops both (CONT-137).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_137_a_firelock_on_the_large_airlock(GameTestHelper h) {
        Site s = new Site(h, 1334);
        run(h, s, () -> {
            rooms(s);
            AirlockBlockEntity a = largeAirlock(s, true);
            seedRooms(s);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            s.cycles(2, c, a);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            useItem(s, player, new ItemStack(AtmosItems.FIRELOCK.get()), 4, 2, 3);
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    h.assertValueEqual(
                            at(s, 4, y, z).getValue(AirlockBlock.FIRELOCK),
                            AirlockFirelock.OPEN,
                            "y " + y + ", z " + z);
                }
            }
            s.addKpa(2, 2, 2, CO2, 20);
            s.addKpa(6, 2, 2, CO2, 20);
            s.cycle(c, a);
            for (int y = 1; y <= 2; y++) {
                for (int z = 2; z <= 3; z++) {
                    h.assertValueEqual(
                            at(s, 4, y, z).getValue(AirlockBlock.FIRELOCK),
                            AirlockFirelock.CLOSED,
                            "the alarm, at y " + y + ", z " + z);
                    h.assertFalse(at(s, 4, y, z).getValue(AirlockBlock.OPEN), "open at y " + y + ", z " + z);
                }
            }
            s.level.destroyBlock(s.abs(4, 1, 3), true);
            h.assertValueEqual(
                    drops(s),
                    Map.of(AtmosItems.LARGE_AIRLOCK.get(), 1, AtmosItems.FIRELOCK.get(), 1),
                    "the drops of a large airlock with a firelock");
            player.discard();
        });
    }

    /**
     * A firelock used on a vanilla door replaces it with a door with a firelock, which keeps the
     * door, opens as it did, and has its geometry in every state. An alarm closes the firelock and
     * the door; the door stays closed when the firelock reopens, and doesn't open while the firelock
     * is shut. An iron door doesn't open by hand. Breaking it drops the door and the firelock
     * (CONT-137, changed 2026-11-07 by the owner).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_137_a_firelock_on_a_vanilla_door(GameTestHelper h) {
        Site s = new Site(h, 1335);
        run(h, s, () -> {
            Block firelockDoor = AtmosBlocks.FIRELOCK_DOOR.get();
            h.assertTrue(firelockDoor.defaultBlockState().is(Geometries.ZONE_BOUNDARY), "not a zone boundary");
            for (BlockState st : firelockDoor.getStateDefinition().getPossibleStates()) {
                BlockState oak = FirelockDoorBlock.doorState(st, Blocks.OAK_DOOR);
                h.assertTrue(
                        geometryLike(
                                Geometries.table().geometry(st),
                                Geometries.table().geometry(oak)),
                        st + " isn't shaped as " + oak);
            }

            rooms(s);
            BlockState door = Blocks.OAK_DOOR
                    .defaultBlockState()
                    .setValue(DoorBlock.FACING, Direction.EAST)
                    .setValue(DoorBlock.OPEN, true);
            s.set(4, 1, 2, door);
            s.set(4, 2, 2, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            seedRooms(s);
            s.set(2, 2, 0, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity c = s.controller(2);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack firelock = new ItemStack(AtmosItems.FIRELOCK.get());
            h.assertTrue(useItem(s, player, firelock, 4, 2, 2).consumesAction(), "the firelock didn't fit the door");
            h.assertTrue(firelock.isEmpty(), "fitting didn't use the firelock");
            for (int y = 1; y <= 2; y++) {
                h.assertTrue(at(s, 4, y, 2).is(firelockDoor), "no door with a firelock at y " + y);
                h.assertValueEqual(
                        s.entity(4, y, 2, FirelockDoorBlockEntity.class).door(),
                        Blocks.OAK_DOOR,
                        "the door kept at y " + y);
            }
            h.assertTrue(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "fitting closed the door");
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(FirelockDoorBlock.FIRELOCK), AirlockFirelock.OPEN, "the firelock");
            FirelockDoorBlockEntity f = s.entity(4, 1, 2, FirelockDoorBlockEntity.class);
            use(s, player, 4, 1, 2);
            h.assertFalse(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "the hand didn't close the oak door");
            use(s, player, 4, 2, 2);
            h.assertTrue(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "the hand didn't open the oak door");
            s.cycles(2, c, f);

            s.addKpa(2, 2, 2, CO2, 20);
            s.addKpa(6, 2, 2, CO2, 20);
            s.cycle(c, f);
            h.assertValueEqual(c.danger(), 2, "A's danger");
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(FirelockDoorBlock.FIRELOCK),
                    AirlockFirelock.CLOSED,
                    "the alarm's firelock");
            h.assertFalse(at(s, 4, 2, 2).getValue(DoorBlock.OPEN), "the firelock closed but the door stayed open");
            s.set(4, 1, 1, Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertFalse(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "a redstone signal opened it through the firelock");
            s.set(4, 1, 1, Site.STONE);
            use(s, player, 4, 1, 2);
            h.assertValueEqual(
                    at(s, 4, 1, 2).getValue(FirelockDoorBlock.FIRELOCK),
                    AirlockFirelock.OPEN,
                    "the hand didn't open it");
            h.assertFalse(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "opening the firelock opened the door");
            use(s, player, 4, 1, 2);
            h.assertTrue(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "the door didn't open once the firelock was");

            s.level.destroyBlock(s.abs(4, 2, 2), true);
            h.assertTrue(at(s, 4, 1, 2).isAir(), "the lower half stayed after a break");
            h.assertValueEqual(
                    drops(s),
                    Map.of(Items.OAK_DOOR, 1, AtmosItems.FIRELOCK.get(), 1),
                    "the drops of a door with a firelock");
            BatteryTests.clear(s);

            // An iron door with a firelock opens by redstone only.
            rooms(s);
            BlockState iron = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
            s.set(4, 1, 2, iron);
            s.set(4, 2, 2, iron.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            useItem(s, player, new ItemStack(AtmosItems.FIRELOCK.get()), 4, 1, 2);
            h.assertTrue(at(s, 4, 1, 2).is(firelockDoor), "the firelock didn't fit the iron door");
            h.assertValueEqual(use(s, player, 4, 1, 2), InteractionResult.PASS, "the hand on an iron door");
            h.assertFalse(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "the hand opened an iron door");
            s.set(4, 1, 1, Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(at(s, 4, 1, 2).getValue(DoorBlock.OPEN), "redstone didn't open the iron door");
            player.discard();
        });
    }

    // ---- CONT-138, CONT-141: links -------------------------------------------------------------

    /**
     * The multitool selects an airlock controller and links two airlocks, from either block, one
     * sensor and two vents; it refuses a third airlock and a second sensor, and using it on a linked
     * part again unlinks it, unbolting an airlock. Sneaking, it links the airlock controller to the
     * selected area air controller instead (CONT-141). Removing the controller unbolts its airlocks
     * (CONT-138).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_138_the_multitool_links_two_airlocks_one_sensor_and_vents(GameTestHelper h) {
        Site s = new Site(h, 1336);
        run(h, s, () -> {
            Lock lock = lock(s, true, true);
            AirlockControllerBlockEntity ac = lock.controller();
            for (BlockPos p : List.of(
                    lock.inner().getBlockPos(),
                    lock.outer().getBlockPos(),
                    lock.sensor().getBlockPos(),
                    lock.vents().get(0).getBlockPos(),
                    lock.vents().get(1).getBlockPos())) {
                ac.toggleLink(s.level, p); // unlink what lock() linked
            }
            h.assertTrue(ac.airlocks()[0] == null && ac.airlocks()[1] == null && ac.sensor() == null, "links left");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
            useItem(s, player, tool, 3, 2, 1);
            h.assertValueEqual(MultitoolItem.selected(tool, s.level).orElse(null), ac.getBlockPos(), "the selection");
            useItem(s, player, tool, 4, 2, 2); // the inner airlock's upper block
            useItem(s, player, tool, 6, 1, 2);
            h.assertValueEqual(ac.airlocks()[0], lock.inner().getBlockPos(), "airlock 1");
            h.assertValueEqual(ac.airlocks()[1], lock.outer().getBlockPos(), "airlock 2");
            useItem(s, player, tool, 5, 2, 2);
            h.assertValueEqual(ac.sensor(), lock.sensor().getBlockPos(), "the sensor");
            useItem(s, player, tool, 5, 0, 2);
            useItem(s, player, tool, 5, 4, 2);
            h.assertValueEqual(ac.linkedVents().size(), 2, "the vents linked");

            AirlockBlockEntity third = airlock(s, 10, 2, Direction.EAST, false);
            h.assertValueEqual(
                    key(MultitoolItem.link(tool, s.level, third.getBlockPos())),
                    "item.zas_atmos.multitool.airlocks_full",
                    "a third airlock");
            AirSensorBlockEntity second = s.place(
                    8,
                    2,
                    1,
                    AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH),
                    AirSensorBlockEntity.class);
            h.assertValueEqual(
                    key(MultitoolItem.link(tool, s.level, second.getBlockPos())),
                    "item.zas_atmos.multitool.sensor_full",
                    "a second sensor");
            h.assertValueEqual(
                    ac.toggleLink(s.level, s.abs(8, 0, 2)),
                    AirlockControllerBlockEntity.LinkResult.NOT_LINKABLE,
                    "linking stone");

            AirlockBlock.setBolted(s.level, lock.inner().getBlockPos(), true);
            h.assertValueEqual(
                    key(MultitoolItem.link(tool, s.level, lock.inner().getBlockPos())),
                    "item.zas_atmos.multitool.unlinked_airlock",
                    "using it on a linked airlock");
            h.assertTrue(ac.airlocks()[0] == null, "airlock 1 is still linked");
            h.assertFalse(bolted(s, 4, 2), "unlinking didn't unbolt the airlock");
            h.assertValueEqual(
                    key(MultitoolItem.link(tool, s.level, third.getBlockPos())),
                    "item.zas_atmos.multitool.linked_airlock",
                    "a second airlock once one was unlinked");

            // Sneaking, with an area air controller selected: the airlock controller is its part.
            ItemStack areaTool = new ItemStack(AtmosItems.MULTITOOL.get());
            MultitoolItem.select(areaTool, s.level, lock.area().getBlockPos());
            player.setShiftKeyDown(true);
            useItem(s, player, areaTool, 3, 2, 1);
            player.setShiftKeyDown(false);
            h.assertTrue(
                    lock.area().links().contains(ac.getBlockPos()), "the airlock controller isn't linked as a part");

            AirlockBlock.setBolted(s.level, lock.outer().getBlockPos(), true);
            AirlockBlock.setBolted(s.level, third.getBlockPos(), true);
            s.set(3, 2, 1, Site.AIR);
            h.assertFalse(bolted(s, 6, 2), "removing the controller left airlock 2 bolted");
            h.assertFalse(bolted(s, 10, 2), "removing the controller left the third airlock bolted");
            player.discard();
        });
    }

    // ---- CONT-139, CONT-140, CONT-142: a cycle --------------------------------------------------

    /**
     * In a vacuum, a chamber at 120 kPa cycles out to space and back into A at 100 kPa. Each cycle
     * bolts both airlocks, runs both vents, powered by the airlock controller, siphoning out and then
     * blowing back the gas they kept, opens the far airlock only once the chamber is within 5.07 kPa
     * of the far side, and leaves the other bolted (CONT-139, CONT-141). The sensor shows cycling
     * (CONT-142). The second cycle is asked for by using the bolted inner airlock, and using the
     * other during it changes nothing (CONT-140).
     */
    @GameTest(template = "empty", batch = "zas_atmos_airlock_vacuum")
    public static void cont_139_a_chamber_cycles_out_to_a_vacuum_and_back_to_1_atm(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, 1337);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            run(h, s, () -> {
                Lock lock = lock(s, false, true);
                AirlockControllerBlockEntity ac = lock.controller();
                fill(s, 5, 2, 2, 120, 0);
                s.cycle(lock.devices());
                for (VentBlockEntity v : lock.vents()) {
                    h.assertValueEqual(v.settings().mode(), VentMode.OFF, "a vent between cycles");
                    h.assertTrue(v.airlockControlled(), "a vent isn't the airlock controller's");
                }
                h.assertValueEqual(
                        lock.sensor().getBlockState().getValue(AirSensorBlock.STATUS),
                        SensorStatus.STANDBY,
                        "the sensor");

                h.assertTrue(ac.requestCycle(lock.outer().getBlockPos()), "the controller refused a cycle out");
                h.assertTrue(bolted(s, 4, 2) && bolted(s, 6, 2), "both airlocks aren't bolted");
                h.assertTrue(
                        ac.getBlockState().getValue(AirlockControllerBlock.CYCLING),
                        "the controller doesn't show cycling");
                cycle(h, s, lock, 1, VentMode.SIPHON);
                h.assertTrue(open(s, 6, 2) && !bolted(s, 6, 2), "the outer airlock isn't open and unbolted");
                h.assertTrue(bolted(s, 4, 2) && !open(s, 4, 2), "the inner airlock isn't bolted shut");

                Player player = h.makeMockPlayer(GameType.SURVIVAL);
                use(s, player, 4, 2, 2); // the bolted inner airlock, from A
                h.assertValueEqual(ac.cycling(), 0, "using the bolted inner airlock didn't cycle toward it");
                h.assertTrue(bolted(s, 6, 2) && !open(s, 6, 2), "the outer airlock isn't bolted shut again");
                s.cycle(lock.devices());
                use(s, player, 6, 1, 2); // the outer one, during the cycle
                h.assertValueEqual(ac.cycling(), 0, "a use during the cycle changed it");
                cycle(h, s, lock, 0, VentMode.BLOW);
                h.assertTrue(open(s, 4, 2) && !bolted(s, 4, 2), "the inner airlock isn't open and unbolted");
                h.assertTrue(bolted(s, 6, 2) && !open(s, 6, 2), "the outer airlock isn't bolted shut");
                player.discard();
            });
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
    }

    /**
     * Runs a cycle toward airlock {@code toward} to its end, checking at each step that the vents do
     * {@code mode}, powered by the controller, that the sensor shows cycling, and that the airlock
     * opens only on a reading within 5.07 kPa of the pressure beyond it.
     */
    private static void cycle(GameTestHelper h, Site s, Lock lock, int toward, VentMode mode) {
        AirlockControllerBlockEntity ac = lock.controller();
        int x = toward == 0 ? 4 : 6;
        for (int i = 0; i < 200 && ac.cycling() >= 0; i++) {
            s.cycle(lock.devices());
            if (ac.cycling() >= 0) {
                h.assertFalse(open(s, x, 2), "the airlock opened during the cycle");
                if (ac.cycleStep() != null) {
                    for (VentBlockEntity v : lock.vents()) {
                        h.assertValueEqual(v.settings().mode(), mode, "a vent during the cycle");
                        h.assertTrue(v.power().source() == ac, "a vent isn't powered by the airlock controller");
                    }
                }
                h.assertValueEqual(
                        lock.sensor().getBlockState().getValue(AirSensorBlock.STATUS),
                        SensorStatus.CYCLING,
                        "the sensor during the cycle");
            }
        }
        h.assertValueEqual(ac.cycling(), -1, "the cycle didn't end in 200 s");
        double target = ac.beyondKpa(toward);
        h.assertTrue(
                Math.abs(ac.chamberKpa() - target) <= AirlockCycle.TOLERANCE_KPA,
                "the airlock opened on " + ac.chamberKpa() + " kPa, cycling toward " + target);
    }

    // ---- CONT-152: vents under an airlock controller --------------------------------------------

    /**
     * An area air controller in the chamber, in Siphon, orders the chamber's vent each step, but the
     * vent follows the airlock controller alone: off between cycles, blowing during a cycle toward
     * the fuller side, and off again once it's stopped (CONT-152, CONT-153).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_152_a_linked_vent_ignores_an_area_air_controller(GameTestHelper h) {
        Site s = new Site(h, 1338);
        run(h, s, () -> {
            Lock lock = lock(s, true, false);
            s.set(5, 3, 1, TestParts.battery().defaultBlockState());
            AreaAirControllerBlockEntity chamber = s.place(
                    5,
                    3,
                    2,
                    AtmosBlocks.AREA_AIR_CONTROLLER
                            .get()
                            .defaultBlockState()
                            .setValue(
                                    io.github.west3436.zas.atmos.controller.AreaAirControllerBlock.FACING,
                                    Direction.SOUTH),
                    AreaAirControllerBlockEntity.class);
            chamber.setMode(ControllerMode.SIPHON);
            VentBlockEntity vent = lock.vents().get(0);
            fill(s, 8, 2, 2, 79, 21);
            fill(s, 5, 2, 2, 30, 0);
            for (int i = 0; i < 3; i++) {
                chamber.step();
                s.cycle(lock.devices());
                chamber.step();
                h.assertValueEqual(vent.settings().mode(), VentMode.OFF, "the vent between cycles, step " + i);
            }
            AirlockControllerMenu menu = new AirlockControllerMenu(
                    0, lock.controller(), lock.controller().getBlockPos());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            h.assertTrue(menu.clickMenuButton(player, AirlockControllerMenu.CYCLE + 1), "the window's cycle toward B");
            for (int i = 0; i < 3; i++) {
                chamber.step();
                s.cycle(lock.devices());
                chamber.step();
                h.assertValueEqual(vent.settings().mode(), VentMode.BLOW, "the vent during the cycle, step " + i);
            }
            h.assertTrue(menu.clickMenuButton(player, AirlockControllerMenu.STOP), "the window's stop");
            chamber.step();
            s.cycle(lock.devices());
            chamber.step();
            h.assertValueEqual(vent.settings().mode(), VentMode.OFF, "the vent once the cycle stopped");
            h.assertTrue(bolted(s, 4, 2) && bolted(s, 6, 2), "stopping unbolted an airlock");
            player.discard();
        });
    }

    // ---- CONT-153: the window --------------------------------------------------------------------

    /**
     * The window's buttons go through the server: Cycle toward either airlock starts a cycle, a
     * second while one runs is refused, Stop ends it, leaving both bolted, and Stop while idle, an
     * unknown button, and a cycle with an airlock unlinked are refused. Its data shows what the
     * controller has (CONT-153, CONT-074).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_153_the_window_starts_and_stops_cycles_through_the_server(GameTestHelper h) {
        Site s = new Site(h, 1339);
        run(h, s, () -> {
            Lock lock = lock(s, true, false);
            AirlockControllerBlockEntity ac = lock.controller();
            AirlockControllerMenu menu = new AirlockControllerMenu(0, ac, ac.getBlockPos());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            h.assertValueEqual(menu.value(AirlockControllerMenu.DATA_LINKS), 7, "the links shown");
            h.assertValueEqual(menu.value(AirlockControllerMenu.DATA_POWERED), 1, "the power shown");
            h.assertValueEqual(menu.value(AirlockControllerMenu.DATA_VENTS), 1, "the vents shown");
            s.close(menu.pressure(AirlockControllerMenu.DATA_CHAMBER), ac.chamberKpa(), 0.01, "the chamber shown");
            s.close(menu.pressure(AirlockControllerMenu.DATA_BEYOND), ac.beyondKpa(0), 0.01, "A shown");
            h.assertTrue(ac.beyondKpa(0) > 95, "the pressure beyond the inner airlock isn't A's: " + ac.beyondKpa(0));

            h.assertFalse(menu.clickMenuButton(player, AirlockControllerMenu.STOP), "stop while idle");
            h.assertTrue(menu.clickMenuButton(player, AirlockControllerMenu.CYCLE), "cycle toward airlock 1");
            h.assertValueEqual(ac.cycling(), 0, "cycling toward");
            h.assertValueEqual(menu.value(AirlockControllerMenu.DATA_CYCLING), 1, "the cycle shown");
            h.assertValueEqual(menu.value(AirlockControllerMenu.DATA_DOORS), 0b1010, "both airlocks shown bolted");
            h.assertFalse(menu.clickMenuButton(player, AirlockControllerMenu.CYCLE + 1), "a second cycle");
            h.assertValueEqual(ac.cycling(), 0, "a second cycle changed the first");
            h.assertTrue(menu.clickMenuButton(player, AirlockControllerMenu.STOP), "stop");
            h.assertValueEqual(ac.cycling(), -1, "stopped");
            h.assertTrue(bolted(s, 4, 2) && bolted(s, 6, 2), "stopping unbolted an airlock");
            h.assertFalse(menu.clickMenuButton(player, 7), "an unknown button");
            ac.toggleLink(s.level, lock.outer().getBlockPos());
            h.assertFalse(
                    menu.clickMenuButton(player, AirlockControllerMenu.CYCLE), "a cycle with an airlock unlinked");
            h.assertFalse(menu.clickMenuButton(player, AirlockControllerMenu.CYCLE + 1), "a cycle toward no airlock");
            h.assertValueEqual(ac.cycling(), -1, "a refused button started a cycle");
            player.discard();
        });
    }

    // ---- CONT-142: the sensor ----------------------------------------------------------------------

    /**
     * An air sensor alone in a room shows standby from 81 to 122 kPa and alert below and above,
     * and cycling over alert while a controller marks it (CONT-142).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_142_the_sensor_shows_standby_alert_and_cycling(GameTestHelper h) {
        Site s = new Site(h, 1340);
        run(h, s, () -> {
            s.clear();
            s.room(0, 4);
            AirSensorBlockEntity sensor = s.place(
                    2,
                    2,
                    1,
                    AtmosBlocks.AIR_SENSOR.get().defaultBlockState().setValue(AirSensorBlock.FACING, Direction.SOUTH),
                    AirSensorBlockEntity.class);
            FirelockTests.seed(s, 2);
            double[][] cases = {{100, 0}, {82, 0}, {80, 0}, {121, 0}, {123, 0}, {0.1, 0}};
            SensorStatus[] expected = {
                SensorStatus.STANDBY,
                SensorStatus.STANDBY,
                SensorStatus.ALERT,
                SensorStatus.STANDBY,
                SensorStatus.ALERT,
                SensorStatus.ALERT
            };
            for (int i = 0; i < cases.length; i++) {
                fill(s, 2, 2, 2, cases[i][0], cases[i][1]);
                s.cycle(sensor);
                h.assertValueEqual(
                        sensor.getBlockState().getValue(AirSensorBlock.STATUS),
                        expected[i],
                        "at " + cases[i][0] + " kPa");
            }
            sensor.markCycling(s.level);
            s.cycle(sensor);
            h.assertValueEqual(
                    sensor.getBlockState().getValue(AirSensorBlock.STATUS), SensorStatus.CYCLING, "marked, at 0.1 kPa");
        });
    }

    // ---- CONT-141: power, in real time ---------------------------------------------------------------

    /**
     * An airlock controller is powered as a part of the area air controller in its room, and powers
     * its vent during a cycle. Once the area air controller's power is cut and the leases run out,
     * neither has power, the cycle waits with the vent off, and a new cycle is refused (CONT-141,
     * CONT-060).
     */
    @GameTest(template = "empty", batch = TIMED, timeoutTicks = 400)
    public static void cont_141_cutting_the_area_air_controllers_power_stops_the_airlock_controller(GameTestHelper h) {
        Site s = new Site(h, 1342);
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        s.level.setChunkForced(cx, cz, true);
        Runnable cleanup = () -> {
            BatteryTests.clear(s);
            s.level.setChunkForced(cx, cz, false);
        };
        Lock[] built = new Lock[1];
        step(cleanup, () -> {
            Lock lock = lock(s, true, false);
            built[0] = lock;
            fill(s, 5, 2, 2, 150, 0); // above B's 1 atm
            h.assertTrue(lock.controller().requestCycle(lock.outer().getBlockPos()), "the cycle was refused");
            s.cycles(2, lock.devices());
            VentBlockEntity vent = lock.vents().get(0);
            h.assertValueEqual(vent.settings().mode(), VentMode.SIPHON, "the vent during the cycle");
            h.assertTrue(vent.power().isPowered(), "the vent has no power");
            h.assertTrue(vent.power().source() == lock.controller(), "the airlock controller doesn't power the vent");
            s.set(2, 2, 0, Site.STONE);
            lock.area().buffer().set(0);
        });
        h.runAfterDelay(DevicePower.LEASE_TICKS + 2L * AtmosTicks.PERIOD, () -> {
            try {
                Lock lock = built[0];
                lock.area().step();
                lock.controller().step();
                VentBlockEntity vent = lock.vents().get(0);
                h.assertFalse(lock.area().powered(), "the area air controller has power");
                h.assertFalse(lock.controller().power().isPowered(), "the airlock controller's lease outlived it");
                h.assertFalse(vent.power().isPowered(), "the vent's lease outlived the airlock controller's power");
                h.assertValueEqual(lock.controller().cycling(), 1, "the cycle ended unpowered");
                h.assertValueEqual(vent.settings().mode(), VentMode.OFF, "the vent of an unpowered controller");
                h.assertTrue(lock.controller().stop(), "stop");
                h.assertFalse(lock.controller().requestCycle(1), "a cycle with no power");
            } finally {
                cleanup.run();
            }
            h.succeed();
        });
    }
}
