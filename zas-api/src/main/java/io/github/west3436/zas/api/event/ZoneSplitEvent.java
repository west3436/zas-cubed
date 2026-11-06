package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A zone was cut in two (API-030): {@link #zone()} keeps its id, and the part cut off is the new zone
 * {@link #remnant()}, with the same composition and temperature, and the same pins. A zone cut into
 * three fires this twice.
 *
 * <p>The remnant fires no {@link ZoneCreatedEvent}. It carries on from its zone's state, so it fires
 * {@link ZoneBreachedEvent} or {@link ZoneSealedEvent} only where it differs from that.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneSplitEvent extends ZoneLifecycleEvent {
    private final ZoneId remnant;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param zone the zone that keeps its id
     * @param remnant the new zone
     * @param pos a block in the zone that keeps its id
     */
    public ZoneSplitEvent(ServerLevel level, ZoneId zone, ZoneId remnant, BlockPos pos) {
        super(level, zone, pos);
        this.remnant = Objects.requireNonNull(remnant, "remnant");
    }

    /**
     * The part cut off.
     *
     * <p>Thread: server thread.
     *
     * @return the new zone's id
     */
    public ZoneId remnant() {
        return remnant;
    }
}
