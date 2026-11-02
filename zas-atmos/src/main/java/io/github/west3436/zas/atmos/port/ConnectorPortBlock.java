package io.github.west3436.zas.atmos.port;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A connector port (CONT-083): set in the floor, facing up. A canister standing on it joins the
 * port's network, and a pipe joins it only on its bottom. Its {@link #CONNECTED} state shows a
 * collar while a canister is attached.
 */
public class ConnectorPortBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<ConnectorPortBlock> CODEC = simpleCodec(ConnectorPortBlock::new);

    /** Whether a canister stands on the port, attached. */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public ConnectorPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CONNECTED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONNECTED);
    }

    /** A pipe below, or a canister above. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == Direction.DOWN || (face == Direction.UP && neighbour.getBlock() instanceof CanisterBlock);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos above = context.getClickedPos().above();
        BlockState state = defaultBlockState();
        return state.setValue(
                CONNECTED,
                PipeConnector.connected(state, Direction.UP, context.getLevel().getBlockState(above)));
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction face,
            BlockState neighbour,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighbourPos) {
        return face == Direction.UP
                ? state.setValue(CONNECTED, PipeConnector.connected(state, face, neighbour))
                : state;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConnectorPortBlockEntity(pos, state);
    }
}
