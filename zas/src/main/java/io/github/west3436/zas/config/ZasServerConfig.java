package io.github.west3436.zas.config;

import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config, {@code config/zas-server.toml}, which a world's {@code
 * serverconfig/zas-server.toml} overrides: the server tier of
 * OPS-001's three (with {@link ZasCommonConfig} and {@link ZasClientConfig}).
 *
 * <p>For now it holds what R0 needs: the atmosphere preset, which selects ambient variants
 * (ENV-010), the dimensions excluded from simulation (ENV-007), the atmos scheduler's timing and
 * debug switches (CORE-041, CORE-047, CORE-071, CORE-073) under {@code [atmos]}, the permission
 * level of the {@code /zas} commands (OPS-011) under {@code [commands]}, and the periodic profiling
 * log (OPS-012) under {@code [profiling]}. The full preset system, with every preset-controlled
 * option and per-option overrides, is OPS-007 and OPS-014 in R1; the sharing table, thresholds and
 * hazard toggles join this file as their features land.
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

    /** The permission level every {@code /zas} command needs (OPS-011). */
    public static final ModConfigSpec.IntValue PERMISSION_LEVEL;

    /** Seconds between profiling reports in the log; 0 turns the log off (OPS-012). */
    public static final ModConfigSpec.IntValue PROFILING_LOG_INTERVAL;

    /** {@code /zas checker on|off} until the server stops; null follows the config (OPS-010). */
    private static volatile Boolean checkerOverride;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PRESET = builder.comment(
                        "Atmosphere difficulty preset: NORMAL or HARDCORE.",
                        "It selects the preset variant of each dimension's ambient atmosphere.")
                .defineEnum("preset", AtmospherePreset.NORMAL);
        EXCLUDED_DIMENSIONS = builder.comment(
                        "Dimension ids excluded from simulation, such as \"minecraft:the_end\".",
                        "No zones form there; queries still return the dimension's ambient.")
                .defineListAllowEmpty(
                        "excluded_dimensions",
                        List.of(),
                        () -> "",
                        o -> o instanceof String s && ResourceLocation.tryParse(s) != null);
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
                        "Results are identical either way.")
                .define("single_threaded", false);
        CONSERVATION_CHECKER = builder.comment(
                        "Debug: check every atmos tick that gas changed only through known sources",
                        "and sinks, and log any discrepancy.")
                .define("conservation_checker", false);
        builder.pop();
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

    /** The active preset; {@link AtmospherePreset#NORMAL} while no world's config is loaded. */
    public static AtmospherePreset preset() {
        return SPEC.isLoaded() ? PRESET.get() : AtmospherePreset.NORMAL;
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

    /** The server-thread budget in nanoseconds. */
    public static long budgetNanos() {
        return (SPEC.isLoaded() ? SERVER_BUDGET_MS.get() : 10) * 1_000_000L;
    }

    /** Whether ticks run on the server thread. */
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
