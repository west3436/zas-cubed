package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.reaction.Reactions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * What the gas turbine can burn, from the engine's own reactions through the API (CONT-160, CONT-164):
 * no gas is named here. The fuels are the gases in {@link GasTags#FLAMMABLE} and the oxidizers those
 * in {@link GasTags#OXIDIZER}, as the fire code reads them, and a fuel and an oxidizer go together if
 * a spark burns a mixture of the two in {@link Reactions#applyStandalone}. The ratio is what that
 * burn used: mol of oxidizer per mol of fuel, so it follows whatever reactions the server loads.
 */
public final class TurbineFuels {
    /** The fuel's mole fractions a test burn tries, in order, until one burns: inside most flammability limits. */
    private static final double[] TRIES = {0.1, 0.2, 0.3};

    /** The test burn's gas: a few mol in a few litres, so one second's parcel burns all of it. */
    private static final double TEST_MOLES = 2;

    private static final double TEST_LITRES = 20;
    private static final double TEST_KELVIN = 293.15;

    private TurbineFuels() {}

    /** The fuels that burn with at least one oxidizer, by id. */
    public static List<ResourceLocation> fuels() {
        return burning(GasTags.FLAMMABLE, GasTags.OXIDIZER, true);
    }

    /** The oxidizers that burn at least one fuel, by id. */
    public static List<ResourceLocation> oxidizers() {
        return burning(GasTags.OXIDIZER, GasTags.FLAMMABLE, false);
    }

    private static List<ResourceLocation> burning(
            GasTag mine, GasTag other, boolean fuel) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation gas : sorted(mine)) {
            for (ResourceLocation partner : sorted(other)) {
                if (Double.isFinite(fuel ? ratio(gas, partner) : ratio(partner, gas))) {
                    out.add(gas);
                    break;
                }
            }
        }
        return List.copyOf(out);
    }

    private static List<ResourceLocation> sorted(GasTag tag) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation gas : GasTags.gases(tag)) {
            if (GasProperties.isDefined(gas)) {
                out.add(gas);
            }
        }
        out.sort(ResourceLocation::compareTo);
        return out;
    }

    /**
     * Mol of an oxidizer a mol of a fuel burns with, from a test burn of the two alone.
     *
     * @param fuel the fuel
     * @param oxidizer the oxidizer
     * @return the ratio, above 0, or NaN if they don't burn together
     */
    public static double ratio(ResourceLocation fuel, ResourceLocation oxidizer) {
        if (fuel == null || oxidizer == null || fuel.equals(oxidizer)) {
            return Double.NaN;
        }
        for (double x : TRIES) {
            Map<ResourceLocation, Double> moles = new TreeMap<>();
            moles.put(fuel, x * TEST_MOLES);
            moles.put(oxidizer, (1 - x) * TEST_MOLES);
            GasMixture before = new GasMixture(moles, TEST_KELVIN, TEST_LITRES);
            GasMixture after = Reactions.applyStandalone(before, 1, true);
            double burnt = before.moles(fuel) - after.moles(fuel);
            double used = before.moles(oxidizer) - after.moles(oxidizer);
            if (burnt > 1e-6 && used > 1e-9) {
                return used / burnt;
            }
        }
        return Double.NaN;
    }

    /**
     * Whether a mixture would burn if sparked now: a spark in {@link Reactions#applyStandalone} burns
     * some of its fuel.
     *
     * @param chamber the chamber's gas
     * @param fuel the fuel to look for
     * @return true if a spark would burn some of it
     */
    public static boolean wouldIgnite(GasMixture chamber, ResourceLocation fuel) {
        if (fuel == null || !(chamber.moles(fuel) > 0)) {
            return false;
        }
        GasMixture after = Reactions.applyStandalone(chamber, 0.05, true);
        return after.moles(fuel) < chamber.moles(fuel) - 1e-9;
    }
}
