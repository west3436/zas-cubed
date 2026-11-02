package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** A pipe cap's block entity: a 70 L network node that never leaks (CONT-004, CONT-007). */
public class PipeCapBlockEntity extends PipeNodeEntity {
    public PipeCapBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PIPE_CAP.get(), pos, state, PipeVolumes.PIPE);
    }

    @Override
    protected void atmosTick(ServerLevel level) {}
}
