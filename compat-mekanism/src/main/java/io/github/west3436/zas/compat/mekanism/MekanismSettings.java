package io.github.west3436.zas.compat.mekanism;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The adapter's server settings, in a file of its own: {@code config/zas-mekanism-server.toml},
 * which a world's {@code serverconfig/zas-mekanism-server.toml} overrides. It is registered on the
 * container the adapter is started with, so the adapter needs nothing of ZAS³'s but the public API,
 * and the file exists only where Mekanism is installed.
 *
 * <p>Every value is read from the loaded config on each call. NeoForge reloads the file when it
 * changes on disk, so an edit applies from the next read, with no restart.
 *
 * <p><b>Adding a setting.</b> Each part of the adapter has a section. Add a field for the value,
 * define it in the static block inside a {@code push}/{@code pop} of that part's section, keeping
 * each part's lines together, and read it through a method that falls back to the default while
 * the config isn't loaded, as {@link #litresPerMillibucket} does.
 */
public final class MekanismSettings {
    /** The config file's name. */
    public static final String FILE = "zas-mekanism-server.toml";

    /** The whole file. */
    public static final ModConfigSpec SPEC;

    /** {@code [units] litres_per_millibucket}: the mB to mol factor (MEK-002). */
    public static final ModConfigSpec.DoubleValue LITRES_PER_MILLIBUCKET;

    /** {@code [connection] min_purity}: how pure a mixture must be to leave for Mekanism (MEK-013). */
    public static final ModConfigSpec.DoubleValue MIN_PURITY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("units");
        LITRES_PER_MILLIBUCKET = builder.comment(
                        "How much gas 1 mB of a Mekanism chemical is: this many litres at 101.325 kPa and",
                        "293.15 K. At 1.0, 1,000 mB fill one block at one atmosphere, and 1 mB is about",
                        "0.0416 mol. It is the same for every chemical, which keeps Mekanism's recipes in",
                        "balance.")
                .defineInRange(
                        "litres_per_millibucket",
                        ChemicalUnits.DEFAULT_LITRES_PER_MILLIBUCKET,
                        ChemicalUnits.MIN_LITRES_PER_MILLIBUCKET,
                        ChemicalUnits.MAX_LITRES_PER_MILLIBUCKET);
        builder.pop();

        // ---- The connection to tubes, tanks and machines (MEK-013) ----
        builder.push("connection");
        MIN_PURITY = builder.comment(
                        "A Mekanism tank holds one chemical, and a ZAS³ pipe holds a mixture. Gas leaves a",
                        "pipe for a tube, tank or machine only while one gas is at least this share of what",
                        "the pipe holds, by moles. Then that gas alone leaves, and only as much of it as",
                        "keeps the rest at this share. At 0.99, a pipe of air gives nothing, a pipe of",
                        "oxygen gives all of it, and a pipe of oxygen with a trace of something else keeps",
                        "the trace and 99 times as much oxygen.",
                        "Without the rule a tube would pick one gas out of a mixture, as a filter does, but",
                        "with no power. 0 turns the rule off: each gas then leaves on its own.",
                        "A chemical coming from Mekanism always enters, whatever the pipe holds.")
                .defineInRange("min_purity", GasPurity.DEFAULT_MIN_SHARE, 0.0, 1.0);
        builder.pop();

        // Sections of the adapter's other parts go here, each in a block of its own.

        SPEC = builder.build();
    }

    private MekanismSettings() {}

    /** Registers the file. Called once, from {@link MekanismCompat#init}. */
    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC, FILE);
    }

    /**
     * The unit factor as it is set: the litres of gas, at standard air's pressure and temperature,
     * that 1 mB of a chemical is (MEK-002).
     *
     * @return litres per mB; the default while no server config is loaded
     */
    public static double litresPerMillibucket() {
        return SPEC.isLoaded() ? LITRES_PER_MILLIBUCKET.get() : ChemicalUnits.DEFAULT_LITRES_PER_MILLIBUCKET;
    }

    /**
     * The unit factor as conversions use it (MEK-002).
     *
     * @return moles per mB
     */
    public static double molesPerMillibucket() {
        return ChemicalUnits.molesPerMillibucket(litresPerMillibucket());
    }

    /**
     * The least share one gas must be, and stay, of a mixture for that gas to leave it for Mekanism
     * (MEK-013, {@link GasPurity}).
     *
     * @return the share, from 0 to 1, where 0 turns the rule off; the default while no server
     *     config is loaded
     */
    public static double minPurity() {
        return SPEC.isLoaded() ? MIN_PURITY.get() : GasPurity.DEFAULT_MIN_SHARE;
    }
}
