package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A zone is gone along with its gas (API-030). It matched the ambient, at once with an opening to it
 * and after a configured time if sealed, unless pinned ({@link
 * io.github.west3436.zas.api.mutation.ZonePins}); an operator reset it; or its last cell was filled.
 * Its cells, if any are left, now read as the ambient.
 *
 * <p>Not fired for a zone merged into another, which has {@link ZoneMergedEvent}, or for one stored
 * with its unloaded chunks, which has {@link ZonePausedEvent}.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneDissolvedEvent extends ZoneLifecycleEvent {
    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone that is gone
     * @param pos where it was: a block it had
     */
    public ZoneDissolvedEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        super(level, zone, pos);
    }
}
