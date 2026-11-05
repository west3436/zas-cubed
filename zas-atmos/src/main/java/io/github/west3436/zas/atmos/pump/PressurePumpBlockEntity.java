package io.github.west3436.zas.atmos.pump;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pressure pump's block entity (CONT-011, CONT-076): it moves what brings its output's network to
 * its target, 0–4,500 kPa, at most 100 L/s at the input's conditions. New, its target is one
 * atmosphere, and it is off.
 */
public class PressurePumpBlockEntity extends PumpBlockEntity {
    /** A new pump's target, kPa. */
    public static final double DEFAULT_TARGET = 101.325;

    public PressurePumpBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PRESSURE_PUMP.get(), pos, state, DEFAULT_TARGET, PumpFlow.MAX_TARGET, 100, 10);
    }

    @Override
    protected double moles(PumpFlow.Side in, PumpFlow.Side out) {
        return PumpFlow.pressurePump(setting(), in, out);
    }

    @Override
    protected int drawWhenOn() {
        return AtmosServerConfig.get(AtmosServerConfig.PRESSURE_PUMP_DRAW);
    }

    @Override
    protected Supplier<SoundEvent> switchSound(boolean on) {
        return on ? AtmosSounds.PUMP_ON : AtmosSounds.PUMP_OFF;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PumpMenu(AtmosMenus.PRESSURE_PUMP.get(), id, this);
    }
}
