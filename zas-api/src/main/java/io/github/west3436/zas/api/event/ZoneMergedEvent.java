package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Two zones became one (API-030): {@link #zone()} took in {@link #absorbed()}'s cells and gas, and
 * that id is gone. The zone with more cells survives, or on a tie the one with the lower id.
 *
 * <p>The absorbed zone fires no {@link ZoneDissolvedEvent}.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 */
@ServerThread
public final class ZoneMergedEvent extends ZoneLifecycleEvent {
    private final ZoneId absorbed;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param survivor the zone that keeps its id
     * @param absorbed the zone that is gone
     * @param pos a block in the survivor
     */
    public ZoneMergedEvent(ServerLevel level, ZoneId survivor, ZoneId absorbed, BlockPos pos) {
        super(level, survivor, pos);
        this.absorbed = Objects.requireNonNull(absorbed, "absorbed");
    }

    /**
     * The zone that was taken in.
     *
     * <p>Thread: server thread.
     *
     * @return its id, which no zone has any more
     */
    public ZoneId absorbed() {
        return absorbed;
    }
}
