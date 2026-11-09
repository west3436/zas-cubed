package io.github.west3436.zas.atmos.airlock;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.controller.ConsoleSide;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.tool.CrowbarItem;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An airlock (CONT-133, CONT-135 to CONT-137): a door one block wide and two high, in the {@code
 * zas:zone_boundary} tag. {@link LargeAirlockBlock} is the 2 by 2 one (CONT-134). Its {@code
 * facing} is the way its passage runs, as the firelock's is: its frame stands on the two sides of
 * the passage, and its leaf crosses the passage when closed.
 *
 * <p>The collision shape is the firelock's: closed, the leaf fills the passage from the face it
 * looks to up to the far side of the visible leaf, so the closed airlock seals that one face, which
 * the framework makes its zone boundary (GEO-007). Its cells join the room behind it, and opening or
 * closing it never splits a zone (GEO-044). The shape follows {@code open} alone, so a closed
 * firelock, which always closes the airlock, keeps the same boundary face.
 *
 * <p>Its blocks keep one state between them: changing the lower (left) block, the origin, changes
 * the others through {@link #updateShape}, and the origin holds the block entity, which works a
 * fitted firelock.
 *
 * <ul>
 *   <li><b>By hand and redstone</b> (CONT-135). Using it opens or closes it, and a redstone signal at
 *       any of its blocks opens it and holds it open. Once open, it closes itself {@link
 *       #AUTO_CLOSE_TICKS} after it opened or after the signal ended, whichever is later, waiting
 *       while anything stands in its doorway. It needs no power.
 *   <li><b>Bolts</b> (CONT-136). A bolted airlock stays closed: the hand, the crowbar and redstone
 *       leave it so. Only an airlock controller bolts or unbolts it. Using a bolted airlock that a
 *       controller links asks that controller to cycle toward it (CONT-140).
 *   <li><b>A firelock</b> (CONT-137). A firelock item used on it fits one ({@link
 *       io.github.west3436.zas.atmos.firelock.FirelockItem}), which works as a firelock
 *       in this doorway (CONT-043, CONT-092 to CONT-094): when it closes it closes the airlock too,
 *       and while it's shut the airlock doesn't open. The hand opens a shut firelock unless it's
 *       locked down; the crowbar forces it either way.
 * </ul>
 */
public class AirlockBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AirlockBlock> CODEC = simpleCodec(AirlockBlock::new);

    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Whether an airlock controller has bolted it shut (CONT-136); never true while open. */
    public static final BooleanProperty BOLTED = BooleanProperty.create("bolted");
    /** Its firelock, if one is fitted (CONT-137). */
    public static final EnumProperty<AirlockFirelock> FIRELOCK = EnumProperty.create("firelock", AirlockFirelock.class);
    /** Which column of a {@link LargeAirlockBlock} a block is, as seen by the player who placed it. */
    public static final EnumProperty<ConsoleSide> SIDE = EnumProperty.create("side", ConsoleSide.class);

    /** Game ticks an open airlock waits before it closes itself, 5 s (CONT-135). */
    public static final int AUTO_CLOSE_TICKS = 100;

    /** Game ticks it waits again when something stands in its doorway as it would close, 1 s. */
    public static final int DOORWAY_WAIT_TICKS = 20;

    /** The outline and collision shapes per facing, indexed by {@link #shapeIndex}. */
    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    private static final Map<Direction, VoxelShape[]> COLLISIONS = new EnumMap<>(Direction.class);

    /** The columns a shape can be for: the 1 by 2 airlock's, then the 2 by 2's left and right. */
    private enum Column {
        SMALL,
        LEFT,
        RIGHT
    }

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape[] outline = new VoxelShape[12];
            VoxelShape[] collision = new VoxelShape[12];
            for (Column c : Column.values()) {
                for (int i = 0; i < 4; i++) {
                    boolean upper = i >= 2;
                    boolean open = (i & 1) == 1;
                    outline[c.ordinal() * 4 + i] = rotate(shape(c, upper, open, false), d);
                    collision[c.ordinal() * 4 + i] = rotate(shape(c, upper, open, true), d);
                }
            }
            SHAPES.put(d, outline);
            COLLISIONS.put(d, collision);
        }
    }

    public AirlockBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(POWERED, false)
                .setValue(BOLTED, false)
                .setValue(FIRELOCK, AirlockFirelock.NONE));
    }

    /**
     * A column's shape facing north, in the model's coordinates: its frame post or posts, the lintel
     * at the top of the upper half, and, closed, the leaf. The model's leaf is z 6–10; the collision
     * leaf runs from the north face to z 10, so it seals the north face.
     */
    private static VoxelShape shape(Column column, boolean upper, boolean open, boolean collision) {
        VoxelShape s = Shapes.empty();
        if (column != Column.RIGHT) {
            s = Shapes.or(s, Block.box(0, 0, 0, 2, 16, 16));
        }
        if (column != Column.LEFT) {
            s = Shapes.or(s, Block.box(14, 0, 0, 16, 16, 16));
        }
        double from = column == Column.RIGHT ? 0 : 2;
        double to = column == Column.LEFT ? 16 : 14;
        if (upper) {
            s = Shapes.or(s, Block.box(from, 14, 0, to, 16, 16));
        }
        if (!open) {
            double top = upper ? 14 : 16;
            s = Shapes.or(s, Block.box(from, 0, collision ? 0 : 6, to, top, 10));
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

    private static int shapeIndex(BlockState state) {
        Column column = !state.hasProperty(SIDE)
                ? Column.SMALL
                : state.getValue(SIDE) == ConsoleSide.LEFT ? Column.LEFT : Column.RIGHT;
        return column.ordinal() * 4
                + (state.getValue(HALF) == DoubleBlockHalf.UPPER ? 2 : 0)
                + (state.getValue(OPEN) ? 1 : 0);
    }

    @Override
    protected MapCodec<? extends AirlockBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, HALF, POWERED, BOLTED, FIRELOCK);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING))[shapeIndex(state)];
    }

    @Override
    protected VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISIONS.get(state.getValue(FACING))[shapeIndex(state)];
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    // ---- Its blocks ----------------------------------------------------------------------------

    /** The way from a 2 by 2 airlock's left column to its right, for one facing {@code facing}. */
    public static Direction toRight(Direction facing) {
        return facing.getClockWise();
    }

    /** The origin's position, the lower (left) block, from any of its blocks. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        BlockPos p = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        return state.hasProperty(SIDE) && state.getValue(SIDE) == ConsoleSide.RIGHT
                ? p.relative(toRight(state.getValue(FACING)).getOpposite())
                : p;
    }

    /**
     * The blocks of an airlock whose origin is at {@code origin}: lower and upper, or for the 2 by 2
     * lower left, lower right, upper left and upper right.
     */
    public List<BlockPos> parts(BlockPos origin, Direction facing) {
        return List.of(origin, origin.above());
    }

    /** The state of block {@code i} of {@link #parts}, from any block's. */
    public BlockState part(BlockState state, int i) {
        return state.setValue(HALF, i >= 1 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    /** Whether a state is the origin's. */
    public static boolean isOrigin(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                && (!state.hasProperty(SIDE) || state.getValue(SIDE) == ConsoleSide.LEFT);
    }

    /** Whether {@code neighbor}, across {@code direction} from {@code state}, is its partner in this airlock. */
    protected boolean partner(BlockState state, Direction direction, BlockState neighbor) {
        DoubleBlockHalf half = state.getValue(HALF);
        Direction vertical = half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        return direction == vertical
                && neighbor.is(this)
                && neighbor.getValue(FACING) == state.getValue(FACING)
                && neighbor.getValue(HALF) != half;
    }

    /** Whether {@code direction} from a block of this state leads to one of its partners. */
    protected boolean towardPartner(BlockState state, Direction direction) {
        return direction == (state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
    }

    /** A partner's state, as this block's: everything but the half (and the side) is shared. */
    protected BlockState copy(BlockState partner, BlockState state) {
        return partner.setValue(HALF, state.getValue(HALF));
    }

    // ---- Placing and removing, as a door ------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        Direction facing = context.getHorizontalDirection();
        if (origin.getY() >= level.getMaxBuildHeight() - 1) {
            return null;
        }
        List<BlockPos> parts = parts(origin, facing);
        boolean powered = false;
        for (int i = 0; i < parts.size(); i++) {
            BlockPos p = parts.get(i);
            if (i > 0
                    && (!level.getWorldBorder().isWithinBounds(p)
                            || !level.getBlockState(p).canBeReplaced(context))) {
                return null;
            }
            if (p.getY() == origin.getY()
                    && !level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                return null;
            }
            powered |= level.hasNeighborSignal(p);
        }
        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(POWERED, powered)
                .setValue(OPEN, powered);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        List<BlockPos> parts = parts(pos, state.getValue(FACING));
        for (int i = 1; i < parts.size(); i++) {
            level.setBlock(parts.get(i), part(state, i), Block.UPDATE_ALL);
        }
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
     * Keeps its blocks alike: each takes its partners' state when it changes, and goes when one of
     * them goes, so breaking any block breaks them all. A lower block also goes when the block under
     * it does.
     */
    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighbor,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (towardPartner(state, direction)) {
            return partner(state, direction, neighbor) ? copy(neighbor, state) : Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER
                && direction == Direction.DOWN
                && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    /**
     * Breaking a block other than the origin in creative, or without the right tool, removes the
     * origin with no drop, as for a door's upper half. Otherwise the origin drops the airlock, and its
     * firelock if it has one, when it goes.
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
        return isOrigin(state) ? new AirlockBlockEntity(pos, state) : null;
    }

    // ---- Redstone (CONT-135) ---------------------------------------------------------------

    /**
     * A redstone signal at any of its blocks opens it and holds it open, unless it is bolted or its
     * firelock is shut (CONT-136, CONT-137): it then only notes the signal. When the signal ends, an
     * open airlock closes itself {@link #AUTO_CLOSE_TICKS} later (CONT-135).
     */
    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        if (!(level instanceof ServerLevel server) || block == this) {
            return;
        }
        BlockPos origin = origin(state, pos);
        BlockState at = server.getBlockState(origin);
        if (!at.is(this)) {
            return;
        }
        boolean powered = false;
        for (BlockPos p : parts(origin, at.getValue(FACING))) {
            powered |= server.hasNeighborSignal(p);
        }
        if (powered == at.getValue(POWERED)) {
            return;
        }
        server.setBlock(origin, at.setValue(POWERED, powered), Block.UPDATE_ALL);
        if (powered && free(at)) {
            setOpen(server, origin, true, null);
        } else if (!powered && at.getValue(OPEN)) {
            closeIn(server, origin, AUTO_CLOSE_TICKS);
        }
    }

    // ---- Closing itself (CONT-135) -----------------------------------------------------------

    /** Has the open airlock whose origin is at {@code origin} close itself {@code ticks} from now. */
    private static void closeIn(ServerLevel level, BlockPos origin, int ticks) {
        if (level.getBlockEntity(origin) instanceof AirlockBlockEntity be) {
            be.setCloseAt(level.getGameTime() + ticks);
            level.scheduleTick(origin, level.getBlockState(origin).getBlock(), ticks);
        }
    }

    /**
     * Closes the open airlock at its time, unless a redstone signal holds it open. It waits while
     * anything stands in its doorway. A tick that comes early, because the airlock was closed and
     * opened again, waits for the later time.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isOrigin(state) || !(level.getBlockEntity(pos) instanceof AirlockBlockEntity be)) {
            return;
        }
        if (be.closeAt() < 0 || !state.getValue(OPEN) || state.getValue(POWERED)) {
            return;
        }
        long left = be.closeAt() - level.getGameTime();
        if (left > 0) {
            level.scheduleTick(pos, this, (int) Math.min(left, AUTO_CLOSE_TICKS));
            return;
        }
        if (inDoorway(level, pos, state)) {
            closeIn(level, pos, DOORWAY_WAIT_TICKS);
            return;
        }
        be.setCloseAt(-1);
        setOpen(level, pos, false, null);
    }

    /** Whether any entity but a spectator stands in the doorway of the airlock at {@code origin}. */
    private boolean inDoorway(ServerLevel level, BlockPos origin, BlockState state) {
        for (BlockPos p : parts(origin, state.getValue(FACING))) {
            if (!level.getEntitiesOfClass(Entity.class, new AABB(p), e -> !e.isSpectator())
                    .isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---- Using it -------------------------------------------------------------------------

    /** Whether an airlock in this state can open: it isn't bolted and no firelock is shut over it. */
    public static boolean free(BlockState state) {
        return !state.getValue(BOLTED) && !state.getValue(FIRELOCK).shut();
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
        // The crowbar forces a fitted firelock in its own useOn (CONT-094): it doesn't open or close
        // it by hand. The firelock item fits a firelock, and the multitool links the airlock, before
        // this is asked (FirelockItem#onItemUseFirst, MultitoolItem#onItemUseFirst; CONT-137, CONT-138).
        if (stack.getItem() instanceof CrowbarItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Opens or closes it by hand. A shut firelock opens first, unless it is locked down (CONT-093). A
     * bolted airlock stays shut, and asks the controller that links it, if any, to cycle toward it
     * (CONT-136, CONT-140).
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            BlockPos origin = origin(state, pos);
            BlockState at = server.getBlockState(origin);
            if (!at.is(this) || !(server.getBlockEntity(origin) instanceof AirlockBlockEntity be)) {
                return InteractionResult.CONSUME;
            }
            if (at.getValue(FIRELOCK).shut()) {
                if (be.firelock().lockdown().isPresent()) {
                    player.displayClientMessage(be.firelock().lockdown().get().message(), true);
                    server.playSound(
                            null, pos, AtmosSounds.FIRELOCK_FORCE.get(), net.minecraft.sounds.SoundSource.BLOCKS);
                } else {
                    be.firelockDoor().setOpen(server, true);
                }
                return InteractionResult.CONSUME;
            }
            if (at.getValue(BOLTED)) {
                Optional<AirlockControllerBlockEntity> controller =
                        AirlockControllerBlockEntity.linking(server, origin);
                if (controller.isEmpty() || !controller.get().requestCycle(origin)) {
                    AtmosSounds.play(server, pos, AtmosSounds.AIRLOCK_DENIED);
                    player.displayClientMessage(Component.translatable("block.zas_atmos.airlock.bolted"), true);
                }
                return InteractionResult.CONSUME;
            }
            setOpen(server, origin, !at.getValue(OPEN), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---- What controllers, firelocks and tools change, server side -------------------------

    /**
     * Opens or closes the airlock whose origin is at {@code origin}, with no checks; a no-op if it is
     * already so. An airlock it opens closes itself {@link #AUTO_CLOSE_TICKS} later, or that long
     * after a redstone signal holding it open ends (CONT-135).
     *
     * @return true if it changed
     */
    public static boolean setOpen(ServerLevel level, BlockPos origin, boolean open, @Nullable Entity by) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof AirlockBlock) || state.getValue(OPEN) == open) {
            return false;
        }
        level.setBlock(origin, state.setValue(OPEN, open), Block.UPDATE_ALL);
        if (open) {
            closeIn(level, origin, AUTO_CLOSE_TICKS);
            AirlockControllerBlockEntity.linking(level, origin).ifPresent(c -> c.opened(origin));
        } else if (level.getBlockEntity(origin) instanceof AirlockBlockEntity be) {
            be.setCloseAt(-1);
        }
        AtmosSounds.play(level, origin, open ? AtmosSounds.AIRLOCK_OPEN : AtmosSounds.AIRLOCK_CLOSE);
        level.gameEvent(by, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, origin);
        return true;
    }

    /**
     * Opens the airlock whose origin is at {@code origin} if it is free to open: not bolted, and with
     * no firelock shut over it.
     *
     * @return true if it is open now
     */
    public static boolean openIfFree(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof AirlockBlock) || !free(state)) {
            return false;
        }
        setOpen(level, origin, true, null);
        return true;
    }

    /** Bolts or unbolts the airlock whose origin is at {@code origin}, closing it first to bolt it (CONT-136). */
    public static void setBolted(ServerLevel level, BlockPos origin, boolean bolted) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof AirlockBlock)) {
            return;
        }
        if (bolted) {
            setOpen(level, origin, false, null);
            state = level.getBlockState(origin);
        }
        if (state.getValue(BOLTED) != bolted) {
            level.setBlock(origin, state.setValue(BOLTED, bolted), Block.UPDATE_ALL);
            AtmosSounds.play(level, origin, bolted ? AtmosSounds.AIRLOCK_BOLT : AtmosSounds.AIRLOCK_UNBOLT);
        }
    }

    /**
     * Fits an open firelock to the airlock whose origin is at {@code origin} (CONT-137).
     *
     * @return true if it fitted one, false if it isn't an airlock or has one
     */
    public static boolean fitFirelock(ServerLevel level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof AirlockBlock)
                || state.getValue(FIRELOCK).fitted()) {
            return false;
        }
        level.setBlock(origin, state.setValue(FIRELOCK, AirlockFirelock.OPEN), Block.UPDATE_ALL);
        AtmosSounds.play(level, origin, AtmosSounds.FIRELOCK_OPEN);
        if (level.getBlockEntity(origin) instanceof AirlockBlockEntity be) {
            be.firelock().reset();
            be.setChanged();
        }
        return true;
    }

    /**
     * Sets the fitted firelock of the airlock whose origin is at {@code origin}. Shutting it closes
     * the airlock too; opening it leaves the airlock closed.
     */
    static void setFirelock(ServerLevel level, BlockPos origin, AirlockFirelock firelock) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof AirlockBlock)
                || !state.getValue(FIRELOCK).fitted()
                || state.getValue(FIRELOCK) == firelock) {
            return;
        }
        BlockState next = state.setValue(FIRELOCK, firelock);
        if (firelock.shut()) {
            next = next.setValue(OPEN, false);
        }
        level.setBlock(origin, next, Block.UPDATE_ALL);
    }

    /** All the positions of the airlock whose origin is at {@code origin}, or none if it isn't one. */
    public static List<BlockPos> blocks(Level level, BlockPos origin) {
        BlockState state = level.getBlockState(origin);
        if (!(state.getBlock() instanceof AirlockBlock airlock)) {
            return List.of();
        }
        return new ArrayList<>(airlock.parts(origin, state.getValue(FACING)));
    }
}
