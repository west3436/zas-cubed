package io.github.west3436.zas.atmos.vent;

/**
 * A vent's settings (CONT-014): its mode and its two pressure bounds, in kPa, each clamped to
 * 0–5,066 kPa.
 *
 * <ul>
 *   <li>Blowing, the vent adds gas while its zone is below the external bound and its network
 *       above the internal bound.
 *   <li>Siphoning, it removes gas while its zone is above the external bound and its network below
 *       the internal bound.
 * </ul>
 *
 * <p>This is what an area air controller commands ({@link VentBlockEntity#command}).
 *
 * @param mode what the vent does; null is off
 * @param external the zone's bound, kPa
 * @param internal the network's bound, kPa
 */
public record VentSettings(VentMode mode, double external, double internal) {
    /** The highest bound, kPa (50 atm). */
    public static final double MAX_KPA = 5066.0;

    /** The external bound a new vent has, kPa: one atmosphere. */
    public static final double DEFAULT_EXTERNAL = 101.325;

    /** Off, with the default bounds. */
    public static final VentSettings OFF = new VentSettings(VentMode.OFF, DEFAULT_EXTERNAL, 0);

    /** Clamps the bounds. */
    public VentSettings {
        mode = mode == null ? VentMode.OFF : mode;
        external = clamp(external);
        internal = clamp(internal);
    }

    /**
     * Blows until the zone reaches a pressure, with no bound on the network.
     *
     * @param target the zone's pressure to fill to, kPa
     */
    public static VentSettings blow(double target) {
        return new VentSettings(VentMode.BLOW, target, 0);
    }

    /**
     * Siphons until the zone falls to a pressure, with no bound on the network but the highest.
     *
     * @param target the zone's pressure to empty to, kPa
     */
    public static VentSettings siphon(double target) {
        return new VentSettings(VentMode.SIPHON, target, MAX_KPA);
    }

    /** The same bounds in another mode. */
    public VentSettings withMode(VentMode mode) {
        return new VentSettings(mode, external, internal);
    }

    /** A bound clamped to 0–5,066 kPa; NaN is 0. */
    public static double clamp(double kPa) {
        return Double.isNaN(kPa) ? 0 : Math.max(0, Math.min(MAX_KPA, kPa));
    }
}
