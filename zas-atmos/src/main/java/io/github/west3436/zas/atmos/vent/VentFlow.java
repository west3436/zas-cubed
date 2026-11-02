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
     * Why blowing would move less than {@code minFlow} moles (CONT-014): the network is empty (too
     * little gas for even its rate cap to reach {@code minFlow}), it has reached the internal bound, or the zone has reached the external bound. The arguments are
     * {@link #blow}'s.
     *
     * @return the reason, or {@link VentStatus#MOVING} if it would move at least {@code minFlow}
     */
    public static VentStatus blowIdle(
            double zonePressure,
            double zoneVolume,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings,
            double minFlow) {
        if (blow(zonePressure, zoneVolume, netMoles, netVolume, netTemperature, settings) >= minFlow) {
            return VentStatus.MOVING;
        }
        if (netTemperature <= 0 || netVolume <= 0 || RATE * netMoles * Math.min(1, BUFFER / netVolume) < minFlow) {
            return VentStatus.NETWORK_EMPTY;
        }
        double rt = GasMixture.GAS_CONSTANT * netTemperature;
        double byZone = (settings.external() - zonePressure) * zoneVolume / rt;
        double byNet = (netMoles * rt / netVolume - settings.internal()) * netVolume / rt;
        return byNet <= byZone ? VentStatus.NETWORK_BOUND : VentStatus.ROOM_BOUND;
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

    /**
     * Why siphoning would move less than {@code minFlow} moles (CONT-014): there is no gas in front
     * (too little for even the rate cap to reach {@code minFlow}), the network has reached the internal bound, or the zone has reached the external bound. The
     * arguments are {@link #siphon}'s.
     *
     * @return the reason, or {@link VentStatus#MOVING} if it would move at least {@code minFlow}
     */
    public static VentStatus siphonIdle(
            double zoneMoles,
            double zoneVolume,
            double zoneTemperature,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings,
            double minFlow) {
        if (siphon(zoneMoles, zoneVolume, zoneTemperature, netMoles, netVolume, netTemperature, settings) >= minFlow) {
            return VentStatus.MOVING;
        }
        if (zoneTemperature <= 0
                || zoneVolume <= 0
                || netVolume <= 0
                || RATE * zoneMoles * Math.min(1, BUFFER / zoneVolume) < minFlow) {
            return VentStatus.ROOM_EMPTY;
        }
        double rt = GasMixture.GAS_CONSTANT * zoneTemperature;
        double netPressure = netMoles * GasMixture.GAS_CONSTANT * netTemperature / netVolume;
        double byZone = (zoneMoles * rt / zoneVolume - settings.external()) * zoneVolume / rt;
        double byNet = (settings.internal() - netPressure) * netVolume / rt;
        return byNet <= byZone ? VentStatus.NETWORK_BOUND : VentStatus.ROOM_BOUND;
    }
}
