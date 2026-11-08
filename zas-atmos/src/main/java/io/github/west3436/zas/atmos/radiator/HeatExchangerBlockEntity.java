package io.github.west3436.zas.atmos.radiator;

import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A heat exchanger's block entity: a 200 L network node, a port's buffer (CONT-147). It isn't a pipe
 * segment, so it doesn't burst.
 *
 * <p><b>A pair</b> (HEAT-022) is two exchangers facing each other from neighbouring blocks. Once per
 * atmos tick the one facing south, east or up, of which a pair has exactly one, brings the two
 * exchangers' shares of their networks' gas to one temperature, moving the heat between the two
 * networks directly: no zone is involved, so nothing goes through the API. Two on one network, or a
 * pair with an empty network, move nothing.
 *
 * <p><b>Its colour</b> (CONT-148) is set each step from its network's temperature, as a band in its
 * block state, which clients tint by. An exchanger whose network holds no gas shows copper.
 */
public class HeatExchangerBlockEntity extends PipeNodeEntity {
    public HeatExchangerBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.HEAT_EXCHANGER.get(), pos, state, PipeVolumes.PORT);
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        Direction facing = getBlockState().getValue(HeatExchangerBlock.FACING);
        if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
            exchange(level, facing);
        }
        show(level);
    }

    /** One atmos tick of the pair this exchanger leads, if it has a partner. */
    private void exchange(ServerLevel level, Direction facing) {
        BlockPos at = worldPosition.relative(facing);
        if (!level.isLoaded(at)
                || !HeatExchangerBlock.paired(getBlockState(), level.getBlockState(at))
                || !(level.getBlockEntity(at) instanceof HeatExchangerBlockEntity partner)
                || partner.isRemoved()) {
            return;
        }
        PipeNetwork theirs = partner.network();
        PipeNetwork mine = network(); // after theirs, which may have been built over this one
        if (mine == theirs || !theirs.valid()) {
            return;
        }
        GasStore a = mine.gas();
        GasStore b = theirs.gas();
        double ca = a.heatCapacity();
        double cb = b.heatCapacity();
        double q = RadiatorMath.pairHeat(
                ca * nodeVolume() / a.volume(),
                a.temperature(),
                cb * partner.nodeVolume() / b.volume(),
                b.temperature());
        if (q == 0) {
            return;
        }
        a.setTemperature(Math.max(0, a.temperature() - q / ca));
        b.setTemperature(Math.max(0, b.temperature() + q / cb));
    }

    /** Sets its block state's band to its network's temperature, telling clients only. */
    private void show(ServerLevel level) {
        GasStore gas = network().gas();
        int band = gas.isEmpty() ? HeatExchangerTint.COPPER : HeatExchangerTint.band(gas.temperature());
        BlockState state = getBlockState();
        if (state.getValue(HeatExchangerBlock.HEAT) != band) {
            level.setBlock(
                    worldPosition,
                    state.setValue(HeatExchangerBlock.HEAT, band),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
}
