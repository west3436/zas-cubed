package io.github.west3436.zas.engine.reaction;

/**
 * Whether a mixture can catch fire (FIRE-010, FIRE-014, FIRE-017).
 *
 * @param flammable whether some reaction that requires ignition has every other condition met: its
 *     fuel within its flammability limits at the mixture's temperature (FIRE-032), its other
 *     conditions, and the fire oxygen threshold (FIRE-030), or only that threshold's partial-pressure
 *     floor if the mixture is at or above that fuel's autoignition temperature (FIRE-035)
 * @param autoignition the lowest autoignition temperature, K, among those reactions; {@link
 *     Double#POSITIVE_INFINITY} if none, or none has one
 */
public record Flammability(boolean flammable, double autoignition) {
    /** Not flammable. */
    public static final Flammability NONE = new Flammability(false, Double.POSITIVE_INFINITY);

    /** Whether the mixture ignites by itself at a temperature (FIRE-010). */
    public boolean ignitesAt(double temperature) {
        return flammable && temperature >= autoignition;
    }
}
