package io.github.west3436.zas.atmos.scrubber;

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
 * A scrubber (CONT-015), shaped like the vent: a full block whose grille faces any of six ways. A
 * pipe joins it only on its back, the face opposite the grille. Its {@link ScrubberMode#PROPERTY}
 * shows what it is doing.
 */
public class ScrubberBlock extends BaseEntityBlock implements PipeConnector {
    public static final MapCodec<ScrubberBlock> CODEC = simpleCodec(ScrubberBlock::new);

    /** The way the grille faces. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ScrubberBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ScrubberMode.PROPERTY, ScrubberMode.OFF));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ScrubberMode.PROPERTY);
    }

    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == self.getValue(FACING).getOpposite();
    }

    /** The grille faces the player who places it, so a scrubber set in a wall or floor faces the room. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Opens the scrubber's GUI. */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ScrubberBlockEntity scrubber) {
            player.openMenu(scrubber);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrubberBlockEntity(pos, state);
    }
}
