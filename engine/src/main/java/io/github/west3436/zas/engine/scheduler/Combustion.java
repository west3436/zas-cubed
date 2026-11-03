package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.gas.GasMixture;

/**
 * The chemistry hotspots need (FIRE-010, FIRE-011, FIRE-014, FIRE-017): whether a mixture can
 * burn, at what temperature it ignites by itself, and burning a parcel of it. Implementations are
 * pure functions of their arguments, called on the worker (architecture §3.5, §3.6).
 *
 * <p>Whether a mixture can burn includes the fire oxygen threshold (FIRE-030): a mixture that
 * fails it isn't flammable, so a hotspot in it goes out.
 */
public interface Combustion {
    /** Nothing burns. */
    Combustion NONE = new Combustion() {
        @Override
        public boolean flammable(GasMixture mixture) {
            return false;
        }

        @Override
        public double autoignition(GasMixture mixture) {
            return Double.POSITIVE_INFINITY;
        }

        @Override
        public double[] burn(GasMixture mixture, double litres) {
            return new double[mixture.registry().size()];
        }
    };

    /**
     * Whether a fuel in the mixture is within its flammability limits and the mixture meets the
     * fire oxygen threshold (FIRE-003, FIRE-030).
     */
    boolean flammable(GasMixture mixture);

    /**
     * The lowest autoignition temperature, in K, among the fuels within their flammability limits,
     * or {@link Double#POSITIVE_INFINITY} if none is (FIRE-010).
     */
    double autoignition(GasMixture mixture);

    /**
     * Burns a parcel of {@code litres} of the mixture, or all of it if it is smaller, in place: the
     * reactants become products and the heat released goes into the whole mixture (FIRE-011).
     * Burns nothing if the mixture isn't {@link #flammable}.
     *
     * @return the change of each gas, in mol, indexed by the mixture's registry
     */
    double[] burn(GasMixture mixture, double litres);
}
