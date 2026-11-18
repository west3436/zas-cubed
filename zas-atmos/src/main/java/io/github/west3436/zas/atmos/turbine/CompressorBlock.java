package io.github.west3436.zas.atmos.turbine;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The gas turbine's compressor (CONT-160): a pipe joins it only on its back, where its buffer joins that
 * pipe's network, and it feeds the combustion chamber in front of it with that network's fuel and
 * oxidizer, as its {@linkplain TurbineControllerBlockEntity controller} sets. Its block entity does
 * the feeding ({@link CompressorBlockEntity}).
 */
public class CompressorBlock extends TurbinePartBlock implements EntityBlock, PipeConnector {
    public static final MapCodec<CompressorBlock> CODEC = simpleCodec(CompressorBlock::new);

    public CompressorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean compressor() {
        return true;
    }

    /** Its back, the face away from the chamber. */
    @Override
    public boolean pipeConnects(BlockState self, Direction face, BlockState neighbour) {
        return face == self.getValue(FACING).getOpposite();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompressorBlockEntity(pos, state);
    }
}
