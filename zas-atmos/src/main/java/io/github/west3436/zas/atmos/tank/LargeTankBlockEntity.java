package io.github.west3436.zas.atmos.tank;

import io.github.west3436.zas.atmos.failure.CanisterFailure;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A large gas tank's block entity, on its inlet half: a 10,000 L node of the network its pipe joins,
 * with no pump and no valve of its own. Its gas is its share of that network's, by volume, as any
 * node's is, so it fills and empties as the network's pressure rises and falls (CONT-003, CONT-008).
 *
 * <p>Removed any way, it releases its share into its cell, as a pipe does (CONT-009): its item never
 * carries gas. Too much pressure or heat, or an explosion, makes it rupture or explode as a canister
 * does ({@link CanisterFailure}).
 */
public class LargeTankBlockEntity extends PipeNodeEntity {
    public LargeTankBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.LARGE_GAS_TANK.get(), pos, state, PipeVolumes.LARGE_TANK);
    }

    /** Keeps the network built, as a connector port does, and fails by its pressure after it has reacted. */
    @Override
    protected void atmosTick(ServerLevel level) {
        CanisterFailure.check(level, worldPosition, network().gas());
    }
}
