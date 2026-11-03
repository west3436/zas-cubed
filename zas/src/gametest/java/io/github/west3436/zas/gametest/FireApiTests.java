package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.HotspotTests.EVERYTHING;
import static io.github.west3436.zas.gametest.HotspotTests.empty;
import static io.github.west3436.zas.gametest.HotspotTests.gasFire;
import static io.github.west3436.zas.gametest.HotspotTests.hydrogenInAir;
import static io.github.west3436.zas.gametest.HotspotTests.hydrogenRoom;
import static io.github.west3436.zas.gametest.HotspotTests.room;
import static io.github.west3436.zas.gametest.HotspotTests.with;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.event.FireExtinguishedEvent;
import io.github.west3436.zas.api.event.FireIgnitedEvent;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.fire.Ignition;
import io.github.west3436.zas.api.fire.IgnitionOutcome;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fire API (API-031, API-055 to API-058): {@link Fires} and the fire events, as a mod calls
 * them, over the hotspots of {@link HotspotTests}' rooms.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireApiTests {
    /** A fire event as a listener saw it: the event, whether it came on the server thread, and the block at its hotspot then. */
    private record Seen(Object event, boolean serverThread, BlockState block) {}

    private static final List<Seen> EVENTS = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((FireIgnitedEvent e) -> EVENTS.add(
                new Seen(e, e.level().getServer().isSameThread(), e.level().getBlockState(e.hotspot()))));
        NeoForge.EVENT_BUS.addListener((FireExtinguishedEvent e) -> EVENTS.add(
                new Seen(e, e.level().getServer().isSameThread(), e.level().getBlockState(e.lastHotspot()))));
    }

    private FireApiTests() {}

    /** The test's own area, apart from every other test's (and from {@link HotspotTests}'). */
    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(12288 + 256 * (16 + index), 100, 12288));
    }

    private static Ignition ignite(Rooms r, BlockPos pos) {
        return r.settle(Fires.ignite(r.level, pos, Rooms.source("fire_api")));
    }

    private static List<Seen> events(Class<?> type, Rooms r, ZoneId zone) {
        return EVENTS.stream()
                .filter(s -> type.isInstance(s.event()))
                .filter(s -> s.event() instanceof FireIgnitedEvent e
                        ? e.level() == r.level && e.zone().equals(zone)
                        : ((FireExtinguishedEvent) s.event()).level() == r.level
                                && ((FireExtinguishedEvent) s.event()).zone().equals(zone))
                .toList();
    }

    // ---- API-031: fire events ----------------------------------------------------------

    /**
     * API-031: a zone catching fire fires a {@link FireIgnitedEvent} once, on the server thread,
     * when the atmos tick that sees its gas fire is applied; its going out fires a {@link FireExtinguishedEvent} naming its
     * last hotspot, once.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_031_fire_events_fire_on_ignition_and_going_out(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 0);
            ZoneId zone = hydrogenRoom(r, 0).id();
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            // The hotspot is pending until the next atmos tick sees its block; then the zone burns.
            h.assertValueEqual(events(FireIgnitedEvent.class, r, zone).size(), 0, "ignition events while pending");
            r.tick();
            List<Seen> lit = events(FireIgnitedEvent.class, r, zone);
            h.assertValueEqual(lit.size(), 1, "ignition events");
            h.assertValueEqual(((FireIgnitedEvent) lit.get(0).event()).hotspot(), middle, "its hotspot");
            h.assertTrue(lit.get(0).serverThread(), "not fired on the server thread");
            h.assertTrue(lit.get(0).block().is(HotspotBlocks.GAS_FIRE.get()), "fired before its gas fire was placed");
            r.ticks(3);
            h.assertValueEqual(events(FireIgnitedEvent.class, r, zone).size(), 1, "ignition events while it burns");
            h.assertValueEqual(events(FireExtinguishedEvent.class, r, zone).size(), 0, "out while it burns");
            empty(r, 0);
            r.tick();
            h.assertFalse(gasFire(r, middle), "the gas fire stayed without gas");
            List<Seen> out = events(FireExtinguishedEvent.class, r, zone);
            h.assertValueEqual(out.size(), 1, "going-out events");
            h.assertValueEqual(((FireExtinguishedEvent) out.get(0).event()).lastHotspot(), middle, "its last hotspot");
            h.assertTrue(out.get(0).serverThread(), "not fired on the server thread");
            h.assertTrue(out.get(0).block().isAir(), "fired before its gas fire was removed");
            r.tick();
            h.assertValueEqual(events(FireExtinguishedEvent.class, r, zone).size(), 1, "going-out events later");
        });
        h.succeed();
    }

    // ---- API-055: hotspot queries ------------------------------------------------------

    /**
     * API-055: the queries answer from the last applied atmos tick: a burning zone's hotspots in
     * ascending order of {@link BlockPos#asLong}, whether a zone burns, and whether a position is a
     * hotspot.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_055_hotspot_queries(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 1);
            ZoneId burning = hydrogenRoom(r, 0).id();
            ZoneId cold = hydrogenRoom(r, 6).id();
            BlockState fire = HotspotBlocks.GAS_FIRE.get().defaultBlockState();
            BlockPos a = r.abs(3, 2, 3);
            BlockPos b = r.abs(1, 1, 1);
            r.set(3, 2, 3, fire);
            r.set(1, 1, 1, fire);
            h.assertFalse(Fires.isBurning(r.level, burning), "burning before an atmos tick saw its blocks");
            r.tick();
            List<BlockPos> expected = a.asLong() < b.asLong() ? List.of(a, b) : List.of(b, a);
            h.assertValueEqual(Fires.hotspots(r.level, burning), expected, "the hotspots");
            h.assertTrue(Fires.isBurning(r.level, burning), "the zone isn't burning");
            h.assertTrue(Fires.isHotspot(r.level, a) && Fires.isHotspot(r.level, b), "a fire isn't a hotspot");
            h.assertFalse(Fires.isHotspot(r.level, r.abs(2, 2, 2)), "air is a hotspot");
            h.assertValueEqual(Fires.hotspots(r.level, cold), List.of(), "the other room's hotspots");
            h.assertFalse(Fires.isBurning(r.level, cold), "the other room burns");
            h.assertFalse(Fires.isBurning(r.level, new ZoneId(Long.MAX_VALUE)), "a zone that doesn't exist burns");
            boolean immutable;
            try {
                Fires.hotspots(r.level, burning).clear();
                immutable = false;
            } catch (UnsupportedOperationException expectedFailure) {
                immutable = true;
            }
            h.assertTrue(immutable, "the list can be changed");
            empty(r, 0);
            empty(r, 6);
            r.tick();
            h.assertFalse(Fires.isBurning(r.level, burning), "burning without gas");
        });
        h.succeed();
    }

    // ---- API-056: ignition -------------------------------------------------------------

    /**
     * API-056: an ignition through the API says what it found: open air, a zone whose gas won't
     * burn, a new fire and its zone, or the fire already burning next to it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_056_ignite_through_the_api(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 2);
            r.clear(0, 12);
            Ignition open = ignite(r, r.abs(1, 5, 1));
            h.assertValueEqual(open.outcome(), IgnitionOutcome.OPEN, "in open air");
            h.assertValueEqual(open.zone(), Optional.empty(), "a zone in open air");
            ZoneId air = room(r, 0, 3, 3, 3, StandardAir.TEMPERATURE, hydrogenInAir(0))
                    .id();
            Ignition cold = ignite(r, r.abs(2, 2, 2));
            h.assertValueEqual(cold.outcome(), IgnitionOutcome.NOT_FLAMMABLE, "in air");
            h.assertValueEqual(cold.zone(), Optional.of(air), "its zone");
            h.assertValueEqual(cold.hotspot(), Optional.empty(), "a hotspot in air");
            ZoneId hydrogen = hydrogenRoom(r, 6).id();
            BlockPos middle = r.abs(8, 2, 2);
            Ignition lit = ignite(r, middle);
            h.assertValueEqual(lit.outcome(), IgnitionOutcome.IGNITED, "in hydrogen");
            h.assertValueEqual(lit.hotspot(), Optional.of(middle), "the new fire");
            h.assertValueEqual(lit.zone(), Optional.of(hydrogen), "its zone");
            h.assertTrue(gasFire(r, middle), "no gas fire when the future completed");
            r.tick();
            Ignition again = ignite(r, r.abs(9, 2, 2));
            h.assertValueEqual(again.outcome(), IgnitionOutcome.BURNING, "next to the fire");
            h.assertValueEqual(again.hotspot(), Optional.of(middle), "the fire found");
            h.assertTrue(again.burning(), "BURNING isn't burning");
            empty(r, 6);
        });
        h.succeed();
    }

    // ---- API-057: extinguishing --------------------------------------------------------

    /**
     * API-057: extinguishing a box removes its gas fires and vanilla fires at once, and the zone's
     * hotspots go with the next atmos tick; its gas is left. A box over the cap is refused.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_057_extinguish_puts_out_fires(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 3);
            ZoneId zone = hydrogenRoom(r, 0).id();
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            r.set(3, 0, 3, Blocks.NETHERRACK.defaultBlockState());
            BlockPos vanilla = r.abs(3, 1, 3);
            r.set(3, 1, 3, Blocks.FIRE.defaultBlockState());
            r.tick();
            h.assertValueEqual(Fires.hotspots(r.level, zone).size(), 2, "hotspots before");
            double h2 = r.zone(2, 2, 2).mixture().moles(HotspotTests.H2);
            int removed = Fires.extinguish(r.level, r.abs(1, 1, 1), r.abs(3, 3, 3));
            h.assertValueEqual(removed, 2, "fire blocks removed");
            h.assertFalse(gasFire(r, middle), "the gas fire stayed");
            h.assertTrue(r.level.getBlockState(vanilla).isAir(), "the vanilla fire stayed");
            h.assertTrue(Fires.isBurning(r.level, zone), "the hotspots went before an atmos tick");
            r.tick();
            h.assertFalse(Fires.isBurning(r.level, zone), "still burning after the atmos tick");
            h.assertTrue(r.zone(2, 2, 2).mixture().moles(HotspotTests.H2) > 0.9 * h2, "the gas went too");
            h.assertValueEqual(Fires.extinguish(r.level, r.abs(1, 1, 1), r.abs(3, 3, 3)), 0, "fires removed again");
            boolean refused;
            try {
                Fires.extinguish(r.level, r.abs(0, 0, 0), r.abs(16, 15, 15));
                refused = false;
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            h.assertTrue(refused, "a box of 4352 blocks wasn't refused");
            empty(r, 0);
        });
        h.succeed();
    }

    /**
     * API-057: a gas fire the batch in flight asks for in a box put out after the batch closed
     * isn't placed: the batch didn't see the spray. The next batch, which did, places fires again.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_057_extinguish_stops_a_fire_in_flight(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 4);
            hydrogenRoom(r, 0);
            hydrogenRoom(r, 6);
            BlockPos sprayed = r.abs(2, 2, 2);
            BlockPos other = r.abs(8, 2, 2);
            var first = Fires.ignite(r.level, sprayed, Rooms.source("fire_api"));
            var second = Fires.ignite(r.level, other, Rooms.source("fire_api"));
            Rooms.service().closeNow(r.level);
            h.assertValueEqual(Fires.extinguish(r.level, r.abs(1, 1, 1), r.abs(3, 3, 3)), 0, "fires removed");
            Rooms.service().applyNow(r.level);
            h.assertTrue(first.isDone() && second.isDone(), "the ignitions weren't in the batch");
            h.assertFalse(gasFire(r, sprayed), "a gas fire placed in the sprayed box");
            h.assertTrue(gasFire(r, other), "no gas fire outside the box");
            r.tick();
            Ignition again = ignite(r, sprayed);
            h.assertTrue(again.burning(), "the sprayed room won't burn after the spray: " + again.outcome());
            h.assertTrue(gasFire(r, again.hotspot().orElseThrow()), "no gas fire after the spray");
            empty(r, 0);
            empty(r, 6);
        });
        h.succeed();
    }

    // ---- API-058: no space -------------------------------------------------------------

    /**
     * API-058: an ignition in a flammable zone where neither the position nor a neighbour in the
     * zone is plain air finds NO_SPACE: here the zone is the space over a bottom slab, the only
     * block of its room. It names the zone and no hotspot, and no block changes.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_058_no_space(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 5);
            for (BlockPos p : BlockPos.betweenClosed(r.abs(0, 0, 0), r.abs(2, 2, 2))) {
                r.level.setBlockAndUpdate(p, Rooms.STONE);
            }
            BlockState slab = Blocks.STONE_SLAB.defaultBlockState();
            BlockPos at = r.abs(1, 1, 1);
            r.level.setBlockAndUpdate(at, slab);
            Vec3 over = Vec3.atBottomCenterOf(at).add(0, 0.75, 0);
            r.settle(GasMutations.remove(r.level, over, EVERYTHING, Rooms.source("fire_api")));
            ZoneInfo before = r.zone(1, 1, 1);
            Map<ResourceLocation, Double> moles = new HashMap<>();
            hydrogenInAir(0.10)
                    .forEach((g, p) ->
                            moles.put(g, p * before.volume() / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
            r.settle(GasMutations.add(
                    r.level, over, new GasParcel(moles, StandardAir.TEMPERATURE), Rooms.source("fire_api")));
            ZoneId zone = r.zone(1, 1, 1).id();
            Ignition none = ignite(r, at);
            h.assertValueEqual(none.outcome(), IgnitionOutcome.NO_SPACE, "over a slab");
            h.assertValueEqual(none.zone(), Optional.of(zone), "its zone");
            h.assertValueEqual(none.hotspot(), Optional.empty(), "a hotspot");
            h.assertTrue(r.level.getBlockState(at) == slab, "the slab changed");
            r.tick();
            h.assertFalse(Fires.isBurning(r.level, zone), "the zone burns");
            r.settle(GasMutations.remove(r.level, over, EVERYTHING, Rooms.source("fire_api")));
        });
        h.succeed();
    }
}
