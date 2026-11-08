package io.github.west3436.zas.atmos.holder;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The tank holder (CONT-149 to CONT-151): a rack, a full block with its open front toward whoever
 * placed it, that holds up to ten personal tanks and keeps their contents. {@link #TANKS} is how many
 * it holds, which picks its model: the tanks stand on two shelves of five, the lower filled first.
 *
 * <p>Using a tank on it puts the tank in, and using it with an empty hand takes out the last tank put
 * in (CONT-150). It has no window. Broken, it drops its tanks. It has no power, moves no gas, and
 * offers hoppers and comparators nothing.
 */
public class TankHolderBlock extends BaseEntityBlock {
    public static final MapCodec<TankHolderBlock> CODEC = simpleCodec(TankHolderBlock::new);

    /** The way its open front faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** How many tanks it holds, 0 to 10. */
    public static final IntegerProperty TANKS = IntegerProperty.create("tanks", 0, TankHolderBlockEntity.PLACES);

    public TankHolderBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TANKS, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TANKS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * A tank in hand goes into the first free place. On a full holder it stays in hand and nothing
     * happens, not even the tank's own screen. Anything else goes on to {@link #useWithoutItem}. The
     * client judges by the block's count, as it doesn't hold the tanks.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!TankHolderBlockEntity.accepts(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(TANKS) >= TankHolderBlockEntity.PLACES) {
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof TankHolderBlockEntity holder
                && holder.put(stack)) {
            level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand takes out the last tank put in. A hand holding anything else does nothing here. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || state.getValue(TANKS) == 0) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TankHolderBlockEntity holder) {
            ItemStack tank = holder.takeLast();
            if (!tank.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, tank);
                level.playSound(null, pos, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 1f);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Puts right a count that a command left wrong (see {@link TankHolderBlockEntity#onLoad}). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof TankHolderBlockEntity holder) {
            holder.settle();
        }
    }

    /** Broken, it drops each tank it holds, which empties its places as it goes. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TankHolderBlockEntity holder) {
            for (int i = 0; i < TankHolderBlockEntity.PLACES; i++) {
                Containers.dropItemStack(
                        level,
                        pos.getX(),
                        pos.getY(),
                        pos.getZ(),
                        holder.tanks().getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankHolderBlockEntity(pos, state);
    }
}
