package io.github.west3436.zas.atmos.airlock;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.controller.ConsoleSide;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The large airlock (CONT-134): an {@link AirlockBlock} two blocks wide and two high, vg's glass
 * door, whose leaves part sideways. The block placed is the lower left one, the origin, and the rest
 * grow up and to the placing player's right; all four spaces must be free, as for the central
 * atmospherics controller's console. Each block has a {@code half} and a {@code side}, and they
 * share the rest of their state.
 */
public class LargeAirlockBlock extends AirlockBlock {
    public static final MapCodec<LargeAirlockBlock> CODEC = simpleCodec(LargeAirlockBlock::new);

    public LargeAirlockBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SIDE, ConsoleSide.LEFT));
    }

    @Override
    protected MapCodec<? extends AirlockBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SIDE);
    }

    /** Lower left, lower right, upper left, upper right. */
    @Override
    public List<BlockPos> parts(BlockPos origin, Direction facing) {
        BlockPos right = origin.relative(toRight(facing));
        return List.of(origin, right, origin.above(), right.above());
    }

    @Override
    public BlockState part(BlockState state, int i) {
        return state.setValue(HALF, i >= 2 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(SIDE, (i & 1) == 1 ? ConsoleSide.RIGHT : ConsoleSide.LEFT);
    }

    /** The way to the other column from a block of this state. */
    private static Direction across(BlockState state) {
        Direction right = toRight(state.getValue(FACING));
        return state.getValue(SIDE) == ConsoleSide.LEFT ? right : right.getOpposite();
    }

    @Override
    protected boolean towardPartner(BlockState state, Direction direction) {
        return super.towardPartner(state, direction) || direction == across(state);
    }

    @Override
    protected boolean partner(BlockState state, Direction direction, BlockState neighbor) {
        if (!neighbor.is(this) || neighbor.getValue(FACING) != state.getValue(FACING)) {
            return false;
        }
        if (direction == across(state)) {
            return neighbor.getValue(SIDE) != state.getValue(SIDE) && neighbor.getValue(HALF) == state.getValue(HALF);
        }
        return neighbor.getValue(HALF) != state.getValue(HALF) && neighbor.getValue(SIDE) == state.getValue(SIDE);
    }

    @Override
    protected BlockState copy(BlockState partner, BlockState state) {
        return partner.setValue(HALF, state.getValue(HALF)).setValue(SIDE, state.getValue(SIDE));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return mirror == Mirror.NONE
                ? state
                : state.rotate(mirror.getRotation(state.getValue(FACING))).cycle(SIDE);
    }
}
