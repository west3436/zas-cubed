package io.github.west3436.zas.atmos.pump;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A volume pump's block entity (CONT-012): it moves its set volume per second, up to 100 L/s, at the
 * input's conditions, whatever the output's pressure. New, it is set to its limit, and it is off.
 */
public class VolumePumpBlockEntity extends PumpBlockEntity {
    /** A new pump's rate, L/s. */
    public static final double DEFAULT_RATE = PumpFlow.MAX_RATE;

    public VolumePumpBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.VOLUME_PUMP.get(), pos, state, DEFAULT_RATE, PumpFlow.MAX_RATE, 10, 1);
    }

    @Override
    protected double moles(PumpFlow.Side in, PumpFlow.Side out) {
        return PumpFlow.volumePump(setting(), in);
    }

    /** It has no target to reach: either its rate is 0, or there is too little gas to count as a flow. */
    @Override
    protected InlineStatus idle(PumpFlow.Side in) {
        return setting() <= 0 ? InlineStatus.RATE_ZERO : InlineStatus.INPUT_EMPTY;
    }

    @Override
    protected int drawWhenOn() {
        return AtmosServerConfig.get(AtmosServerConfig.VOLUME_PUMP_DRAW);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PumpMenu(AtmosMenus.VOLUME_PUMP.get(), id, this);
    }
}
