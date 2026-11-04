package io.github.west3436.zas.atmos.pump;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A volume pump (CONT-012), with the same shape as a pressure pump. */
public class VolumePumpBlock extends InlineDeviceBlock {
    public static final MapCodec<VolumePumpBlock> CODEC = simpleCodec(VolumePumpBlock::new);

    public VolumePumpBlock(Properties properties) {
        super(properties, PressurePumpBlock.SHAPE);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VolumePumpBlockEntity(pos, state);
    }
}
