package io.github.west3436.zas.atmos.firelock;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.airlock.AirlockFirelock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.tool.CrowbarItem;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A vanilla door with a firelock fitted to it (CONT-137). A firelock used on a door replaces the
 * door's two blocks with this block, which keeps the door's {@code facing}, {@code hinge}, {@code
 * open} and {@code powered}, and remembers which door it was in its block entity on each half
 * ({@link FirelockDoorBlockEntity}). It draws that door with the firelock over it, and breaking it
 * drops the door and the firelock.
 *
 * <p>It is a door: its shapes are the door's, so its zone boundary is the door's (GEO-007), and it
 * opens as that door did, by hand if the door does and by redstone, with the door's sounds. The
 * firelock works in its doorway as a standalone one does ({@link FirelockLogic}): when it closes it
 * closes the door too, and while it's shut the door doesn't open. The hand opens a shut firelock
 * unless it's locked down (CONT-093); the crowbar forces it either way (CONT-094). Reopening the
 * firelock leaves the door closed.
 *
 * <p>It isn't in {@code #minecraft:doors}, so villagers don't open it; it is in {@code
 * #zas:zone_boundary}.
 */
public class FirelockDoorBlock extends DoorBlock implements EntityBlock {
    public static final MapCodec<FirelockDoorBlock> CODEC = simpleCodec(FirelockDoorBlock::new);

    /** The firelock over the door: open, closed or locked down. */
    public static final EnumProperty<AirlockFirelock> FIRELOCK =
            EnumProperty.create("firelock", AirlockFirelock.class, AirlockFirelock::fitted);

    public FirelockDoorBlock(Properties properties) {
        super(BlockSetType.IRON, properties.noOcclusion());
        registerDefaultState(defaultBlockState().setValue(FIRELOCK, AirlockFirelock.OPEN));
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FIRELOCK);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FirelockDoorBlockEntity(pos, state);
    }

    // ---- The door it was --------------------------------------------------------------------

    /** Whether a firelock fits to a block in this state: a door of any kind but this one. */
    public static boolean fits(BlockState state) {
        return state.getBlock() instanceof DoorBlock && !(state.getBlock() instanceof FirelockDoorBlock);
    }

    /** The lower half's position, from either half. */
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** The door kept at {@code pos}, or an iron door if it isn't known yet. */
    public static Block door(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FirelockDoorBlockEntity be ? be.door() : Blocks.IRON_DOOR;
    }

    /** The state of {@code door} that looks as a block in {@code state} does. */
    public static BlockState doorState(BlockState state, Block door) {
        BlockState out = door.defaultBlockState();
        out = copy(out, state, FACING);
        out = copy(out, state, HALF);
        out = copy(out, state, HINGE);
        out = copy(out, state, OPEN);
        return copy(out, state, POWERED);
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState to, BlockState from, Property<T> p) {
        return to.hasProperty(p) && from.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }

    /** How the kept door opens and sounds. */
    private static BlockSetType type(BlockGetter level, BlockPos pos) {
        return door(level, pos) instanceof DoorBlock d ? d.type() : BlockSetType.IRON;
    }

    /**
     * Fits a firelock to the door at {@code pos}, either half, if it is a whole door: both halves
     * become this block, open or closed as the door was, with the firelock open.
     *
     * @return true if it fitted
     */
    public static boolean fit(ServerLevel level, BlockPos pos) {
        BlockState clicked = level.getBlockState(pos);
        if (!fits(clicked)) {
            return false;
        }
        BlockPos lower = lower(clicked, pos);
        BlockPos upper = lower.above();
        BlockState lowerState = level.getBlockState(lower);
        BlockState upperState = level.getBlockState(upper);
        Block door = lowerState.getBlock();
        if (!upperState.is(door)
                || lowerState.getValue(HALF) != DoubleBlockHalf.LOWER
                || upperState.getValue(HALF) != DoubleBlockHalf.UPPER) {
            return false;
        }
        BlockState ours = AtmosBlocks.FIRELOCK_DOOR
                .get()
                .defaultBlockState()
                .setValue(FACING, lowerState.getValue(FACING))
                .setValue(HINGE, lowerState.getValue(HINGE))
                .setValue(OPEN, lowerState.getValue(OPEN))
                .setValue(POWERED, lowerState.getValue(POWERED))
                .setValue(FIRELOCK, AirlockFirelock.OPEN);
        // Neither half may see the other gone, which would break it: set both before any shape update.
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(upper, ours.setValue(HALF, DoubleBlockHalf.UPPER), flags);
        level.setBlock(lower, ours.setValue(HALF, DoubleBlockHalf.LOWER), flags);
        for (BlockPos p : List.of(lower, upper)) {
            if (level.getBlockEntity(p) instanceof FirelockDoorBlockEntity be) {
                be.setDoor(door);
            }
            level.blockUpdated(p, AtmosBlocks.FIRELOCK_DOOR.get());
        }
        AtmosSounds.play(level, lower, AtmosSounds.FIRELOCK_OPEN);
        return true;
    }

    // ---- Using it -----------------------------------------------------------------------------

    /** The crowbar and the multitool work it in their own useOn (CONT-094, CONT-043): neither opens it. */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (stack.getItem() instanceof CrowbarItem || stack.getItem() instanceof MultitoolItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Opens or closes the door by hand, if the door it was opens by hand. A shut firelock opens
     * first, unless it is locked down (CONT-093), and the door stays closed.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos lower = lower(state, pos);
        if (state.getValue(FIRELOCK).shut()) {
            if (level instanceof ServerLevel server
                    && server.getBlockEntity(lower) instanceof FirelockDoorBlockEntity be) {
                if (be.firelock().lockdown().isPresent()) {
                    player.displayClientMessage(be.firelock().lockdown().get().message(), true);
                    server.playSound(null, pos, AtmosSounds.FIRELOCK_FORCE.get(), SoundSource.BLOCKS);
                } else {
                    be.firelockDoor().setOpen(server, true);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!type(level, pos).canOpenByHand()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel) {
            setOpen(player, level, state, pos, !state.getValue(OPEN));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens or closes the door, with the kept door's sound; a shut firelock keeps it closed. */
    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (!state.is(this)
                || state.getValue(OPEN) == open
                || (open && state.getValue(FIRELOCK).shut())) {
            return;
        }
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
        sound(level, pos, open);
        level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }

    private static void sound(Level level, BlockPos pos, boolean open) {
        BlockSetType type = type(level, pos);
        level.playSound(
                null,
                pos,
                open ? type.doorOpen() : type.doorClose(),
                SoundSource.BLOCKS,
                1.0f,
                level.getRandom().nextFloat() * 0.1f + 0.9f);
    }

    /**
     * A redstone signal at either half opens it and its end closes it, as the door's did, unless the
     * firelock is shut: it then only notes the signal.
     */
    @Override
    protected void neighborChanged(
            BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        Direction other = state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN;
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.relative(other));
        if (defaultBlockState().is(block) || powered == state.getValue(POWERED)) {
            return;
        }
        boolean open = powered && !state.getValue(FIRELOCK).shut();
        if (open != state.getValue(OPEN)) {
            sound(level, pos, open);
            level.gameEvent(null, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
        level.setBlock(pos, state.setValue(POWERED, powered).setValue(OPEN, open), Block.UPDATE_CLIENTS);
    }

    /**
     * Sets the firelock of the door whose lower half is at {@code lower}. Shutting it closes the door
     * too; opening it leaves the door closed.
     */
    static void setFirelock(ServerLevel level, BlockPos lower, AirlockFirelock firelock) {
        BlockState state = level.getBlockState(lower);
        if (!(state.getBlock() instanceof FirelockDoorBlock) || state.getValue(FIRELOCK) == firelock) {
            return;
        }
        BlockState next = state.setValue(FIRELOCK, firelock);
        if (firelock.shut()) {
            next = next.setValue(OPEN, false);
        }
        level.setBlock(lower, next, Block.UPDATE_ALL);
    }

    // ---- Breaking it --------------------------------------------------------------------------

    /** The lower half drops the door it was and the firelock; the upper drops nothing. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>();
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        drops.add(new ItemStack(be instanceof FirelockDoorBlockEntity d ? d.door() : Blocks.IRON_DOOR));
        drops.add(new ItemStack(AtmosItems.FIRELOCK.get()));
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(door(level, pos));
    }
}
