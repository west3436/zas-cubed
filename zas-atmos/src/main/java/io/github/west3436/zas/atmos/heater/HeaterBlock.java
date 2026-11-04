package io.github.west3436.zas.atmos.heater;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A heater (CONT-019): a portable that heats the zone it stands in toward a setpoint, on a battery
 * (D-73). Its {@link #LIT} property is true while it heats.
 */
public class HeaterBlock extends PortableBlock {
    public static final MapCodec<HeaterBlock> CODEC = simpleCodec(HeaterBlock::new);

    public HeaterBlock(Properties properties) {
        super(properties, 14, 12, 14); // models/block/heater.json
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HeaterBlockEntity(pos, state);
    }
}
