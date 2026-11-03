package io.github.west3436.zas.atmos.battery;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The recharger (CONT-113): a floor plate that charges one battery (CONT-112) from its area air
 * controller's power. Its {@link #BATTERY} property shows whether it holds one, and {@link #CHARGE}
 * that battery's gauge, 0 (empty) to 4 (full). Broken, it drops its battery.
 */
public class RechargerBlock extends BaseEntityBlock {
    public static final MapCodec<RechargerBlock> CODEC = simpleCodec(RechargerBlock::new);

    /** The way the front faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether it holds a battery. */
    public static final BooleanProperty BATTERY = BooleanProperty.create("battery");

    /** Its battery's gauge: 0 empty, 1–3 part charged, 4 full. */
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);

    /** The plate and tower's outline (models/block/recharger.json), front north or south. */
    private static final VoxelShape NORTH_SOUTH = Block.box(1, 0, 2, 15, 13, 14);

    /** The same, turned east or west. */
    private static final VoxelShape EAST_WEST = Block.box(2, 0, 1, 14, 13, 15);

    public RechargerBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BATTERY, false)
                .setValue(CHARGE, 0));
    }

    /** The gauge step for a battery's charge. */
    public static int gauge(long energy, long capacity) {
        if (capacity <= 0 || energy <= 0) {
            return 0;
        }
        if (energy >= capacity) {
            return 4;
        }
        return 1 + (int) Math.min(2, 3 * energy / capacity);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BATTERY, CHARGE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Opens the recharger's GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof RechargerBlockEntity recharger) {
            player.openMenu(recharger);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof RechargerBlockEntity be) {
            Containers.dropItemStack(
                    level, pos.getX(), pos.getY(), pos.getZ(), be.battery().battery());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RechargerBlockEntity(pos, state);
    }
}
