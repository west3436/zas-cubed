package io.github.west3436.zas.atmos.turbine;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * The gas turbine's compressor (CONT-160): it draws air from the cell behind it into the combustion
 * chamber in front of it, more the faster the shaft it shares with its {@linkplain TurbineBlock
 * turbine} turns, and the shaft pays for the work. It has no block entity: its turbine does the work
 * of the pair, and using it opens the turbine's window.
 */
public class CompressorBlock extends TurbinePartBlock {
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
}
