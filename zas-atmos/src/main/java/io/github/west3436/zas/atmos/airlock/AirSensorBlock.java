package io.github.west3436.zas.atmos.airlock;

import com.mojang.serialization.MapCodec;
import io.github.west3436.zas.atmos.gauge.GaugeReading;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * An air sensor (CONT-142): a small wall panel that reads the gas in front of the wall. Its {@code
 * status} picks its model: standby, cycling or alert. Its work is in {@link AirSensorBlockEntity}.
 * Pressing one that a controller links asks for a cycle (CONT-140). Pressing one while sneaking turns
 * what a comparator reads of it to the next reading, as a gauge's dial is turned, and the action bar
 * names it (CONT-177).
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

    /**
     * Asks the controller that links it to cycle away from the side the airlock is accessing
     * (CONT-140, {@link AirlockControllerBlockEntity#pressSensor}). It buzzes if none links it or the
     * controller refuses.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player.isSecondaryUseActive()) {
            // A sneaking use turns it to its next reading, as a gauge is turned (CONT-177).
            if (server.getBlockEntity(pos) instanceof AirSensorBlockEntity sensor) {
                GaugeReading next = sensor.reading().next();
                sensor.setReading(next);
                server.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, 0.8f);
                player.displayClientMessage(next.label(), true);
            }
        } else if (level instanceof ServerLevel server) {
            Optional<AirlockControllerBlockEntity> controller = AirlockControllerBlockEntity.linkingSensor(server, pos);
            if (controller.isEmpty() || !controller.get().pressSensor(pos)) {
                AtmosSounds.play(server, pos, AtmosSounds.AIRLOCK_DENIED);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Where its reading stands on its scale (CONT-176). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AirSensorBlockEntity sensor ? sensor.signal() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AirSensorBlockEntity(pos, state);
    }
}
