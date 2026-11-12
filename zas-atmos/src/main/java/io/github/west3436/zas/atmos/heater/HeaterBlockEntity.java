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
 * A heater's block entity (CONT-019, CONT-087): it adds heat to the zone it stands in, up to a
 * setpoint of 293–573 K, at up to {@code [power] heater_watts} (5 kW), drawing {@code [power] heater}
 * FE/t (60) at full power. It never cools.
 */
public class HeaterBlockEntity extends ThermoMachineBlockEntity {
    /** Who the heat comes from, in the conservation ledger. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "heater");

    public HeaterBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.HEATER.get(), pos, state, HeaterMath.DEFAULT_SETPOINT);
    }

    @Override
    public double clampSetpoint(double kelvin) {
        return HeaterMath.clampSetpoint(kelvin);
    }

    @Override
    protected double maxWatts() {
        return AtmosServerConfig.get(AtmosServerConfig.HEATER_WATTS);
    }

    @Override
    protected int drawPerTick() {
        return AtmosServerConfig.get(AtmosServerConfig.HEATER_DRAW);
    }

    @Override
    protected double joules(double heatCapacity, double temperature, double setpoint, double seconds, double maxWatts) {
        return HeaterMath.heat(heatCapacity, temperature, setpoint, seconds, maxWatts);
    }

    @Override
    protected CompletableFuture<HeatResult> move(ServerLevel level, double joules) {
        return HeatMutations.add(level, point(), joules, SOURCE);
    }

    @Override
    protected ThermoStatus working() {
        return ThermoStatus.HEATING;
    }

    @Override
    protected SoundEvent switchOnSound() {
        return AtmosSounds.HEATER_ON.get();
    }

    @Override
    protected SoundEvent switchOffSound() {
        return AtmosSounds.HEATER_OFF.get();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new HeaterMenu(
                id,
                inventory,
                battery(),
                menuData(HeaterMenu.DATA_COUNT),
                ContainerLevelAccess.create(level, worldPosition),
                this);
    }
}
