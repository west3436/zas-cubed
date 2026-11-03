package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * A zone stopped burning (API-031): its last hotspot went out, was put out, or left the zone
 * ({@link io.github.west3436.zas.api.fire.Fires}). Fired once per zone, after a {@link
 * FireIgnitedEvent} for it.
 *
 * <p>Zones are named by id, so a fire that a merge or split carries into a zone with another id goes
 * out in the old zone and catches in the new one, in the same atmos tick. A zone that dissolves or
 * whose chunk unloads while burning goes out too.
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the server thread, when the atmos tick in which the
 * zone went out is applied (API-032), after its gas fire blocks are removed. Not fired for work
 * that is discarded and redone. Not cancellable: the fire is out already.
 */
@ServerThread
public final class FireExtinguishedEvent extends Event {
    private final ServerLevel level;
    private final ZoneId zone;
    private final BlockPos lastHotspot;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone that stopped burning
     * @param lastHotspot one of the hotspots it had: the lowest by {@link BlockPos#asLong}
     */
    public FireExtinguishedEvent(ServerLevel level, ZoneId zone, BlockPos lastHotspot) {
        this.level = Objects.requireNonNull(level, "level");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.lastHotspot = lastHotspot.immutable();
    }

    /**
     * The level.
     *
     * <p>Thread: server thread.
     *
     * @return the level the fire burned in
     */
    public ServerLevel level() {
        return level;
    }

    /**
     * The zone that stopped burning.
     *
     * <p>Thread: server thread.
     *
     * @return its id
     */
    public ZoneId zone() {
        return zone;
    }

    /**
     * One of the hotspots the zone had before going out: the lowest by {@link BlockPos#asLong}.
     *
     * <p>Thread: server thread.
     *
     * @return an immutable position
     */
    public BlockPos lastHotspot() {
        return lastHotspot;
    }
}
