package io.github.west3436.zas.example;

import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.api.relocation.Relocations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A worked mover (API-065): moves a box of blocks, their block entities and the entities in it to a
 * {@link Destination}, and asks ZAS³ to carry the rooms inside along, gas, temperature and pins. A
 * mod that moves blocks, such as a ship or a sub-level, calls {@link Relocations} around its own move
 * in the same way. Without ZAS³ the blocks move just the same, and the steps answer {@code ABSENT}.
 *
 * <p>A move must leave nothing behind and make nothing twice. Removing blocks one by one with {@code
 * setBlock(pos, AIR, UPDATE_ALL)}, as a player breaking them would, does both: a container spills
 * what it holds, though its data is copied to the destination, and each removal tells its neighbours,
 * so the upper half of a door, a torch on a wall or a block on a floor breaks and drops before its own
 * turn comes. So this mover:
 *
 * <ol>
 *   <li>copies every block state and block entity's data before anything changes;
 *   <li>removes each block's block entity before its block, so nothing is left to spill, and removes
 *       the blocks top down with {@link #QUIET}, which tells no neighbour and drops nothing;
 *   <li>sets the blocks down with {@link #QUIET} too, so no block is asked whether it can stand before
 *       its support and its other half are in place, then loads the copied data;
 *   <li>once every block stands, tells the neighbours at both sites, as {@code UPDATE_ALL} would have.
 * </ol>
 *
 * <p>The destination should be clear: the mover sets its blocks over whatever stands there.
 */
public final class ShipMover {
    /** Who moves the blocks, as {@code ZoneRelocatedEvent#mover()} names it. */
    public static final SourceId MOVER = SourceId.of(ExampleAddonMod.MOD_ID, "ship_mover");

    /**
     * Flags that change a block and tell the clients, and nothing else: no neighbour updates or shape
     * checks, no drops, and the block hears that it is moved, as by a piston, rather than broken or
     * placed.
     */
    public static final int QUIET =
            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS | Block.UPDATE_MOVE_BY_PISTON;

    private ShipMover() {}

    /**
     * Moves everything in a box. Server thread only.
     *
     * @param from the level the blocks are in
     * @param box the blocks to move
     * @param to where they go, and how they turn
     * @return the complete step's future: {@code MOVED} once the rooms have arrived, or why they didn't
     */
    public static CompletableFuture<RelocationResult> move(ServerLevel from, BoundingBox box, Destination to) {
        // 1. Copy what moves, block entity data included, before anything changes.
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> data = new LinkedHashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(
                new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
            BlockPos at = p.immutable();
            states.put(at, from.getBlockState(at));
            BlockEntity be = from.getBlockEntity(at);
            if (be != null) {
                data.put(at, be.saveWithoutMetadata(from.registryAccess()));
            }
        }
        List<Entity> riders = new ArrayList<>(from.getEntities((Entity) null, AABB.of(box), e -> !e.isPassenger()));

        // 2. Begin before the first block changes: the rooms inside are held for the move. Naming the
        // destination holds it too, so a mover that sets its blocks down over several ticks keeps the
        // devices that arrive first waiting for the rest (API-069); this one does it all in one tick.
        RelocationTicket ticket = Relocations.begin(from, box, to, MOVER);

        // 3. Take the blocks away without breaking them: each block entity first, so a container has
        // nothing to spill, then its block. Top down, so what rests on a block goes before it, even if
        // a block's own onRemove tells its neighbours.
        List<BlockPos> topDown = new ArrayList<>(states.keySet());
        topDown.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).reversed());
        for (BlockPos p : topDown) {
            from.removeBlockEntity(p);
            from.setBlock(p, Blocks.AIR.defaultBlockState(), QUIET);
        }

        // 4. Set them down turned, all of them before any is asked whether it can stand, with their data.
        ServerLevel there = to.level();
        states.forEach((p, state) -> there.setBlock(to.map(p), state.rotate(to.rotation()), QUIET));
        data.forEach((p, tag) -> {
            BlockEntity be = there.getBlockEntity(to.map(p));
            if (be != null) {
                be.loadWithComponents(tag, there.registryAccess());
                be.setChanged();
            }
        });

        // 5. Now tell the neighbours, once, at both sites: blocks outside the box see the ship gone and
        // the ship arrived, and the moved blocks fit their new surroundings.
        states.forEach((p, state) -> {
            settle(from, p, state);
            settle(there, to.map(p), Blocks.AIR.defaultBlockState());
        });

        for (Entity e : riders) {
            Vec3 at = map(to, e.position());
            float yaw = e.getYRot() + 90 * turns(to);
            e.teleportTo(there, at.x, at.y, at.z, Set.of(), yaw, e.getXRot());
        }

        // 6. Complete once every block is in place, in the same tick.
        return Relocations.complete(ticket, to);
    }

    /**
     * What {@code setBlock} with {@code UPDATE_ALL} does after a change, done once the move is over: the
     * neighbours hear of it, and those whose shape depends on it, such as a fence or a torch, fit it.
     */
    private static void settle(ServerLevel level, BlockPos p, BlockState was) {
        BlockState now = level.getBlockState(p);
        level.updateNeighborsAt(p, was.getBlock());
        was.updateIndirectNeighbourShapes(level, p, Block.UPDATE_ALL);
        now.updateNeighbourShapes(level, p, Block.UPDATE_ALL);
        now.updateIndirectNeighbourShapes(level, p, Block.UPDATE_ALL);
    }

    /** Where a destination puts a point, as it puts the block the point is in. */
    static Vec3 map(Destination to, Vec3 p) {
        double x = p.x - (to.pivot().getX() + 0.5);
        double z = p.z - (to.pivot().getZ() + 0.5);
        double rx = x;
        double rz = z;
        for (int i = 0; i < turns(to); i++) { // a clockwise quarter: (x, z) to (-z, x)
            double t = rx;
            rx = -rz;
            rz = t;
        }
        return new Vec3(
                to.target().getX() + 0.5 + rx,
                p.y - to.pivot().getY() + to.target().getY(),
                to.target().getZ() + 0.5 + rz);
    }

    private static int turns(Destination to) {
        return switch (to.rotation()) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }
}
