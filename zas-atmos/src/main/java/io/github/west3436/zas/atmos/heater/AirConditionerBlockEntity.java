package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An air conditioner's block entity (CONT-019, CONT-087): it takes heat from the zone it stands in,
 * down to a setpoint of 73–293 K and never below it, at up to {@code [power]
 * air_conditioner_watts} (5 kW), drawing {@code [power] air_conditioner} FE/t (60) at full power. It
 * never heats. The heat it takes leaves the world: nothing warms up behind it.
 */
public class AirConditionerBlockEntity extends ThermoMachineBlockEntity {
    /** Who takes the heat, in the conservation ledger. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "air_conditioner");

    public AirConditionerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.AIR_CONDITIONER.get(), pos, state, AirConditionerMath.DEFAULT_SETPOINT);
    }

    @Override
    protected double clampSetpoint(double kelvin) {
        return AirConditionerMath.clampSetpoint(kelvin);
    }

    @Override
    protected double maxWatts() {
        return AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_WATTS);
    }

    @Override
    protected int drawPerTick() {
        return AtmosServerConfig.get(AtmosServerConfig.AIR_CONDITIONER_DRAW);
    }

    @Override
    protected double joules(double heatCapacity, double temperature, double setpoint, double seconds, double maxWatts) {
        return AirConditionerMath.cool(heatCapacity, temperature, setpoint, seconds, maxWatts);
    }

    @Override
    protected CompletableFuture<HeatResult> move(ServerLevel level, double joules) {
        return HeatMutations.remove(level, point(), joules, SOURCE);
    }

    @Override
    protected ThermoStatus working() {
        return ThermoStatus.COOLING;
    }

    @Override
    protected SoundEvent switchOnSound() {
        return AtmosSounds.AIR_CONDITIONER_ON.get();
    }

    @Override
    protected SoundEvent switchOffSound() {
        return AtmosSounds.AIR_CONDITIONER_OFF.get();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new AirConditionerMenu(
                id,
                inventory,
                battery(),
                menuData(AirConditionerMenu.DATA_COUNT),
                ContainerLevelAccess.create(level, worldPosition),
                this);
    }
}
