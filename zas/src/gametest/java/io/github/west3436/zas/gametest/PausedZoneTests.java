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
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.simulation.DimensionAtmos;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
     * unresolved, with the ambient as its last-known state (GEO-035). Breathing and devices come
     * with LIFE and zas_atmos; they read the same table.
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
