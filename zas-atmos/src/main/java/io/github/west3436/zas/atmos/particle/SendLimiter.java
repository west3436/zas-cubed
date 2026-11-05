package io.github.west3436.zas.atmos.particle;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caps how many flowing jets a player is sent in a window of game ticks (CLIENT-019), so a station full
 * of open ends can't take more than its share of a player's connection (DATA-006). Past the cap they
 * are just not sent: the next atmos tick sends them again.
 *
 * <p>It counts over a window as long as a device's period, not per game tick: a device's place in the
 * period comes from the order it was added, so a few flowing ends can share a game tick, and a cap on
 * each game tick would hide one of them every time while the rest of the period sat unused. Server
 * thread.
 */
final class SendLimiter {
    private final int perWindow;
    private final int windowTicks;
    private final Map<UUID, Integer> sent = new HashMap<>();
    private long window = Long.MIN_VALUE;

    /**
     * @param perWindow the most a player is sent in a window
     * @param windowTicks the game ticks in a window
     */
    SendLimiter(int perWindow, int windowTicks) {
        this.perWindow = perWindow;
        this.windowTicks = windowTicks;
    }

    /**
     * Whether a player may be sent another now, counting it if so.
     *
     * @param player the player
     * @param now the game tick
     * @return whether to send it
     */
    boolean allow(UUID player, long now) {
        long current = Math.floorDiv(now, windowTicks);
        if (current != window) {
            window = current;
            sent.clear();
        }
        int already = sent.getOrDefault(player, 0);
        if (already >= perWindow) {
            return false;
        }
        sent.put(player, already + 1);
        return true;
    }
}
