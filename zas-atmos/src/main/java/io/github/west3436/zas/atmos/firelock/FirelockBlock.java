package io.github.west3436.zas.atmos.firelock;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.tool.CrowbarItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A standalone firelock (CONT-043, CONT-092 to CONT-094): a door one block wide and two high, in
 * the {@code zas:zone_boundary} tag, that an area air controller closes at danger 2. Its
 * {@code facing} is the way its passage runs. Its frame stands on the two sides of the passage, and
 * its panel crosses the passage when closed. The work is in {@link FirelockBlockEntity}, on the
 * lower half.
 *
 * <p>The collision shape is not quite the model. Closed, the panel fills the passage from the face
 * it looks to up to the far side of the visible panel, so the closed firelock seals that one face.
 * The framework then makes that face, and only that face, the zone boundary (GEO-007): the
 * firelock's cells join the room behind it, and opening or closing it never splits a zone
 * (GEO-044). A panel in the middle of the block, as the model draws it, would seal no face.
 *
 * <p>By hand it opens and closes like a door, but a locked-down firelock refuses to open and
 * shows a warning (CONT-093). A crowbar forces it either way, even locked down (CONT-094).
 * Redstone doesn't move it.
 */
public class FirelockBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<FirelockBlock> CODEC = simpleCodec(FirelockBlock::new);

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Whether a closed firelock is locked down (CONT-093); never true while open. */
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    /** The outline and collision shapes per facing, indexed by {@link #index}. */
    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    private static final Map<Direction, VoxelShape[]> COLLISIONS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape[] outline = new VoxelShape[4];
            VoxelShape[] collision = new VoxelShape[4];
            for (int i = 0; i < 4; i++) {
                boolean upper = i >= 2;
                boolean open = (i & 1) == 1;
                outline[i] = rotate(shape(upper, open, false), d);
                collision[i] = rotate(shape(upper, open, true), d);
            }
            SHAPES.put(d, outline);
            COLLISIONS.put(d, collision);
        }
    }

    public FirelockBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, true)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(LOCKED, false));
    }

    /**
     * The shape facing north, in the model's coordinates: the frame posts at x 0–2 and 14–16, the
     * lintel at the top of the upper half, and, closed, the panel. The model's panel is z 6–10; the
     * collision panel runs from the north face to z 10, so it seals the north face.
     */
    private static VoxelShape shape(boolean upper, boolean open, boolean collision) {
        VoxelShape s = Shapes.or(Block.box(0, 0, 0, 2, 16, 16), Block.box(14, 0, 0, 16, 16, 16));
        if (upper) {
            s = Shapes.or(s, Block.box(2, 14, 0, 14, 16, 16));
        }
        if (!open) {
            double top = upper ? 14 : 16;
            s = Shapes.or(s, Block.box(2, 0, collision ? 0 : 6, 14, top, 10));
        }
        return s.optimize();
    }

    /** Turns a north-facing shape to face {@code to}, as the blockstate's y rotation does. */
    private static VoxelShape rotate(VoxelShape shape, Direction to) {
        int turns = to.get2DDataValue() - Direction.NORTH.get2DDataValue();
        VoxelShape out = shape;
        for (int t = 0; t < ((turns % 4) + 4) % 4; t++) {
            VoxelShape[] buf = {Shapes.empty()};
            out.forAllBoxes(
                    (x0, y0, z0, x1, y1, z1) -> buf[0] = Shapes.or(buf[0], Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
            out = buf[0];
        }
        return out.optimize();
    }

    private static int index(BlockState state) {
        return (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 2 : 0) + (state.getValue(OPEN) ? 1 : 0);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF, LOCKED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING))[index(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS.get(state.getValue(FACING))[index(state)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    // ---- Placing and removing, as a door -----------------------------------------------------

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
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? under.isFaceSturdy(level, below, Direction.UP)
                : under.is(this);
    }

    /**
     * Keeps the two halves alike: each takes the other's state when it changes, and goes when the
     * other goes. The lower half also goes when the block under it does.
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
        boolean towardOther = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOther) {
            return neighbor.is(this) && neighbor.getValue(HALF) != half
                    ? neighbor.setValue(HALF, half)
                    : Blocks.AIR.defaultBlockState();
        }
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
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
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new FirelockBlockEntity(pos, state) : null;
    }

    // ---- Using it ------------------------------------------------------------------------

    /** The lower half's position, from either half. */
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
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
        // The crowbar forces it in its own useOn (CONT-094).
        if (stack.getItem() instanceof CrowbarItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Opens or closes it by hand. A locked-down firelock refuses to open and says why (CONT-093).
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            BlockPos lower = lower(state, pos);
            if (!state.getValue(OPEN)
                    && server.getBlockEntity(lower) instanceof FirelockBlockEntity be
                    && be.lockdown().isPresent()) {
                player.displayClientMessage(be.lockdown().get().message(), true);
                server.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 0.6f);
                return InteractionResult.CONSUME;
            }
            setOpen(server, lower, !state.getValue(OPEN), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Opens or closes the firelock whose lower half is at {@code lower}, with no checks; a no-op if
     * it is already so. Opening clears the lockdown, which applies only to a closed firelock.
     *
     * @return true if it changed
     */
    public static boolean setOpen(ServerLevel level, BlockPos lower, boolean open, @Nullable Entity by) {
        BlockState state = level.getBlockState(lower);
        if (!(state.getBlock() instanceof FirelockBlock) || state.getValue(OPEN) == open) {
            return false;
        }
        BlockState next = state.setValue(OPEN, open);
        if (open) {
            next = next.setValue(LOCKED, false);
        }
        level.setBlock(lower, next, Block.UPDATE_ALL);
        level.playSound(
                null,
                lower,
                open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS,
                1.0f,
                level.getRandom().nextFloat() * 0.1f + 0.9f);
        level.gameEvent(by, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, lower);
        if (level.getBlockEntity(lower) instanceof FirelockBlockEntity be) {
            be.changed(open);
        }
        return true;
    }

    /** Sets the lockdown shown on a firelock whose lower half is at {@code lower}. */
    static void setLocked(ServerLevel level, BlockPos lower, boolean locked) {
        BlockState state = level.getBlockState(lower);
        if (state.getBlock() instanceof FirelockBlock && state.getValue(LOCKED) != locked) {
            level.setBlock(lower, state.setValue(LOCKED, locked), Block.UPDATE_ALL);
        }
    }

    /** What a refused opening says; a component for each lockdown reason. */
    static Component lockedMessage(String reason, String value) {
        return Component.translatable("block.zas_atmos.firelock.locked." + reason, value);
    }
}
