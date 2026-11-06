package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * Something happened in a zone's life (API-030). This is only the base of those events: the bus takes
 * no listener for an abstract class, so listen for {@link ZoneCreatedEvent}, {@link ZoneMergedEvent},
 * {@link ZoneSplitEvent}, {@link ZoneBreachedEvent}, {@link ZoneSealedEvent}, {@link
 * ZoneDissolvedEvent}, {@link ZonePausedEvent} or {@link ZoneResumedEvent}.
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the server thread, when the atmos tick in which it
 * happened is applied (API-032), once per change and in the order the changes happened, ahead of that
 * tick's fire events. So a listener reading a zone finds it as it stands after the whole tick, which
 * may be past the change. Not fired for work that is discarded and redone. Not cancellable: it has
 * happened.
 */
@ServerThread
public abstract class ZoneLifecycleEvent extends Event {
    private final ServerLevel level;
    private final ZoneId zone;
    private final BlockPos pos;

    /**
     * Creates the event. Only ZAS³ fires these.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone
     * @param pos a block in the zone, or where it was if it is gone
     */
    protected ZoneLifecycleEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        this.level = Objects.requireNonNull(level, "level");
        this.zone = Objects.requireNonNull(zone, "zone");
        this.pos = pos.immutable();
    }

    /**
     * The level.
     *
     * <p>Thread: server thread.
     *
     * @return the level the zone is in
     */
    public final ServerLevel level() {
        return level;
    }

    /**
     * The zone.
     *
     * <p>Thread: server thread.
     *
     * @return its id
     */
    public final ZoneId zone() {
        return zone;
    }

    /**
     * A block in the zone, or where it was if it is gone. Which of its blocks isn't promised.
     *
     * <p>Thread: server thread.
     *
     * @return an immutable position
     */
    public final BlockPos pos() {
        return pos;
    }
}
