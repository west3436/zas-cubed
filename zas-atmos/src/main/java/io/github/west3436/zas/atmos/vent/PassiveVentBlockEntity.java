package io.github.west3436.zas.atmos.vent;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A passive vent's block entity (CONT-016): a 400 L network node that needs no power. While its
 * network and the cell in front of its grille differ by more than {@link #THRESHOLD_KPA}, it
 * exchanges gas with that cell at the one-connection ratio (CORE-031), as an open pipe end does
 * ({@link PipeBlockEntity#openEndFlow}). Within the threshold it moves nothing, whatever the gases.
 */
public class PassiveVentBlockEntity extends PipeNodeEntity {
    /** Who a passive vent's exchanges are made by. */
    public static final SourceId PASSIVE_VENT = SourceId.of(ZasAtmosMod.MOD_ID, "passive_vent");

    /** The pressure difference it acts above, in kPa (CONT-016). */
    public static final double THRESHOLD_KPA = 0.5;

    private final ZonePort port = new ZonePort(PASSIVE_VENT);

    public PassiveVentBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PASSIVE_VENT.get(), pos, state, PipeVolumes.VENT);
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        GasStore net = network().gas();
        Vec3 point = Vec3.atCenterOf(worldPosition.relative(getBlockState().getValue(PassiveVentBlock.FACING)));
        AtmosphereSample sample = Atmospheres.at(level, point);
        if (!cellWatch().trust(sample)
                || Math.abs(net.pressure() - sample.mixture().pressure()) <= THRESHOLD_KPA) {
            return;
        }
        exchange(
                level,
                port,
                point,
                PipeBlockEntity.openEndFlow(
                        net.moles(),
                        net.volume(),
                        sample.mixture(),
                        sample.state() == AtmosphereState.AMBIENT,
                        1 - Math.pow(1 - PipeBlockEntity.OPEN_END_RATIO, AtmosTicks.SECONDS)));
    }
}
