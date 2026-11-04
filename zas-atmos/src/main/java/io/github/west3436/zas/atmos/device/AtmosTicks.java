package io.github.west3436.zas.atmos.device;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

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
     * Whether gas carried by an entity is due its atmos tick now. An item stack has no schedule, and
     * the game offers it a tick each game tick only while it is in a player's inventory or dropped,
     * so an item that holds gas asks this each time (architecture §11.9). It is true once per {@link
     * #PERIOD} for each entity, at a phase given by its id, which spreads entities across game ticks
     * as {@link DeviceSchedule} does devices.
     *
     * @param level the level, on the server
     * @param holder the entity carrying the gas: the player, or the dropped item
     * @return true in one game tick of every {@link #PERIOD}
     */
    public static boolean due(ServerLevel level, Entity holder) {
        return Math.floorMod(now(level) + holder.getId(), PERIOD) == 0;
    }
}
