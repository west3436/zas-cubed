package io.github.west3436.zas.compat.aeronautics;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The adapter's server settings, in a file of its own: {@code config/zas-aeronautics-server.toml},
 * which a world's {@code serverconfig/zas-aeronautics-server.toml} overrides. It is registered on the
 * container the adapter is started with, and exists only where Create Aeronautics is installed.
 *
 * <p>Every value is read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read, with no restart: the lift factor at the
 * end of the next server tick, the seed at the next atmos tick.
 */
public final class AeroSettings {
    /** The config file's name. */
    public static final String FILE = "zas-aeronautics-server.toml";

    /** The whole file. */
    public static final ModConfigSpec SPEC;

    /** {@code [lift] follow_ambient}: whether lift follows the ambient's density (COMPAT-083). */
    public static final ModConfigSpec.BooleanValue FOLLOW_AMBIENT;

    /** What {@link #FOLLOW_AMBIENT} is unless set. */
    public static final boolean DEFAULT_FOLLOW_AMBIENT = true;

    /** {@code [ambient] seed_from_dimension_physics}: whether Sable's data seeds ambients (COMPAT-082). */
    public static final ModConfigSpec.BooleanValue SEED_FROM_DIMENSION_PHYSICS;

    /** What {@link #SEED_FROM_DIMENSION_PHYSICS} is unless set. */
    public static final boolean DEFAULT_SEED_FROM_DIMENSION_PHYSICS = true;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("lift");
        FOLLOW_AMBIENT = builder.comment(
                        "Whether the air pressure Create Aeronautics' balloons, wings and propellers feel",
                        "follows the density of the dimension's air: Sable's pressure times the air's",
                        "density over standard air's at 293.15 K. 1 in the Overworld, 0 in a vacuum.",
                        "false leaves Sable's pressure as it is everywhere.")
                .define("follow_ambient", DEFAULT_FOLLOW_AMBIENT);
        builder.pop();

        builder.push("ambient");
        SEED_FROM_DIMENSION_PHYSICS = builder.comment(
                        "Whether a dimension with no ambient of its own (no data pack definition and no",
                        "mod's provider) takes it from the base_pressure in Sable's dimension_physics",
                        "data: 0 is a vacuum, anything else standard air at base_pressure x 101.325 kPa",
                        "and 293.15 K. Lift there is Sable's pressure as it is, which already holds it.")
                .define("seed_from_dimension_physics", DEFAULT_SEED_FROM_DIMENSION_PHYSICS);
        builder.pop();

        SPEC = builder.build();
    }

    private AeroSettings() {}

    /** Registers the file. Called once, from {@link AeronauticsCompat#init}. */
    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE);
    }

    /**
     * Whether lift follows the ambient's density (COMPAT-083).
     *
     * @return the setting; the default while no server config is loaded
     */
    public static boolean followAmbient() {
        return SPEC.isLoaded() ? FOLLOW_AMBIENT.get() : DEFAULT_FOLLOW_AMBIENT;
    }

    /**
     * Whether Sable's {@code dimension_physics} data seeds ambients (COMPAT-082).
     *
     * @return the setting; the default while no server config is loaded
     */
    public static boolean seedFromDimensionPhysics() {
        return SPEC.isLoaded() ? SEED_FROM_DIMENSION_PHYSICS.get() : DEFAULT_SEED_FROM_DIMENSION_PHYSICS;
    }
}
