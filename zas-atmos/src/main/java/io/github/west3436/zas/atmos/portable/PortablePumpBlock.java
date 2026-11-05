package io.github.west3436.zas.atmos.portable;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A portable pump (CONT-018): it pumps gas between its store and the zone it stands in or a held
 * tank, on a battery (D-73). Its {@link #LIT} property is true while it pumps.
 */
public class PortablePumpBlock extends PortableGasBlock {
    public static final MapCodec<PortablePumpBlock> CODEC = simpleCodec(PortablePumpBlock::new);

    public PortablePumpBlock(Properties properties) {
        super(properties, 12, 12, 14); // models/block/portable_pump.json
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortablePumpBlockEntity(pos, state);
    }
}
