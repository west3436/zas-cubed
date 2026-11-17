package io.github.west3436.zas.compat.pneumaticcraft;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The adapter's server settings, in a file of its own: {@code config/zas-pneumaticcraft-server.toml},
 * which a world's {@code serverconfig/zas-pneumaticcraft-server.toml} overrides. It is registered on
 * the container the adapter is started with, and exists only where PneumaticCraft is installed.
 *
 * <p>Every value is read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read, with no restart.
 */
public final class PncSettings {
    /** The config file's name. */
    public static final String FILE = "zas-pneumaticcraft-server.toml";

    /** The whole file. */
    public static final ModConfigSpec SPEC;

    /** {@code [air] moles_per_millilitre}: what one unit of PneumaticCraft's air is (Q-24). */
    public static final ModConfigSpec.DoubleValue MOLES_PER_ML;

    /** {@code [air] leaks_fill_rooms}: whether a machine's leak adds its air to the room (COMPAT-012). */
    public static final ModConfigSpec.BooleanValue LEAKS_FILL_ROOMS;

    /** What {@link #LEAKS_FILL_ROOMS} is unless set. */
    public static final boolean DEFAULT_LEAKS_FILL_ROOMS = true;

    /** {@code [heat] joules_per_heat_unit}: how many joules one unit of PneumaticCraft's heat is (Q-25). */
    public static final ModConfigSpec.DoubleValue JOULES_PER_HEAT_UNIT;

    /** {@code [heat] face_conductance}: what one face of a heat block conducts to a zone's gas (COMPAT-013). */
    public static final ModConfigSpec.DoubleValue FACE_CONDUCTANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("air");
        MOLES_PER_ML = builder.comment(
                        "How much gas one mL of PneumaticCraft's air is, in moles. PneumaticCraft counts",
                        "air as bar above the outside times mL, so one mL is 1 mL at 1 bar: by default",
                        "100 kPa x 0.001 L / (R x 293.15 K), about 0.000041 mol. A leak and the air",
                        "internals draw from Pneumatic Armor are standard air at 293.15 K.")
                .defineInRange(
                        "moles_per_millilitre",
                        AirUnits.DEFAULT_MOLES_PER_ML,
                        AirUnits.MIN_MOLES_PER_ML,
                        AirUnits.MAX_MOLES_PER_ML);
        LEAKS_FILL_ROOMS = builder.comment(
                        "Whether the air a PneumaticCraft machine or tube leaks into a sealed room is",
                        "added to that room. false leaves it to PneumaticCraft, which deletes it.")
                .define("leaks_fill_rooms", DEFAULT_LEAKS_FILL_ROOMS);
        builder.pop();

        builder.push("heat");
        JOULES_PER_HEAT_UNIT = builder.comment(
                        "How many joules of a zone's heat one unit of PneumaticCraft's heat is. A block",
                        "of thermal capacity C warms by 1/C K for each unit. At 100, as PneumaticCraft's",
                        "own Mekanism integration counts it, a Compressed Iron Block holds 1,000 J/K.")
                .defineInRange(
                        "joules_per_heat_unit",
                        HeatExchange.DEFAULT_JOULES_PER_HEAT_UNIT,
                        HeatExchange.MIN_JOULES_PER_HEAT_UNIT,
                        HeatExchange.MAX_JOULES_PER_HEAT_UNIT);
        FACE_CONDUCTANCE = builder.comment(
                        "What one face of a PneumaticCraft heat block conducts to the gas of a zone it",
                        "touches, in W/K, before the block's own thermal resistance. Heat moves both",
                        "ways and none is made or lost. 0 turns the exchange off. PneumaticCraft's own",
                        "loss to the air beside a block is left as it is.")
                .defineInRange(
                        "face_conductance",
                        HeatExchange.DEFAULT_FACE_CONDUCTANCE,
                        0.0,
                        HeatExchange.MAX_FACE_CONDUCTANCE);
        builder.pop();

        SPEC = builder.build();
    }

    private PncSettings() {}

    /** Registers the file. Called once, from {@link PneumaticCraftCompat#init}. */
    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE);
    }

    /**
     * What one unit of PneumaticCraft's air is (Q-24).
     *
     * @return moles per mL, at least 0; the default while no server config is loaded
     */
    public static double molesPerMillilitre() {
        return SPEC.isLoaded() ? MOLES_PER_ML.get() : AirUnits.DEFAULT_MOLES_PER_ML;
    }

    /**
     * Whether a machine's leak adds its air to the room it leaks into (COMPAT-012).
     *
     * @return the setting; the default while no server config is loaded
     */
    public static boolean leaksFillRooms() {
        return SPEC.isLoaded() ? LEAKS_FILL_ROOMS.get() : DEFAULT_LEAKS_FILL_ROOMS;
    }

    /**
     * How many joules one unit of PneumaticCraft's heat is (Q-25).
     *
     * @return joules per unit, above 0; the default while no server config is loaded
     */
    public static double joulesPerHeatUnit() {
        return SPEC.isLoaded() ? JOULES_PER_HEAT_UNIT.get() : HeatExchange.DEFAULT_JOULES_PER_HEAT_UNIT;
    }

    /**
     * What one face of a heat block conducts to a zone's gas (COMPAT-013).
     *
     * @return the conductance in W/K, at least 0; the default while no server config is loaded
     */
    public static double faceConductance() {
        return SPEC.isLoaded() ? FACE_CONDUCTANCE.get() : HeatExchange.DEFAULT_FACE_CONDUCTANCE;
    }
}
