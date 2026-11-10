package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.STONE;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.config.WorldConfigCopy;
import io.github.west3436.zas.config.ZasCommonConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.life.HazardLevels;
import io.github.west3436.zas.life.HazardProfile;
import io.github.west3436.zas.life.HazardProfiles;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Live config changes (OPS-016): a server setting applies from the next atmos tick without a
 * restart, unless it is marked restart-only.
 *
 * <p>A changed setting reaches every test running beside it. The tests in {@link #BATCH} change a
 * setting and put it back within one call, so nothing else sees it. The two that wait for real game
 * ticks have a batch each.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiveConfigTests {
    private static final String BATCH = "ops_016_live_config";
    private static final String COPY_BATCH = "ops_016_live_config_copy";
    private static final String PERIOD_BATCH = "ops_016_live_config_period";

    private LiveConfigTests() {}

    /**
     * OPS-016's Verify: the venting model, changed with a room already venting, sets how much the
     * room loses on the very next atmos tick, each way. The exact fractions are {@code core_068}'s;
     * here, in the open air, a 27-cell room loses about 4 times as much under FAITHFUL.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_016_a_setting_applies_on_the_next_atmos_tick(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 990);
            r.clear(0, 8);
            r.shell(0, 4);
            ExchangeSettings.Venting configured = ZasServerConfig.VENTING.get();
            try {
                ZasServerConfig.VENTING.set(ExchangeSettings.Venting.VOLUME_AWARE);
                MutationOutcome formed = r.settle(GasMutations.add(
                                r.level,
                                r.point(2, 2, 2),
                                new GasParcel(Map.of(O2, 10_000.0), 293.15),
                                source("ops_016")))
                        .outcome();
                h.assertValueEqual(formed, MutationOutcome.ZONE, "the room's zone");
                r.set(4, 2, 2, AIR);
                r.tick();
                h.assertValueEqual(r.zone(2, 2, 2).edges().ambientEdges(), 1, "the room's openings");

                double aware = loss(r);
                ZasServerConfig.VENTING.set(ExchangeSettings.Venting.FAITHFUL);
                double faithful = loss(r);
                ZasServerConfig.VENTING.set(ExchangeSettings.Venting.VOLUME_AWARE);
                double awareAgain = loss(r);

                h.assertTrue(aware > 0, "the room didn't vent");
                h.assertTrue(
                        faithful > 3 * aware,
                        "the tick after the change to FAITHFUL lost " + faithful + " mol; before it, " + aware);
                h.assertTrue(
                        awareAgain < faithful / 3,
                        "the tick after the change back lost " + awareAgain + " mol; before it, " + faithful);
            } finally {
                ZasServerConfig.VENTING.set(configured);
                r.set(4, 2, 2, STONE);
            }
        });
        h.succeed();
    }

    /**
     * {@code [atmos] delete_displaced_gas} (CORE-029): a block placed in a sealed room raises its
     * pressure with the option off (CORE-022) and leaves it as it was with the option on, the gas in
     * the filled cell gone.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void core_029_delete_displaced_gas_reaches_the_engine(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 989);
            r.clear(0, 8);
            r.shell(0, 4);
            boolean configured = ZasServerConfig.DELETE_DISPLACED_GAS.get();
            try {
                ZasServerConfig.DELETE_DISPLACED_GAS.set(true);
                MutationOutcome formed = r.settle(GasMutations.add(
                                r.level, r.point(2, 2, 2), new GasParcel(Map.of(O2, 10.0), 293.15), source("core_029")))
                        .outcome();
                h.assertValueEqual(formed, MutationOutcome.ZONE, "the room's zone");
                double moles = r.zone(2, 2, 2).mixture().totalMoles();
                double pressure = r.zone(2, 2, 2).mixture().pressure();

                r.set(1, 1, 1, STONE);
                r.tick();
                r.close(r.zone(2, 2, 2).mixture().totalMoles(), moles * 26 / 27, "the moles with the option on");
                r.close(r.zone(2, 2, 2).mixture().pressure(), pressure, "the pressure with the option on");

                ZasServerConfig.DELETE_DISPLACED_GAS.set(false);
                r.set(3, 3, 3, STONE);
                r.tick();
                r.close(r.zone(2, 2, 2).mixture().totalMoles(), moles * 26 / 27, "the moles with the option off");
                r.close(r.zone(2, 2, 2).mixture().pressure(), pressure * 26 / 25, "the pressure with the option off");
            } finally {
                ZasServerConfig.DELETE_DISPLACED_GAS.set(configured);
            }
        });
        h.succeed();
    }

    /**
     * {@code [atmos] enclosure_max_cells} (GEO-020, D-23): a sealed room of 27 cells is open air with
     * the limit at 26, and zones on the next atmos tick once it is 27, though its cells were found
     * open a tick before.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void geo_020_the_enclosure_limit_applies_on_the_next_atmos_tick(GameTestHelper h) {
        Rooms.withoutHeat(() -> {
            Rooms r = new Rooms(h, 1518);
            r.clear(0, 8);
            r.shell(0, 4);
            int configured = ZasServerConfig.ENCLOSURE_MAX_CELLS.get();
            try {
                ZasServerConfig.ENCLOSURE_MAX_CELLS.set(26);
                MutationOutcome over = r.settle(GasMutations.add(
                                r.level, r.point(2, 2, 2), new GasParcel(Map.of(O2, 10.0), 293.15), source("geo_020")))
                        .outcome();
                h.assertValueEqual(over, MutationOutcome.AMBIENT, "the room with the limit at 26 cells");

                ZasServerConfig.ENCLOSURE_MAX_CELLS.set(27);
                MutationOutcome at = r.settle(GasMutations.add(
                                r.level, r.point(2, 2, 2), new GasParcel(Map.of(O2, 10.0), 293.15), source("geo_020")))
                        .outcome();
                h.assertValueEqual(at, MutationOutcome.ZONE, "the room with the limit at 27 cells");
                h.assertValueEqual(r.zone(2, 2, 2).cellCount(), 27, "the zone's cells");
            } finally {
                ZasServerConfig.ENCLOSURE_MAX_CELLS.set(configured);
            }
        });
        h.succeed();
    }

    /**
     * A sealed room brought back to the ambient dissolves after {@code [atmos]
     * sealed_zone_dissolve_seconds}: at the default period, 3 s is 3 atmos ticks. Its cells then read
     * as the ambient, and a later addition forms a zone there again (CORE-052).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void core_052_a_sealed_zone_at_the_ambient_dissolves_after_the_delay(GameTestHelper h) {
        Rooms r = new Rooms(h, 988);
        r.clear(0, 8);
        r.shell(0, 4);
        double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
        try {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(3.0);
            SourceId src = source("core_052");
            r.settle(GasMutations.add(r.level, r.point(2, 2, 2), Rooms.oxygen(500), src));
            ZoneId zone = r.zone(2, 2, 2).id();
            r.ticks(5);
            h.assertTrue(r.zoned(2, 2, 2), "a zone that doesn't match the ambient stays");

            r.settle(GasMutations.remove(r.level, r.point(2, 2, 2), Map.of(O2, 500.0), src));
            h.assertTrue(r.zoned(2, 2, 2), "1 s at the ambient");
            r.tick();
            h.assertTrue(r.zoned(2, 2, 2), "2 s at the ambient");
            r.tick();
            h.assertFalse(r.zoned(2, 2, 2), "3 s at the ambient");
            h.assertTrue(Atmospheres.zone(r.level, zone).isEmpty(), "the zone is gone");
            h.assertValueEqual(
                    Atmospheres.at(r.level, r.abs(2, 2, 2)).state(), AtmosphereState.AMBIENT, "the room's cells");

            MutationOutcome again = r.settle(GasMutations.add(r.level, r.point(2, 2, 2), Rooms.oxygen(500), src))
                    .outcome();
            h.assertValueEqual(again, MutationOutcome.ZONE, "a later addition");
        } finally {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured);
        }
        h.succeed();
    }

    /** A pinned sealed room at the ambient stays past the delay, and goes the delay after its pin does (CORE-052). */
    @GameTest(template = "empty", batch = BATCH)
    public static void core_052_a_pinned_zone_stays_past_the_delay(GameTestHelper h) {
        Rooms r = new Rooms(h, 987);
        r.clear(0, 8);
        r.shell(0, 4);
        double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
        try {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(3.0);
            SourceId owner = source("core_052_pin");
            h.assertValueEqual(
                    r.settle(ZonePins.pin(r.level, r.point(2, 2, 2), owner)).outcome(), PinOutcome.ZONE, "the pin");
            ZoneId zone = r.zone(2, 2, 2).id();
            r.ticks(12);
            h.assertTrue(Atmospheres.zone(r.level, zone).isPresent(), "the pinned zone was dissolved");

            r.settle(ZonePins.unpin(r.level, owner));
            h.assertTrue(Atmospheres.zone(r.level, zone).isPresent(), "the wait starts when the pin goes");
            r.ticks(3);
            h.assertTrue(Atmospheres.zone(r.level, zone).isEmpty(), "the zone outlived its pin by the delay");
        } finally {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured);
        }
        h.succeed();
    }

    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");

    /** The moles of a gas that give the 27-block room at (2, 2, 2) this partial pressure, in kPa, at room temperature. */
    private static double molesFor(double kPa) {
        return kPa * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
    }

    /**
     * Brings the sealed room at (2, 2, 2) to the open air's nitrogen and oxygen plus carbon monoxide at
     * a partial pressure, with its zone formed, and has it sit there for 1 s.
     *
     * @return the moles of carbon monoxide in the room
     */
    private static double roomWithCarbonMonoxide(Rooms r, double kPa, SourceId src) {
        r.clear(0, 8);
        r.shell(0, 4);
        double moles = molesFor(kPa);
        r.settle(GasMutations.add(r.level, r.point(2, 2, 2), Rooms.oxygen(500), src));
        r.settle(GasMutations.add(
                r.level, r.point(2, 2, 2), new GasParcel(Map.of(CO, moles), StandardAir.TEMPERATURE), src));
        r.settle(GasMutations.remove(r.level, r.point(2, 2, 2), Map.of(O2, 500.0), src));
        r.close(r.zone(2, 2, 2).mixture().partialPressure(CO), kPa, "carbon monoxide in the room, kPa");
        return moles;
    }

    /**
     * A sealed room nobody is in, holding 0.2 kPa of carbon monoxide, which harms from 0.12 kPa, keeps
     * its zone and its carbon monoxide well past {@code [atmos] sealed_zone_dissolve_seconds}. By the
     * engine's tolerances that air is the open air's, 0.2 kPa being a twenty-fifth of what they let
     * through, so the room used to dissolve and the gas to vanish. What keeps it is the gas's hazard
     * profile: once that is gone, as after a {@code /reload} without it, the room waits the delay
     * and dissolves (CORE-052, LIFE-004).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void core_052_a_sealed_room_holding_a_harmful_gas_keeps_its_zone(GameTestHelper h) {
        Rooms r = new Rooms(h, 986);
        double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
        HazardProfile profile = HazardProfiles.of(CO).orElseThrow();
        try {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(3.0);
            double moles = roomWithCarbonMonoxide(r, 0.2, source("core_052_harmful"));
            ZoneId zone = r.zone(2, 2, 2).id();
            h.assertTrue(profile.damagingAt(0.2), "0.2 kPa of carbon monoxide doesn't hurt");

            r.ticks(12);
            h.assertTrue(r.zoned(2, 2, 2), "the room turned back into open air");
            h.assertValueEqual(r.zone(2, 2, 2).id(), zone, "the room's zone");
            r.close(r.zone(2, 2, 2).mixture().moles(CO), moles, "carbon monoxide still in the room, mol");
            r.close(
                    Atmospheres.at(r.level, r.abs(2, 2, 2)).mixture().partialPressure(CO),
                    0.2,
                    "carbon monoxide a reading in the room finds, kPa");

            HazardProfiles.putForTest(CO, null);
            r.ticks(2);
            h.assertTrue(r.zoned(2, 2, 2), "2 s without the profile");
            r.tick();
            h.assertFalse(r.zoned(2, 2, 2), "3 s without the profile");
        } finally {
            HazardProfiles.putForTest(CO, profile);
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured);
        }
        h.succeed();
    }

    /**
     * A trace under half the gas's lowest threshold doesn't keep a room: with 0.002 kPa of carbon
     * monoxide, whose HUD warning starts at 0.005 kPa, the room dissolves on time, and the trace goes
     * with it, as any gas within the tolerances always has (CORE-052).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void core_052_a_trace_under_half_the_lowest_threshold_dissolves_on_time(GameTestHelper h) {
        Rooms r = new Rooms(h, 992);
        double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
        try {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(3.0);
            SourceId src = source("core_052_trace");
            double lowest =
                    HazardProfiles.of(CO).orElseThrow().thresholds().get(0).partialPressure();
            h.assertTrue(0.002 < HazardLevels.KEPT_FRACTION * lowest, "0.002 kPa isn't under the kept level");
            roomWithCarbonMonoxide(r, 0.002, src);
            h.assertTrue(r.zoned(2, 2, 2), "1 s at the ambient");
            r.tick();
            h.assertTrue(r.zoned(2, 2, 2), "2 s at the ambient");
            r.tick();
            h.assertFalse(r.zoned(2, 2, 2), "3 s at the ambient");
            h.assertValueEqual(
                    Atmospheres.at(r.level, r.abs(2, 2, 2)).state(), AtmosphereState.AMBIENT, "the room's cells");

            r.settle(GasMutations.add(r.level, r.point(2, 2, 2), Rooms.oxygen(500), src));
            h.assertValueEqual(r.zone(2, 2, 2).mixture().moles(CO), 0.0, "carbon monoxide in the room's new zone");
        } finally {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured);
        }
        h.succeed();
    }

    /** The moles the room at (2, 2, 2) loses in one atmos tick. */
    private static double loss(Rooms r) {
        double before = r.zone(2, 2, 2).mixture().totalMoles();
        r.tick();
        return before - r.zone(2, 2, 2).mixture().totalMoles();
    }

    /** {@code [heat] enabled} stops and starts a hot room's cooling on the next atmos tick. */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_016_heat_stops_and_starts_on_the_next_atmos_tick(GameTestHelper h) {
        Rooms r = new Rooms(h, 991);
        r.clear(0, 8);
        r.shell(0, 4);
        boolean configured = ZasServerConfig.HEAT_ENABLED.get();
        try {
            ZasServerConfig.HEAT_ENABLED.set(true);
            MutationOutcome formed = r.settle(GasMutations.add(
                            r.level, r.point(2, 2, 2), new GasParcel(Map.of(O2, 100.0), 500), source("ops_016")))
                    .outcome();
            h.assertValueEqual(formed, MutationOutcome.ZONE, "the room's zone");

            h.assertTrue(cooling(r) > 1e-3, "the hot room didn't cool with heat on");
            ZasServerConfig.HEAT_ENABLED.set(false);
            double off = cooling(r);
            h.assertTrue(Math.abs(off) < 1e-9, "the tick after heat went off, the room's temperature moved " + off);
            ZasServerConfig.HEAT_ENABLED.set(true);
            h.assertTrue(cooling(r) > 1e-3, "the tick after heat came back on, the room didn't cool");
        } finally {
            ZasServerConfig.HEAT_ENABLED.set(configured);
        }
        h.succeed();
    }

    /** How far the room at (2, 2, 2) cools in one atmos tick, in K. */
    private static double cooling(Rooms r) {
        double before = r.zone(2, 2, 2).mixture().temperature();
        r.tick();
        return before - r.zone(2, 2, 2).mixture().temperature();
    }

    /**
     * What NeoForge does when the file changes, new values in the loaded config and then {@code
     * afterReload}, reaches each kind of setting and each section's reader: none keeps an old value,
     * and the hazard overrides parse again. GameTests can't wait for NeoForge's own reload, which
     * lands whenever the file watcher fires and drops other tests' unsaved values.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_016_a_reload_reaches_every_kind_of_setting(GameTestHelper h) {
        ResourceLocation excluded = ResourceLocation.fromNamespaceAndPath("zas_gametest", "ops_016");
        Config values = loaded();
        List<Runnable> restore = new ArrayList<>();
        try {
            // Read first, so each one holds the cached value a reload must drop.
            AtmospherePreset preset = ZasServerConfig.preset();
            ExchangeSettings.Venting venting = ZasServerConfig.venting();
            int period = ZasServerConfig.scheduler().periodTicks();
            double oxygen = BreathingConfig.oxygenMinimum();
            int permission = ZasServerConfig.permissionLevel();
            h.assertFalse(ZasServerConfig.excludes(excluded), "excluded already");
            h.assertTrue(HazardConfig.enabled(Level.END, HazardType.BURNS), "burns are off in the End already");

            AtmospherePreset otherPreset =
                    preset == AtmospherePreset.NORMAL ? AtmospherePreset.HARDCORE : AtmospherePreset.NORMAL;
            ExchangeSettings.Venting otherVenting = venting == ExchangeSettings.Venting.VOLUME_AWARE
                    ? ExchangeSettings.Venting.FAITHFUL
                    : ExchangeSettings.Venting.VOLUME_AWARE;
            put(values, restore, List.of("preset"), otherPreset.name());
            put(values, restore, List.of("atmos", "venting"), otherVenting.name());
            put(values, restore, List.of("atmos", "period_ticks"), period + 20);
            put(values, restore, List.of("breathing", "oxygen_minimum_kpa"), oxygen + 1);
            put(values, restore, List.of("commands", "permission_level"), permission == 4 ? 3 : permission + 1);
            put(values, restore, List.of("excluded_dimensions"), List.of(excluded.toString()));
            put(values, restore, List.of("hazards", "dimension_overrides"), List.of("minecraft:the_end burns off"));
            ZasServerConfig.SPEC.afterReload();

            h.assertValueEqual(ZasServerConfig.preset(), otherPreset, "the preset");
            h.assertValueEqual(ZasServerConfig.venting(), otherVenting, "the venting model");
            h.assertValueEqual(ZasServerConfig.scheduler().periodTicks(), period + 20, "the period");
            h.assertTrue(BreathingConfig.oxygenMinimum() == oxygen + 1, "the O2 minimum");
            h.assertValueEqual(
                    ZasServerConfig.permissionLevel(), permission == 4 ? 3 : permission + 1, "the permission level");
            h.assertTrue(ZasServerConfig.excludes(excluded), "the excluded dimensions");
            h.assertFalse(HazardConfig.enabled(Level.END, HazardType.BURNS), "the hazard overrides");
        } finally {
            restore.forEach(Runnable::run);
            ZasServerConfig.SPEC.afterReload();
        }
        h.succeed();
    }

    /**
     * The settings marked restart-only are exactly the two the wiki names, and a reload leaves one as
     * it was. Fails when a key gains the mark, as a reminder to mark it on the wiki's
     * Server-Configuration page too.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void ops_016_only_the_thread_settings_are_restart_only(GameTestHelper h) {
        h.assertValueEqual(restartOnly(ZasServerConfig.SPEC), Set.of("atmos.single_threaded"), "the server config");
        h.assertValueEqual(restartOnly(ZasCommonConfig.SPEC), Set.of("worker_threads"), "the common config");

        Config values = loaded();
        List<String> key = List.of("atmos", "single_threaded");
        boolean running = ZasServerConfig.singleThreaded();
        List<Runnable> restore = new ArrayList<>();
        try {
            put(values, restore, key, !running);
            ZasServerConfig.SPEC.afterReload();
            h.assertValueEqual(ZasServerConfig.singleThreaded(), running, "the thread mode after a reload");
        } finally {
            restore.forEach(Runnable::run);
            ZasServerConfig.SPEC.afterReload();
        }
        h.succeed();
    }

    /** The keys of a config marked restart-only, dotted. Public for the content mod's test. */
    public static Set<String> restartOnly(ModConfigSpec spec) {
        Set<String> keys = new TreeSet<>();
        collectRestartOnly(spec.getValues(), keys);
        return keys;
    }

    private static void collectRestartOnly(UnmodifiableConfig section, Set<String> keys) {
        for (Object value : section.valueMap().values()) {
            if (value instanceof UnmodifiableConfig inner) {
                collectRestartOnly(inner, keys);
            } else if (value instanceof ModConfigSpec.ConfigValue<?> setting
                    && setting.getSpec().restartType() != ModConfigSpec.RestartType.NONE) {
                keys.add(String.join(".", setting.getPath()));
            }
        }
    }

    /**
     * {@code period_ticks} and {@code apply_delay_ticks} change how often atmos ticks run from the
     * next close, over real game ticks: 41 game ticks hold 2 atmos ticks at the default period, and
     * about 10 at a period of 4.
     */
    @GameTest(template = "empty", batch = PERIOD_BATCH, timeoutTicks = 100)
    public static void ops_016_the_period_applies_from_the_next_close(GameTestHelper h) {
        int period = ZasServerConfig.PERIOD_TICKS.get();
        int delay = ZasServerConfig.APPLY_DELAY_TICKS.get();
        Runnable restore = () -> {
            ZasServerConfig.PERIOD_TICKS.set(period);
            ZasServerConfig.APPLY_DELAY_TICKS.set(delay);
        };
        DimensionAtmos d = Rooms.service().dimensionFor(h.getLevel());
        long first = d.published().tick();
        ZasServerConfig.PERIOD_TICKS.set(4);
        ZasServerConfig.APPLY_DELAY_TICKS.set(2);
        h.assertValueEqual(Atmospheres.periodTicks(), 4, "the period the API gives");
        after(h, 41, restore, () -> {
            long ticks = d.published().tick() - first;
            restore.run();
            // 10 or 11 closes, the last perhaps not applied yet, and perhaps a batch in flight at the start.
            h.assertTrue(ticks >= 9 && ticks <= 12, "41 game ticks at a period of 4 ran " + ticks + " atmos ticks");
            h.succeed();
        });
    }

    /**
     * A hand edit of a world's copy of the config, on disk, with real game ticks: NeoForge doesn't
     * watch a copy made this session, so {@link WorldConfigCopy#pickUpEdits} applies it. A copy that
     * doesn't parse leaves the values as they are, and a value the config wouldn't accept takes its
     * default, as in NeoForge's own load, so neither can take the server down.
     */
    @GameTest(template = "empty", batch = COPY_BATCH, timeoutTicks = 200)
    public static void ops_016_a_hand_edit_of_a_world_copy_applies_by_the_next_atmos_tick(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Path copy = server.getWorldPath(new LevelResource("serverconfig")).resolve("zas-server.toml");
        h.assertFalse(Files.exists(copy), "the GameTest world already has its own server config");
        h.assertTrue(WorldConfigCopy.active().isEmpty(), "a copy is already active");
        ExchangeSettings.Venting venting = ZasServerConfig.VENTING.get();
        int period = ZasServerConfig.PERIOD_TICKS.get();
        ExchangeSettings.Venting edited = venting == ExchangeSettings.Venting.VOLUME_AWARE
                ? ExchangeSettings.Venting.FAITHFUL
                : ExchangeSettings.Venting.VOLUME_AWARE;
        Runnable restore = () -> {
            ZasServerConfig.VENTING.set(venting);
            ZasServerConfig.PERIOD_TICKS.set(period);
            try {
                Files.deleteIfExists(copy);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            WorldConfigCopy.forgetForTest();
        };
        Optional<WorldConfigCopy.Saved> saved = ZasServerConfig.save(server);
        h.assertTrue(saved.isPresent() && saved.get().newCopy(), "the save didn't make a copy: " + saved);
        int wait = period + 1; // so an atmos tick closes in between

        after(h, 1, restore, () -> {
            h.assertValueEqual(ZasServerConfig.venting(), venting, "the venting model before the edit");
            edit(h, copy, text -> text.replace("venting = \"" + venting.name(), "venting = \"" + edited.name()));
        });
        after(h, 1 + wait, restore, () -> {
            h.assertValueEqual(ZasServerConfig.venting(), edited, "the venting model after the edit");
            edit(h, copy, text -> text + "\n[atmos\nnot toml");
        });
        after(h, 1 + 2 * wait, restore, () -> {
            h.assertValueEqual(ZasServerConfig.venting(), edited, "the venting model with a broken file");
            edit(
                    h,
                    copy,
                    text -> text.replace("\n[atmos\nnot toml", "")
                            .replaceFirst("period_ticks = \\d+", "period_ticks = \"soon\""));
        });
        after(h, 1 + 3 * wait, restore, () -> {
            int corrected = ZasServerConfig.scheduler().periodTicks();
            ExchangeSettings.Venting kept = ZasServerConfig.venting();
            restore.run();
            h.assertValueEqual(corrected, SchedulerSettings.DEFAULT.periodTicks(), "a period that isn't a number");
            h.assertValueEqual(kept, edited, "the venting model beside it");
            h.succeed();
        });
    }

    /** Runs a step after a delay; if it fails, puts things back first, so later tests aren't affected. */
    private static void after(GameTestHelper h, int ticks, Runnable restore, Runnable step) {
        h.runAfterDelay(ticks, () -> {
            try {
                step.run();
            } catch (RuntimeException | Error e) {
                restore.run();
                throw e;
            }
        });
    }

    /**
     * Edits a file and moves its time on. This server's game ticks run far faster than real time, so
     * two edits here can fall within one tick of a file system's clock, as no person's would.
     */
    private static void edit(GameTestHelper h, Path file, UnaryOperator<String> change) {
        try {
            String before = Files.readString(file, StandardCharsets.UTF_8);
            String after = change.apply(before);
            h.assertFalse(after.equals(before), "the edit changed nothing in:\n" + before);
            FileTime later = FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 1000);
            Files.writeString(file, after, StandardCharsets.UTF_8);
            Files.setLastModifiedTime(file, later);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The values NeoForge holds for the server config. */
    private static Config loaded() {
        return ZasServerConfig.modConfig().orElseThrow().getLoadedConfig().config();
    }

    /** Sets a raw value, as a reloaded file would, and notes how to put it back. */
    private static void put(Config values, List<Runnable> restore, List<String> key, Object value) {
        Object was = values.get(key);
        restore.add(() -> values.set(key, was));
        values.set(key, value);
    }
}
