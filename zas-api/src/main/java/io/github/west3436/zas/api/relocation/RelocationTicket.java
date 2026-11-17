package io.github.west3436.zas.api.relocation;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A move in progress (API-065), from {@link Relocations#begin} until {@link Relocations#complete} or
 * {@link Relocations#cancel}. Pass it back to one of those, once. Tickets are equal only to
 * themselves, and aren't saved: a server restart ends every open ticket, and the zones it held carry
 * on where they are.
 *
 * @since 1.2.0
 */
@AnyThread
public final class RelocationTicket {
    private final ServerLevel level;
    private final BoundingBox box;
    private final SourceId mover;
    private final long id;
    private final CompletableFuture<RelocationResult> begun;

    /**
     * Creates a ticket. Only a provider creates them; a mover gets one from {@link
     * Relocations#begin}.
     *
     * <p>Thread: any.
     *
     * @param level the level the blocks leave
     * @param box the blocks
     * @param mover who moves them
     * @param id the provider's number for it
     * @param begun completes with the begin's result
     */
    public RelocationTicket(
            ServerLevel level, BoundingBox box, SourceId mover, long id, CompletableFuture<RelocationResult> begun) {
        this.level = Objects.requireNonNull(level, "level");
        this.box = Objects.requireNonNull(box, "box");
        this.mover = Objects.requireNonNull(mover, "mover");
        this.id = id;
        this.begun = Objects.requireNonNull(begun, "begun");
    }

    /**
     * The level the blocks leave.
     *
     * <p>Thread: any.
     *
     * @return the level
     */
    public ServerLevel level() {
        return level;
    }

    /**
     * The blocks being moved, both corners inside.
     *
     * <p>Thread: any.
     *
     * @return the box
     */
    public BoundingBox box() {
        return box;
    }

    /**
     * Who moves them.
     *
     * <p>Thread: any.
     *
     * @return the mover's id
     */
    public SourceId mover() {
        return mover;
    }

    /**
     * The provider's number for the ticket, unique within a server session.
     *
     * <p>Thread: any.
     *
     * @return the number; 0 without ZAS³
     */
    public long id() {
        return id;
    }

    /**
     * The begin's result: {@link RelocationOutcome#BEGUN} with the zones suspended, or why the move
     * can't carry them. Completes on the server thread once the atmos tick that applies the begin has
     * been applied, so usually a second or less after {@link Relocations#begin}.
     *
     * <p>Thread: any; it completes on the server thread.
     *
     * @return the future; the same one every time
     */
    public CompletableFuture<RelocationResult> begun() {
        return begun;
    }

    @Override
    public String toString() {
        return "RelocationTicket[" + id + " by " + mover + " in "
                + level.dimension().location() + "]";
    }
}
