package io.github.west3436.zas.atmos.airlock;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An airlock controller (CONT-138 to CONT-141, CONT-153): a small wall panel that cycles an airlock.
 * Its {@code cycling} property picks its model, idle or cycling. Using it opens its window. The
 * multitool selects it, and once it is selected opens its window too; sneaking, the multitool links
 * it to an area air controller as a part instead. Its work is in {@link AirlockControllerBlockEntity}.
 */
public class AirlockControllerBlock extends WallPanelBlock implements EntityBlock {
    public static final MapCodec<AirlockControllerBlock> CODEC = simpleCodec(AirlockControllerBlock::new);

    public static final BooleanProperty CYCLING = BooleanProperty.create("cycling");

    public AirlockControllerBlock(Properties properties) {
        // models/block/airlock_controller.json: the panel is x 3–13, y 4–15 on the wall.
        super(properties, 3, 4, 13, 15);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CYCLING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CYCLING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirlockControllerBlockEntity(pos, state);
    }

    /**
     * Opens its window. The multitool selects or links it before this is asked (CONT-138, CONT-141,
     * {@link MultitoolItem#onItemUseFirst}).
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity be && player instanceof ServerPlayer sp) {
            sp.openMenu(be);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof AirlockControllerBlockEntity be) {
            be.removedFromWorld(server);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
