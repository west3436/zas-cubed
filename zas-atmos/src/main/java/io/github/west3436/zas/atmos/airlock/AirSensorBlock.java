package io.github.west3436.zas.atmos.airlock;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * An air sensor (CONT-142): a small wall panel that reads the gas in front of the wall. Its {@code
 * status} picks its model: standby, cycling or alert. Its work is in {@link AirSensorBlockEntity}.
 */
public class AirSensorBlock extends WallPanelBlock implements EntityBlock {
    public static final MapCodec<AirSensorBlock> CODEC = simpleCodec(AirSensorBlock::new);

    public static final EnumProperty<SensorStatus> STATUS = EnumProperty.create("status", SensorStatus.class);

    public AirSensorBlock(Properties properties) {
        // models/block/air_sensor.json: the panel is x 3–13, y 7–13 on the wall.
        super(properties, 3, 7, 13, 13);
        registerDefaultState(
                stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, SensorStatus.STANDBY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirSensorBlockEntity(pos, state);
    }
}
