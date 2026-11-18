package io.github.west3436.zas.atmos.turbine;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * A gas turbine controller (CONT-163, CONT-164): a full block, its screen facing the player who
 * places it, set anywhere in reach of the engine's parts and of cables, outside the chamber. The
 * multitool selects it and then links a turbine, a compressor and an igniter to it ({@link
 * MultitoolItem}). It holds the engine's FE and offers it on every face, and its window drives the
 * engine. Its screen lights while the turbine runs ({@link #RUNNING}).
 */
public class TurbineControllerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<TurbineControllerBlock> CODEC = simpleCodec(TurbineControllerBlock::new);

    /** Whether its turbine's shaft turns. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");

    public TurbineControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RUNNING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RUNNING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Opens its window. The multitool selects it before this is asked. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TurbineControllerBlockEntity be) {
            sp.openMenu(be, be::writeMenu);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurbineControllerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != AtmosBlockEntities.TURBINE_CONTROLLER.get()) {
            return null;
        }
        return (l, p, s, be) -> ((TurbineControllerBlockEntity) be).serverTick();
    }
}
