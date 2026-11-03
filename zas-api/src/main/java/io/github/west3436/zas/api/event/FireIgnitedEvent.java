package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * A zone caught fire (API-031): its first hotspot appeared ({@link
 * io.github.west3436.zas.api.fire.Fires}). Fired once per zone, not per hotspot; it is fired again
 * only after the zone has gone out ({@link FireExtinguishedEvent}). A hotspot counts once an atmos
 * tick sees its block, so a zone set alight by an ignition catches fire in the tick after it.
 *
 * <p>Zones are named by id, so a fire that a merge or split carries into a zone with another id goes
 * out in the old zone and catches in the new one, in the same atmos tick.
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the server thread, when the atmos tick in which the
 * zone caught fire is applied (API-032), after its gas fire blocks are placed. Not fired for work
 * that is discarded and redone. Not cancellable: the fire burns already.
 */
@ServerThread
public final class FireIgnitedEvent extends Event {
    private final ServerLevel level;
    private final ZoneId zone;
    private final BlockPos hotspot;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone that caught fire
     * @param hotspot one of its hotspots: the lowest by {@link BlockPos#asLong}
     */
    public FireIgnitedEvent(ServerLevel level, ZoneId zone, BlockPos hotspot) {
        this.level = Objects.requireNonNull(level, "level");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.hotspot = hotspot.immutable();
    }

    /**
     * The level.
     *
     * <p>Thread: server thread.
     *
     * @return the level the fire burns in
     */
    public ServerLevel level() {
        return level;
    }

    /**
     * The zone that caught fire.
     *
     * <p>Thread: server thread.
     *
     * @return its id
     */
    public ZoneId zone() {
        return zone;
    }

    /**
     * One of the zone's hotspots: the lowest by {@link BlockPos#asLong}.
     *
     * <p>Thread: server thread.
     *
     * @return an immutable position
     */
    public BlockPos hotspot() {
        return hotspot;
    }
}
