package io.github.west3436.zas.atmos.radiator;

import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A radiator adaptor's block entity: a 70 L network segment (CONT-145) that bursts as a pipe does
 * (CONT-146). It exchanges no heat itself, and has no open end.
 */
public class RadiatorAdaptorBlockEntity extends PipeNodeEntity {
    public RadiatorAdaptorBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.RADIATOR_ADAPTOR.get(), pos, state, PipeVolumes.PIPE);
    }

    @Override
    protected boolean isSegment() {
        return true;
    }

    @Override
    protected void atmosTick(ServerLevel level) {}
}
