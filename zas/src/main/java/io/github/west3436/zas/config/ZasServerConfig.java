package io.github.west3436.zas.config;

import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.Presets;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.Millibuckets;
import io.github.west3436.zas.engine.classify.EnclosureFill;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import io.github.west3436.zas.engine.zone.ExchangeSettings;
import io.github.west3436.zas.engine.zone.ZoneSettings;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config, {@code config/zas-server.toml}, which a world's {@code
 * serverconfig/zas-server.toml} overrides: the server tier of
 * OPS-001's three (with {@link ZasCommonConfig} and {@link ZasClientConfig}).
 *
 * <p>It holds the dimensions excluded from simulation (ENV-007); the atmos scheduler's
 * timing and debug switches (CORE-041, CORE-047, CORE-071, CORE-073) and the venting model
 * (CORE-035, CORE-068) under {@code [atmos]}; conduction and radiation (HEAT-008, HEAT-011,
 * HEAT-015, HEAT-004) under {@code [heat]}; breathing (LIFE-003, LIFE-007, LIFE-042) under
 * {@code [breathing]} ({@link BreathingConfig}); the
 * hazards' comfort band, switches, scales and per-dimension overrides (LIFE-011, LIFE-031,
 * LIFE-036) under {@code [hazards]} ({@link HazardConfig}); each armor slot's coverage (LIFE-024)
 * under {@code [protection]} ({@link ProtectionConfig}); airflow (FLOW) under {@code [airflow]}
 * ({@link AirflowConfig}); the fire oxygen threshold (FIRE-030) under {@code [fire]} ({@link
 * FireConfig}); the litres one millibucket of another mod's gas is (COMPAT-143) under {@code
 * [units]}; the permission level of the {@code /zas} commands (OPS-011) under
 * {@code [commands]}; and the
 * periodic profiling log (OPS-012) under {@code [profiling]}. The sharing table and thresholds join
 * this file as their features land.
 *
 * <p>The atmosphere preset and the options it sets (D-14, OPS-007, OPS-014) moved to SS13
 * Framework's {@code ss13_framework-presets.toml} ({@link PresetOptions}). Their old entries, {@code
 * preset} and {@code [preset_options]}, stay defined here, commented as moved, so NeoForge keeps a
 * 1.2.0 file's values until {@link PresetMigration} has carried them over.
 *
 * <p>Values are read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read.
 */
public final class ZasServerConfig {
    public static final ModConfigSpec SPEC;

    /**
     * The atmosphere preset as 1.2.0 kept it, which moved to SS13 Framework's preset file: read once
     * by {@link PresetMigration}, then reset to {@link AtmospherePreset#NORMAL}.
     */
    public static final ModConfigSpec.EnumValue<AtmospherePreset> MOVED_PRESET;

    /**
     * Each preset option's override as 1.2.0 kept it under {@code [preset_options]}, by key: {@code
     * "preset"} or a value. Moved with {@link #MOVED_PRESET}.
     */
    public static final Map<String, ModConfigSpec.ConfigValue<Object>> MOVED_PRESET_OPTIONS;

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

    /** Whether a filled cell's gas is deleted, as ZAS does, instead of staying in its zone (CORE-029, D-41). */
    public static final ModConfigSpec.BooleanValue DELETE_DISPLACED_GAS;

    /** How long a sealed zone must match the ambient before it dissolves, in s (CORE-052). */
    public static final ModConfigSpec.DoubleValue SEALED_ZONE_DISSOLVE_SECONDS;

    /** The most cells an enclosure can have when a zone forms (GEO-020, D-23). */
    public static final ModConfigSpec.IntValue ENCLOSURE_MAX_CELLS;

    /** The most {@link #ENCLOSURE_MAX_CELLS} can be: a hall 5 × 5 chunks wide and 16 blocks high. */
    public static final int ENCLOSURE_MAX_CELLS_LIMIT = EnclosureFill.SETTABLE_MAX_CELLS;

    /** The most cells the zones of one move can have (API-065, GEO-047). */
    public static final ModConfigSpec.IntValue RELOCATION_MAX_CELLS;

    /** How long a move may stay open before it is cancelled, in s (API-065, GEO-047). */
    public static final ModConfigSpec.IntValue RELOCATION_TIMEOUT_SECONDS;

    /** The permission level every {@code /zas} command needs (OPS-011). */
    public static final ModConfigSpec.IntValue PERMISSION_LEVEL;

    public static final ModConfigSpec.BooleanValue HEAT_ENABLED;
    public static final ModConfigSpec.DoubleValue FALLBACK_TRANSMITTANCE;
    public static final ModConfigSpec.IntValue WALL_THICKNESS;
    public static final ModConfigSpec.DoubleValue NEAR_VACUUM_PERCENT;
    public static final ModConfigSpec.DoubleValue EMISSIVITY;

    /** Seconds between profiling reports in the log; 0 turns the log off (OPS-012). */
    public static final ModConfigSpec.IntValue PROFILING_LOG_INTERVAL;

    /** {@code [units] litres_per_millibucket}: what 1 mB of another mod's gas is (COMPAT-143, MEK-002). */
    public static final ModConfigSpec.DoubleValue LITRES_PER_MILLIBUCKET;

    /** {@code /zas checker on|off} until the server stops; null follows the config (OPS-010). */
    private static volatile Boolean checkerOverride;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        MOVED_PRESET = builder.comment(
                        "Moved: the atmosphere preset is SS13 Framework's now, in " + Presets.FILE_NAME + ",",
                        "which sets every mod's options at once; /zas preset and /ss13 preset change it. A",
                        "HARDCORE left here by ZAS 1.2.0 is carried over once, then set back to NORMAL.")
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
                        "Moved: the options the preset sets are in " + Presets.FILE_NAME + ", under",
                        "[options.zas]. An override left here by ZAS 1.2.0 is carried over once, then set back",
                        "to \"" + PresetOption.FOLLOW_PRESET + "\".")
                .push("preset_options");
        Map<String, ModConfigSpec.ConfigValue<Object>> moved = new LinkedHashMap<>();
        for (PresetOption<?> option : PresetOptions.all()) {
            String key = option.id().getPath();
            moved.put(
                    key,
                    builder.comment("Moved to " + Presets.FILE_NAME + " as " + option.id() + ".")
                            .define(
                                    key,
                                    PresetOption.FOLLOW_PRESET,
                                    raw -> PresetOption.followsPreset(raw)
                                            || option.type().parse(raw).isPresent()));
        }
        MOVED_PRESET_OPTIONS = Collections.unmodifiableMap(moved);
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
        DELETE_DISPLACED_GAS = builder.comment(
                        "What happens to the gas in a cell a block fills. false: it stays in the zone, so the",
                        "zone's pressure rises. true: it is deleted, as ZAS does, so the pressure stays as it was.")
                .define("delete_displaced_gas", ZoneSettings.DEFAULT.deleteDisplacedGas());
        SEALED_ZONE_DISSOLVE_SECONDS = builder.comment(
                        "How long a sealed room's air must match the ambient before its zone is dissolved, in",
                        "seconds. A pinned zone, such as an air controller's, is never dissolved, nor is a room",
                        "holding a gas at half its lowest hazard threshold or more.")
                .defineInRange("sealed_zone_dissolve_seconds", ZoneSettings.DEFAULT.sealedDissolveSeconds(), 0.0, 1e9);
        ENCLOSURE_MAX_CELLS = builder.comment(
                        "The most cells (blocks of air) a sealed space can have and still become a zone. A larger",
                        "one counts as open air. A higher limit costs more time when a large roofed space or cave",
                        "is checked. Zones that formed under another limit stay as they are.")
                .defineInRange("enclosure_max_cells", EnclosureFill.DEFAULT_MAX_CELLS, 1, ENCLOSURE_MAX_CELLS_LIMIT);
        RELOCATION_MAX_CELLS = builder.comment(
                        "The most cells (blocks of air) the rooms of one move by another mod can have, such as a",
                        "ship taking off. A larger move fails, and the rooms stay where they are.")
                .defineInRange("relocation_max_cells", EnclosureFill.DEFAULT_MAX_CELLS, 0, 1 << 24);
        RELOCATION_TIMEOUT_SECONDS = builder.comment(
                        "How long another mod may take to move blocks whose rooms it asked to carry along, in",
                        "seconds. After that the move is cancelled, and the rooms carry on where they are.")
                .defineInRange("relocation_timeout_seconds", 30, 1, 3600);
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
        builder.push("units");
        LITRES_PER_MILLIBUCKET = builder.comment(
                        "How much gas 1 mB of another mod's gas is, a Mekanism chemical or a fluid that is a",
                        "gas: this many litres at 101.325 kPa and 293.15 K. At 1.0, 1,000 mB fill one block at",
                        "one atmosphere, and 1 mB is about 0.0416 mol. It is the same for every gas and every",
                        "mod, which keeps their recipes in balance.")
                .defineInRange(
                        "litres_per_millibucket",
                        Millibuckets.DEFAULT_LITRES_PER_MILLIBUCKET,
                        Millibuckets.MIN_LITRES_PER_MILLIBUCKET,
                        Millibuckets.MAX_LITRES_PER_MILLIBUCKET);
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

    /**
     * What happens to a filled cell's gas (CORE-029), and how long a sealed zone waits to dissolve
     * (CORE-052); the defaults while no world's config is loaded.
     */
    public static ZoneSettings zones() {
        return SPEC.isLoaded()
                ? new ZoneSettings(DELETE_DISPLACED_GAS.get(), SEALED_ZONE_DISSOLVE_SECONDS.get())
                : ZoneSettings.DEFAULT;
    }

    /** The most cells one move's zones can have (GEO-047); the default while no world's config is loaded. */
    public static int relocationMaxCells() {
        return SPEC.isLoaded() ? RELOCATION_MAX_CELLS.get() : EnclosureFill.DEFAULT_MAX_CELLS;
    }

    /** How long a move may stay open, in game ticks (GEO-047); the default while no config is loaded. */
    public static long relocationTimeoutTicks() {
        return 20L * (SPEC.isLoaded() ? RELOCATION_TIMEOUT_SECONDS.get() : 30);
    }

    /** The most cells an enclosure can have (GEO-020, D-23); the default while no world's config is loaded. */
    public static int enclosureMaxCells() {
        return SPEC.isLoaded() ? ENCLOSURE_MAX_CELLS.get() : EnclosureFill.DEFAULT_MAX_CELLS;
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

    /**
     * The litres of gas, at standard air's pressure and temperature, that 1 mB of another mod's gas
     * is (COMPAT-143); the default while no world's config is loaded.
     */
    public static double litresPerMillibucket() {
        return SPEC.isLoaded() ? LITRES_PER_MILLIBUCKET.get() : Millibuckets.DEFAULT_LITRES_PER_MILLIBUCKET;
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
