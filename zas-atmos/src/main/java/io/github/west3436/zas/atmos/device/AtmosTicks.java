package io.github.west3436.zas.atmos.device;

import net.minecraft.server.level.ServerLevel;

/**
 * The content mod's clock. Devices and pipe networks step once per atmos tick (D-31, CONT-006),
 * which is 1 s by default (D-20), and every rate is defined per second.
 *
 * <p>The content mod steps every {@link #PERIOD} game ticks whatever the framework's period
 * ({@code [atmos] period_ticks}, which the API gives as {@code Atmospheres.periodTicks()}) is set
 * to. Rates are per second, so a changed period changes how often the framework applies the
 * devices' changes, not how fast they act (architecture §11.2).
 */
public final class AtmosTicks {
    /** Game ticks per device step: 20, one second (D-20). */
    public static final int PERIOD = 20;

    /** Seconds per device step: every per-second rate is multiplied by this once per step. */
    public static final double SECONDS = PERIOD / 20.0;

    private AtmosTicks() {}

    /**
     * The game tick that schedules and power leases count in: the server's tick counter, which
     * never goes backwards while the server runs.
     *
     * @param level any level of the server
     * @return the current game tick
     */
    public static long now(ServerLevel level) {
        return level.getServer().getTickCount();
    }

    /**
     * Whether items step in this game tick. A tank in an inventory or on the ground is in no
     * device schedule; it asks here each game tick, and steps once per atmos tick.
     *
     * @param level the level it is in
     * @return true once every {@link #PERIOD} game ticks
     */
    public static boolean itemsStepNow(ServerLevel level) {
        return now(level) % PERIOD == 0;
    }
}
