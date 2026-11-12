package io.github.west3436.zas.atmos.controller;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The central atmospherics controller (CONT-115, D-75): a console 2 blocks wide, 2 tall and 1 deep
 * that powers, lists and runs the area air controllers around it. Its {@code facing} is the way its
 * front looks, toward the player who placed it. Each of its four blocks is one {@code half} (lower
 * or upper) and one {@code side} (left or right, as seen from the front), and all show the same
 * {@code screen}: no power, or the worst danger among its area air controllers.
 *
 * <p>It behaves as vanilla's two-block blocks do:
 *
 * <ul>
 *   <li>The block placed is the lower left one, and the console grows up and to its right, as the
 *       placing player sees it. All four spaces must be free.
 *   <li>Breaking any block breaks the whole console. Only the lower left block drops the item,
 *       which it does whichever block was broken, and not in creative or without the right tool.
 *   <li>The lower left block holds the block entity ({@link CentralControllerBlockEntity}); the
 *       others send use, the multitool and FE to it.
 *   <li>Each block is a full block to collide with, so the whole console is gas-tight, and its
 *       selection outline is the whole console.
 * </ul>
 */
public class CentralControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<CentralControllerBlock> CODEC = simpleCodec(CentralControllerBlock::new);

    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<ConsoleSide> SIDE = EnumProperty.create("side", ConsoleSide.class);
    public static final EnumProperty<ConsoleScreen> SCREEN = EnumProperty.create("screen", ConsoleScreen.class);

    /** The whole console's outline from each block, per facing, indexed as {@link #parts}. */
    private static final Map<Direction, VoxelShape[]> OUTLINES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] shapes = new VoxelShape[4];
            for (int i = 0; i < 4; i++) {
                shapes[i] = outline(facing, i);
            }
            OUTLINES.put(facing, shapes);
        }
    }

    public CentralControllerBlock(Properties properties) {
        super(properties.noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(SIDE, ConsoleSide.LEFT)
                .setValue(SCREEN, ConsoleScreen.UNPOWERED));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, SIDE, SCREEN);
    }

    // ---- Its four blocks ---------------------------------------------------------------------

    /** The way from its left column to its right, for a console facing {@code facing}. */
    public static Direction toRight(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** The lower left block's position, from any of the four. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.getValue(SIDE) == ConsoleSide.RIGHT
                ? p.relative(toRight(state.getValue(FACING)).getOpposite())
                : p;
    }

    /** The four blocks of a console: lower left, lower right, upper left, upper right. */
    public static List<BlockPos> parts(BlockPos origin, Direction facing) {
        BlockPos right = origin.relative(toRight(facing));
        return List.of(origin, right, origin.above(), right.above());
    }

    /** The state of block {@code i} of {@link #parts}, from any block's. */
    public static BlockState part(BlockState state, int i) {
        return state.setValue(HALF, i >= 2 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(SIDE, (i & 1) == 1 ? ConsoleSide.RIGHT : ConsoleSide.LEFT);
    }

    private static int index(BlockState state) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 2 : 0)
                + (state.getValue(SIDE) == ConsoleSide.RIGHT ? 1 : 0);
    }

    /** The box of all four blocks, in the coordinates of block {@code i}. */
    private static VoxelShape outline(Direction facing, int i) {
        Direction right = toRight(facing);
        int ox = (i & 1) == 1 ? -right.getStepX() : 0;
        int oz = (i & 1) == 1 ? -right.getStepZ() : 0;
        int oy = i >= 2 ? -1 : 0;
        return Shapes.box(
                Math.min(ox, ox + right.getStepX()),
                oy,
                Math.min(oz, oz + right.getStepZ()),
                Math.max(ox, ox + right.getStepX()) + 1,
                oy + 2,
                Math.max(oz, oz + right.getStepZ()) + 1);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.get(state.getValue(FACING))[index(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    // ---- Placing and removing, as vanilla's two-block blocks ---------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        if (origin.getY() >= level.getMaxBuildHeight() - 1) {
            return null;
        }
        List<BlockPos> parts = parts(origin, facing);
        for (int i = 1; i < parts.size(); i++) {
            BlockPos p = parts.get(i);
            if (!level.getWorldBorder().isWithinBounds(p)
                    || !level.getBlockState(p).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        List<BlockPos> parts = parts(pos, state.getValue(FACING));
        for (int i = 1; i < parts.size(); i++) {
            level.setBlock(parts.get(i), part(state, i), Block.UPDATE_ALL);
        }
    }

    /**
     * Keeps the four blocks alike: each takes the screen of the block beside it or above or below
     * it in the console, and goes when that one goes, so breaking any block breaks them all.
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        Direction facing = state.getValue(FACING);
        DoubleBlockHalf half = state.getValue(HALF);
        ConsoleSide side = state.getValue(SIDE);
        Direction vertical = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        Direction across =
                side == ConsoleSide.LEFT ? toRight(facing) : toRight(facing).getOpposite();
        if (direction != vertical && direction != across) {
            return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
        }
        boolean partner = neighbor.is(this)
                && neighbor.getValue(FACING) == facing
                && (direction == vertical
                        ? neighbor.getValue(HALF) != half && neighbor.getValue(SIDE) == side
                        : neighbor.getValue(SIDE) != side && neighbor.getValue(HALF) == half);
        return partner ? state.setValue(SCREEN, neighbor.getValue(SCREEN)) : Blocks.AIR.defaultBlockState();
    }

    /**
     * Breaking a block other than the lower left one in creative, or without the right tool, removes
     * the lower left one with no drop, as for a door. Otherwise the lower left one drops the item when
     * it goes.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && (player.isCreative() || !player.hasCorrectToolForDrops(state, level, pos))) {
            BlockPos origin = origin(state, pos);
            BlockState there = level.getBlockState(origin);
            if (!origin.equals(pos) && there.is(this)) {
                level.setBlock(origin, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                level.levelEvent(player, 2001, origin, Block.getId(there));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (!state.is(oldState.getBlock())) {
            capabilitiesChanged(state, level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof CentralControllerBlockEntity be) {
            be.removedFromWorld(server);
        }
        super.onRemove(state, level, pos, newState, moved);
        if (!state.is(newState.getBlock())) {
            capabilitiesChanged(state, level, pos);
        }
    }

    /**
     * Tells the game that the console's capabilities came or went at each of its four blocks. Any of
     * them stands for the console (CONT-115) but only the lower left one has a block entity, which
     * is all the game watches by itself: without this a cable or a computer that looked at another
     * block before the console was whole would never look again.
     */
    private static void capabilitiesChanged(BlockState state, Level level, BlockPos pos) {
        for (BlockPos part : parts(origin(state, pos), state.getValue(FACING))) {
            level.invalidateCapabilities(part);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE
                ? state
                : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(SIDE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return index(state) == 0 ? new CentralControllerBlockEntity(pos, state) : null;
    }

    /** The console's block entity, from any of its blocks; null if it isn't there or loaded. */
    @Nullable
    public static CentralControllerBlockEntity entity(Level level, BlockPos pos, BlockState state) {
        BlockPos origin = origin(state, pos);
        return level.isLoaded(origin) && level.getBlockEntity(origin) instanceof CentralControllerBlockEntity be
                ? be
                : null;
    }

    // ---- Using it ------------------------------------------------------------------------

    /**
     * Opens its window. The multitool moves its selected area air controller here before this is
     * asked (CONT-118, {@link MultitoolItem#onItemUseFirst}).
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            CentralControllerBlockEntity be = entity(level, pos, state);
            if (be != null) {
                BlockPos origin = be.getBlockPos();
                sp.openMenu(be, buf -> buf.writeBlockPos(origin));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
