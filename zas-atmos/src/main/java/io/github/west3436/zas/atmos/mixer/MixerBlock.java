package io.github.west3436.zas.atmos.mixer;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.inline.SidePortBlock;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A mixer (CONT-077): a box on the pipe, with a second input at its side. */
public class MixerBlock extends SidePortBlock {
    public static final MapCodec<MixerBlock> CODEC = simpleCodec(MixerBlock::new);

    public MixerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemLike mirroredItem() {
        return AtmosItems.MIXER_MIRRORED;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MixerBlockEntity(pos, state);
    }
}
