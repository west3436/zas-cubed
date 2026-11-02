package io.github.west3436.zas.atmos.pipe;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block that pipes can join: a pipe or cap, or a device on the face where its pipe goes. Two
 * neighbours are connected when each offers a connection on the face toward the other ({@link
 * #connected}), so a vent joins only a pipe at its back (CONT-014) and a connector port only a pipe
 * below it (CONT-083).
 */
public interface PipeConnector {
    /**
     * Whether this block offers a pipe connection on a face.
     *
     * @param self this block's state
     * @param face the face, toward the neighbour
     * @param neighbour the neighbour's state
     * @return true to connect if the neighbour agrees
     */
    boolean pipeConnects(BlockState self, Direction face, BlockState neighbour);

    /**
     * Whether two neighbouring blocks are connected.
     *
     * @param a one block's state
     * @param face the face of {@code a} toward {@code b}
     * @param b the other block's state
     * @return true if both offer a connection on their shared face
     */
    static boolean connected(BlockState a, Direction face, BlockState b) {
        return a.getBlock() instanceof PipeConnector pa
                && b.getBlock() instanceof PipeConnector pb
                && pa.pipeConnects(a, face, b)
                && pb.pipeConnects(b, face.getOpposite(), a);
    }
}
