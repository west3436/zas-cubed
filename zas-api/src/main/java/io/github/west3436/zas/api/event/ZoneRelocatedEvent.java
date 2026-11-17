package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * A zone arrived where a mover put its blocks (API-066, {@link
 * io.github.west3436.zas.api.relocation.Relocations}): {@link #zone()} in {@link #level()} is the
 * zone that was {@link #from()} in {@link #fromDimension()}, with the same gas, temperature and pins.
 * Within a dimension it keeps its id; in another it has a new one, and the old id names no zone.
 *
 * <p>This is the only event of the move: the zone fires no {@link ZonePausedEvent} or {@link
 * ZoneResumedEvent} for it, and no {@link ZoneCreatedEvent} or {@link ZoneDissolvedEvent}.
 *
 * <p>How it fires is in {@link ZoneLifecycleEvent}.
 *
 * @since 1.2.0
 */
@ServerThread
public final class ZoneRelocatedEvent extends ZoneLifecycleEvent {
    private final ResourceKey<Level> fromDimension;
    private final ZoneId from;
    private final SourceId mover;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level the zone arrived in
     * @param zone its id there
     * @param pos a block in it
     * @param fromDimension the dimension it left
     * @param from its id there
     * @param mover who moved it
     */
    public ZoneRelocatedEvent(
            ServerLevel level,
            ZoneId zone,
            BlockPos pos,
            ResourceKey<Level> fromDimension,
            ZoneId from,
            SourceId mover) {
        super(level, zone, pos);
        this.fromDimension = Objects.requireNonNull(fromDimension, "fromDimension");
        this.from = Objects.requireNonNull(from, "from");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /**
     * The dimension the zone left.
     *
     * <p>Thread: server thread.
     *
     * @return its key; {@link #level()}'s for a move within a dimension
     */
    public ResourceKey<Level> fromDimension() {
        return fromDimension;
    }

    /**
     * The zone's id where it left.
     *
     * <p>Thread: server thread.
     *
     * @return the id, the same as {@link #zone()} within a dimension
     */
    public ZoneId from() {
        return from;
    }

    /**
     * Who moved it.
     *
     * <p>Thread: server thread.
     *
     * @return the mover's id, as given to {@link
     *     io.github.west3436.zas.api.relocation.Relocations#begin}
     */
    public SourceId mover() {
        return mover;
    }
}
