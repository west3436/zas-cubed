package io.github.west3436.zas.atmos.vent;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A vent (CONT-014): a full block whose grille faces any of six ways. A pipe joins it only on its
 * back, the face opposite the grille. Its {@link VentMode#PROPERTY} shows whether it is blowing,
 * siphoning or idle.
 */
public class VentBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<VentBlock> CODEC = simpleCodec(VentBlock::new);

    /** The way the grille faces. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public VentBlock(Properties properties) {
        super(properties);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VentMode.PROPERTY, VentMode.OFF));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VentMode.PROPERTY);
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

    /** Opens the vent's GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof VentBlockEntity vent) {
            player.openMenu(vent);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VentBlockEntity(pos, state);
    }
}
