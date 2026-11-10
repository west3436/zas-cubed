package io.github.west3436.zas.compat.mekanism;

import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Which gas of a mixture may leave ZAS³ for Mekanism, and how much of it (MEK-013). A Mekanism tank
 * holds one chemical and a ZAS³ store holds a mixture, so something has to decide what a tube finds
 * in a pipe of air.
 *
 * <p>The rule: gas leaves only while one gas is at least a set share of the whole, by moles. Then
 * that gas alone leaves, and only as much of it as keeps what stays behind at that share. So:
 *
 * <ul>
 *   <li>a tube never draws one gas out of a mixture, which would be a filter that needs no power;
 *   <li>the trace of other gases in a nearly pure pipe never leaves, however much is drawn. A tube
 *       carries one chemical at a time, and a trace that got into it would block it;
 *   <li>the trace holds some of the main gas back with it: 99 times its own amount, at a share of
 *       0.99. A pipe of one gas alone gives all of it.
 * </ul>
 *
 * <p>The share is a server setting ({@link MekanismSettings#minPurity}); at 0 the rule is off and
 * each gas leaves on its own.
 *
 * <p>Nothing here applies on the way in: a chemical is one gas already, and enters whatever the
 * store holds.
 *
 * <p>Pure arithmetic: this class names nothing of the game's but ids, and nothing of Mekanism's.
 */
public final class GasPurity {
    /** The default share: a mixture gives gas only while it is at least 99 % one gas. */
    public static final double DEFAULT_MIN_SHARE = 0.99;

    private GasPurity() {}

    /**
     * What may leave a mixture.
     *
     * @param gas the gas
     * @param moles how much of it, above 0
     */
    public record Leaving(ResourceLocation gas, double moles) {}

    /**
     * The gas a mixture has most of. Of two with the same amount, the one whose id sorts first.
     *
     * @param moles the mixture, in moles of each gas
     * @return that gas, or empty if the mixture holds none
     */
    public static Optional<ResourceLocation> mainGas(Map<ResourceLocation, Double> moles) {
        ResourceLocation main = null;
        double most = 0;
        for (Map.Entry<ResourceLocation, Double> e : moles.entrySet()) {
            double n = e.getValue();
            if (n > most || n == most && main != null && e.getKey().compareTo(main) < 0) {
                main = e.getKey();
                most = n;
            }
        }
        return Optional.ofNullable(main);
    }

    /**
     * What may leave a mixture: its main gas, as far as the rest stays at the share. With x moles of
     * the main gas and y of everything else, that is x − y·s/(1 − s) moles for a share s, which is
     * all of it when there is nothing else.
     *
     * @param moles the mixture, in moles of each gas
     * @param minShare the least share the main gas must stay, above 0 and at most 1
     * @return the main gas and how much of it may leave, or empty if none may
     */
    public static Optional<Leaving> leaving(Map<ResourceLocation, Double> moles, double minShare) {
        ResourceLocation main = mainGas(moles).orElse(null);
        if (main == null) {
            return Optional.empty();
        }
        double others = 0;
        for (Map.Entry<ResourceLocation, Double> e : moles.entrySet()) {
            if (!e.getKey().equals(main)) {
                others += e.getValue();
            }
        }
        double there = moles.get(main);
        double held = others <= 0 ? 0 : minShare >= 1 ? there : others * minShare / (1 - minShare);
        return held < there ? Optional.of(new Leaving(main, there - held)) : Optional.empty();
    }
}
