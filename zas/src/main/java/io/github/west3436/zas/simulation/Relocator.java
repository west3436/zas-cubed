package io.github.west3436.zas.simulation;

import io.github.west3436.ss13.api.area.Areas;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.BlocksMovedEvent;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationOutcome;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.scheduler.Relocation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * The server's side of moves (API-065, API-067): the open tickets, what each step waits for, and the
 * hand-over of departed zones to the destination's {@link DimensionAtmos}, which keeps them until they
 * arrive (DATA-020). Tickets aren't saved: a restart ends them, and their zones are saved where they
 * were, unless they had left, in which case the destination's data holds them. Server thread.
 */
public final class Relocator {
    private final AtmosService service;
    private long nextTicket = 1;
    private final Map<RelocationTicket, Move> moves = new IdentityHashMap<>();
    /** Why each ended ticket ended, while anyone holds it. */
    private final Map<RelocationTicket, RelocationOutcome> ended = new WeakHashMap<>();

    private enum State {
        /** The begin is queued. */
        BEGINNING,
        /** The zones are suspended. */
        OPEN,
        /** Complete was called; the zones are leaving or on their way. */
        COMPLETING
    }

    private static final class Move {
        final RelocationTicket ticket;
        final DimensionAtmos source;
        final long openedAt;
        State state = State.BEGINNING;
        Set<ZoneId> left = Set.of();
        CompletableFuture<RelocationResult> completed;
        DimensionAtmos target;
        BoundingBox targetBox;
        /** Where the begin said the blocks go (API-069), held as moving until the complete. */
        @Nullable
        ServerLevel plannedLevel;

        @Nullable
        BoundingBox plannedBox;

        long[] targetChunks = new long[0];
        int waiting;
        final Map<ZoneId, ZoneId> moved = new LinkedHashMap<>();

        Move(RelocationTicket ticket, DimensionAtmos source, long openedAt) {
            this.ticket = ticket;
            this.source = source;
            this.openedAt = openedAt;
        }
    }

    Relocator(AtmosService service) {
        this.service = service;
    }

    /**
     * Starts a move ({@link io.github.west3436.zas.api.relocation.Relocations#begin}). A destination
     * named now is held as moving from now on (API-069), so blocks set down there before the move
     * completes wait for the rest.
     */
    public RelocationTicket begin(
            ServerLevel level, BoundingBox box, @Nullable Destination destination, SourceId mover) {
        DimensionAtmos d = service.dimensionFor(level);
        CompletableFuture<RelocationResult> begun = new CompletableFuture<>();
        RelocationTicket ticket = new RelocationTicket(level, box, destination, mover, nextTicket++, begun);
        Move m = new Move(ticket, d, level.getServer().getTickCount());
        if (destination != null) {
            m.plannedLevel = destination.level();
            m.plannedBox = destination.map(box);
        }
        if (!allLoaded(d, box)) {
            end(m, RelocationOutcome.UNLOADED);
            begun.complete(RelocationResult.of(RelocationOutcome.UNLOADED));
            return ticket;
        }
        moves.put(ticket, m);
        long seq;
        try {
            seq = d.beginRelocation(ticket.id(), box, ZasServerConfig.relocationMaxCells(), o -> begun(m, o));
        } catch (IllegalStateException e) {
            moves.remove(ticket);
            ended.put(ticket, RelocationOutcome.ENDED);
            begun.completeExceptionally(e);
            return ticket;
        }
        if (seq < 0) {
            // Not simulated: no zones to carry.
            m.state = State.OPEN;
            begun.complete(new RelocationResult(RelocationOutcome.BEGUN, Map.of(), Set.of()));
        }
        return ticket;
    }

    private void begun(Move m, Relocation.Outcome o) {
        CompletableFuture<RelocationResult> begun = m.ticket.begun();
        if (o.status() == Relocation.Status.BEGUN) {
            if (m.state == State.BEGINNING) {
                m.state = State.OPEN;
            }
            m.left = ids(o.straddling());
            Map<ZoneId, ZoneId> zones = new LinkedHashMap<>();
            for (long id : o.zones()) {
                zones.put(new ZoneId(id), new ZoneId(id));
            }
            begun.complete(new RelocationResult(RelocationOutcome.BEGUN, zones, m.left));
            return;
        }
        RelocationOutcome why = switch (o.status()) {
            case PAUSED -> RelocationOutcome.PAUSED;
            case BUSY -> RelocationOutcome.BUSY;
            case TOO_LARGE -> RelocationOutcome.TOO_LARGE;
            default -> RelocationOutcome.ENDED;
        };
        if (moves.get(m.ticket) == m && m.state != State.COMPLETING) {
            end(m, why);
        } else {
            ended.put(m.ticket, why); // a complete in flight answers with this
        }
        begun.complete(RelocationResult.of(why));
    }

    /** Ends a move ({@link io.github.west3436.zas.api.relocation.Relocations#complete}). */
    public CompletableFuture<RelocationResult> complete(RelocationTicket ticket, Destination destination) {
        Move m = moves.get(ticket);
        if (m == null) {
            return done(endedOutcome(ticket));
        }
        if (m.state == State.COMPLETING) {
            return done(RelocationOutcome.ENDED);
        }
        BoundingBox to = destination.map(ticket.box());
        DimensionAtmos target = service.dimensionFor(destination.level());
        if (!allLoaded(m.source, ticket.box()) || !allLoaded(target, to)) {
            return done(RelocationOutcome.UNLOADED);
        }
        ApiEvents.post(new BlocksMovedEvent(ticket.level(), ticket.box(), destination, ticket.mover()));
        // SS13 Framework's stored areas go with the blocks, so a mover that hands over here doesn't
        // move them itself.
        Areas.moved(
                ticket.level(),
                ticket.box(),
                destination.level(),
                destination.pivot(),
                destination.target(),
                destination.rotation());
        m.state = State.COMPLETING;
        m.completed = new CompletableFuture<>();
        m.target = target;
        m.targetBox = to;
        m.targetChunks = chunks(to);
        target.expectArrival(m.targetChunks);
        CompletableFuture<RelocationResult> result = m.completed;
        long seq = m.source.completeRelocation(ticket.id(), transform(destination), o -> departed(m, o));
        if (seq < 0) {
            finish(m); // not simulated: nothing to carry
        }
        return result;
    }

    private void departed(Move m, Relocation.Outcome o) {
        switch (o.status()) {
            case DEPARTED -> {
                List<Relocation.Departure> left = o.departed();
                if (left.isEmpty()) {
                    finish(m);
                    return;
                }
                m.waiting = left.size();
                boolean same = m.target == m.source;
                String mover = m.ticket.mover().toString();
                for (Relocation.Departure d : left) {
                    PendingArrival a = new PendingArrival(
                            0,
                            same ? d.zoneId() : 0,
                            d.zoneId(),
                            m.source.dimension(),
                            mover,
                            d.blocks(),
                            d.hints(),
                            m.source.molesById(d.moles()),
                            d.temperature(),
                            d.pins(),
                            d.breached());
                    long from = d.zoneId();
                    m.target.queueArrival(a, zone -> arrivedOne(m, from, zone));
                }
            }
            case UNLOADED -> {
                // A zone's chunk unloaded since complete was called: the ticket stays open.
                m.target.arrived(m.targetChunks);
                m.state = State.OPEN;
                CompletableFuture<RelocationResult> f = m.completed;
                m.completed = null;
                f.complete(RelocationResult.of(RelocationOutcome.UNLOADED));
            }
            default -> {
                m.target.arrived(m.targetChunks);
                RelocationOutcome why = ended.getOrDefault(m.ticket, RelocationOutcome.ENDED);
                end(m, why);
                m.completed.complete(RelocationResult.of(why));
            }
        }
    }

    private void arrivedOne(Move m, long from, long zone) {
        if (zone < 0) {
            // The destination stopped first: its data keeps the zone, which arrives when it starts.
            if (moves.get(m.ticket) == m) {
                end(m, RelocationOutcome.ENDED);
                m.completed.completeExceptionally(
                        new CancellationException("server stopping; the zones arrive after the restart"));
            }
            return;
        }
        if (zone != 0) {
            m.moved.put(new ZoneId(from), new ZoneId(zone));
        }
        if (--m.waiting == 0) {
            finish(m);
        }
    }

    private void finish(Move m) {
        m.target.arrived(m.targetChunks);
        end(m, RelocationOutcome.MOVED);
        m.completed.complete(new RelocationResult(RelocationOutcome.MOVED, m.moved, m.left));
    }

    /** Ends a move without moving ({@link io.github.west3436.zas.api.relocation.Relocations#cancel}). */
    public CompletableFuture<RelocationResult> cancel(RelocationTicket ticket) {
        Move m = moves.get(ticket);
        if (m == null) {
            return done(endedOutcome(ticket));
        }
        if (m.state == State.COMPLETING) {
            return done(RelocationOutcome.ENDED);
        }
        end(m, RelocationOutcome.CANCELLED);
        CompletableFuture<RelocationResult> f = new CompletableFuture<>();
        long seq = m.source.cancelRelocation(ticket.id(), o -> {
            Map<ZoneId, ZoneId> zones = new LinkedHashMap<>();
            for (long id : o.zones()) {
                zones.put(new ZoneId(id), new ZoneId(id));
            }
            f.complete(
                    o.status() == Relocation.Status.CANCELLED
                            ? new RelocationResult(RelocationOutcome.CANCELLED, zones, Set.of())
                            : RelocationResult.of(RelocationOutcome.ENDED));
        });
        if (seq < 0) {
            f.complete(new RelocationResult(RelocationOutcome.CANCELLED, Map.of(), Set.of()));
        }
        return f;
    }

    /** Whether a move holds a block (API-067, API-069). */
    public boolean isMoving(ServerLevel level, BlockPos pos) {
        for (Move m : moves.values()) {
            if (m.state == State.COMPLETING) {
                if (m.target.level() == level && m.targetBox.isInside(pos)) {
                    return true;
                }
            } else if ((m.ticket.level() == level && m.ticket.box().isInside(pos))
                    || (m.plannedLevel == level && m.plannedBox.isInside(pos))) {
                return true;
            }
        }
        return false;
    }

    /** Cancels the moves open longer than the server allows (GEO-047). Once per game tick. */
    void tick(long gameTick) {
        if (moves.isEmpty()) {
            return;
        }
        long limit = ZasServerConfig.relocationTimeoutTicks();
        for (Move m : new ArrayList<>(moves.values())) {
            if (m.state != State.COMPLETING && gameTick - m.openedAt > limit) {
                end(m, RelocationOutcome.TIMED_OUT);
                m.source.cancelRelocation(m.ticket.id(), o -> {});
            }
        }
    }

    /** Ends every move, as the server stops: their zones are saved where they are, or as arrivals. */
    void stop() {
        for (Move m : new ArrayList<>(moves.values())) {
            end(m, RelocationOutcome.ENDED);
            m.ticket.begun().complete(RelocationResult.of(RelocationOutcome.ENDED));
            if (m.completed != null) {
                m.completed.completeExceptionally(
                        new CancellationException("server stopping; the zones arrive after the restart"));
            }
        }
    }

    /** How many moves are open. */
    public int openMoves() {
        return moves.size();
    }

    private void end(Move m, RelocationOutcome why) {
        moves.remove(m.ticket);
        ended.put(m.ticket, why);
    }

    /** What complete and cancel answer for a ticket that is over. */
    private RelocationOutcome endedOutcome(RelocationTicket ticket) {
        return ended.get(ticket) == RelocationOutcome.TIMED_OUT ? RelocationOutcome.TIMED_OUT : RelocationOutcome.ENDED;
    }

    private static CompletableFuture<RelocationResult> done(RelocationOutcome outcome) {
        return CompletableFuture.completedFuture(RelocationResult.of(outcome));
    }

    private static Set<ZoneId> ids(long[] ids) {
        Set<ZoneId> out = new TreeSet<>(Comparator.comparingLong(ZoneId::value));
        for (long id : ids) {
            out.add(new ZoneId(id));
        }
        return out;
    }

    private static boolean allLoaded(DimensionAtmos d, BoundingBox box) {
        for (long c : chunks(box)) {
            if (!d.isChunkLoaded(c)) {
                return false;
            }
        }
        return true;
    }

    private static long[] chunks(BoundingBox box) {
        int x0 = box.minX() >> 4;
        int x1 = box.maxX() >> 4;
        int z0 = box.minZ() >> 4;
        int z1 = box.maxZ() >> 4;
        long[] out = new long[(x1 - x0 + 1) * (z1 - z0 + 1)];
        int i = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out[i++] = ChunkPos.asLong(x, z);
            }
        }
        return out;
    }

    /** The engine's form of a destination's placement. */
    static Relocation.Transform transform(Destination d) {
        int turns = switch (d.rotation()) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
        return new Relocation.Transform(
                d.pivot().getX(),
                d.pivot().getY(),
                d.pivot().getZ(),
                d.target().getX(),
                d.target().getY(),
                d.target().getZ(),
                turns);
    }
}
