package io.github.west3436.zas.engine.flow;

/**
 * How hard airflow pushes (FLOW-001, FLOW-002, FLOW-010). Speeds are in blocks per game tick.
 *
 * <p>Next to an opening the flow's speed is {@code speedAtOneAtm} × ΔP / 101.325 kPa, capped at
 * {@code maxSpeed}, and it falls linearly to 0 at {@code range} blocks. Each game tick an entity's
 * speed along the flow closes {@code response} of the gap to the flow's speed; an entity already
 * moving faster along it is left alone.
 *
 * @param range how far from an opening airflow reaches, in blocks (FLOW-001)
 * @param speedAtOneAtm the flow's speed next to an opening at a difference of 1 atm (FLOW-010)
 * @param maxSpeed the cap on the flow's speed (FLOW-002)
 * @param response the share of the gap to the flow's speed closed each game tick, 0 to 1
 */
public record FlowSettings(double range, double speedAtOneAtm, double maxSpeed, double response) {
    /** One standard atmosphere, kPa. */
    public static final double ONE_ATM = 101.325;

    /** FLOW-010's starting point: 1 block per tick at 1 atm next to an opening, 0 at 9 blocks. */
    public static final FlowSettings DEFAULT = new FlowSettings(9.0, 1.0, 1.5, 0.5);

    public FlowSettings {
        if (!(range > 0) || !(speedAtOneAtm >= 0) || !(maxSpeed >= 0) || !(response >= 0 && response <= 1)) {
            throw new IllegalArgumentException(
                    "bad airflow settings: " + range + ", " + speedAtOneAtm + ", " + maxSpeed + ", " + response);
        }
    }

    /**
     * The flow's speed at a distance from an opening for a pressure difference: proportional to the
     * difference, capped, and falling linearly to 0 at the range (FLOW-002).
     *
     * @param difference the pressure difference, kPa
     * @param distance the distance from the opening, blocks
     * @return blocks per game tick, 0 at or beyond the range
     */
    public double speed(double difference, double distance) {
        if (distance >= range || difference <= 0) {
            return 0;
        }
        double near = Math.min(maxSpeed, speedAtOneAtm * difference / ONE_ATM);
        return near * (1 - Math.max(0, distance) / range);
    }
}
