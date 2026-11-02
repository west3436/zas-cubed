package io.github.west3436.zas.life;

import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathability;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A breathing entity's breathing between checks, server side: the verdict of its last check, how
 * fast its air drains, and how long it has breathed each hazardous gas (LIFE-004). Kept in an
 * entity attachment that isn't saved, so it starts afresh when the entity loads. Server thread
 * only.
 */
public final class BreathState {
    private @Nullable Breathability verdict;
    private double drain;
    private boolean inFluid;
    private long lastCheck = -1;
    private double carry;
    /** Per gas, the seconds its partial pressure has been at or above each threshold. */
    private final Map<ResourceLocation, double[]> exposure = new HashMap<>();

    /** The verdict of the last check, or null before the first. */
    public @Nullable Breathability verdict() {
        return verdict;
    }

    /** The game time of the last check, or -1 before the first. */
    public long lastCheck() {
        return lastCheck;
    }

    /** Whether the last check found the eyes in a fluid, where vanilla's rules apply. */
    boolean inFluid() {
        return inFluid;
    }

    /** Whether the last check found too little O2 (LIFE-003). */
    boolean hypoxic() {
        return verdict != null && verdict.reason() == BreathReason.LOW_OXYGEN;
    }

    /** The fraction of the vanilla underwater rate at which the air meter drains, 0 to 1 (LIFE-003). */
    double drain() {
        return drain;
    }

    void checked(Breathability verdict, double drain, boolean inFluid, long gameTime) {
        this.verdict = verdict;
        this.drain = drain;
        this.inFluid = inFluid;
        this.lastCheck = gameTime;
        if (!hypoxic()) {
            carry = 0;
        }
    }

    /**
     * Drains the air meter by {@code consume}, the vanilla amount this tick, times the drain
     * fraction, carrying the fraction of a unit to later ticks.
     *
     * @return the whole units to consume this tick
     */
    int drainThisTick(int consume) {
        carry += consume * drain;
        int whole = (int) carry;
        carry -= whole;
        return whole;
    }

    /** A gas's exposure per threshold, reset if its profile's thresholds changed in number. */
    double[] exposure(ResourceLocation gas, int thresholds) {
        double[] e = exposure.get(gas);
        if (e == null || e.length != thresholds) {
            e = new double[thresholds];
            exposure.put(gas, e);
        }
        return e;
    }

    /** Whether any exposure is recorded for a gas. */
    boolean exposed(ResourceLocation gas) {
        return exposure.containsKey(gas);
    }

    /** Forgets a gas's exposure. */
    void clearExposure(ResourceLocation gas) {
        exposure.remove(gas);
    }

    /** Forgets every gas's exposure, when the entity breathes no gas. */
    void clearExposure() {
        exposure.clear();
    }
}
