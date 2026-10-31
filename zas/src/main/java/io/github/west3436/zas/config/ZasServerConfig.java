package io.github.west3436.zas.config;

import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The per-world server config, {@code <world>/serverconfig/zas-server.toml} (OPS-001).
 *
 * <p>For now it holds only what R0 needs: the atmosphere preset, which selects ambient variants
 * (ENV-010), the dimensions excluded from simulation (ENV-007), and the atmos scheduler's timing
 * and debug switches (CORE-041, CORE-047, CORE-071, CORE-073). The full preset system,
 * with every preset-controlled option and per-option overrides, is OPS-007 and OPS-014 in R1.
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

    /** Whether the conservation checker runs. */
    public static boolean conservationChecker() {
        return SPEC.isLoaded() && CONSERVATION_CHECKER.get();
    }
}
