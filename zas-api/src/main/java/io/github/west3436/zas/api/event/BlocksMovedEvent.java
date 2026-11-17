package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.Event;

/**
 * A mover has moved a box of blocks and told ZAS³ so (API-067, {@link
 * io.github.west3436.zas.api.relocation.Relocations#complete}). For a mod whose blocks or saved data
 * refer to other blocks by position: a link from a block inside the box to another inside it now
 * reads {@link #map}ped, as the blocks have kept their places relative to each other. A link to or
 * from a block outside the box is the mod's call; ZAS³'s own devices drop one that would cross
 * dimensions or lead out of the box.
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the server thread, from within {@link
 * io.github.west3436.zas.api.relocation.Relocations#complete}, once the blocks are in place and
 * before the zones follow. Not fired when complete refuses. Not cancellable: the blocks have moved.
 *
 * @since 1.2.0
 */
@ServerThread
public final class BlocksMovedEvent extends Event {
    private final ServerLevel from;
    private final BoundingBox box;
    private final Destination destination;
    private final SourceId mover;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param from the level the blocks left
     * @param box the blocks, where they were
     * @param destination where they went
     * @param mover who moved them
     */
    public BlocksMovedEvent(ServerLevel from, BoundingBox box, Destination destination, SourceId mover) {
        this.from = Objects.requireNonNull(from, "from");
        this.box = Objects.requireNonNull(box, "box");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /**
     * The level the blocks left.
     *
     * <p>Thread: server thread.
     *
     * @return the level
     */
    public ServerLevel from() {
        return from;
    }

    /**
     * The blocks, where they were, both corners inside.
     *
     * <p>Thread: server thread.
     *
     * @return the box
     */
    public BoundingBox box() {
        return box;
    }

    /**
     * Where the blocks went.
     *
     * <p>Thread: server thread.
     *
     * @return the destination
     */
    public Destination destination() {
        return destination;
    }

    /**
     * Who moved them.
     *
     * <p>Thread: server thread.
     *
     * @return the mover's id
     */
    public SourceId mover() {
        return mover;
    }

    /**
     * Whether a position in {@link #from()} was among the blocks moved.
     *
     * <p>Thread: server thread.
     *
     * @param pos the position before the move
     * @return whether it is inside {@link #box()}
     */
    public boolean moved(BlockPos pos) {
        return box.isInside(pos);
    }

    /**
     * Where a block went.
     *
     * <p>Thread: server thread.
     *
     * @param pos its position before the move
     * @return its position after, in {@link Destination#level()}
     */
    public BlockPos map(BlockPos pos) {
        return destination.map(pos);
    }
}
