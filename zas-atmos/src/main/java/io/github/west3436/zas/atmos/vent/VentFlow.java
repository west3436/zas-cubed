package io.github.west3436.zas.atmos.vent;

import io.github.west3436.zas.api.gas.GasMixture;

/**
 * How many moles a vent moves in one second (CONT-014, CONT-078). Each is the least of: what
 * brings the zone to the external bound, what brings the network to the internal bound, and half
 * a vent buffer's worth (400 L) of the side gas leaves.
 */
public final class VentFlow {
    /** The vent's buffer, L (CONT-007), whose moles cap its rate. */
    public static final double BUFFER = 400.0;

    /** The fraction of a buffer's moles a vent moves per second at most (CONT-078). */
    public static final double RATE = 0.5;

    private VentFlow() {}

    /**
     * Blowing: moles out of the network into the zone.
     *
     * @param zonePressure the zone's pressure, kPa
     * @param zoneVolume the zone's volume, L, or one cell's for the ambient
     * @param netMoles the network's moles
     * @param netVolume the network's volume, L
     * @param netTemperature the network's temperature, K
     * @param settings the bounds
     * @return the moles, at least 0
     */
    public static double blow(
            double zonePressure,
            double zoneVolume,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings) {
        if (netMoles <= 0 || netTemperature <= 0 || netVolume <= 0) {
            return 0;
        }
        double rt = GasMixture.GAS_CONSTANT * netTemperature;
        double netPressure = netMoles * rt / netVolume;
        double byZone = (settings.external() - zonePressure) * zoneVolume / rt;
        double byNet = (netPressure - settings.internal()) * netVolume / rt;
        double cap = RATE * netMoles * Math.min(1, BUFFER / netVolume);
        return Math.max(0, Math.min(Math.min(byZone, byNet), cap));
    }

    /**
     * Siphoning: moles out of the zone into the network.
     *
     * @param zoneMoles the zone's moles
     * @param zoneVolume the zone's volume, L, or one cell's for the ambient
     * @param zoneTemperature the zone's temperature, K
     * @param netMoles the network's moles
     * @param netVolume the network's volume, L
     * @param netTemperature the network's temperature, K
     * @param settings the bounds
     * @return the moles, at least 0
     */
    public static double siphon(
            double zoneMoles,
            double zoneVolume,
            double zoneTemperature,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings) {
        if (zoneMoles <= 0 || zoneTemperature <= 0 || zoneVolume <= 0 || netVolume <= 0) {
            return 0;
        }
        double rt = GasMixture.GAS_CONSTANT * zoneTemperature;
        double zonePressure = zoneMoles * rt / zoneVolume;
        double netPressure = netMoles * GasMixture.GAS_CONSTANT * netTemperature / netVolume;
        double byZone = (zonePressure - settings.external()) * zoneVolume / rt;
        double byNet = (settings.internal() - netPressure) * netVolume / rt;
        double cap = RATE * zoneMoles * Math.min(1, BUFFER / zoneVolume);
        return Math.max(0, Math.min(Math.min(byZone, byNet), cap));
    }
}
