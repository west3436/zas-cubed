package io.github.west3436.zas.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client config, {@code config/zas-client.toml}: the client tier of OPS-001, for visuals and
 * display units. The simulation always works in kPa and K (A-04); these settings change only how
 * values are shown on this client. A dedicated server registers it but never loads it.
 */
public final class ZasClientConfig {
    public static final ModConfigSpec SPEC;

    /** The unit pressures are shown in. */
    public static final ModConfigSpec.EnumValue<PressureUnit> PRESSURE_UNIT;

    /** The unit temperatures are shown in. */
    public static final ModConfigSpec.EnumValue<TemperatureUnit> TEMPERATURE_UNIT;

    /** Whether mobs and players a breach flings off their feet tumble (CLIENT-029). */
    public static final ModConfigSpec.BooleanValue TUMBLE;

    /** A display unit for pressure. */
    public enum PressureUnit {
        KPA("kPa", 1.0),
        ATM("atm", 1 / 101.325),
        BAR("bar", 0.01),
        PSI("psi", 1 / 6.894_757);

        private final String symbol;
        private final double perKpa;

        PressureUnit(String symbol, double perKpa) {
            this.symbol = symbol;
            this.perKpa = perKpa;
        }

        public String symbol() {
            return symbol;
        }

        /** A pressure in kPa, in this unit. */
        public double fromKpa(double kpa) {
            return kpa * perKpa;
        }
    }

    /** A display unit for temperature. */
    public enum TemperatureUnit {
        KELVIN("K"),
        CELSIUS("°C"),
        FAHRENHEIT("°F");

        private final String symbol;

        TemperatureUnit(String symbol) {
            this.symbol = symbol;
        }

        public String symbol() {
            return symbol;
        }

        /** A temperature in K, in this unit. */
        public double fromKelvin(double kelvin) {
            return switch (this) {
                case KELVIN -> kelvin;
                case CELSIUS -> kelvin - 273.15;
                case FAHRENHEIT -> (kelvin - 273.15) * 9 / 5 + 32;
            };
        }
    }

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("units");
        PRESSURE_UNIT = builder.comment("The unit pressures are shown in: KPA, ATM, BAR or PSI.")
                .defineEnum("pressure", PressureUnit.KPA);
        TEMPERATURE_UNIT = builder.comment("The unit temperatures are shown in: KELVIN, CELSIUS or FAHRENHEIT.")
                .defineEnum("temperature", TemperatureUnit.CELSIUS);
        builder.pop();
        builder.push("effects");
        TUMBLE = builder.comment("Mobs and players a breach flings off their feet tumble through the air.")
                .define("tumble", true);
        builder.pop();
        SPEC = builder.build();
    }

    private ZasClientConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    /** The configured pressure unit; kPa before the config loads, and on a dedicated server. */
    public static PressureUnit pressureUnit() {
        return SPEC.isLoaded() ? PRESSURE_UNIT.get() : PressureUnit.KPA;
    }

    /** Whether flung bodies tumble (CLIENT-029); on before the config loads. */
    public static boolean tumble() {
        return !SPEC.isLoaded() || TUMBLE.get();
    }

    /** The configured temperature unit; °C before the config loads, and on a dedicated server. */
    public static TemperatureUnit temperatureUnit() {
        return SPEC.isLoaded() ? TEMPERATURE_UNIT.get() : TemperatureUnit.CELSIUS;
    }
}
