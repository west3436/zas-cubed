package io.github.west3436.zas.atmos.vent;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * A passive vent (CONT-016), shaped like the vent: a full block whose grille faces any of six ways.
 * A pipe joins it only on its back, the face opposite the grille. It has no power, no settings and
 * no GUI.
 */
public class PassiveVentBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<PassiveVentBlock> CODEC = simpleCodec(PassiveVentBlock::new);

    /** The way the grille faces. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public PassiveVentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == self.getValue(FACING).getOpposite();
    }

    /** The grille faces the player who places it, so a vent set in a wall or floor faces the room. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PassiveVentBlockEntity(pos, state);
    }
}
