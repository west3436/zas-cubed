package io.github.west3436.zas.life;

import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A living entity's breathing and hazards between checks, server side: the verdict of its last
 * check, how fast its air drains, how long it has breathed each hazardous gas (LIFE-004), and
 * whether a check is waiting for room under the per-tick cap (LIFE-038). Kept in an entity
 * attachment that isn't saved, so it starts afresh when the entity loads. Server thread only.
 */
public final class BreathState {
    private @Nullable Breathability verdict;
    private double drain;
    private boolean inFluid;
    private long lastCheck = -1;
    private long lastAttempt = -1;
    private boolean deferred;
    private double carry;
    /** Per gas, the seconds its partial pressure has been at or above each threshold. */
    private final Map<ResourceLocation, double[]> exposure = new HashMap<>();
    /** For a player, the state last synced to its client (LIFE-023), or null before the first. */
    private @Nullable BreathSyncPayload sent;

    private long sentAt;

    /** The fewest game ticks between two syncs of a player's breathing state: at most 4 a second. */
    public static final int SYNC_INTERVAL_TICKS = 5;

    /** The verdict of the last check, or null before the first. */
    public @Nullable Breathability verdict() {
        return verdict;
    }

    /** The game time of the last check, or -1 before the first. */
    public long lastCheck() {
        return lastCheck;
    }

    /**
     * The game time of the last check that waited for an unresolved atmosphere before any verdict
     * (LIFE-048), or -1.
     */
    long lastAttempt() {
        return lastAttempt;
    }

    /** Whether a check fell due but waits for room under the per-tick cap (LIFE-038). */
    public boolean deferred() {
        return deferred;
    }

    /** A due check that the per-tick cap put off to the next tick (LIFE-038). */
    void defer() {
        deferred = true;
    }

    /** Whether the last check found the eyes in a fluid, where vanilla's rules apply. */
    boolean inFluid() {
        return inFluid;
    }

    /** Whether the last check found too little O2 (LIFE-003). */
    boolean hypoxic() {
        return verdict != null && verdict.reason() == BreathReason.LOW_OXYGEN;
    }

    /**
     * Whether the last check found its eyes in a fluid and it breathing from its tank all the same,
     * so it doesn't drown there (LIFE-047).
     */
    boolean breathesTankInFluid() {
        return inFluid && verdict != null && verdict.source() == BreathSource.INTERNALS && verdict.canBreathe();
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
        this.deferred = false;
        if (!hypoxic()) {
            carry = 0;
        }
    }

    /**
     * A check at {@code gameTime} that kept the last verdict, its drain and the exposures, since
     * the atmosphere at the eyes was unresolved (LIFE-048). The next check counts its seconds from
     * here, so the held time charges no hazard. Its eyes weren't in a fluid, or vanilla's verdict
     * would have applied.
     */
    void held(long gameTime) {
        this.inFluid = false;
        this.lastCheck = gameTime;
        this.deferred = false;
    }

    /**
     * A check at {@code gameTime} with no verdict yet that waited, since the atmosphere at the eyes
     * was unresolved (LIFE-048).
     */
    void waited(long gameTime) {
        this.lastAttempt = gameTime;
        this.deferred = false;
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

    /** The breathing state to sync to a player's client now: the last verdict without its gas, and the drain. */
    public BreathSyncPayload syncState() {
        return BreathSyncPayload.of(verdict, drain);
    }

    /** The state last synced to the player's client, or null before the first sync (LIFE-023). */
    public @Nullable BreathSyncPayload sent() {
        return sent;
    }

    /**
     * The state to sync to the player's client at game time {@code now}, if a sync is due, which it
     * records as sent: the first state, and later one that differs from the last sent, at most once
     * every {@link #SYNC_INTERVAL_TICKS}. A change held back by the limit goes on a later tick.
     *
     * @return the state to send, or null if none is due
     */
    public @Nullable BreathSyncPayload syncDue(long now) {
        BreathSyncPayload current = syncState();
        if (sent != null && (current.equals(sent) || (now >= sentAt && now - sentAt < SYNC_INTERVAL_TICKS))) {
            return null;
        }
        sent = current;
        sentAt = now;
        return current;
    }
}
