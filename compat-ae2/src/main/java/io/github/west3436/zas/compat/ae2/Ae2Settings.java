package io.github.west3436.zas.compat.ae2;

import io.github.west3436.zas.api.gas.StandardAir;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The adapter's server settings, in a file of its own: {@code config/zas-ae2-server.toml}, which a
 * world's {@code serverconfig/zas-ae2-server.toml} overrides. It is registered by {@link Ae2Compat},
 * so the file exists only where AE2 is installed.
 *
 * <p>Every value is read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read, with no restart.
 */
public final class Ae2Settings {
    /** The config file's name. */
    public static final String FILE = "zas-ae2-server.toml";

    /** What {@link #LEAVING_TEMPERATURE} is unless set: standard air's, 293.15 K (Q-26). */
    public static final double DEFAULT_LEAVING_TEMPERATURE = StandardAir.TEMPERATURE;

    /** The least {@link #LEAVING_TEMPERATURE} can be set to, in K. */
    public static final double MIN_LEAVING_TEMPERATURE = 3.0;

    /** The most {@link #LEAVING_TEMPERATURE} can be set to, in K. */
    public static final double MAX_LEAVING_TEMPERATURE = 10_000.0;

    /** The whole file. */
    public static final ModConfigSpec SPEC;

    /** {@code [gas] leaving_temperature}: the temperature gas leaves ME storage at (COMPAT-072). */
    public static final ModConfigSpec.DoubleValue LEAVING_TEMPERATURE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("gas");
        LEAVING_TEMPERATURE = builder.comment(
                        "ME storage keeps how much of each gas it holds, not how hot it is. Gas it gives to a",
                        "block, through an export bus or a storage bus, arrives at this temperature, in K,",
                        "and mixes with what the block holds. The default is standard air's, 293.15 K.")
                .defineInRange(
                        "leaving_temperature",
                        DEFAULT_LEAVING_TEMPERATURE,
                        MIN_LEAVING_TEMPERATURE,
                        MAX_LEAVING_TEMPERATURE);
        builder.pop();
        SPEC = builder.build();
    }

    private Ae2Settings() {}

    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE);
    }

    /**
     * The temperature gas leaves ME storage at (COMPAT-072).
     *
     * @return kelvin; the default while no server config is loaded
     */
    public static double leavingTemperature() {
        return SPEC.isLoaded() ? LEAVING_TEMPERATURE.get() : DEFAULT_LEAVING_TEMPERATURE;
    }
}
