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

    /** {@code [gear] scuba_protects}: whether scuba gear protects against pressure and temperature (MEK-031). */
    public static final ModConfigSpec.BooleanValue SCUBA_PROTECTS;

    /** What {@link #SCUBA_PROTECTS} is unless set: scuba gear gives breathing and filtering only. */
    public static final boolean DEFAULT_SCUBA_PROTECTS = false;

    /** {@code [venting] enabled}: whether what Mekanism voids is released as gas (MEK-020). */
    public static final ModConfigSpec.BooleanValue VENTING_ENABLED;

    /** {@code [venting] max_millibuckets_per_second}: the most one source releases in a second (MEK-021). */
    public static final ModConfigSpec.LongValue VENTING_MAX_PER_SECOND;

    /** {@code [venting] release_above_cap}: whether what is above the cap is released too (MEK-021). */
    public static final ModConfigSpec.BooleanValue VENTING_RELEASE_ABOVE_CAP;

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

        // Breathing gear (MEK-030 to MEK-034).
        builder.push("gear");
        SCUBA_PROTECTS = builder.comment(
                        "Whether Mekanism's scuba mask and tank, worn together, also protect the wearer",
                        "against every pressure and temperature, as a whole suit does. Off, they give",
                        "breathing and gas filtering only, as in Mekanism.")
                .define("scuba_protects", DEFAULT_SCUBA_PROTECTS);
        builder.pop();

        // Sections of the adapter's other parts go here, each in a block of its own.

        builder.push("venting");
        VENTING_ENABLED = builder.comment(
                        "Whether a chemical that Mekanism voids is released as gas where it is voided, if",
                        "the chemical map makes it a gas: a tank or machine set to dump, a dump button, a",
                        "gauge dropper, a broken tube. false leaves all of it to Mekanism, which deletes it.",
                        "An exhaust port works either way.")
                .define("enabled", VentBudget.DEFAULT_ENABLED);
        VENTING_MAX_PER_SECOND = builder.comment(
                        "The most one source releases in a second, in mB. A source is one machine, one",
                        "multiblock, one broken tube or one player's gauge dropper. An Ultimate chemical",
                        "tank set to dump sheds 409,600 mB a second, which at 1 L per mB is 409.6 cubic",
                        "metres of gas. What is above the cap is deleted, as Mekanism does without ZAS.",
                        "An exhaust port is a source too: it takes no more than this in a second, and what",
                        "is above it stays in the tube or machine.")
                .defineInRange("max_millibuckets_per_second", VentBudget.DEFAULT_MAX_PER_SECOND, 0L, Long.MAX_VALUE);
        VENTING_RELEASE_ABOVE_CAP = builder.comment(
                        "true releases what is above the cap as well, so nothing voided is deleted and a",
                        "large dump can raise a room's pressure a great deal at once. An exhaust port then",
                        "takes all it is given.")
                .define("release_above_cap", VentBudget.DEFAULT_RELEASE_ABOVE_CAP);
        builder.pop();

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
     * Whether scuba gear protects against pressure and temperature as it is set (MEK-031).
     *
     * @return true if it does; false, the default, while no server config is loaded
     */
    public static boolean scubaProtects() {
        return SPEC.isLoaded() ? SCUBA_PROTECTS.get() : DEFAULT_SCUBA_PROTECTS;
    }

    /**
     * Whether what Mekanism voids is released as gas (MEK-020).
     *
     * @return the setting; the default while no server config is loaded
     */
    public static boolean ventingEnabled() {
        return SPEC.isLoaded() ? VENTING_ENABLED.get() : VentBudget.DEFAULT_ENABLED;
    }

    /**
     * The most one source releases in a second (MEK-021).
     *
     * @return the cap in mB, 0 or more; the default while no server config is loaded
     */
    public static long ventingMaxPerSecond() {
        return SPEC.isLoaded() ? VENTING_MAX_PER_SECOND.get() : VentBudget.DEFAULT_MAX_PER_SECOND;
    }

    /**
     * Whether what is above the cap is released as well (MEK-021).
     *
     * @return the setting; the default while no server config is loaded
     */
    public static boolean ventingReleasesAboveCap() {
        return SPEC.isLoaded() ? VENTING_RELEASE_ABOVE_CAP.get() : VentBudget.DEFAULT_RELEASE_ABOVE_CAP;
    }
}
