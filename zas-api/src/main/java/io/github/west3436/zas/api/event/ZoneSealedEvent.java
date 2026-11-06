package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A breached zone lost its last opening to the ambient (API-030), and has stopped venting. A zone that
 * forms sealed fires nothing for it: this follows {@link ZoneBreachedEvent}, or a zone that formed
 * with an opening.
 *
 * <p>A paused zone fires it once it resumes.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneSealedEvent extends ZoneLifecycleEvent {
    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone sealed
     * @param pos a block in the zone
     */
    public ZoneSealedEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        super(level, zone, pos);
    }
}
