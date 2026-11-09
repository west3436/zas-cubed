package io.github.west3436.zas.atmos.vent;

import io.github.west3436.zas.api.gas.GasMixture;

/**
 * How many moles a vent moves in one second (CONT-014, CONT-078). Each is the least of: what
 * brings the zone to the external bound, what brings the network to the internal bound, and half
 * a vent buffer's worth (400 L) of the side gas leaves.
 *
 * <p>Several vents an airlock controller drives in one zone each take a share of what brings the
 * zone to the bound (CONT-152): {@link #shares} splits it by what each could move, so together
 * they reach the bound and don't pass it. A vent alone has a share of 1.
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
        return blow(zonePressure, zoneVolume, netMoles, netVolume, netTemperature, settings, 1);
    }

    /**
     * Blowing, as one of several vents into a zone: {@link #blow}, with what brings the zone to the
     * external bound scaled by {@code share}.
     *
     * @param share the vent's share of what the zone needs, 0 to 1
     */
    public static double blow(
            double zonePressure,
            double zoneVolume,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings,
            double share) {
        if (netMoles <= 0 || netTemperature <= 0 || netVolume <= 0) {
            return 0;
        }
        double rt = GasMixture.GAS_CONSTANT * netTemperature;
        double byZone = share * (settings.external() - zonePressure) * zoneVolume / rt;
        return Math.max(0, Math.min(byZone, blowCapacity(netMoles, netVolume, netTemperature, settings)));
    }

    /**
     * What a vent could blow in a second with no bound on the zone: the lesser of what brings the
     * network to the internal bound and the rate cap. The arguments are {@link #blow}'s.
     *
     * @return the moles, at least 0
     */
    public static double blowCapacity(double netMoles, double netVolume, double netTemperature, VentSettings settings) {
        if (netMoles <= 0 || netTemperature <= 0 || netVolume <= 0) {
            return 0;
        }
        double rt = GasMixture.GAS_CONSTANT * netTemperature;
        double byNet = (netMoles * rt / netVolume - settings.internal()) * netVolume / rt;
        double cap = RATE * netMoles * Math.min(1, BUFFER / netVolume);
        return Math.max(0, Math.min(byNet, cap));
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
        return blowIdle(zonePressure, zoneVolume, netMoles, netVolume, netTemperature, settings, 1, minFlow);
    }

    /** {@link #blowIdle}, as one of several vents into a zone, with the share {@link #blow} takes. */
    public static VentStatus blowIdle(
            double zonePressure,
            double zoneVolume,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings,
            double share,
            double minFlow) {
        if (blow(zonePressure, zoneVolume, netMoles, netVolume, netTemperature, settings, share) >= minFlow) {
            return VentStatus.MOVING;
        }
        if (netTemperature <= 0 || netVolume <= 0 || RATE * netMoles * Math.min(1, BUFFER / netVolume) < minFlow) {
            return VentStatus.NETWORK_EMPTY;
        }
        double rt = GasMixture.GAS_CONSTANT * netTemperature;
        double byZone = share * (settings.external() - zonePressure) * zoneVolume / rt;
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
        return siphon(zoneMoles, zoneVolume, zoneTemperature, netMoles, netVolume, netTemperature, settings, 1);
    }

    /**
     * Siphoning, as one of several vents from a zone: {@link #siphon}, with what brings the zone to
     * the external bound scaled by {@code share}.
     *
     * @param share the vent's share of what the zone needs, 0 to 1
     */
    public static double siphon(
            double zoneMoles,
            double zoneVolume,
            double zoneTemperature,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings,
            double share) {
        if (zoneMoles <= 0 || zoneTemperature <= 0 || zoneVolume <= 0 || netVolume <= 0) {
            return 0;
        }
        double rt = GasMixture.GAS_CONSTANT * zoneTemperature;
        double byZone = share * (zoneMoles * rt / zoneVolume - settings.external()) * zoneVolume / rt;
        double capacity =
                siphonCapacity(zoneMoles, zoneVolume, zoneTemperature, netMoles, netVolume, netTemperature, settings);
        return Math.max(0, Math.min(byZone, capacity));
    }

    /**
     * What a vent could siphon in a second with no bound on the zone: the lesser of what brings the
     * network to the internal bound and the rate cap. The arguments are {@link #siphon}'s.
     *
     * @return the moles, at least 0
     */
    public static double siphonCapacity(
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
        double netPressure = netMoles * GasMixture.GAS_CONSTANT * netTemperature / netVolume;
        double byNet = (settings.internal() - netPressure) * netVolume / rt;
        double cap = RATE * zoneMoles * Math.min(1, BUFFER / zoneVolume);
        return Math.max(0, Math.min(byNet, cap));
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
        return siphonIdle(
                zoneMoles, zoneVolume, zoneTemperature, netMoles, netVolume, netTemperature, settings, 1, minFlow);
    }

    /** {@link #siphonIdle}, as one of several vents from a zone, with the share {@link #siphon} takes. */
    public static VentStatus siphonIdle(
            double zoneMoles,
            double zoneVolume,
            double zoneTemperature,
            double netMoles,
            double netVolume,
            double netTemperature,
            VentSettings settings,
            double share,
            double minFlow) {
        if (siphon(zoneMoles, zoneVolume, zoneTemperature, netMoles, netVolume, netTemperature, settings, share)
                >= minFlow) {
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
        double byZone = share * (zoneMoles * rt / zoneVolume - settings.external()) * zoneVolume / rt;
        double byNet = (settings.internal() - netPressure) * netVolume / rt;
        return byNet <= byZone ? VentStatus.NETWORK_BOUND : VentStatus.ROOM_BOUND;
    }

    /**
     * Each vent's share of what brings one zone to a bound (CONT-152), in proportion to what each
     * could move. While the zone needs less than they could move together, each moves its share
     * and together they move just what it needs; while it needs more, each moves its most. The
     * shares add up to 1. With nothing any could move, they are equal.
     *
     * @param capacities what each vent could move, from {@link #blowCapacity} or {@link
     *     #siphonCapacity}
     * @return the shares, in the same order
     */
    public static double[] shares(double[] capacities) {
        double[] out = new double[capacities.length];
        double total = 0;
        for (double c : capacities) {
            total += c > 0 ? c : 0;
        }
        for (int i = 0; i < out.length; i++) {
            out[i] = total > 0 ? Math.max(0, capacities[i]) / total : 1.0 / out.length;
        }
        return out;
    }
}
