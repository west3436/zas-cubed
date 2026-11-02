package io.github.west3436.zas.atmos.port;

import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** A connector port's block entity: a 200 L buffer in the port's network (CONT-007, CONT-083). */
public class ConnectorPortBlockEntity extends PipeNodeEntity {
    public ConnectorPortBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.CONNECTOR_PORT.get(), pos, state, PipeVolumes.PORT);
    }

    /** Keeps the network built, so a canister's gas is shared with it as soon as it attaches. */
    @Override
    protected void atmosTick(ServerLevel level) {
        network();
    }
}
