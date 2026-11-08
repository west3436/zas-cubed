package io.github.west3436.zas.atmos.radiator;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A radiator pipe segment's block entity: a 70 L network segment (CONT-143) that bursts as a pipe
 * does (CONT-146) and never leaks, since its ends are closed (CONT-144). Its heat moves in its
 * network's atmos tick ({@link RadiatorExchange}), which reads the segment's cell through {@link
 * #trusts}.
 */
public class RadiatorPipeBlockEntity extends PipeNodeEntity {
    public RadiatorPipeBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.RADIATOR_PIPE.get(), pos, state, PipeVolumes.PIPE);
    }

    @Override
    protected boolean isSegment() {
        return true;
    }

    @Override
    public boolean radiates() {
        return true;
    }

    /**
     * Whether to act on a reading of the segment's cell, recording it: not while the segment settles
     * after it loads, nor on a cell that changed since the last reading or is unresolved (CellWatch).
     */
    boolean trusts(AtmosphereSample cell) {
        return cellWatch().trust(cell);
    }

    /**
     * Takes heat back out of the network the segment is in now: what a zone didn't deliver of what
     * the network already took. A segment that has been removed has nothing to give it from.
     *
     * @param joules the heat, J; nothing happens below a millijoule
     */
    void giveBack(double joules) {
        if (!(joules > 1e-3) || isRemoved() || !(level instanceof ServerLevel)) {
            return;
        }
        GasStore gas = network().gas();
        double c = gas.heatCapacity();
        if (c > 0) {
            gas.setTemperature(Math.max(0, gas.temperature() - joules / c));
        }
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        // Rebuild a dissolved network now, so its heat is exchanged at its next atmos tick.
        network();
    }
}
