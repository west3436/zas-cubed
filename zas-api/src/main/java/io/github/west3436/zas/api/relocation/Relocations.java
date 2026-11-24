package io.github.west3436.zas.api.relocation;

import io.github.west3436.zas.api.LevelThread;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Moving blocks with their air (API-065): for a mod that moves a box of blocks, such as a ship that
 * takes off or a structure that is picked up and put down, within a dimension or into another, with
 * or without a quarter turn. Every zone wholly inside the box goes with the blocks and arrives with
 * the same moles of each gas, the same temperature and the same pins (GEO-046). Nothing breaches,
 * vents or pushes on the way, no {@link io.github.west3436.zas.api.event.ZoneBreachedEvent} fires,
 * and a breathing entity carried along neither loses its air nor is hurt.
 *
 * <p><b>How to move.</b>
 *
 * <ol>
 *   <li>{@link #begin} with the box, before any block in it changes. The zones inside pause where
 *       they are and hold their gas, and the block changes around them wait, so take the blocks
 *       away when you like. The begin is applied by the next atmos tick, with the block changes made
 *       in the meantime, so you can start at once; {@link RelocationTicket#begun()} says whether it
 *       took. If it didn't, the zones weren't suspended, and react to the blocks leaving as to any
 *       change.
 *   <li>Move the blocks, block entities and entities as you do now.
 *   <li>{@link #complete} with the {@link Destination}, once every block is in place: in the same
 *       tick if you can. The zones go there; the future completes once they have arrived.
 * </ol>
 *
 * <p><b>A move over several ticks.</b> If you take the blocks away and set them down a few at a
 * time, name the destination when you begin ({@link #begin(ServerLevel, BoundingBox, Destination,
 * SourceId)}) and complete with the same one. Then {@link #isMoving} holds both boxes from the begin,
 * and a block that has arrived waits for the rest: a pipe keeps its gas while its neighbours are
 * still on their way (CONT-157).
 *
 * <p>Or {@link #cancel} instead of completing, if the move doesn't happen: the zones carry on where
 * they are. A ticket that is neither completed nor cancelled within the server's time limit (30 s
 * by default) is cancelled for you, and a server restart cancels every open ticket. Either way
 * nothing is lost: until a move completes, its zones are saved where they started.
 *
 * <p><b>What goes.</b> A zone goes if all its cells are inside the box. A zone with cells inside
 * and outside, such as the air of a hangar the box cuts through, stays: it is listed in {@link
 * RelocationResult#left()}, and its cells inside the box react to the blocks as they would to any
 * change. At the destination a zone takes its cells as the moved blocks give them. Where another
 * zone held those cells, that zone loses them and keeps its gas, as when blocks fill a room; a
 * zone that loses all its cells, such as the air the arriving hull closes off from a hangar, goes
 * to the open air. A zone that arrives open to the open air (its hull has a hole there) is then
 * opened as any zone would be, and fires a breach event if it wasn't open before. Fires burn on
 * through the move, carried with their blocks.
 *
 * <p><b>Events.</b> Each zone that arrives fires a {@link
 * io.github.west3436.zas.api.event.ZoneRelocatedEvent} in the destination's level, naming its id in
 * both places. {@link #complete} fires a {@link io.github.west3436.zas.api.event.BlocksMovedEvent}
 * before it returns, so a mod whose blocks link to each other by position can update the links. It
 * also carries SS13 Framework's stored areas wholly inside the box with the blocks ({@code
 * Areas.moved}), so a mover that hands over here doesn't move them itself.
 *
 * <p><b>Limits.</b> A move fails whole, saying why ({@link RelocationOutcome}): when a zone in the
 * box is paused or already being moved, when the zones together have more cells than the server
 * allows in one move (102,400 by default), and when a chunk the box, the destination or a zone in
 * it spans isn't loaded. The work is done on the atmos worker, about as much as forming the same
 * zones costs; the server thread only queues the steps.
 *
 * <p><b>Threads:</b> every method may be called only from the server thread, and throws {@link
 * IllegalStateException} otherwise (API-045). The returned futures complete on the server thread
 * once the atmos tick that applies the step has been applied, as mutations do (API-016).
 *
 * <p>Without ZAS³ installed, every step completes at once with {@link RelocationResult#ABSENT}, and
 * {@link #isMoving} is false (D-17).
 *
 * @since 1.2.0
 */
public final class Relocations {
    private Relocations() {}

    /**
     * Starts a move: suspends every zone wholly inside {@code box} (API-065).
     *
     * <p>Thread: server thread only.
     *
     * @param level the level the blocks leave
     * @param box the blocks that move, both corners inside
     * @param mover who moves them
     * @return the ticket, at once; its {@link RelocationTicket#begun()} completes on the server thread
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static RelocationTicket begin(ServerLevel level, BoundingBox box, SourceId mover) {
        ServerThreads.require(level, "Relocations.begin");
        return ZasApi.provider()
                .beginRelocation(level, Objects.requireNonNull(box, "box"), Objects.requireNonNull(mover, "mover"));
    }

    /**
     * Starts a move whose destination is known (API-069): as {@link #begin(ServerLevel, BoundingBox,
     * SourceId)}, and {@link #isMoving} holds the blocks where {@code destination} puts the box from now
     * on, not only once {@link #complete} is called. A mover that sets its blocks down over several
     * ticks begins this way, so what arrives first waits for the rest. Complete with the same
     * destination.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level the blocks leave
     * @param box the blocks that move, both corners inside
     * @param destination where they go
     * @param mover who moves them
     * @return the ticket, at once; its {@link RelocationTicket#begun()} completes on the server thread
     * @throws IllegalStateException if called off the server thread
     * @since 1.3.0
     */
    @ServerThread
    public static RelocationTicket begin(ServerLevel level, BoundingBox box, Destination destination, SourceId mover) {
        ServerThreads.require(level, "Relocations.begin");
        ServerThreads.require(Objects.requireNonNull(destination, "destination").level(), "Relocations.begin");
        return ZasApi.provider()
                .beginRelocation(
                        level, Objects.requireNonNull(box, "box"), destination, Objects.requireNonNull(mover, "mover"));
    }

    /**
     * Ends a move: the ticket's zones go to where {@code destination} puts their blocks (API-065).
     * Call it once the blocks are in place. If a chunk the box or its destination spans isn't
     * loaded, the result is {@link RelocationOutcome#UNLOADED} at once and the ticket stays open.
     *
     * <p>Thread: server thread only.
     *
     * @param ticket the ticket {@link #begin} gave
     * @param destination where the blocks went
     * @return a future completing on the server thread with {@link RelocationOutcome#MOVED} and where
     *     each zone is now, once every zone has arrived; or why nothing moved
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static CompletableFuture<RelocationResult> complete(RelocationTicket ticket, Destination destination) {
        Objects.requireNonNull(ticket, "ticket");
        ServerThreads.require(ticket.level(), "Relocations.complete");
        ServerThreads.require(Objects.requireNonNull(destination, "destination").level(), "Relocations.complete");
        return ZasApi.provider().completeRelocation(ticket, destination);
    }

    /**
     * Ends a move without moving: the ticket's zones carry on where they are (API-065).
     *
     * <p>Thread: server thread only.
     *
     * @param ticket the ticket {@link #begin} gave
     * @return a future completing on the server thread with {@link RelocationOutcome#CANCELLED}, or
     *     {@link RelocationOutcome#ENDED} or {@link RelocationOutcome#TIMED_OUT} if the ticket was over
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static CompletableFuture<RelocationResult> cancel(RelocationTicket ticket) {
        Objects.requireNonNull(ticket, "ticket");
        ServerThreads.require(ticket.level(), "Relocations.cancel");
        return ZasApi.provider().cancelRelocation(ticket);
    }

    /**
     * Whether a block is being moved (API-067): it is inside the box of an open ticket, or where a
     * completed move put blocks whose zones haven't arrived yet, or where an open ticket that named
     * its destination at the begin is putting them (API-069). A block entity that hands gas back to
     * the world when it is removed should keep it instead while this is true, since the mover carries
     * the block entity's data to the destination. ZAS³'s devices don't step while it is true
     * (CONT-157): a neighbour they would react to may be gone or not yet there.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param pos the block
     * @return whether a move holds it; false without ZAS³
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static boolean isMoving(ServerLevel level, BlockPos pos) {
        ServerThreads.require(level, "Relocations.isMoving");
        return ZasApi.provider().isRelocating(level, Objects.requireNonNull(pos, "pos"));
    }

    /**
     * Where the world shows a point of a level (API-068). A mod may keep blocks in one place in their
     * level and show them in another: Sable keeps a sub-level's blocks in its plot, far out in the
     * level, and shows them where the ship is. Zones, cells and block positions name the place the
     * blocks are kept; a player sees them, and should be told of them, where they show. Use it for
     * coordinates a player reads.
     *
     * <p>Thread: the level's own: the server thread for a server-side level, the client thread for a
     * client-side one.
     *
     * @param level the level
     * @param point a point of it, where blocks are kept
     * @return where the world shows it, now; the point itself where no mod shows it elsewhere, and
     *     without ZAS³
     * @throws IllegalStateException if called off the server thread for a server-side level
     * @since 1.2.0
     */
    @LevelThread
    public static Vec3 shownAt(Level level, Vec3 point) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(point, "point");
        if (level instanceof ServerLevel server) {
            ServerThreads.require(server, "Relocations.shownAt");
        }
        return ZasApi.provider().shownAt(level, point);
    }

    /**
     * Where the world shows a block of a level (API-068): the block that holds where its centre shows,
     * as {@link #shownAt(Level, Vec3)} places it.
     *
     * <p>Thread: the level's own, as for {@link #shownAt(Level, Vec3)}.
     *
     * @param level the level
     * @param pos a block of it, where blocks are kept
     * @return the block where the world shows it; {@code pos} itself where no mod shows it elsewhere,
     *     and without ZAS³
     * @throws IllegalStateException if called off the server thread for a server-side level
     * @since 1.2.0
     */
    @LevelThread
    public static BlockPos shownAt(Level level, BlockPos pos) {
        Objects.requireNonNull(pos, "pos");
        Vec3 centre = Vec3.atCenterOf(pos);
        Vec3 shown = shownAt(level, centre);
        return shown.equals(centre) ? pos : BlockPos.containing(shown);
    }
}
