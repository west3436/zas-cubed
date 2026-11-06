package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A sealed zone gained an opening to the ambient (API-030), and now vents through it. Fired for the
 * first opening only, and again only after {@link ZoneSealedEvent}.
 *
 * <p>While the region beyond a new opening is still being classified the zone is treated as open, but
 * this waits until that is known, so a hole into a pocket that turns out to be enclosed fires nothing.
 * A paused zone fires it once it resumes.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneBreachedEvent extends ZoneLifecycleEvent {
    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone breached
     * @param pos a block in the zone
     */
    public ZoneBreachedEvent(ServerLevel level, ZoneId zone, BlockPos pos) {
        super(level, zone, pos);
    }
}
