package io.github.west3436.zas.atmos.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block entity that pipes join: it says which network node each of its faces leads to. Most have
 * one node, themselves ({@link PipeNodeEntity}). A device with several ports has one per port
 * ({@link PipePort}), so one block can sit in several networks.
 */
public interface PipeHost {
    /**
     * The node a connection on a face leads to.
     *
     * @param face the face, toward the neighbour
     * @return the node, or null if nothing connects on that face
     */
    PipeNode nodeOn(Direction face);

    /**
     * Dissolves the networks of its nodes and of the nodes they join, so the next ones built follow
     * its connections as they are now: call it after they change, as when a wrench turns the block
     * (CONT-035). Each member keeps its share by volume (CONT-003). Server side.
     */
    void reconnect();

    /**
     * The pressure it holds, in kPa: the highest of its nodes' networks', building any that has
     * dissolved. A node's buffer is part of its network, so this is a pipe's network's pressure, or
     * the highest of a device's ports'. Server side.
     */
    double highestPressure();

    /**
     * The node across a face of a block, if gas passes between the two blocks there ({@link
     * PipeConnector#joined}, so not through a shut valve) and the neighbour is loaded. A neighbour in
     * an unloaded chunk isn't reached, and that chunk keeps its share saved.
     *
     * @param level the level
     * @param pos the block's position
     * @param self the block's state
     * @param face the face, toward the neighbour
     * @return the neighbour's node, or null if gas doesn't pass there
     */
    static PipeNode across(Level level, BlockPos pos, BlockState self, Direction face) {
        BlockPos next = pos.relative(face);
        if (!level.isLoaded(next) || !PipeConnector.joined(self, face, level.getBlockState(next))) {
            return null;
        }
        BlockEntity neighbour = level.getBlockEntity(next);
        return neighbour instanceof PipeHost host && !neighbour.isRemoved() ? host.nodeOn(face.getOpposite()) : null;
    }
}
