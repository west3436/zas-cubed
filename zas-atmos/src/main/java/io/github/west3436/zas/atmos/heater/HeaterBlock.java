package io.github.west3436.zas.atmos.heater;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A heater (CONT-019): a portable floor machine that heats the zone it stands in toward a setpoint,
 * on a battery (D-73). Its front faces the player who places it. Its {@link #LIT} property is true
 * while it heats. Broken, it drops its battery.
 *
 * <p>It isn't a full block: gas passes around it, so it stands in its room's cell.
 */
public class HeaterBlock extends BaseEntityBlock {
    public static final MapCodec<HeaterBlock> CODEC = simpleCodec(HeaterBlock::new);

    /** The way the front faces. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether it is heating. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** The body's outline (models/block/heater.json), front north or south. */
    private static final VoxelShape NORTH_SOUTH = Block.box(1, 0, 2, 15, 14, 14);

    /** The same, turned east or west. */
    private static final VoxelShape EAST_WEST = Block.box(2, 0, 1, 14, 14, 15);

    public HeaterBlock(Properties properties) {
        super(properties.noOcclusion());
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
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

    /** Opens the heater's GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof HeaterBlockEntity heater) {
            player.openMenu(heater);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof HeaterBlockEntity be) {
            Containers.dropItemStack(
                    level, pos.getX(), pos.getY(), pos.getZ(), be.battery().battery());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeaterBlockEntity(pos, state);
    }
}
