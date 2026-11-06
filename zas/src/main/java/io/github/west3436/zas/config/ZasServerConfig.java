package io.github.west3436.zas.config;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The server config, {@code config/zas-server.toml}, which a world's {@code
 * serverconfig/zas-server.toml} overrides: the server tier of
 * OPS-001's three (with {@link ZasCommonConfig} and {@link ZasClientConfig}).
 *
 * <p>It holds the atmosphere preset (D-14, OPS-007) and, under {@code [preset_options]}, an entry
 * for each option the preset sets ({@link PresetOptions}), which either follows the preset or
 * overrides it (OPS-014); the dimensions excluded from simulation (ENV-007); the atmos scheduler's
 * timing and debug switches (CORE-041, CORE-047, CORE-071, CORE-073) and the venting model
 * (CORE-035, CORE-068) under {@code [atmos]}; conduction and radiation (HEAT-008, HEAT-011,
 * HEAT-015, HEAT-004) under {@code [heat]}; breathing (LIFE-003, LIFE-007, LIFE-042) under
 * {@code [breathing]} ({@link BreathingConfig}); the
 * hazards' comfort band, switches, scales and per-dimension overrides (LIFE-011, LIFE-031,
 * LIFE-036) under {@code [hazards]} ({@link HazardConfig}); each armor slot's coverage (LIFE-024)
 * under {@code [protection]} ({@link ProtectionConfig}); airflow (FLOW) under {@code [airflow]}
 * ({@link AirflowConfig}); the fire oxygen threshold (FIRE-030) under {@code [fire]} ({@link
 * FireConfig}); the permission level of the {@code /zas} commands (OPS-011) under
 * {@code [commands]}; and the
 * periodic profiling log (OPS-012) under {@code [profiling]}. The sharing table and thresholds join
 * this file as their features land.
 *
 * <p>Values are read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read; commands that change a value write the
 * file with {@link #save}.
 */
public final class ZasServerConfig {
    public static final ModConfigSpec SPEC;

    /** The atmosphere difficulty preset (D-14). */
    public static final ModConfigSpec.EnumValue<AtmospherePreset> PRESET;

    /** Dimension ids excluded from simulation (ENV-007), in addition to those datapacks exclude. */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> EXCLUDED_DIMENSIONS;

    /** The atmos period in game ticks (CORE-041). */
    public static final ModConfigSpec.IntValue PERIOD_TICKS;

    /** Game ticks from batch close to apply; less than the period (architecture §3.2, O-7). */
    public static final ModConfigSpec.IntValue APPLY_DELAY_TICKS;

    /** The server-thread budget for atmos work per game tick, in ms (CORE-047, NFR-002). */
    public static final ModConfigSpec.IntValue SERVER_BUDGET_MS;

    /** Run each atmos tick on the server thread when its batch closes (CORE-071; debug). */
    public static final ModConfigSpec.BooleanValue SINGLE_THREADED;

    /** Run the conservation checker every atmos tick (CORE-073; debug). */
    public static final ModConfigSpec.BooleanValue CONSERVATION_CHECKER;

    /** The venting model: volume-aware (CORE-035) or ZAS's (CORE-068), D-21. */
    public static final ModConfigSpec.EnumValue<ExchangeSettings.Venting> VENTING;

    /** The permission level every {@code /zas} command needs (OPS-011). */
    public static final ModConfigSpec.IntValue PERMISSION_LEVEL;

    public static final ModConfigSpec.BooleanValue HEAT_ENABLED;
    public static final ModConfigSpec.DoubleValue FALLBACK_TRANSMITTANCE;
    public static final ModConfigSpec.IntValue WALL_THICKNESS;
    public static final ModConfigSpec.DoubleValue NEAR_VACUUM_PERCENT;
    public static final ModConfigSpec.DoubleValue EMISSIVITY;

    /** Seconds between profiling reports in the log; 0 turns the log off (OPS-012). */
    public static final ModConfigSpec.IntValue PROFILING_LOG_INTERVAL;

    /** {@code /zas checker on|off} until the server stops; null follows the config (OPS-010). */
    private static volatile Boolean checkerOverride;

    /** Whether {@link #save} leaves the file alone, for GameTests. */
    private static volatile boolean savesHeldForTest;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PRESET = builder.comment(
                        "Atmosphere difficulty preset: NORMAL or HARDCORE.",
                        "It sets the default of every option under [preset_options]. Options overridden there",
                        "keep their values when the preset changes. /zas preset shows each option's value and",
                        "whether it comes from the preset or an override.")
                .defineEnum("preset", AtmospherePreset.NORMAL);
        EXCLUDED_DIMENSIONS = builder.comment(
                        "Dimension ids excluded from simulation, such as \"minecraft:the_end\".",
                        "No zones form there; queries still return the dimension's ambient.")
                .defineListAllowEmpty(
                        "excluded_dimensions",
                        List.of(),
                        () -> "",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
        builder.comment(
                        "Options the preset sets. Each is \"" + PresetOption.FOLLOW_PRESET
                                + "\" to take the active preset's default, or a value,",
                        "which overrides the preset: it applies under either preset and stays when the preset",
                        "changes. The comment on each gives its Normal and Hardcore defaults.")
                .push("preset_options");
        PresetOptions.define(builder);
        builder.pop();
        builder.push("atmos");
        PERIOD_TICKS = builder.comment(
                        "The atmos tick period in game ticks (20 = 1 s). Rates are per second, so a",
                        "different period changes smoothness and cost, not behaviour.")
                .defineInRange("period_ticks", SchedulerSettings.DEFAULT.periodTicks(), 2, 200);
        APPLY_DELAY_TICKS = builder.comment(
                        "Game ticks between closing an atmos batch and applying its result.",
                        "Must be less than period_ticks; otherwise the defaults are used.")
                .defineInRange("apply_delay_ticks", SchedulerSettings.DEFAULT.applyDelayTicks(), 1, 199);
        SERVER_BUDGET_MS = builder.comment(
                        "Server-thread time per game tick for atmos work, in ms. Prefetching beyond it",
                        "waits for the next atmos tick; applying a result is never split.")
                .defineInRange("server_budget_ms", 10, 1, 1000);
        SINGLE_THREADED = builder.comment(
                        "Debug: run the simulation on the server thread instead of the atmos workers.",
                        "Results are identical either way. Restart-only: read when the server starts.")
                .worldRestart()
                .define("single_threaded", false);
        CONSERVATION_CHECKER = builder.comment(
                        "Debug: check every atmos tick that gas changed only through known sources",
                        "and sinks, and log any discrepancy.")
                .define("conservation_checker", false);
        VENTING = builder.comment(
                        "How fast zones exchange gas through openings: VOLUME_AWARE or FAITHFUL.",
                        "VOLUME_AWARE: the same openings drain a large room proportionally slower than a small",
                        "one, into the open or into a much larger room. FAITHFUL: ZAS's model, where a room loses",
                        "about the same fraction of its air per second whatever its size.")
                .defineEnum("venting", ExchangeSettings.Venting.VOLUME_AWARE);
        builder.pop();
        builder.push("heat");
        HEAT_ENABLED = builder.comment(
                        "Whether zones conduct heat through their walls, to and from heat sources, and",
                        "radiate it to a vacuum.")
                .define("enabled", true);
        FALLBACK_TRANSMITTANCE = builder.comment(
                        "The thermal transmittance U, in W/(m²·K) per block-thick layer, of a block with",
                        "none defined in a datapack. 1.7 is stone's.")
                .defineInRange("fallback_transmittance", HeatSettings.DEFAULT.fallbackTransmittance(), 0.001, 1e6);
        WALL_THICKNESS = builder.comment(
                        "How many blocks deep a wall is searched for the space on its far side. Past",
                        "that, heat goes to the ground, at the ambient temperature.")
                .defineInRange("wall_thickness", HeatSettings.DEFAULT.maxThickness(), 1, 16);
        NEAR_VACUUM_PERCENT = builder.comment(
                        "A zone thinner than this, in percent of standard air's density, neither",
                        "conducts nor radiates heat.")
                .defineInRange("near_vacuum_percent", 1.0, 0.0, 100.0);
        EMISSIVITY = builder.comment("The emissivity of a wall's outer surface facing a vacuum, 0 to 1.")
                .defineInRange("emissivity", HeatSettings.DEFAULT.emissivity(), 0.0, 1.0);
        builder.pop();
        BreathingConfig.define(builder);
        HazardConfig.define(builder);
        ProtectionConfig.define(builder);
        AirflowConfig.define(builder);
        FireConfig.define(builder);
        builder.push("commands");
        PERMISSION_LEVEL = builder.comment(
                        "The permission level every /zas command needs: 0 everyone, 2 operators (the",
                        "vanilla level for game-changing commands), 4 the server owner and console.")
                .defineInRange("permission_level", 2, 0, 4);
        builder.pop();
        builder.push("profiling");
        PROFILING_LOG_INTERVAL = builder.comment(
                        "Seconds between profiling reports (the /zas profile report) in the server log.",
                        "0 turns the log off.")
                .defineInRange("log_interval_seconds", 0, 0, 86_400);
        builder.pop();
        SPEC = builder.build();
    }

    private ZasServerConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
        IEventBus modBus = container.getEventBus();
        if (modBus != null) {
            WorldConfigCopy.register(modBus, NeoForge.EVENT_BUS);
        }
    }

    /** The active preset; {@link AtmospherePreset#NORMAL} while no world's config is loaded. */
    public static AtmospherePreset preset() {
        return SPEC.isLoaded() ? PRESET.get() : AtmospherePreset.NORMAL;
    }

    /**
     * Switches the preset in the loaded config. Overrides stay (OPS-014). Not written to the file:
     * {@link #save} does that.
     *
     * @throws IllegalStateException if no world's config is loaded
     */
    public static void setPreset(AtmospherePreset preset) {
        if (!SPEC.isLoaded()) {
            throw new IllegalStateException("no world's server config is loaded");
        }
        PRESET.set(preset);
    }

    /**
     * Writes the loaded config so a change made by command lasts: to the file it came from, or, in
     * a singleplayer world without a server config of its own, to a new copy in the world's {@code
     * serverconfig/}, leaving the shared file to the other worlds ({@link WorldConfigCopy},
     * OPS-007). Server thread.
     *
     * @return what it wrote; empty while no world's config is loaded, or while saves are held
     * @throws java.io.UncheckedIOException if the world's copy can't be written
     */
    public static Optional<WorldConfigCopy.Saved> save(MinecraftServer server) {
        if (!SPEC.isLoaded() || savesHeldForTest) {
            return Optional.empty();
        }
        return modConfig().map(config -> WorldConfigCopy.save(server, config));
    }

    /**
     * For GameTests: makes {@link #save} leave the file alone, so commands under test don't write
     * the config, whose reload would drop other tests' unsaved values.
     */
    public static void holdSavesForTest(boolean hold) {
        savesHeldForTest = hold;
    }

    /**
     * The file the loaded server config is kept in: the world's {@code serverconfig/zas-server.toml}
     * if it has one, including one {@link #save} made this session, else {@code
     * config/zas-server.toml}. Empty while none is loaded, or for a config a server synced to this
     * client.
     */
    public static Optional<Path> file() {
        if (!SPEC.isLoaded()) {
            return Optional.empty();
        }
        Optional<Path> copy = WorldConfigCopy.active();
        if (copy.isPresent()) {
            return copy;
        }
        return modConfig().map(WorldConfigCopy::path);
    }

    /** NeoForge's record of this config. */
    public static Optional<ModConfig> modConfig() {
        for (ModConfig config : ModConfigs.getModConfigs(ZasMod.MOD_ID)) {
            if (config.getType() == ModConfig.Type.SERVER && config.getSpec() == SPEC) {
                return Optional.of(config);
            }
        }
        return Optional.empty();
    }

    /** Whether the config excludes a dimension; false while no world's config is loaded. */
    public static boolean excludes(ResourceLocation dimension) {
        return SPEC.isLoaded() && EXCLUDED_DIMENSIONS.get().contains(dimension.toString());
    }

    /** The scheduler timing from the config; the defaults before it loads or if it is inconsistent. */
    public static SchedulerSettings scheduler() {
        if (!SPEC.isLoaded()) {
            return SchedulerSettings.DEFAULT;
        }
        int period = PERIOD_TICKS.get();
        int delay = APPLY_DELAY_TICKS.get();
        return delay < period ? new SchedulerSettings(period, delay) : SchedulerSettings.DEFAULT;
    }

    /** The heat settings from the config, or a GameTest's override; the defaults before it loads. */
    public static HeatSettings heat() {
        HeatSettings override = heatOverride;
        if (override != null) {
            return override;
        }
        if (!SPEC.isLoaded()) {
            return HeatSettings.DEFAULT;
        }
        return new HeatSettings(
                HEAT_ENABLED.get(),
                FALLBACK_TRANSMITTANCE.get(),
                WALL_THICKNESS.get(),
                NEAR_VACUUM_PERCENT.get() / 100 * HeatSettings.STANDARD_AIR_DENSITY,
                EMISSIVITY.get());
    }

    private static volatile HeatSettings heatOverride;

    /** Uses {@code settings} instead of the config until called with null. For GameTests. */
    public static void overrideHeatForTest(HeatSettings settings) {
        heatOverride = settings;
    }

    /** The venting model (D-21); volume-aware while no world's config is loaded. */
    public static ExchangeSettings.Venting venting() {
        return SPEC.isLoaded() ? VENTING.get() : ExchangeSettings.Venting.VOLUME_AWARE;
    }

    /** The server-thread budget in nanoseconds. */
    public static long budgetNanos() {
        return (SPEC.isLoaded() ? SERVER_BUDGET_MS.get() : 10) * 1_000_000L;
    }

    /** Whether ticks run on the server thread. Restart-only: a reload leaves it as it was (OPS-016). */
    public static boolean singleThreaded() {
        return SPEC.isLoaded() && SINGLE_THREADED.get();
    }

    /** Whether the conservation checker runs: {@code /zas checker} if it was used, else the config. */
    public static boolean conservationChecker() {
        Boolean override = checkerOverride;
        if (override != null) {
            return override;
        }
        return SPEC.isLoaded() && CONSERVATION_CHECKER.get();
    }

    /**
     * Switches the conservation checker on or off until the server stops, whatever the config says;
     * null returns to the config (OPS-010). Any thread.
     */
    public static void overrideConservationChecker(Boolean on) {
        checkerOverride = on;
    }

    /** Whether {@code /zas checker} overrides the config. */
    public static boolean conservationCheckerOverridden() {
        return checkerOverride != null;
    }

    /** The permission level of the {@code /zas} commands; 2 while no world's config is loaded. */
    public static int permissionLevel() {
        return SPEC.isLoaded() ? PERMISSION_LEVEL.get() : 2;
    }

    /** Game ticks between profiling log reports, or 0 when the log is off. */
    public static int profilingLogIntervalTicks() {
        return SPEC.isLoaded() ? PROFILING_LOG_INTERVAL.get() * 20 : 0;
    }
}
