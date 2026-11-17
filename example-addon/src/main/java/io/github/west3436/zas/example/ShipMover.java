package io.github.west3436.zas.example;

import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.relocation.Destination;
import io.github.west3436.zas.api.relocation.RelocationResult;
import io.github.west3436.zas.api.relocation.RelocationTicket;
import io.github.west3436.zas.api.relocation.Relocations;
import java.util.ArrayList;
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
 */
public final class ShipMover {
    /** Who moves the blocks, as {@code ZoneRelocatedEvent#mover()} names it. */
    public static final SourceId MOVER = SourceId.of(ExampleAddonMod.MOD_ID, "ship_mover");

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

        // 2. Begin before the first block changes: the rooms inside are held for the move.
        RelocationTicket ticket = Relocations.begin(from, box, MOVER);

        // 3. Take the blocks away, then set them down turned, with their data.
        for (BlockPos p : states.keySet()) {
            from.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        states.forEach((p, state) -> to.level().setBlock(to.map(p), state.rotate(to.rotation()), Block.UPDATE_ALL));
        data.forEach((p, tag) -> {
            BlockEntity be = to.level().getBlockEntity(to.map(p));
            if (be != null) {
                be.loadWithComponents(tag, to.level().registryAccess());
                be.setChanged();
            }
        });
        for (Entity e : riders) {
            Vec3 at = map(to, e.position());
            float yaw = e.getYRot() + 90 * turns(to);
            e.teleportTo(to.level(), at.x, at.y, at.z, Set.of(), yaw, e.getXRot());
        }

        // 4. Complete once every block is in place, in the same tick.
        return Relocations.complete(ticket, to);
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
