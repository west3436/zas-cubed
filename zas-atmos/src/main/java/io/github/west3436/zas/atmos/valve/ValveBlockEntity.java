package io.github.west3436.zas.atmos.valve;

import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A valve's block entity: its 200 L body, a network node (CONT-007, CONT-075). Open, it is a member
 * of the one network it joins on both sides. Shut, it is a network of its own, holding its share of
 * the gas sealed inside until it opens again, or releasing it if it is removed (CONT-009).
 *
 * <p><b>Turning.</b> The block state says whether the valve is open, and the networks follow at the
 * valve's next atmos step: they dissolve, each member keeps its share by volume, and the next ones
 * built follow the new connections (CONT-003). Stepping once per atmos tick (CONT-006), a valve
 * rebuilds its networks at most that often however fast a redstone clock turns it, and not at all
 * if it is back where it was by the next step.
 */
public class ValveBlockEntity extends PipeNodeEntity {
    /** Whether the networks around the valve were last built with it open. */
    private boolean builtOpen;

    public ValveBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.VALVE.get(), pos, state, PipeVolumes.VALVE);
        builtOpen = state.getValue(ValveBlock.OPEN);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        boolean open = getBlockState().getValue(ValveBlock.OPEN);
        if (open != builtOpen) {
            builtOpen = open;
            reconnect();
        }
        PipeNetwork network = network();
        if (open && sideApart(network)) {
            reconnect();
            network();
        }
    }

    /**
     * Whether a connected neighbour is in another network. It can be: a side whose chunk was away when
     * the valve opened, its nodes in memory, comes back with the network it had, and nothing else tells
     * either side.
     */
    private boolean sideApart(PipeNetwork network) {
        for (PipeNodeEntity side : connectedNodes()) {
            if (side.currentNetwork() != network) {
                return true;
            }
        }
        return false;
    }
}
