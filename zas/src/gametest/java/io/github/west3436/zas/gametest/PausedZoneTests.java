package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.simulation.DimensionAtmos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Paused zones in a real level (GEO-024, GEO-026, GEO-030 to GEO-038) and the paused and
 * unresolved cases of the API (API-010, API-011, API-016).
 *
 * <p>A GameTest can't unload a chunk on demand, and must never force one (GEO-034), so these tests
 * unload a chunk only as far as the atmosphere is concerned ({@link DimensionAtmos#simulateUnload}):
 * the chunk saves its atmosphere exactly as at a real unload, stops being captured and reads as
 * unloaded to the engine and the API; {@link DimensionAtmos#simulateLoad} loads it again from the
 * saved bytes. Each room runs from x 11 to 20, across the border of two chunks at x 16.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PausedZoneTests {
    private static final int WEST = 0;
    private static final int EAST = 16;

    private PausedZoneTests() {}

    /** A sealed room across two chunks, zoned by adding oxygen. */
    private static Rooms spanning(GameTestHelper h, int index, double o2) {
        Rooms r = new Rooms(h, index);
        r.clear(8, 23);
        r.shell(10, 21);
        MutationResult m = r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(o2), source("paused")));
        h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "the room didn't zone");
        h.assertValueEqual(r.zone(12, 2, 2).id(), r.zone(19, 2, 2).id(), "one zone across the chunks");
        return r;
    }

    /** Unloading part of a zone pauses it whole: nothing in it changes over 10 s (GEO-030). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_030_paused_zone_does_not_change(GameTestHelper h) {
        Rooms r = spanning(h, 1, 500);
        r.set(10, 2, 2, AIR); // a hole in the west wall: the zone vents
        r.tick();
        ZoneInfo venting = r.zone(12, 2, 2);
        h.assertTrue(venting.breached(), "the zone isn't venting");
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        ZoneInfo paused = r.zone(12, 2, 2);
        h.assertTrue(paused.paused(), "the zone didn't pause");
        r.same(venting, paused, "on pausing");
        r.ticks(10);
        r.same(venting, r.zone(12, 2, 2), "after 10 s");
        h.assertTrue(r.zone(12, 2, 2).paused(), "the zone resumed while part of it is unloaded");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        h.assertFalse(r.zone(12, 2, 2).paused(), "the zone didn't resume");
        h.succeed();
    }

    /** A paused zone resumes with its mixture unchanged once its chunks load (GEO-033). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_033_resumes_unchanged(GameTestHelper h) {
        Rooms r = spanning(h, 2, 40);
        ZoneInfo before = r.zone(12, 2, 2);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.ticks(3);
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        ZoneInfo after = r.zone(12, 2, 2);
        h.assertFalse(after.paused(), "the zone didn't resume");
        r.same(before, after, "after resuming");
        h.succeed();
    }

    /** Pausing and resuming zones never adds a chunk ticket (GEO-034). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_034_no_force_loading(GameTestHelper h) {
        LongOpenHashSet forced = new LongOpenHashSet(h.getLevel().getForcedChunks());
        Rooms r = spanning(h, 3, 40);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.ticks(3);
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.ticks(2);
        h.assertValueEqual(new LongOpenHashSet(h.getLevel().getForcedChunks()), forced, "forced chunks");
        h.succeed();
    }

    /**
     * An API query at a paused zone sees its last-known state, and a point in an unloaded chunk is
     * unresolved, with the ambient as its last-known state (GEO-035). A breathing player and an
     * active zone are checked below, and devices and pipes in {@code zas_atmos}'s {@code
     * PausedZoneDeviceTests}.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_035_queries_see_last_known_state(GameTestHelper h) {
        Rooms r = spanning(h, 4, 60);
        ZoneInfo before = r.zone(12, 2, 2);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.ticks(2);
        AtmosphereSample s = Atmospheres.at(r.level, r.abs(12, 2, 2));
        h.assertValueEqual(s.state(), AtmosphereState.PAUSED, "state");
        h.assertValueEqual(s.mixture(), before.mixture(), "the last-known mixture");
        AtmosphereSample far = Atmospheres.at(r.level, r.abs(19, 6, 6));
        h.assertValueEqual(far.state(), AtmosphereState.UNRESOLVED, "a point in the unloaded chunk");
        h.assertValueEqual(
                far.mixture(), Atmospheres.at(r.level, r.abs(5, 6, 6)).mixture(), "unresolved reads the ambient");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        h.succeed();
    }

    /**
     * A breathing player in a paused zone goes by the zone's last-known composition, pressure and
     * temperature, none of them the ambient's: its verdict, its air meter and its damage all come
     * from the room as it paused, and it draws nothing from it (GEO-035).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_035_a_breathing_player_sees_last_known_state(GameTestHelper h) {
        Rooms r = new Rooms(h, 970);
        r.clear(8, 23);
        r.shell(10, 21);
        r.settle(GasMutations.remove(r.level, r.point(12, 2, 2), Map.of(O2, 1e6, Rooms.N2, 1e6), source("geo_035")));
        double hot = 400;
        double moles = 5 * 90_000 / (GasMixture.GAS_CONSTANT * hot); // 5 kPa of O2 in the 90,000 L room
        r.settle(
                GasMutations.add(r.level, r.point(12, 2, 2), new GasParcel(Map.of(O2, moles), hot), source("geo_035")));
        FakePlayer player = TestPlayers.hurtable(r.level, "zas_geo_035");
        player.moveTo(Vec3.atBottomCenterOf(r.abs(12, 1, 2)));

        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        try {
            r.tick();
            ZoneInfo known = r.zone(12, 2, 2);
            h.assertTrue(known.paused(), "the zone didn't pause");
            h.assertValueEqual(Atmospheres.of(player).state(), AtmosphereState.PAUSED, "the air at its eyes");
            h.assertValueEqual(Atmospheres.of(player).mixture(), known.mixture(), "the last-known mixture");

            long t = r.level.getGameTime();
            Breathability verdict = Breathers.checkAt(player, t).verdict();
            h.assertValueEqual(verdict.reason(), BreathReason.LOW_OXYGEN, "the composition");
            h.assertValueEqual(verdict.source(), BreathSource.ATMOSPHERE, "what it breathes");
            h.assertValueEqual(verdict.breath(), Optional.of(known.mixture()), "its breath");
            h.assertTrue(verdict.hazards().contains(Hazard.LOW_PRESSURE), "the pressure: " + verdict);
            h.assertTrue(verdict.hazards().contains(Hazard.HEAT), "the temperature: " + verdict);
            h.assertValueEqual(Breathing.of(player), verdict, "the API's verdict");

            float health = player.getHealth();
            Breathers.checkAt(player, t + 40);
            h.assertTrue(player.getHealth() < health, "2 s in the room did no harm");
            int air = player.getAirSupply();
            for (int i = 0; i < 20; i++) {
                player.baseTick();
            }
            h.assertTrue(player.getAirSupply() < air, "the air meter didn't drain");

            r.ticks(2);
            r.same(known, r.zone(12, 2, 2), "after being breathed");
            h.assertTrue(r.zone(12, 2, 2).paused(), "the zone resumed");
        } finally {
            r.dimension().simulateLoad(r.chunk(EAST), saved);
            r.tick();
        }
        h.succeed();
    }

    /**
     * An active zone across an edge from a paused one exchanges with the paused zone's last-known
     * state: it moves toward it and never past it, the paused zone doesn't change, and the ledger
     * records the gas as the paused category (GEO-035).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_035_an_active_zone_across_an_edge_sees_last_known_state(GameTestHelper h) {
        Rooms r = new Rooms(h, 971);
        r.clear(0, 23);
        r.shell(2, 9); // A, in the west chunk
        r.shell(9, 21); // B, across both chunks, sharing the wall at x = 9
        r.settle(GasMutations.add(r.level, r.point(5, 2, 2), oxygen(10), source("geo_035")));
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(400), source("geo_035")));
        r.set(9, 2, 2, AIR);
        r.tick();
        h.assertFalse(r.zone(5, 2, 2).id().equals(r.zone(12, 2, 2).id()), "the rooms merged");

        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        try {
            r.tick();
            ZoneInfo known = r.zone(12, 2, 2);
            h.assertTrue(known.paused(), "B didn't pause");
            h.assertFalse(r.zone(5, 2, 2).paused(), "A paused");

            double before = r.zone(5, 2, 2).mixture().moles(O2);
            CompletableFuture<ConservationReport> check = r.dimension().checkConservation();
            r.tick();
            double gained = r.zone(5, 2, 2).mixture().moles(O2) - before;
            h.assertTrue(gained > 0, "A didn't draw from B");
            ConservationReport report = check.join();
            h.assertTrue(report.conserved(), "the checker reported " + report);
            int o2 = r.dimension().registry().indexOf(O2.toString());
            h.assertTrue(
                    report.recorded(LedgerCategory.PAUSED, o2) > 0, "the gas isn't in the ledger's paused category");
            r.same(known, r.zone(12, 2, 2), "B after the exchange");

            double last = r.zone(5, 2, 2).mixture().pressure();
            for (int i = 0; i < 20; i++) {
                r.tick();
                double now = r.zone(5, 2, 2).mixture().pressure();
                h.assertTrue(now > last, "A stopped rising at " + now + " kPa, tick " + i);
                h.assertTrue(now < known.mixture().pressure(), "A went past B's last-known pressure, tick " + i);
                last = now;
            }
            r.same(known, r.zone(12, 2, 2), "B after 20 s");
        } finally {
            r.dimension().simulateLoad(r.chunk(EAST), saved);
            r.tick();
        }
        h.succeed();
    }

    /** Gas sent into a paused zone is voided; the zone doesn't change and the checker records it (GEO-036). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_036_gas_into_paused_zone_is_voided(GameTestHelper h) {
        boolean was = ZasServerConfig.CONSERVATION_CHECKER.get();
        ZasServerConfig.CONSERVATION_CHECKER.set(true);
        try {
            Rooms r = spanning(h, 5, 40);
            ZoneInfo before = r.zone(12, 2, 2);
            byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
            r.tick();
            MutationResult m = r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(7), source("geo_036")));
            h.assertValueEqual(m.outcome(), MutationOutcome.PAUSED, "outcome");
            h.assertValueEqual(m.moved(), oxygen(7), "the gas left the caller");
            r.same(before, r.zone(12, 2, 2), "the paused zone");
            ConservationReport report = r.dimension().lastConservation();
            h.assertTrue(report != null && report.conserved(), "the checker reported " + report);
            int o2 = r.dimension().registry().indexOf(O2.toString());
            r.close(report.voided(o2), 7, "O2 voided");
            r.dimension().simulateLoad(r.chunk(EAST), saved);
            r.tick();
            r.same(before, r.zone(12, 2, 2), "after resuming");
        } finally {
            ZasServerConfig.CONSERVATION_CHECKER.set(was);
        }
        h.succeed();
    }

    /** Gas drawn from a paused zone is supplied at its last-known state without depleting it (GEO-038). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_038_paused_zone_supplies_without_depleting(GameTestHelper h) {
        Rooms r = spanning(h, 6, 40);
        ZoneInfo before = r.zone(12, 2, 2);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        MutationResult m =
                r.settle(GasMutations.remove(r.level, r.point(12, 2, 2), Map.of(O2, 5.0), source("geo_038")));
        h.assertValueEqual(m.outcome(), MutationOutcome.PAUSED, "outcome");
        r.close(m.moved().moles(O2), 5, "O2 supplied");
        r.close(m.moved().temperature(), before.mixture().temperature(), "at the zone's temperature");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        r.same(before, r.zone(12, 2, 2), "the zone on resuming");
        h.succeed();
    }

    /**
     * Breaking a wall of a paused zone in its loaded part is held, and applies when the zone
     * resumes (GEO-037).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_037_changes_to_paused_zone_are_held(GameTestHelper h) {
        Rooms r = spanning(h, 7, 300);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        r.set(10, 2, 2, AIR);
        r.ticks(2);
        ZoneInfo held = r.zone(12, 2, 2);
        h.assertTrue(held.paused(), "paused");
        h.assertFalse(held.breached(), "the change applied while the zone was paused");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        h.assertTrue(r.zone(12, 2, 2).breached(), "the held change didn't apply on resuming");
        h.succeed();
    }

    /** A room that reaches an unloaded chunk stays unresolved, and zones once it loads (GEO-024). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_024_room_reaching_unloaded_chunk_is_unresolved(GameTestHelper h) {
        Rooms r = new Rooms(h, 8);
        r.clear(8, 23);
        r.shell(10, 21);
        r.ticks(2);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        h.assertTrue(saved == null, "an unzoned chunk saved atmosphere");
        r.tick();
        MutationResult m = r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(10), source("geo_024")));
        h.assertValueEqual(m.outcome(), MutationOutcome.PAUSED, "an unresolved region's outcome");
        h.assertFalse(r.zoned(12, 2, 2), "a zone formed in an unresolved region");
        r.dimension().simulateLoad(r.chunk(EAST), null);
        m = r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(10), source("geo_024")));
        h.assertValueEqual(m.outcome(), MutationOutcome.ZONE, "once loaded");
        h.assertValueEqual(r.zone(19, 2, 2).id(), r.zone(12, 2, 2).id(), "the whole room");
        h.succeed();
    }

    /**
     * Opening a zone into a region that reaches an unloaded chunk pauses it; it resumes once the
     * chunk loads and the region classifies (GEO-026).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void geo_026_breach_into_unresolved_region_pauses(GameTestHelper h) {
        Rooms r = new Rooms(h, 9);
        r.clear(0, 23);
        r.shell(2, 9); // A, in the west chunk
        r.shell(9, 21); // B, across both, sharing the wall at x = 9
        r.settle(GasMutations.add(r.level, r.point(5, 2, 2), oxygen(50), source("geo_026")));
        ZoneInfo a = r.zone(5, 2, 2);
        h.assertFalse(r.zoned(15, 2, 2), "B zoned");
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        r.set(9, 2, 2, AIR);
        r.tick();
        h.assertTrue(r.zone(5, 2, 2).paused(), "the zone didn't pause at the unresolved breach");
        r.ticks(2);
        r.same(a, r.zone(5, 2, 2), "while paused");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.ticks(3);
        ZoneInfo joined = r.zone(5, 2, 2);
        h.assertFalse(joined.paused(), "the zone didn't resume");
        h.assertValueEqual(r.zone(18, 2, 2).id(), joined.id(), "the region didn't join the zone");
        h.succeed();
    }

    /**
     * The unzoned cells of a region held at a breach because it reaches an unloaded chunk read as
     * unresolved, not ambient, though their own chunk is loaded; they zone once the chunk loads
     * (API-010, GEO-026).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_010_held_breach_region_is_unresolved(GameTestHelper h) {
        Rooms r = new Rooms(h, 72);
        r.clear(0, 23);
        r.shell(2, 9); // A, in the west chunk
        r.shell(9, 21); // B, unzoned, across both, sharing the wall at x = 9
        r.settle(GasMutations.add(r.level, r.point(5, 2, 2), oxygen(50), source("api_010")));
        ZoneInfo a = r.zone(5, 2, 2);
        h.assertValueEqual(
                Atmospheres.at(r.level, r.abs(12, 2, 2)).state(), AtmosphereState.AMBIENT, "B before the breach");
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        r.set(9, 2, 2, AIR);
        r.tick();
        h.assertTrue(r.zone(5, 2, 2).paused(), "the zone didn't pause at the held breach");
        AtmosphereSample ambient = Atmospheres.at(r.level, r.abs(5, 6, 6));
        h.assertValueEqual(ambient.state(), AtmosphereState.AMBIENT, "the open air");
        for (int[] p : new int[][] {{9, 2, 2}, {12, 2, 2}, {15, 3, 1}}) {
            String what = "B's loaded part at " + p[0] + ", " + p[1] + ", " + p[2];
            AtmosphereSample s = Atmospheres.at(r.level, r.abs(p[0], p[1], p[2]));
            h.assertValueEqual(s.state(), AtmosphereState.UNRESOLVED, what);
            h.assertTrue(s.zone().isEmpty(), what + ": a zone id");
            h.assertValueEqual(s.mixture(), ambient.mixture(), what + ": the last-known state is the ambient");
            h.assertValueEqual(
                    Atmospheres.at(r.level, r.point(p[0], p[1], p[2])).state(),
                    AtmosphereState.UNRESOLVED,
                    what + ", as a point");
        }
        h.assertValueEqual(
                Atmospheres.at(r.level, r.abs(19, 2, 2)).state(), AtmosphereState.UNRESOLVED, "the unloaded part");
        h.assertValueEqual(Atmospheres.at(r.level, r.abs(5, 2, 2)).state(), AtmosphereState.PAUSED, "A");
        h.assertValueEqual(Atmospheres.at(r.level, r.abs(5, 2, 2)).zone(), Optional.of(a.id()), "A's zone");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.ticks(3);
        AtmosphereSample joined = Atmospheres.at(r.level, r.abs(12, 2, 2));
        h.assertValueEqual(joined.state(), AtmosphereState.ZONED, "B once the region classifies");
        h.assertValueEqual(joined.zone(), Optional.of(a.id()), "B joined A's zone");
        h.succeed();
    }

    /** The API reports a zoned, an ambient, a paused and an unresolved point, every field (API-010). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_010_paused_and_unresolved_points(GameTestHelper h) {
        Rooms r = spanning(h, 10, 60);
        ZoneInfo z = r.zone(12, 2, 2);
        AtmosphereSample zoned = Atmospheres.at(r.level, r.abs(12, 2, 2));
        h.assertValueEqual(zoned.state(), AtmosphereState.ZONED, "zoned state");
        AtmosphereSample ambient = Atmospheres.at(r.level, r.abs(5, 6, 6));
        h.assertValueEqual(ambient.state(), AtmosphereState.AMBIENT, "ambient state");
        h.assertTrue(ambient.zone().isEmpty(), "an ambient zone id");
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        AtmosphereSample paused = Atmospheres.at(r.level, r.abs(12, 2, 2));
        h.assertValueEqual(paused.state(), AtmosphereState.PAUSED, "paused state");
        h.assertValueEqual(paused.zone(), Optional.of(z.id()), "paused zone id");
        r.close(paused.pressure(), z.mixture().pressure(), "paused pressure");
        r.close(paused.temperature(), z.mixture().temperature(), "paused temperature");
        h.assertValueEqual(paused.composition(), z.mixture().composition(), "paused composition");
        h.assertValueEqual(paused.partialPressures(), z.mixture().partialPressures(), "paused partial pressures");
        AtmosphereSample unresolved = Atmospheres.at(r.level, r.abs(19, 6, 6));
        h.assertValueEqual(unresolved.state(), AtmosphereState.UNRESOLVED, "unresolved state");
        h.assertTrue(unresolved.zone().isEmpty(), "an unresolved zone id");
        r.close(unresolved.pressure(), ambient.pressure(), "unresolved pressure");
        r.close(unresolved.temperature(), ambient.temperature(), "unresolved temperature");
        h.assertValueEqual(unresolved.composition(), ambient.composition(), "unresolved composition");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        h.succeed();
    }

    /** A paused zone queried by id and by point reports every field, paused included (API-011). */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_011_paused_zone_query(GameTestHelper h) {
        Rooms r = spanning(h, 11, 60);
        ZoneInfo before = r.zone(12, 2, 2);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        ZoneInfo byPoint = r.zone(12, 2, 2);
        ZoneInfo byId = Atmospheres.zone(r.level, before.id()).orElseThrow();
        h.assertValueEqual(byId, byPoint, "by id and by point");
        h.assertTrue(byId.paused(), "paused");
        h.assertTrue(byId.enclosed(), "enclosed");
        h.assertFalse(byId.breached(), "breached");
        h.assertValueEqual(byId.edges(), before.edges(), "edges");
        r.same(before, byId, "the last-known state");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        h.assertFalse(Atmospheres.zone(r.level, before.id()).orElseThrow().paused(), "resumed");
        h.succeed();
    }

    /**
     * A mutation at a paused zone completes after the applying tick with the paused outcome: an
     * addition voided whole, a removal supplied (API-016).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_016_paused_mutation_results(GameTestHelper h) {
        Rooms r = spanning(h, 12, 60);
        byte[] saved = r.dimension().simulateUnload(r.chunk(EAST));
        r.tick();
        GasParcel gas = oxygen(3);
        CompletableFuture<MutationResult> add = GasMutations.add(r.level, r.point(12, 2, 2), gas, source("api_016"));
        CompletableFuture<MutationResult> remove =
                GasMutations.remove(r.level, r.point(12, 2, 2), Map.of(O2, 2.0), source("api_016"));
        h.assertFalse(add.isDone() || remove.isDone(), "completed before the applying tick");
        r.tick();
        h.assertTrue(add.isDone() && remove.isDone(), "not completed by the applying tick");
        h.assertValueEqual(add.join().outcome(), MutationOutcome.PAUSED, "addition outcome");
        h.assertValueEqual(add.join().moved(), gas, "addition moved");
        h.assertValueEqual(remove.join().outcome(), MutationOutcome.PAUSED, "removal outcome");
        r.close(remove.join().moved().moles(O2), 2, "removal moved");
        r.dimension().simulateLoad(r.chunk(EAST), saved);
        r.tick();
        h.succeed();
    }
}
