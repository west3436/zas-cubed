package io.github.west3436.zas.atmos.radiator;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A radiator adaptor (CONT-145): inline, with a pipe end and a radiator end on opposite faces. Its
 * pipe end joins whatever a pipe would there but radiator pipe, and its radiator end only radiator
 * pipe. It is one 70 L node, so both ends share a network.
 */
public class RadiatorAdaptorBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<RadiatorAdaptorBlock> CODEC = simpleCodec(RadiatorAdaptorBlock::new);

    /** The way its radiator end points; its pipe end points the other way. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(4, 4, 0, 12, 12, 16),
            Direction.SOUTH, Block.box(4, 4, 0, 12, 12, 16),
            Direction.WEST, Block.box(0, 4, 4, 16, 12, 12),
            Direction.EAST, Block.box(0, 4, 4, 16, 12, 12),
            Direction.DOWN, Block.box(4, 0, 4, 12, 16, 12),
            Direction.UP, Block.box(4, 0, 4, 12, 16, 12)));

    public RadiatorAdaptorBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Its radiator end takes only radiator pipe, and its pipe end anything else that offers a pipe. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        Direction radiator = self.getValue(FACING);
        boolean radiatorPipe = neighbour.getBlock() instanceof RadiatorPipeBlock;
        if (face == radiator) {
            return radiatorPipe;
        }
        return face == radiator.getOpposite() && !radiatorPipe;
    }

    /**
     * Placed against radiator pipe, its radiator end faces it; against anything else, its pipe end
     * does, so clicking the end of either kind of pipe carries the line on.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        BlockState against =
                context.getLevel().getBlockState(context.getClickedPos().relative(clicked.getOpposite()));
        Direction radiator = against.getBlock() instanceof RadiatorPipeBlock ? clicked.getOpposite() : clicked;
        return defaultBlockState().setValue(FACING, radiator);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RadiatorAdaptorBlockEntity(pos, state);
    }
}
