package io.github.west3436.zas.atmos.inline;

import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block of an {@link InlineDeviceEntity}: it says which face each of its pipe ports is on. An
 * {@link InlineDeviceBlock} is one, and so is the electrolyzer, which sits on the floor rather than in
 * a pipe line.
 */
public interface PortedBlock {
    /**
     * The faces it has a pipe port on, in the order of its block entity's ports.
     *
     * @param state the block's state
     * @return the faces, one per port
     */
    List<Direction> portFaces(BlockState state);
}
