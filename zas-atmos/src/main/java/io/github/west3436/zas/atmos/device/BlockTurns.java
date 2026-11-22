package io.github.west3436.zas.atmos.device;

import io.github.west3436.zas.atmos.pipe.PipeBlock;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * How a block of this mod turns with what it is part of (CONT-156): a structure placed a quarter turn
 * round, a Create contraption, or a Sable sub-level whose blocks are put back into the world the way
 * the ship was turned. The game turns a block with {@link BlockState#rotate} and {@link
 * BlockState#mirror}, which leave a state as it is unless its block says otherwise; a block that
 * didn't kept facing the way it faced before, so a vent came back facing away from its pipe.
 *
 * <p>These turn every way a state faces: each direction property, the six faces a pipe's arms go to,
 * and an axis. Blocks whose state says more than that, such as a filter's side port, add their own
 * turn on top.
 */
public final class BlockTurns {
    private BlockTurns() {}

    /**
     * A state turned about the vertical.
     *
     * @param state the state
     * @param rotation the turn
     * @return the state facing each way turned by {@code rotation}
     */
    public static BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return state;
        }
        BlockState out = turn(state, rotation::rotate);
        if (rotation != Rotation.CLOCKWISE_180) {
            out = swapAxis(out);
        }
        return out;
    }

    /**
     * A state mirrored.
     *
     * @param state the state
     * @param mirror the mirror
     * @return the state facing each way mirrored by {@code mirror}
     */
    public static BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE ? state : turn(state, mirror::mirror);
    }

    private static BlockState turn(BlockState state, UnaryOperator<Direction> way) {
        BlockState out = state;
        for (Property<?> p : state.getProperties()) {
            if (p instanceof DirectionProperty facing) {
                Direction to = way.apply(state.getValue(facing));
                if (facing.getPossibleValues().contains(to)) {
                    out = out.setValue(facing, to);
                }
            }
        }
        for (Map.Entry<Direction, BooleanProperty> arm : PipeBlock.CONNECTIONS.entrySet()) {
            BooleanProperty to = PipeBlock.CONNECTIONS.get(way.apply(arm.getKey()));
            if (state.hasProperty(arm.getValue()) && out.hasProperty(to)) {
                out = out.setValue(to, state.getValue(arm.getValue()));
            }
        }
        return out;
    }

    /** A quarter turn takes an x axis to z and z to x. */
    private static BlockState swapAxis(BlockState state) {
        BlockState out = state;
        for (Property<?> p : state.getProperties()) {
            if (p.getValueClass() == Direction.Axis.class) {
                @SuppressWarnings("unchecked")
                EnumProperty<Direction.Axis> axis = (EnumProperty<Direction.Axis>) p;
                Direction.Axis to = switch (state.getValue(axis)) {
                    case X -> Direction.Axis.Z;
                    case Z -> Direction.Axis.X;
                    case Y -> Direction.Axis.Y;
                };
                if (axis.getPossibleValues().contains(to)) {
                    out = out.setValue(axis, to);
                }
            }
        }
        return out;
    }
}
