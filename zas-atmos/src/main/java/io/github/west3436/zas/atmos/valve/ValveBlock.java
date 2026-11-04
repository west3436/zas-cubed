package io.github.west3436.zas.atmos.valve;

import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A valve (CONT-010): an inline part with a pipe flange at each end of its {@link #AXIS}. Pipes join
 * both flanges whether it is open or shut, so a pipe ending at a shut valve has no open end, but gas
 * passes only while it is {@link #OPEN} (CONT-075). The block state is all a valve keeps of its
 * position, so it is saved with the chunk; {@link ValveBlockEntity} acts on it.
 */
public abstract class ValveBlock extends BaseEntityBlock implements PipeConnector {
    /** The line of the pipe through it. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /** Whether gas passes. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    /** The body and the pipe through it (models/block/manual_valve.json), per axis. */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.Axis.X, Shapes.or(Block.box(4, 4, 4, 12, 12, 12), Block.box(0, 5, 5, 16, 11, 11)),
            Direction.Axis.Y, Shapes.or(Block.box(4, 4, 4, 12, 12, 12), Block.box(5, 0, 5, 11, 16, 11)),
            Direction.Axis.Z, Shapes.or(Block.box(4, 4, 4, 12, 12, 12), Block.box(5, 5, 0, 11, 11, 16))));

    protected ValveBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, OPEN);
    }

    /** Both flanges, open or shut. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face.getAxis() == self.getValue(AXIS);
    }

    @Override
    public boolean carriesGas(BlockState self, Direction face) {
        return self.getValue(OPEN);
    }

    /**
     * A valve lines up with the parts beside it: it takes the axis with the most neighbours offering
     * it a connection. The axis the player is looking along wins a tie, so with no pipes around the
     * look alone decides.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState best = defaultBlockState()
                .setValue(AXIS, context.getNearestLookingDirection().getAxis());
        int most = neighbours(context, best);
        for (Direction.Axis axis : Direction.Axis.values()) {
            BlockState state = defaultBlockState().setValue(AXIS, axis);
            int count = neighbours(context, state);
            if (count > most) {
                most = count;
                best = state;
            }
        }
        return best;
    }

    /** How many of the parts on a placed valve's flanges would connect to it. */
    private static int neighbours(BlockPlaceContext context, BlockState state) {
        int count = 0;
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction d = Direction.fromAxisAndDirection(state.getValue(AXIS), sign);
            BlockPos next = context.getClickedPos().relative(d);
            if (PipeConnector.connected(state, d, context.getLevel().getBlockState(next))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Turns the valve, by changing its block state; its block entity acts on that at its next step.
     *
     * @param open whether it opens
     * @param sound the sound it makes
     */
    protected static void turn(Level level, BlockPos pos, BlockState state, boolean open, SoundEvent sound) {
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.5f, open ? 0.8f : 0.6f);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return RotatedPillarBlock.rotatePillar(state, rotation);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(AXIS));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ValveBlockEntity(pos, state);
    }
}
