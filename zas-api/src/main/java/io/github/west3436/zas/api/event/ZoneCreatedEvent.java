package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A zone formed (API-030): gas was added to a sealed room, or a pin formed one there ({@link
 * io.github.west3436.zas.api.mutation.ZonePins}). A zone that forms with an opening to the ambient
 * fires no {@link ZoneBreachedEvent} for it.
 *
 * <p>Not fired for the new zone of a split, which has {@link ZoneSplitEvent}, or for a zone coming
 * back from chunk storage, which has {@link ZoneResumedEvent}.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneCreatedEvent extends ZoneLifecycleEvent {
    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the new zone
     * @param pos a block in the zone
     */
    public ZoneCreatedEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        super(level, zone, pos);
    }
}
