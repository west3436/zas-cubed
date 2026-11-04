package io.github.west3436.zas.life;

/**
 * CONT-057's arithmetic: a plant's O₂ scales linearly with the light on it, from 0 at light 0 to its
 * rate at light 15.
 *
 * <p>A pass over a zone's plants adds up their light levels, one sum per rate, so a pass costs no
 * floating point per plant and the preset is read once, when the zone's moles are worked out.
 */
final class PhotosynthesisRates {
    /** The light level a rate is given at. */
    static final int FULL_LIGHT = 15;

    private static final double SECONDS_PER_HOUR = 3600;

    private PhotosynthesisRates() {}

    /**
     * The moles of CO₂ a zone's plants turn into O₂ in a stretch of time.
     *
     * @param cropLight the sum of the light levels on its plants at the crop rate
     * @param leafLight the sum of the light levels on its plants at the leaf rate
     * @param cropRate the crop rate at light 15, in mol/h
     * @param leafRate the leaf rate at light 15, in mol/h
     * @param seconds the stretch of time
     */
    static double moles(long cropLight, long leafLight, double cropRate, double leafRate, double seconds) {
        return (cropLight * cropRate + leafLight * leafRate) / FULL_LIGHT * seconds / SECONDS_PER_HOUR;
    }
}
