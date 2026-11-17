package io.github.west3436.zas.compat.nuclearcraft;

/**
 * The arithmetic of a fission reactor's heat (COMPAT-153), apart from the game so that it can be
 * tested alone.
 *
 * <p><b>What the sinks remove.</b> Each tick NuclearCraft adds the heat its fuel makes to the
 * reactor's heat buffer, then takes off what its heat sinks can carry, {@code cooldownPerTick}, but
 * never below empty. So the sinks carry their whole {@code cooldownPerTick} while heat is left in
 * the buffer after them, and otherwise only what was there: the heat made that tick, which is
 * {@code heatPerTick} times the reactor's reactivity, at least half. That is what reaches the room. A
 * reactor whose sinks could carry more than its fuel makes gives the room what the fuel makes, not
 * what the sinks could carry; an idle one gives it nothing.
 */
public final class SinkHeat {
    private SinkHeat() {}

    /**
     * The heat a reactor's sinks removed in its last tick, from what its controller shows.
     *
     * @param currentHeat the heat left in the buffer after the sinks, in H
     * @param heatPerTick the heat its fuel makes at full reactivity, in H a tick
     * @param cooldownPerTick what its sinks can carry, in H a tick
     * @param reactivity its reactivity, in percent
     * @return the heat removed, in H, never below 0
     */
    public static double removedPerTick(
            double currentHeat, double heatPerTick, double cooldownPerTick, int reactivity) {
        if (!(cooldownPerTick > 0) || !Double.isFinite(cooldownPerTick)) {
            return 0;
        }
        if (currentHeat > 0) {
            return cooldownPerTick;
        }
        double made = heatPerTick * Math.max(0.5, reactivity / 100.0);
        if (!(made > 0) || !Double.isFinite(made)) {
            return 0;
        }
        return Math.min(cooldownPerTick, made);
    }

    /**
     * The joules a reactor gives its rooms for the heat its sinks removed over some ticks.
     *
     * @param heat the heat removed, in H
     * @param ticks the game ticks it was removed over
     * @param joulesPerUnit the joules one H is
     * @param maxWatts the most the reactor gives, in W; 0 or less is no ceiling
     * @return the joules, never below 0
     */
    public static double joules(double heat, int ticks, double joulesPerUnit, double maxWatts) {
        double joules = heat * joulesPerUnit;
        if (!(joules > 0) || !Double.isFinite(joules) || ticks <= 0) {
            return 0;
        }
        if (maxWatts > 0) {
            joules = Math.min(joules, maxWatts * ticks / 20.0);
        }
        return joules;
    }
}
