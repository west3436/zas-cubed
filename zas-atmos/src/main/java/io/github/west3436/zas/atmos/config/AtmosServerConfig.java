package io.github.west3436.zas.atmos.config;

import io.github.west3436.zas.atmos.failure.ContainerFailure;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The content mod's server config, {@code zas_atmos-server.toml} (per world, like the framework's).
 * Its {@code [power]} section holds the FE values, all starting points for playtesting (D-37,
 * CONT-065).
 */
public final class AtmosServerConfig {
    public static final ModConfigSpec SPEC;

    /** The vent's draw while it runs, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue VENT_DRAW;

    /** The heater's draw from its battery at full power, in FE/t; less in proportion below it (CONT-087). */
    public static final ModConfigSpec.IntValue HEATER_DRAW;

    /** A scrubber's draw while it scrubs or siphons, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue SCRUBBER_DRAW;

    /** A scrubber's draw in panic, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue SCRUBBER_PANIC_DRAW;

    /** The most heat a heater adds, in W (CONT-087). */
    public static final ModConfigSpec.IntValue HEATER_WATTS;

    /** The air conditioner's draw from its battery at full power, in FE/t; less in proportion below it (CONT-087). */
    public static final ModConfigSpec.IntValue AIR_CONDITIONER_DRAW;

    /** The most heat an air conditioner takes, in W (CONT-087). */
    public static final ModConfigSpec.IntValue AIR_CONDITIONER_WATTS;

    /** The portable scrubber's draw from its battery while it moves gas, in FE/t (CONT-018). */
    public static final ModConfigSpec.IntValue PORTABLE_SCRUBBER_DRAW;

    /** The portable pump's draw from its battery while it moves gas, in FE/t (CONT-018). */
    public static final ModConfigSpec.IntValue PORTABLE_PUMP_DRAW;

    /** The recharger's draw from its controller while it charges, in FE/t (CONT-113). */
    public static final ModConfigSpec.IntValue RECHARGER_DRAW;

    /** The igniter's draw from its controller while it gets a redstone signal, in FE/t (CONT-105). */
    public static final ModConfigSpec.IntValue IGNITER_DRAW;

    /** The pressure pump's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue PRESSURE_PUMP_DRAW;

    /** What a volume pump draws from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue VOLUME_PUMP_DRAW;

    /** The filter's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue FILTER_DRAW;

    /** The mixer's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue MIXER_DRAW;

    /** What the recharger puts into its battery while it charges, in FE/t (CONT-113). */
    public static final ModConfigSpec.IntValue RECHARGER_RATE;

    /** The area air controller's own draw, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue CONTROLLER_DRAW;

    /** The fire alarm's draw, in FE/t (CONT-044, CONT-065). */
    public static final ModConfigSpec.IntValue FIRE_ALARM_DRAW;

    /** The area air controller's buffer, in FE (CONT-062). */
    public static final ModConfigSpec.IntValue CONTROLLER_BUFFER;

    /** What the generator makes while it burns, in FE/t (CONT-061). */
    public static final ModConfigSpec.IntValue GENERATOR_OUTPUT;

    /** The generator's own store, in FE. */
    public static final ModConfigSpec.IntValue GENERATOR_BUFFER;

    /** How long one coal burns in the generator, in seconds; other fuels scale by burn time (CONT-066). */
    public static final ModConfigSpec.IntValue GENERATOR_SECONDS_PER_FUEL;

    /** The pressure fall within one atmos tick that alarms a controller at once, in kPa (CONT-043). */
    public static final ModConfigSpec.DoubleValue BREACH_DROP_KPA;

    /** The most power a tank's or canister's explosion can have (CONT-109). TNT is 4. */
    public static final ModConfigSpec.DoubleValue EXPLOSION_POWER_CAP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Power: what each device draws while it runs, in FE/t (CONT-065), and the power sources.")
                .push("power");
        VENT_DRAW = builder.comment("Vent").defineInRange("vent", 10, 0, 100_000);
        SCRUBBER_DRAW =
                builder.comment("Scrubber, while it scrubs or siphons").defineInRange("scrubber", 15, 0, 100_000);
        SCRUBBER_PANIC_DRAW = builder.comment("Scrubber, in panic").defineInRange("scrubber_panic", 30, 0, 100_000);
        HEATER_DRAW = builder.comment(
                        "Heater, from its battery while it heats at full power; in proportion below it (CONT-087)")
                .defineInRange("heater", 60, 0, 100_000);
        HEATER_WATTS = builder.comment("The most heat a heater adds, in W, not FE/t (CONT-087)")
                .defineInRange("heater_watts", 5_000, 0, 1_000_000);
        AIR_CONDITIONER_DRAW = builder.comment(
                        "Air conditioner, from its battery while it cools at full power; in proportion below it (CONT-087)")
                .defineInRange("air_conditioner", 60, 0, 100_000);
        AIR_CONDITIONER_WATTS = builder.comment("The most heat an air conditioner takes, in W, not FE/t (CONT-087)")
                .defineInRange("air_conditioner_watts", 5_000, 0, 1_000_000);
        PORTABLE_SCRUBBER_DRAW = builder.comment("Portable scrubber, from its battery while it moves gas (CONT-018)")
                .defineInRange("portable_scrubber", 15, 0, 100_000);
        PORTABLE_PUMP_DRAW = builder.comment("Portable pump, from its battery while it moves gas (CONT-018)")
                .defineInRange("portable_pump", 20, 0, 100_000);
        RECHARGER_DRAW = builder.comment("Recharger, from its controller while it charges (CONT-113)")
                .defineInRange("recharger", 40, 0, 100_000);
        RECHARGER_RATE = builder.comment("What a recharger puts into its battery while it charges, in FE/t (CONT-113)")
                .defineInRange("recharger_rate", 28, 0, 100_000);
        IGNITER_DRAW = builder.comment("Igniter, from its controller while it gets a redstone signal (CONT-105)")
                .defineInRange("igniter", 20, 0, 100_000);
        PRESSURE_PUMP_DRAW = builder.comment("Pressure pump, from its controller while it is on (CONT-065)")
                .defineInRange("pressure_pump", 20, 0, 100_000);
        VOLUME_PUMP_DRAW = builder.comment("Volume pump, from its controller while it is on (CONT-065)")
                .defineInRange("volume_pump", 20, 0, 100_000);
        FILTER_DRAW = builder.comment("Filter, from its controller while it is on (CONT-065)")
                .defineInRange("filter", 20, 0, 100_000);
        MIXER_DRAW = builder.comment("Mixer, from its controller while it is on (CONT-065)")
                .defineInRange("mixer", 20, 0, 100_000);
        CONTROLLER_DRAW = builder.comment("Area air controller, its own base draw (CONT-065)")
                .defineInRange("area_air_controller", 5, 0, 100_000);
        FIRE_ALARM_DRAW = builder.comment("Fire alarm, from its controller (CONT-044)")
                .defineInRange("fire_alarm", 1, 0, 100_000);
        CONTROLLER_BUFFER = builder.comment(
                        "The area air controller's buffer, in FE (CONT-062). It fills from FE blocks next to it.")
                .defineInRange("area_air_controller_buffer", 10_000, 1_000, 1_000_000);
        GENERATOR_OUTPUT = builder.comment("What the generator makes while it burns, in FE/t (CONT-061)")
                .defineInRange("generator_output", 40, 0, 100_000);
        GENERATOR_BUFFER = builder.comment("The generator's own store, in FE. It stops taking new fuel when full.")
                .defineInRange("generator_buffer", 8_000, 1_000, 30_000);
        GENERATOR_SECONDS_PER_FUEL = builder.comment(
                        "How long one coal burns in the generator, in seconds. Other furnace fuels burn",
                        "for this times their furnace burn time over coal's (CONT-066)")
                .defineInRange("generator_seconds_per_fuel", 80, 1, 1_600);
        builder.pop();

        builder.comment("The area air controller (CONT-040, CONT-043).").push("controller");
        BREACH_DROP_KPA = builder.comment(
                        "A fall in its zone's pressure, in kPa within one atmos tick, that sends a controller to danger 2 at once, as a",
                        "breach does (CONT-043). Only for presets with a pressure min2, and only while the pressure is below min1. 0 turns it off.")
                .defineInRange("breach_drop_kpa", 10.0, 0.0, 1_000.0);
        builder.pop();

        builder.comment("How tanks and canisters fail (CONT-038, CONT-102).").push("containers");
        EXPLOSION_POWER_CAP = builder.comment(
                        "The most power a tank's or canister's explosion can have (CONT-109). TNT is 4.")
                .defineInRange("explosion_power_cap", ContainerFailure.DEFAULT_POWER_CAP, 0.0, 64.0);
        builder.pop();
        SPEC = builder.build();
    }

    private AtmosServerConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /** A config value, or its default before the config loads. */
    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** A config value, or its default before the config loads. */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
