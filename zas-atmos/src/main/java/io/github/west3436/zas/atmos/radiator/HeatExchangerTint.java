package io.github.west3436.zas.atmos.radiator;

/**
 * The colour a heat exchanger's coil and pipe take from the temperature of its gas (CONT-148): frost
 * blue when cold, copper at room temperature, then dark red, red, orange and yellow-white as it heats.
 * Plain arithmetic, so it is unit-tested without a world.
 *
 * <p>The block state carries the colour as a band, so the server sets it and every client draws
 * the same: {@link #STEPS} bands between each pair of stops, the stops themselves included, so
 * {@link #BANDS} in all. A band's colour is the blend between the stops on either side.
 */
public final class HeatExchangerTint {
    /** The stops' temperatures, K: −60, 20, 200, 600, 1,000 and 1,500 °C. */
    static final double[] STOPS = {213.15, 293.15, 473.15, 873.15, 1273.15, 1773.15};

    /** The stops' colours, 0xRRGGBB: frost blue, copper, dark red, red, orange and yellow-white. */
    static final int[] COLOURS = {0x8ec5ff, 0xc07a45, 0x9c4a32, 0xe8341c, 0xff8c1a, 0xffe8b0};

    /** Bands from one stop to the next. */
    public static final int STEPS = 6;

    /** How many bands there are. */
    public static final int BANDS = (STOPS.length - 1) * STEPS + 1;

    /** Copper's band: room temperature, and an exchanger with no gas. */
    public static final int COPPER = STEPS;

    private HeatExchangerTint() {}

    /**
     * The band of a temperature: the nearest to where it falls between the stops. Colder than the
     * first stop is the first band, hotter than the last the last.
     *
     * @param kelvin the temperature
     * @return 0 to {@link #BANDS} − 1
     */
    public static int band(double kelvin) {
        if (!(kelvin > STOPS[0])) {
            return 0;
        }
        for (int i = 1; i < STOPS.length; i++) {
            if (kelvin <= STOPS[i]) {
                double along = (kelvin - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]);
                return (i - 1) * STEPS + (int) Math.round(along * STEPS);
            }
        }
        return BANDS - 1;
    }

    /**
     * A band's colour.
     *
     * @param band 0 to {@link #BANDS} − 1; clamped
     * @return 0xRRGGBB
     */
    public static int colour(int band) {
        int b = Math.max(0, Math.min(BANDS - 1, band));
        int stop = b / STEPS;
        int step = b % STEPS;
        if (step == 0) {
            return COLOURS[stop];
        }
        return blend(COLOURS[stop], COLOURS[stop + 1], (double) step / STEPS);
    }

    /** The colour a fraction of the way from one colour to another, each channel rounded. */
    static int blend(int from, int to, double t) {
        int out = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (from >> shift) & 0xff;
            int b = (to >> shift) & 0xff;
            out |= ((int) Math.round(a + (b - a) * t)) << shift;
        }
        return out;
    }
}
