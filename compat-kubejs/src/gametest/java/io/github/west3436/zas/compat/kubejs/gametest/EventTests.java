package io.github.west3436.zas.compat.kubejs.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.compat.kubejs.gametest.Probe.Heard;
import io.github.west3436.zas.compat.kubejs.gametest.Probe.OpenAir;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.engine.scheduler.HotspotSettings;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ZAS³'s events in a pack's server script (COMPAT-050). The run's {@code events.js} listens for
 * each one in the group {@code ZasEvents} and tells {@link Probe} what its listener was given;
 * each test here makes the thing happen, as the framework's own tests of the events do, and checks
 * that the script heard it once, on the server thread, with the event's own values.
 */
@GameTestHolder(EventTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class EventTests {
    /** The namespace of the tests for a KubeJS the adapter supports. */
    public static final String NAMESPACE = "zas_kubejs";

    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");

    private EventTests() {}

    private static GasParcel oxygen(double moles) {
        return new GasParcel(Map.of(Lab.O2, moles), StandardAir.TEMPERATURE);
    }

    /** A sealed room from x0 whose zone doesn't match the ambient, so it stays. */
    private static ZoneId room(Lab lab, int x0) {
        lab.shell(x0, x0 + 4);
        lab.settle(GasMutations.add(lab.level, lab.point(x0 + 2, 2, 2), oxygen(500), Lab.SOURCE));
        return lab.zone(x0 + 2, 2, 2).id();
    }

    /**
     * A room forming reaches a script as {@code zoneCreated}; its roof opening as {@code
     * zoneBreached}, and closing again as {@code zoneSealed}. Each carries the level, the server,
     * the zone and a position in it.
     */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_a_zone_form_breach_and_seal(GameTestHelper h) {
        try (Lab lab = new Lab(h, 0)) {
            lab.clear(0, 8);
            lab.shell(0, 4);
            SourceId owner = SourceId.of("zas_kubejs", "breach");
            h.assertValueEqual(
                    lab.settle(ZonePins.pin(lab.level, lab.point(2, 2, 2), owner))
                            .outcome(),
                    PinOutcome.ZONE,
                    "the pin");
            ZoneId zone = lab.zone(2, 2, 2).id();
            lab.tick();
            Heard created = Probe.one(lab, "zoneCreated");
            h.assertValueEqual(created.zone(), zone, "the zone created");
            h.assertValueEqual(
                    Atmospheres.zoneAt(lab.level, created.pos()).orElseThrow().id(), zone, "its position's zone");
            h.assertValueEqual(Probe.heard(lab, "zoneBreached").size(), 0, "breaches heard while sealed");

            lab.set(2, 4, 2, Lab.NOTHING);
            lab.ticks(8); // a classification may take a few
            h.assertValueEqual(Probe.one(lab, "zoneBreached").zone(), zone, "the zone breached");
            h.assertValueEqual(Probe.heard(lab, "zoneSealed").size(), 0, "seals heard while open");

            lab.set(2, 4, 2, Lab.STONE);
            lab.ticks(2);
            h.assertValueEqual(Probe.one(lab, "zoneSealed").zone(), zone, "the zone sealed");
            h.assertValueEqual(Probe.heard(lab, "zoneBreached").size(), 1, "breaches heard once sealed again");
            lab.settle(ZonePins.unpin(lab.level, owner));
        }
        h.succeed();
    }

    /** Two rooms joined reach a script as {@code zoneMerged}, with the zone that was absorbed. */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_two_zones_merge(GameTestHelper h) {
        try (Lab lab = new Lab(h, 1)) {
            lab.clear(0, 12);
            ZoneId west = room(lab, 0);
            ZoneId east = room(lab, 4);
            h.assertFalse(west.equals(east), "one room");

            lab.set(4, 2, 2, Lab.NOTHING);
            lab.ticks(3);
            Heard merged = Probe.one(lab, "zoneMerged");
            h.assertValueEqual(merged.zone(), lab.zone(2, 2, 2).id(), "the survivor");
            h.assertValueEqual(merged.more(), merged.zone().equals(west) ? east : west, "the one absorbed");
            h.assertValueEqual(Probe.heard(lab, "zoneCreated").size(), 2, "zones a script heard form");
        }
        h.succeed();
    }

    /** A room walled in two reaches a script as {@code zoneSplit}, with the zone split off. */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_a_zone_split(GameTestHelper h) {
        try (Lab lab = new Lab(h, 2)) {
            lab.clear(0, 12);
            lab.shell(0, 8);
            lab.settle(GasMutations.add(lab.level, lab.point(2, 2, 2), oxygen(500), Lab.SOURCE));
            ZoneId zone = lab.zone(2, 2, 2).id();
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    lab.set(4, y, z, Lab.STONE);
                }
            }
            lab.ticks(3);
            Heard split = Probe.one(lab, "zoneSplit");
            h.assertValueEqual(split.zone(), zone, "the zone that keeps its id");
            ZoneId west = lab.zone(2, 2, 2).id();
            ZoneId east = lab.zone(6, 2, 2).id();
            h.assertValueEqual(split.more(), west.equals(zone) ? east : west, "the one split off");
        }
        h.succeed();
    }

    /** A pinned room with an opening goes when its pin does, and reaches a script as {@code zoneDissolved}. */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_a_zone_dissolve(GameTestHelper h) {
        try (Lab lab = new Lab(h, 3)) {
            lab.clear(0, 8);
            lab.shell(0, 4);
            SourceId owner = SourceId.of("zas_kubejs", "dissolve");
            lab.settle(ZonePins.pin(lab.level, lab.point(2, 2, 2), owner));
            ZoneId zone = lab.zone(2, 2, 2).id();
            lab.set(2, 4, 2, Lab.NOTHING);
            lab.ticks(8);
            h.assertValueEqual(Probe.heard(lab, "zoneDissolved").size(), 0, "dissolutions heard while pinned");

            lab.settle(ZonePins.unpin(lab.level, owner));
            lab.ticks(2);
            h.assertValueEqual(Probe.one(lab, "zoneDissolved").zone(), zone, "the zone dissolved");
        }
        h.succeed();
    }

    /**
     * A zone whose chunk unloads reaches a script as {@code zonePaused}, and as {@code zoneResumed}
     * when the chunk is back.
     */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_a_zone_pause_and_resume(GameTestHelper h) {
        // Heat off, as in the framework's test: it would change the zone across the pause.
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try (Lab lab = new Lab(h, 4)) {
            lab.clear(0, 8);
            ZoneId zone = room(lab, 0);
            lab.tick();
            ChunkPos chunk = lab.chunk(0);
            byte[] saved = lab.dimension().simulateUnload(chunk);
            lab.ticks(3);
            h.assertValueEqual(Probe.one(lab, "zonePaused").zone(), zone, "the zone paused");
            h.assertValueEqual(Probe.heard(lab, "zoneResumed").size(), 0, "resumptions heard while unloaded");

            lab.dimension().simulateLoad(chunk, saved);
            lab.ticks(3);
            h.assertValueEqual(Probe.one(lab, "zoneResumed").zone(), zone, "the zone resumed");
            h.assertValueEqual(Probe.heard(lab, "zonePaused").size(), 1, "pauses heard once loaded");
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
        h.succeed();
    }

    /**
     * A room of hydrogen in air catching fire reaches a script as {@code fireIgnited}, with the
     * hotspot, and its fire going out as {@code fireExtinguished}, with the last one.
     */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_a_fire_start_and_go_out(GameTestHelper h) {
        FireConfig.overrideHotspotsForTest(new HotspotSettings(0));
        try (Lab lab = new Lab(h, 5)) {
            double atm = StandardAir.PRESSURE;
            ZoneId zone = lab.room(0, Map.of(H2, 0.10 * atm, Lab.N2, 0.90 * 0.79 * atm, Lab.O2, 0.90 * 0.21 * atm))
                    .id();
            BlockPos middle = lab.abs(2, 2, 2);
            lab.settle(Fires.ignite(lab.level, middle, Lab.SOURCE));
            h.assertValueEqual(Probe.heard(lab, "fireIgnited").size(), 0, "ignitions heard while pending");
            lab.tick();
            Heard lit = Probe.one(lab, "fireIgnited");
            h.assertValueEqual(lit.zone(), zone, "the zone on fire");
            h.assertValueEqual(lit.pos(), middle, "its hotspot");
            lab.ticks(3);
            h.assertValueEqual(Probe.heard(lab, "fireIgnited").size(), 1, "ignitions heard while it burns");
            h.assertValueEqual(Probe.heard(lab, "fireExtinguished").size(), 0, "fires heard out while it burns");

            // With nothing left to burn it goes out.
            lab.settle(GasMutations.remove(
                    lab.level, lab.point(2, 2, 2), Map.of(H2, 1e6, Lab.N2, 1e6, Lab.O2, 1e6), Lab.SOURCE));
            lab.ticks(2);
            Heard out = Probe.one(lab, "fireExtinguished");
            h.assertValueEqual(out.zone(), zone, "the zone whose fire went out");
            h.assertValueEqual(out.pos(), middle, "its last hotspot");
        } finally {
            FireConfig.overrideHotspotsForTest(null);
        }
        h.succeed();
    }

    /**
     * Gas let out in the open reaches a script as {@code openAirGas}, with where, that it was
     * added, how much of what at which temperature, and whose it was.
     */
    @GameTest(template = "empty")
    public static void compat_050_a_script_hears_gas_let_out_in_the_open(GameTestHelper h) {
        try (Lab lab = new Lab(h, 6)) {
            lab.clear(0, 4);
            SourceId source = SourceId.of("zas_kubejs", "open_air");
            GasParcel let = new GasParcel(Map.of(Lab.O2, 7.0), 310);
            lab.settle(GasMutations.add(lab.level, lab.point(2, 20, 2), let, source));

            Heard heard = Probe.one(lab, "openAirGas");
            h.assertValueEqual(heard.pos(), lab.abs(2, 20, 2), "where");
            OpenAir more = (OpenAir) heard.more();
            h.assertTrue(more.added(), "the gas wasn't added");
            h.assertValueEqual(more.source(), source, "whose it was");
            lab.close(more.gas().moles(Lab.O2), 7, 1e-9, "the oxygen let out");
            lab.close(more.gas().temperature(), 310, 1e-9, "its temperature");
            // What a script is handed is the API's own parcel, with ids for keys.
            h.assertValueEqual(more.gas().moles().keySet(), let.moles().keySet(), "the gases");
        }
        h.succeed();
    }
}
