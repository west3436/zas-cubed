package io.github.west3436.zas.atmos.pipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The floor grille (#486): a grating of iron bars that is a full block. It is walked on and it is
 * gas-tight, as any full cube is (GEO-041), and it is seen through and lets light by, as glass does.
 *
 * <p>It is the casing of a pipe ({@link EncasedPipeBlock}), as a station wall is: a pipe used on it
 * goes into it, and it goes round a pipe it is used on. Either way the result is the one block, a
 * pipe under a floor grille, which for the air is this block still. A run of pipes can so lie in a
 * floor and show through it.
 */
public class FloorGrilleBlock extends TransparentBlock {
    public static final MapCodec<FloorGrilleBlock> CODEC = simpleCodec(FloorGrilleBlock::new);

    public FloorGrilleBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends FloorGrilleBlock> codec() {
        return CODEC;
    }

    /** It shows no face against its own kind, with a pipe in it or without: a floor of them is one grating. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return adjacent.getBlock() instanceof EncasedPipeBlock cased && cased.shell() == this
                || super.skipRendering(state, adjacent, direction);
    }
}
