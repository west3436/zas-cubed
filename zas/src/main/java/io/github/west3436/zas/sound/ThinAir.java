package io.github.west3436.zas.sound;

/**
 * How much of this mod's sounds a listener hears for the pressure around it (CLIENT-016): a straight
 * line in amplitude from exact silence at {@value #SILENT_KPA} kPa to full volume at {@value
 * #FULL_KPA} kPa. A sound wave's amplitude in a gas follows the gas's density, so a straight line is
 * also roughly what thin air does.
 *
 * <table>
 *   <caption>The curve</caption>
 *   <tr><th>kPa</th><td>≤ 1</td><td>5</td><td>10</td><td>20</td><td>30</td><td>40</td><td>≥ 50</td></tr>
 *   <tr><th>Factor</th><td>0</td><td>0.08</td><td>0.18</td><td>0.39</td><td>0.59</td><td>0.80</td><td>1</td></tr>
 * </table>
 *
 * <p>Full volume starts at the HUD's low-pressure warning, so the sound starts to fade where the
 * player is first warned, and normal air never needs a message (DATA-019). Platform-free.
 */
public final class ThinAir {
    /** At or below this pressure nothing is heard, in kPa. */
    public static final int SILENT_KPA = 1;

    /** At or above this pressure a sound is at full volume, in kPa: the HUD's low-pressure warning. */
    public static final int FULL_KPA = 50;

    /** What a client holds before the server has told it anything. */
    public static final int UNKNOWN = -1;

    private ThinAir() {}

    /**
     * The volume factor at a pressure.
     *
     * @param kPa the pressure, or {@link #UNKNOWN} (any value below 0), which counts as normal air
     * @return 0 to 1: exactly 0 at {@value #SILENT_KPA} kPa and below, and exactly 1 at {@value
     *     #FULL_KPA} kPa and above
     */
    public static double factor(double kPa) {
        if (kPa < 0) {
            return 1;
        }
        return Math.max(0, Math.min(1, (kPa - SILENT_KPA) / (FULL_KPA - SILENT_KPA)));
    }

    /**
     * What the server tells a client of a pressure (DATA-019): whole kPa, capped where the factor
     * stops changing.
     *
     * @param kPa the pressure, not below 0
     * @return 0 to {@value #FULL_KPA}
     */
    public static int reading(double kPa) {
        return (int) Math.floor(Math.max(0, Math.min(FULL_KPA, kPa)));
    }
}
