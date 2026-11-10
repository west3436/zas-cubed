package io.github.west3436.zas.compat.mekanism;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The exhaust port's block (MEK-022): a full block with a grille on one face and a socket on the
 * face behind it. What it does is {@link ExhaustPortHandler}'s; the block only says which way it
 * faces. It has no block entity, since it holds nothing.
 *
 * <p>Its capability is looked up from its block state, so it tells the level whenever that state
 * appears, changes or goes: a tube beside it keeps what it last found until then.
 */
public class ExhaustPortBlock extends Block {
    public static final MapCodec<ExhaustPortBlock> CODEC = simpleCodec(ExhaustPortBlock::new);

    /** The way the grille faces. Chemicals come in through the face opposite. */
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public ExhaustPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /**
     * The grille faces the player who places it, as a passive vent's does, so a port put against a
     * machine has its socket on the machine and its grille towards the room.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite());
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
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.invalidateCapabilities(pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        level.invalidateCapabilities(pos);
    }
}
