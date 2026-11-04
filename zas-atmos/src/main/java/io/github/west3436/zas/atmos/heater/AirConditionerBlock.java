package io.github.west3436.zas.atmos.heater;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.portable.PortableBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An air conditioner, the requirements' freezer (CONT-019): a portable that cools the zone it stands
 * in toward a setpoint, on a battery (D-73). Its {@link #LIT} property is true while it cools.
 */
public class AirConditionerBlock extends PortableBlock {
    public static final MapCodec<AirConditionerBlock> CODEC = simpleCodec(AirConditionerBlock::new);

    public AirConditionerBlock(Properties properties) {
        super(properties, 14, 12, 14); // models/block/air_conditioner.json
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirConditionerBlockEntity(pos, state);
    }
}
