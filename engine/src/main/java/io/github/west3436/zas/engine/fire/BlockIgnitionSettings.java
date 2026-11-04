package io.github.west3436.zas.engine.fire;

/**
 * How hot gas and gas fires set flammable blocks alight (FIRE-033, FIRE-034). Both chances are per
 * second, for one flammable block's face, and scale with the block's vanilla ignite odds. Pure; any
 * thread.
 *
 * <ul>
 *   <li><b>Hot gas</b> (FIRE-033): {@code odds / 100 × heat}. The heat share is 0.1 at the block's
 *       ignition temperature, rising linearly to 1 at {@value #HEAT_RANGE} K above it.
 *   <li><b>A gas fire</b> (FIRE-034): {@code hotspotChance × odds / 100}.
 * </ul>
 *
 * Odds above 100 count as 100. A chance per second becomes a chance per atmos tick as FIRE-016's
 * does, so the rate doesn't depend on the period.
 *
 * @param enabled whether blocks catch fire this way at all: the {@code doFireTick} game rule
 * @param temperature the ignition temperature, K, of a block with no override of its own
 * @param hotspotChance the chance per second that a gas fire lights a neighbouring block of ignite
 *     odds 100, 0 to 1
 */
public record BlockIgnitionSettings(boolean enabled, double temperature, double hotspotChance) {
    /** About the autoignition point of wood, K. */
    public static final double DEFAULT_TEMPERATURE = 573;

    /** At this, a gas fire lights wool, of odds 30, about as fast as it spreads through air (FIRE-016). */
    public static final double DEFAULT_HOTSPOT_CHANCE = 1;

    /** The ignite odds that count in full. */
    public static final int FULL_ODDS = 100;

    /** The heat share at the ignition temperature itself. */
    public static final double HEAT_AT_IGNITION = 0.1;

    /** How far above the ignition temperature the heat share reaches 1, K. */
    public static final double HEAT_RANGE = 500;

    /** On, with the default temperature and chance. */
    public static final BlockIgnitionSettings DEFAULT =
            new BlockIgnitionSettings(true, DEFAULT_TEMPERATURE, DEFAULT_HOTSPOT_CHANCE);

    public BlockIgnitionSettings {
        if (!(temperature >= 0) || !Double.isFinite(temperature)) {
            throw new IllegalArgumentException("ignition temperature must be finite and >= 0: " + temperature);
        }
        if (!(hotspotChance >= 0 && hotspotChance <= 1)) {
            throw new IllegalArgumentException("hotspotChance must be in [0, 1]: " + hotspotChance);
        }
    }

    /** These settings on or off. */
    public BlockIgnitionSettings withEnabled(boolean on) {
        return on == enabled ? this : new BlockIgnitionSettings(on, temperature, hotspotChance);
    }

    /**
     * The chance per second that gas sets one face of a block alight (FIRE-033).
     *
     * @param odds the block's ignite odds
     * @param gas the gas's temperature, K
     * @param ignition the block's ignition temperature, K
     * @return the chance, 0 below the ignition temperature
     */
    public double hotGasChance(int odds, double gas, double ignition) {
        if (!(gas >= ignition)) {
            return 0;
        }
        double heat = HEAT_AT_IGNITION + (1 - HEAT_AT_IGNITION) * (gas - ignition) / HEAT_RANGE;
        return share(odds) * Math.min(1, heat);
    }

    /** The chance per second that a gas fire lights one neighbouring block (FIRE-034). */
    public double gasFireChance(int odds) {
        return hotspotChance * share(odds);
    }

    /** A chance per second as a chance in an atmos tick of {@code seconds}: 1 − (1 − p)^t. */
    public static double inPeriod(double perSecond, double seconds) {
        if (perSecond >= 1) {
            return 1;
        }
        return perSecond <= 0 ? 0 : 1 - Math.pow(1 - perSecond, seconds);
    }

    private static double share(int odds) {
        return odds <= 0 ? 0 : Math.min(odds, FULL_ODDS) / (double) FULL_ODDS;
    }
}
