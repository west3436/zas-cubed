package io.github.west3436.zas.atmos.tank;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A large gas tank: a fixed store of {@link io.github.west3436.zas.atmos.pipe.PipeVolumes#LARGE_TANK
 * 10,000 L}, ten canisters' worth, a vessel standing upright, one block wide and two high. Its lower
 * half holds the gas, in {@link LargeTankBlockEntity}, and is the one a pipe joins: from below, and
 * nowhere else. A connector port below it joins it as it joins a canister. It looks the same from every
 * side, so it has no facing.
 *
 * <p>It is a pipe part, not a portable: it has no valve and no window, a wrench takes it down
 * (CONT-035), and broken any way it drops empty, its gas going into its cell as a pipe's does
 * (CONT-009). It is rated as a canister is, and fails the same way (CONT-101, CONT-102). Like a
 * canister it seals no face, so the room's air passes round it. It starts grey, and a dye colours it
 * as it does a pipe (CONT-114); its item keeps the colour.
 *
 * <p>The two halves are placed and removed together, as a door's are: each takes the other's state
 * when it changes, so a dye on either colours both, and goes when the other goes.
 */
public class LargeTankBlock extends Block implements EntityBlock, PipeConnector {
    public static final MapCodec<LargeTankBlock> CODEC = simpleCodec(LargeTankBlock::new);

    /** Which half of the tank the block is. */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    /** The tint of an undyed tank: vg's grey, from the light shell drawn for the dyes. */
    public static final int GREY = 0xA4A4A4;

    /** The lower half: four legs, the pipe's end between them, and the round shell from its foot up. */
    private static final VoxelShape LOWER = Shapes.or(
                    Block.box(2, 0, 2, 4, 4, 4),
                    Block.box(12, 0, 2, 14, 4, 4),
                    Block.box(2, 0, 12, 4, 4, 14),
                    Block.box(12, 0, 12, 14, 4, 14),
                    Block.box(5, 0, 5, 11, 3, 11),
                    Block.box(1, 3, 4, 15, 16, 12),
                    Block.box(4, 3, 1, 12, 16, 15),
                    Block.box(2, 3, 2, 14, 16, 14))
            .optimize();

    /** The upper half: the shell, its dome and the cap. */
    private static final VoxelShape UPPER = Shapes.or(
                    Block.box(1, 0, 4, 15, 13, 12),
                    Block.box(4, 0, 1, 12, 13, 15),
                    Block.box(2, 0, 2, 14, 14, 14),
                    Block.box(6, 14, 6, 10, 16, 10))
            .optimize();

    public LargeTankBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition
                .any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(PipeColor.PROPERTY, PipeColor.NONE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, PipeColor.PROPERTY);
    }

    /**
     * Only the bottom of the lower half, toward a pipe, a connector port, or another mod's block that
     * offers gas there. One face of one block keeps the tank one node, met once.
     */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == Direction.DOWN && self.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** A dye colours the tank, both halves, as it does a pipe (CONT-114). */
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

    /** Picking a dyed tank gives a tank of its colour. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return PipeBlock.dyed(super.getCloneItemStack(level, pos, state), state);
    }

    /**
     * The colour a tank is drawn in: {@link #GREY} undyed, its dye's otherwise.
     *
     * @param color its colour
     * @return the tint, as 0xRRGGBB
     */
    public static int tint(PipeColor color) {
        return color == PipeColor.NONE ? GREY : color.tint();
    }

    // ---- Placing and removing, as a door -----------------------------------------------------

    /** The lower half goes where the player places it, the upper half above it, which must be free. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1
                || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState();
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /**
     * Each half takes the other's state when it changes, so they are dyed together, and goes when the
     * other goes.
     */
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
