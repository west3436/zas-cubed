package io.github.west3436.zas.api.relocation;

import io.github.west3436.zas.api.AnyThread;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a move puts its blocks (API-065): the block at {@code pivot} goes to {@code target} in {@code
 * level}, and every other block keeps its place relative to it, turned by {@code rotation} about the
 * vertical axis, as {@link BlockPos#rotate} turns a position. So a block at {@code p} goes to {@code
 * target + (p - pivot).rotate(rotation)}: the same rule as a structure template placed with that
 * rotation about that pivot.
 *
 * <p>Turn each block's state with the same rotation ({@code BlockState.rotate}), so a block that holds
 * several cells, such as a pane, keeps the right one on the right side.
 *
 * @param level the level the blocks arrive in: the same level, or another
 * @param pivot a position before the move
 * @param target where {@code pivot} goes
 * @param rotation how the blocks turn
 * @since 1.2.0
 */
@AnyThread
public record Destination(ServerLevel level, BlockPos pivot, BlockPos target, Rotation rotation) {
    /**
     * Checks the arguments and makes the positions immutable.
     *
     * @throws NullPointerException if an argument is null
     */
    public Destination {
        Objects.requireNonNull(level, "level");
        pivot = pivot.immutable();
        target = target.immutable();
        Objects.requireNonNull(rotation, "rotation");
    }

    /**
     * A move by an offset, without turning: every block goes from {@code p} to {@code p + offset}.
     *
     * <p>Thread: any.
     *
     * @param level the level the blocks arrive in
     * @param offset how far they go
     * @return the destination
     */
    @AnyThread
    public static Destination offset(ServerLevel level, Vec3i offset) {
        return new Destination(level, BlockPos.ZERO, new BlockPos(offset), Rotation.NONE);
    }

    /**
     * Where a block goes.
     *
     * <p>Thread: any.
     *
     * @param pos its position before the move
     * @return its position after
     */
    @AnyThread
    public BlockPos map(BlockPos pos) {
        return target.offset(pos.subtract(pivot).rotate(rotation));
    }

    /**
     * Where a box of blocks goes: the box its corners go to.
     *
     * <p>Thread: any.
     *
     * @param box the box before the move
     * @return the box after
     */
    @AnyThread
    public BoundingBox map(BoundingBox box) {
        return BoundingBox.fromCorners(
                map(new BlockPos(box.minX(), box.minY(), box.minZ())),
                map(new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
    }
}
