package io.github.west3436.zas.atmos.pipe;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pipe end cap: a short pipe closed at the block's middle. It joins only the neighbour it faces,
 * and, unlike a pipe, never leaks (CONT-004). It is a 70 L network node, like a pipe, and it can be
 * dyed (CONT-114).
 */
public class PipeCapBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<PipeCapBlock> CODEC = simpleCodec(PipeCapBlock::new);

    /** The face the cap joins: toward the pipe it closes. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 9),
            Direction.SOUTH, Block.box(5, 5, 7, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 9, 11, 11),
            Direction.EAST, Block.box(7, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 9, 11),
            Direction.UP, Block.box(5, 7, 5, 11, 16, 11)));

    public PipeCapBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PipeColor.PROPERTY, PipeColor.NONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PipeColor.PROPERTY);
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == self.getValue(FACING);
    }

    /** Placed against a block, the cap faces it, so clicking a pipe's end caps it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        return PipeBlock.dye(stack, state, level, pos, player);
    }

    /** Picking a dyed cap gives a cap of its colour (CONT-114). */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return PipeBlock.dyed(super.getCloneItemStack(level, pos, state), state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PipeCapBlockEntity(pos, state);
    }
}
