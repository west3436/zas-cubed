package io.github.west3436.zas.atmos.controller;

/**
 * A controller's breach check (CONT-043): it sends the controller to danger 2 at once, rather than
 * when the falling pressure reaches the preset's min2, so the firelocks shut before the next room
 * loses much air. One reading per atmos tick. It is a breach when either holds:
 *
 * <ul>
 *   <li><b>Open to a low ambient.</b> The zone is breached (it has an edge to the ambient) and the
 *       ambient's pressure is at or below the preset's pressure min2, as space is. A door opened to
 *       ordinary outside air is not one.
 *   <li><b>A fast fall.</b> The pressure fell by at least {@code dropKpa} since the last reading and
 *       is now below the preset's pressure min1 (or min2, if min1 is off): air rushing out somewhere
 *       the first rule can't see, such as into an emptied hall.
 * </ul>
 *
 * Neither applies to a preset with no pressure min2, such as Vacuum, for which low pressure is no
 * danger.
 */
public final class BreachAlarm {
    private double last = Double.NaN;

    /**
     * Takes one reading.
     *
     * @param pressure the preset's pressure bounds
     * @param kpa the zone's pressure now
     * @param breached whether the zone has an edge to the ambient
     * @param ambientKpa the ambient's pressure, or NaN if unknown
     * @param dropKpa the fall within one reading that counts, kPa; 0 or less turns the rule off
     * @return whether this reading is a breach
     */
    public boolean check(Thresholds pressure, double kpa, boolean breached, double ambientKpa, double dropKpa) {
        double before = last;
        last = kpa;
        if (pressure.min2() == Thresholds.OFF) {
            return false;
        }
        boolean open = breached && !Double.isNaN(ambientKpa) && ambientKpa <= pressure.min2();
        double floor = pressure.min1() == Thresholds.OFF ? pressure.min2() : pressure.min1();
        boolean fall = dropKpa > 0 && !Double.isNaN(before) && before - kpa >= dropKpa && kpa < floor;
        return open || fall;
    }

    /** Forgets the last reading, as when the controller loses power or its air can't be read. */
    public void reset() {
        last = Double.NaN;
    }
}
