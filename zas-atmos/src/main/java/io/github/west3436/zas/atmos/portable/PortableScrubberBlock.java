package io.github.west3436.zas.atmos.portable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A portable scrubber (CONT-018): it scrubs the zone it stands in or a held tank into its store, on a
 * battery (D-73). Its {@link #LIT} property is true while it scrubs.
 */
public class PortableScrubberBlock extends PortableGasBlock {
    public static final MapCodec<PortableScrubberBlock> CODEC = simpleCodec(PortableScrubberBlock::new);

    public PortableScrubberBlock(Properties properties) {
        super(properties, 14, 12, 9); // models/block/portable_scrubber.json
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortableScrubberBlockEntity(pos, state);
    }
}
