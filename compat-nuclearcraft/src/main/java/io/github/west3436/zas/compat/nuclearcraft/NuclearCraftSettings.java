package io.github.west3436.zas.compat.nuclearcraft;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The adapter's server settings, in a file of its own: {@code config/zas-nuclearcraft-server.toml},
 * which a world's {@code serverconfig/zas-nuclearcraft-server.toml} overrides. It is registered on
 * the container the adapter is started with, and exists only where NuclearCraft is installed.
 *
 * <p>Every value is read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read, with no restart.
 */
public final class NuclearCraftSettings {
    /** The config file's name. */
    public static final String FILE = "zas-nuclearcraft-server.toml";

    /** The whole file. */
    public static final ModConfigSpec SPEC;

    /** {@code [heat] joules_per_heat_unit}: how many joules one unit of NuclearCraft's heat is (COMPAT-153). */
    public static final ModConfigSpec.DoubleValue JOULES_PER_HEAT_UNIT;

    /** {@code [heat] max_watts}: the most heat one reactor gives its rooms (COMPAT-153). */
    public static final ModConfigSpec.DoubleValue MAX_WATTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("heat");
        JOULES_PER_HEAT_UNIT = builder.comment(
                        "How many joules of a room's heat one unit of NuclearCraft's heat (H) is. The heat",
                        "a fission reactor's heat sinks remove each tick goes into the rooms round the",
                        "reactor. At 1.0 a reactor whose sinks remove 1,200 H/t, twenty water sinks, warms",
                        "its room with 24,000 W. 0 turns it off.")
                .defineInRange(
                        "joules_per_heat_unit",
                        ReactorHeat.DEFAULT_JOULES_PER_HEAT_UNIT,
                        0.0,
                        ReactorHeat.MAX_JOULES_PER_HEAT_UNIT);
        MAX_WATTS = builder.comment(
                        "The most heat one fission reactor gives the rooms round it, in W. A large reactor",
                        "removes tens of thousands of H/t. At 50,000, as the Mekanism adapter holds its",
                        "Fusion Reactor to, a sealed room round a reactor at full power grows deadly in",
                        "minutes, not seconds. The rest is lost, as it is without ZAS. 0 is no ceiling.")
                .defineInRange("max_watts", ReactorHeat.DEFAULT_MAX_WATTS, 0.0, ReactorHeat.HIGHEST_MAX_WATTS);
        builder.pop();

        SPEC = builder.build();
    }

    private NuclearCraftSettings() {}

    /** Registers the file. Called once, from {@link NuclearCraftCompat#init}. */
    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE);
    }

    /**
     * How many joules one unit of NuclearCraft's heat is (COMPAT-153).
     *
     * @return J per H; the default while no server config is loaded
     */
    public static double joulesPerHeatUnit() {
        return SPEC.isLoaded() ? JOULES_PER_HEAT_UNIT.get() : ReactorHeat.DEFAULT_JOULES_PER_HEAT_UNIT;
    }

    /**
     * The most heat one reactor gives its rooms (COMPAT-153).
     *
     * @return W, where 0 is no ceiling; the default while no server config is loaded
     */
    public static double maxWatts() {
        return SPEC.isLoaded() ? MAX_WATTS.get() : ReactorHeat.DEFAULT_MAX_WATTS;
    }
}
