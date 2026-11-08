package io.github.west3436.zas.atmos.radiator;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Radiator pipe (CONT-143): a finned tube that exchanges heat with the gas around it (HEAT-020,
 * HEAT-021). It joins only radiator pipe and a radiator adaptor's radiator end, never pipe, a
 * device or a gas handler, and its free ends are closed (CONT-144). It isn't dyed.
 */
public class RadiatorPipeBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<RadiatorPipeBlock> CODEC = simpleCodec(RadiatorPipeBlock::new);

    /** Whether it connects on each face: the pipe's own properties. */
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.CONNECTIONS;

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    /** An arm out to a face, as wide as its fins. */
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(4, 4, 0, 12, 12, 6),
            Direction.SOUTH, Block.box(4, 4, 10, 12, 12, 16),
            Direction.WEST, Block.box(0, 4, 4, 6, 12, 12),
            Direction.EAST, Block.box(10, 4, 4, 16, 12, 12),
            Direction.DOWN, Block.box(4, 0, 4, 12, 6, 12),
            Direction.UP, Block.box(4, 10, 4, 12, 16, 12)));
    /** By the bits of the faces it connects on. */
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction d : Direction.values()) {
                if ((mask & (1 << d.ordinal())) != 0) {
                    shape = Shapes.or(shape, ARMS.get(d));
                }
            }
            SHAPES[mask] = shape;
        }
    }

    public RadiatorPipeBlock(Properties properties) {
        super(properties.noOcclusion());
        BlockState state = stateDefinition.any();
        for (BooleanProperty p : CONNECTIONS.values()) {
            state = state.setValue(p, false);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
    }

    /** Only radiator pipe and an adaptor, which takes it only on its radiator end. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return neighbour.getBlock() instanceof RadiatorPipeBlock || neighbour.getBlock() instanceof RadiatorAdaptorBlock;
    }

    private BlockState connect(BlockState state, Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            state = state.setValue(
                    CONNECTIONS.get(d), PipeConnector.connected(state, d, level.getBlockState(pos.relative(d))));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction face,
            BlockState neighbour,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos) {
        return state.setValue(CONNECTIONS.get(face), PipeConnector.connected(state, face, neighbour));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction d : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(d))) {
                mask |= 1 << d.ordinal();
            }
        }
        return SHAPES[mask];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadiatorPipeBlockEntity(pos, state);
    }
}
