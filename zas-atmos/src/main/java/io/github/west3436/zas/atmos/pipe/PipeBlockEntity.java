package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
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

    /**
     * The atmos ticks an open end waits after it loads before it moves gas. A chunk's zones reach
     * the framework's published state a little after the chunk loads, and until then an enclosed
     * cell can read as the ambient (a framework bug, #153; this works around it until it's fixed).
     */
    public static final int SETTLE_STEPS = 3;

    private final ZonePort port = new ZonePort(OPEN_END);
    private int settling = SETTLE_STEPS;
    private AtmosphereState lastState;
    private Optional<ZoneId> lastZone = Optional.empty();

    public PipeBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.PIPE.get(), pos, state, PipeVolumes.PIPE);
    }

    /** Whether the pipe has an open end: fewer than two connections. */
    public boolean isOpen() {
        return PipeBlock.connections(getBlockState()) < 2;
    }

    @Override
    public boolean readyToStep() {
        return !port.busy();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        settling = SETTLE_STEPS;
        lastState = null;
        lastZone = Optional.empty();
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        boolean settled = settling == 0;
        if (!settled) {
            settling--;
        }
        // Rebuild a dissolved network now, so its gas is shared over its new members even where
        // no open end needs it.
        network();
        if (!isOpen()) {
            return;
        }
        AtmosphereSample sample = Atmospheres.at(level, worldPosition);
        // The cell must read the same twice running, so a passing reading (such as the ambient
        // before a loading chunk's zones are published) moves nothing.
        boolean steady = sample.state() == lastState && sample.zone().equals(lastZone);
        lastState = sample.state();
        lastZone = sample.zone();
        if (!settled || !steady) {
            return;
        }
        if (sample.state() != AtmosphereState.ZONED && sample.state() != AtmosphereState.AMBIENT) {
            return; // paused or unresolved: wait until the cell is known
        }
        GasStore pipe = network().gas();
        Flow flow = openEndFlow(
                pipe.moles(),
                pipe.volume(),
                sample.mixture(),
                sample.state() == AtmosphereState.AMBIENT,
                1 - Math.pow(1 - OPEN_END_RATIO, AtmosTicks.SECONDS));
        if (flow.out().isEmpty() && flow.in().isEmpty()) {
            return;
        }
        Vec3 point = Vec3.atCenterOf(worldPosition);
        GasParcel out = pipe.take(flow.out());
        if (flow.in().isEmpty()) {
            port.add(level, point, out, r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())));
        } else if (out.isEmpty()) {
            port.remove(level, point, flow.in(), r -> deliver(r.moved()));
        } else {
            port.exchange(
                    level,
                    point,
                    out,
                    flow.in(),
                    r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())),
                    r -> deliver(r.moved()));
        }
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
    public record Flow(Map<ResourceLocation, Double> out, Map<ResourceLocation, Double> in) {}
}
