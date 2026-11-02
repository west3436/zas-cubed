package io.github.west3436.zas.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server config's {@code [breathing]} section (LIFE): how often breathing is checked, the O2
 * minimum and the respiration rate. None of them is preset-controlled: D-27 sets the same
 * respiration in both presets.
 *
 * <p>Each getter reads the loaded config on each call, so an edited file applies from the next
 * read, and gives the default while no world's config is loaded. Any thread.
 */
public final class BreathingConfig {
    /** Game ticks between breath checks by default: 2 s (LIFE-007). */
    public static final int DEFAULT_CHECK_INTERVAL_TICKS = 40;

    /** The O2 partial pressure below which an entity suffocates by default, in kPa (LIFE-003). */
    public static final double DEFAULT_OXYGEN_MINIMUM = 16.0;

    /** O2 one breather uses per hour by default, in mol (LIFE-042, D-27). */
    public static final double RESPIRATION_MOLES_PER_HOUR = 1.0;

    /** Game ticks between breath checks (LIFE-007). */
    public static ModConfigSpec.IntValue CHECK_INTERVAL_TICKS;

    /** The O2 minimum in kPa (LIFE-003). */
    public static ModConfigSpec.DoubleValue OXYGEN_MINIMUM;

    /** The respiration multiplier (LIFE-042). */
    public static ModConfigSpec.DoubleValue RESPIRATION_MULTIPLIER;

    private BreathingConfig() {}

    /** Defines the section; called once, by the server config. */
    static void define(ModConfigSpec.Builder builder) {
        builder.push("breathing");
        CHECK_INTERVAL_TICKS = builder.comment(
                        "Game ticks between checks of each breathing entity's breath and gas hazards (20 = 1 s).",
                        "Rates are per second, so a different interval changes smoothness and cost, not how",
                        "fast anything happens. Entities are spread across the interval.")
                .defineInRange("check_interval_ticks", DEFAULT_CHECK_INTERVAL_TICKS, 1, 1200);
        OXYGEN_MINIMUM = builder.comment(
                        "The O2 partial pressure of the breath, in kPa, below which an entity's air meter",
                        "drains: at the underwater rate in a vacuum, proportionally slower closer to the minimum.")
                .defineInRange("oxygen_minimum_kpa", DEFAULT_OXYGEN_MINIMUM, 0.0, 1000.0);
        RESPIRATION_MULTIPLIER = builder.comment(
                        "Multiplies respiration inside zones: each breather turns about 1 mol of O2 an hour into",
                        "the same moles of CO2. 0 turns respiration off.")
                .defineInRange("respiration_multiplier", 1.0, 0.0, 1_000_000.0);
        builder.pop();
    }

    /** Game ticks between breath checks. */
    public static int checkIntervalTicks() {
        return ZasServerConfig.SPEC.isLoaded() ? CHECK_INTERVAL_TICKS.get() : DEFAULT_CHECK_INTERVAL_TICKS;
    }

    /** The O2 minimum in kPa. */
    public static double oxygenMinimum() {
        return ZasServerConfig.SPEC.isLoaded() ? OXYGEN_MINIMUM.get() : DEFAULT_OXYGEN_MINIMUM;
    }

    /** O2 each breather uses per second, in mol, with the multiplier applied. */
    public static double respirationMolesPerSecond() {
        double multiplier = ZasServerConfig.SPEC.isLoaded() ? RESPIRATION_MULTIPLIER.get() : 1.0;
        return RESPIRATION_MOLES_PER_HOUR / 3600.0 * multiplier;
    }
}
