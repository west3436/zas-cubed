package io.github.west3436.zas.atmos.tank;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A large gas tank: a fixed store of {@link io.github.west3436.zas.atmos.pipe.PipeVolumes#LARGE_TANK
 * 10,000 L}, ten canisters' worth, one block wide and two high. A pipe joins it on the back of its
 * lower half, the face opposite its {@code facing}, and nowhere else. The gas is in {@link
 * LargeTankBlockEntity}, on the lower half.
 *
 * <p>It is a pipe part, not a portable: a wrench turns it and takes it down (CONT-035), and broken any
 * way it drops empty, its gas going into its cell as a pipe's does (CONT-009). It is rated as a
 * canister is, and fails the same way (CONT-101, CONT-102). Like a canister it seals no face, so the
 * room's air passes round it.
 *
 * <p>The two halves are placed and removed together, as a door's are: each takes the other's state
 * when it changes, and goes when the other goes.
 */
public class LargeTankBlock extends HorizontalDirectionalBlock implements EntityBlock, PipeConnector {
    public static final MapCodec<LargeTankBlock> CODEC = simpleCodec(LargeTankBlock::new);

    /** Which half of the tank the block is. */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** The shell, 14 px across, on its skirt. */
    private static final VoxelShape LOWER = Shapes.or(Block.box(2, 0, 2, 14, 1, 14), Block.box(1, 1, 1, 15, 16, 15));

    /** The shell up to its shoulder, the dome and the cap. */
    private static final VoxelShape UPPER =
            Shapes.or(Block.box(1, 0, 1, 15, 11, 15), Block.box(2, 11, 2, 14, 13, 14), Block.box(6, 13, 6, 10, 15, 10));

    /** The lower half with its pipe flange, by the way the tank faces. */
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = new EnumMap<>(Map.of(
            Direction.NORTH, Shapes.or(LOWER, Block.box(5, 5, 15, 11, 11, 16)),
            Direction.SOUTH, Shapes.or(LOWER, Block.box(5, 5, 0, 11, 11, 1)),
            Direction.WEST, Shapes.or(LOWER, Block.box(15, 5, 5, 16, 11, 11)),
            Direction.EAST, Shapes.or(LOWER, Block.box(0, 5, 5, 1, 11, 11))));

    public LargeTankBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    /** Only the back of the lower half. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return self.getValue(HALF) == DoubleBlockHalf.LOWER
                && face == self.getValue(FACING).getOpposite();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES.get(state.getValue(FACING)) : UPPER;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // ---- Placing and removing, as a door -----------------------------------------------------

    /** Its front faces the player who places it. It needs the block above free. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** Each half takes the other's state when it changes, so they turn together, and goes when the other goes. */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN)) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half
                    ? neighbor.setValue(HALF, half)
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * Breaking the upper half in creative, or without the right tool, removes the lower half with no
     * drop, as for a door; otherwise the lower half drops the item when it goes.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide
                && (player.isCreative() || !player.hasCorrectToolForDrops(state, level, pos))
                && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, below, Block.getId(lower));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new LargeTankBlockEntity(pos, state) : null;
    }

    // ---- Either half stands for the tank -------------------------------------------------------

    /**
     * The lower half's position, which holds the tank's gas, from either half.
     *
     * @param state a tank's state
     * @param pos its position
     * @return {@code pos}, or the block below it for an upper half
     */
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /**
     * The tank a block is part of, from either half.
     *
     * @param level the level
     * @param pos a position
     * @return the tank's block entity, or null if the block there is no tank's
     */
    @Nullable
    public static LargeTankBlockEntity entity(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LargeTankBlock
                        && level.getBlockEntity(lower(state, pos)) instanceof LargeTankBlockEntity tank
                ? tank
                : null;
    }
}
