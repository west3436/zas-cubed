package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.particle.GasJets;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * A pipe segment's block entity: a 70 L network node (CONT-007). A pipe with fewer than two
 * connections has an open end, which exchanges gas with its cell each atmos tick as a
 * one-connection edge does (CONT-004).
 */
public class PipeBlockEntity extends PipeNodeEntity {
    /** The one-connection sharing ratio per second (CORE-031's default, CONT-004). */
    public static final double OPEN_END_RATIO = 0.163;

    /** Gas flows smaller than this, in moles, are left out, so a settled open end goes quiet. */
    public static final double MIN_FLOW = 1e-4;

    /** Who an open end's exchanges are made by. */
    public static final SourceId OPEN_END = SourceId.of(ZasAtmosMod.MOD_ID, "open_pipe_end");

    /** The atmos ticks an open end waits after it loads; see {@link CellWatch}. */
    public static final int SETTLE_STEPS = CellWatch.SETTLE_STEPS;

    private final ZonePort port = new ZonePort(OPEN_END);

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PIPE.get(), pos, state, PipeVolumes.PIPE);
    }

    @Override
    protected boolean isSegment() {
        return true;
    }

    /** Whether the pipe has an open end: fewer than two connections, and no casing to close it. */
    public boolean isOpen() {
        BlockState state = getBlockState();
        return !(state.getBlock() instanceof PipeBlock pipe) || pipe.leaks(state);
    }

    /**
     * Its block changes under it when a casing goes on or comes off, and it stays ({@link
     * PipeBlock#onRemove}). The cell it acts on is then a new one, so its watch starts again.
     */
    @Override
    @SuppressWarnings("deprecation") // overridden as vanilla's block entities do
    public void setBlockState(BlockState state) {
        if (state.getBlock() != getBlockState().getBlock()) {
            cellWatch().reset();
        }
        super.setBlockState(state);
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    /**
     * Its arms toward anything but a pipe part follow the gas handlers there, so a capability that
     * comes or goes with no block changing is followed within an atmos tick (API-025). An arm into a
     * chunk that's away stays as it is, as an arm to a pipe does. It changes only its own state, so it
     * updates no neighbour, and a read at a chunk's edge can't bring back a chunk that's away.
     */
    @Override
    protected void followHandlers(ServerLevel level, int faces) {
        BlockState state = getBlockState();
        BlockState arms = state;
        for (Direction d : Direction.values()) {
            BooleanProperty arm = PipeBlock.CONNECTIONS.get(d);
            boolean handler = GasHandlers.has(faces, d);
            if (state.getValue(arm) != handler) {
                BlockState next = LoadedBlocks.state(level, worldPosition.relative(d));
                if (next != null && !(next.getBlock() instanceof PipeConnector)) {
                    arms = arms.setValue(arm, handler);
                }
            }
        }
        if (arms != state) {
            level.setBlock(worldPosition, arms, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        cellWatch().step();
        // Rebuild a dissolved network now, so its gas is shared over its new members even where
        // no open end needs it.
        network();
        if (!isOpen()) {
            reportStopped();
            return;
        }
        AtmosphereSample sample = Atmospheres.at(level, worldPosition);
        if (!cellWatch().trust(sample)) {
            reportStopped();
            return; // settling, a passing reading, or a paused or unresolved cell
        }
        GasStore pipe = network().gas();
        Flow flow = openEndFlow(
                pipe.moles(),
                pipe.volume(),
                sample.mixture(),
                sample.state() == AtmosphereState.AMBIENT,
                1 - Math.pow(1 - OPEN_END_RATIO, AtmosTicks.SECONDS));
        exchange(level, port, Vec3.atCenterOf(worldPosition), flow);
        GasJets.openEnd(
                level,
                worldPosition,
                PipeBlock.openFace(getBlockState()),
                flow.net() / AtmosTicks.SECONDS,
                GasJets.PIPE_MOUTH);
    }

    /**
     * What an open end moves in one atmos tick, per gas: the one-connection exchange of ZAS
     * between the network and its cell's atmosphere (CORE-031). With the zone's volume V_z, a gas
     * with n_p moles in the network's V_p and n_z in the zone moves r·(n_p·V_z − n_z·V_p)/(V_p +
     * V_z) out of the pipe; the ambient is an infinite reservoir, where that is r·(n_p − c·V_p) for
     * the ambient's concentration c.
     *
     * @param pipe moles per gas in the network
     * @param pipeVolume the network's volume
     * @param cell the zone's mixture, or one cell of the ambient
     * @param ambient whether the cell is open to the ambient
     * @param ratio the sharing ratio for one atmos tick
     * @return moles per gas leaving the pipe, and entering it
     */
    public static Flow openEndFlow(
            Map<ResourceLocation, Double> pipe, double pipeVolume, GasMixture cell, boolean ambient, double ratio) {
        TreeSet<ResourceLocation> gases = new TreeSet<>(pipe.keySet());
        gases.addAll(cell.moles().keySet());
        TreeMap<ResourceLocation, Double> out = new TreeMap<>();
        TreeMap<ResourceLocation, Double> in = new TreeMap<>();
        double vz = cell.volume();
        for (ResourceLocation gas : gases) {
            double np = pipe.getOrDefault(gas, 0.0);
            double nz = cell.moles(gas);
            double move = ambient
                    ? ratio * (np - nz / vz * pipeVolume)
                    : ratio * (np * vz - nz * pipeVolume) / (pipeVolume + vz);
            if (move > MIN_FLOW) {
                out.put(gas, move);
            } else if (move < -MIN_FLOW) {
                in.put(gas, -move);
            }
        }
        return new Flow(out, in);
    }

    /**
     * An open end's flow for one atmos tick.
     *
     * @param out moles per gas leaving the pipe
     * @param in moles per gas entering it
     */
    public record Flow(Map<ResourceLocation, Double> out, Map<ResourceLocation, Double> in) {
        /** The net moles leaving the pipe: negative if more enter it, and 0 if gases only pass each other. */
        public double net() {
            double net = 0;
            for (double moles : out.values()) {
                net += moles;
            }
            for (double moles : in.values()) {
                net -= moles;
            }
            return net;
        }
    }
}
