package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.particle.GasJets;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pipe leaks (CONT-005, CONT-168). When a network's pressure is more than {@code [pipes] leak_kpa}
 * over a segment's cell, and it doesn't burst (CONT-108), it leaks there: one such segment, chosen at
 * random, lets gas into its cell each atmos tick until the network is back under the threshold. Off
 * by default ({@code leak_kpa = 0}).
 *
 * <p><b>How much.</b> A leak is a small open end: it lets out {@code [pipes] leak_rate} (1 %) a second
 * of what would even the network out with its cell, gas by gas, by the open end's rule ({@link
 * PipeBlockEntity#openEndFlow}), so it leaks faster the higher the pressure difference. Gas only
 * leaves through it: none comes in. What the cell doesn't take comes back to the network, so no mole
 * is lost.
 *
 * <p><b>Where.</b> Segments leak as they burst: pipes and caps, bare or in a casing, whose cell is
 * the one beside the casing (API-015). A device's buffer doesn't leak. Once a segment leaks, it goes
 * on leaking while it is over the threshold; then a new one is chosen. The leak is the network's, not
 * saved: a rebuilt network chooses again.
 *
 * <p><b>What shows.</b> A level jet of what left, the way the leak chose as it began, and the pipe's
 * hiss as it begins and every other atmos tick while it lasts.
 *
 * <p><b>Cost.</b> One pressure test per network per atmos tick, as for bursts. Above the threshold
 * it reads the leaking segment's cell, or up to {@value #TRIES} members' at random while it finds
 * one, so a large network costs no more than a small one.
 */
public final class PipeLeak {
    /** The default share a second of what would even the network out with its cell that leaks. */
    public static final double DEFAULT_RATE = 0.01;

    /** Who a leak's gas is released by, for the conservation ledger. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "pipe_leak");

    /** How many members, at random, a network over the threshold tries per atmos tick for a leak. */
    static final int TRIES = 4;

    /** How often a leak hisses, in atmos ticks. */
    static final int HISS_EVERY = 2;

    /** Where a network leaks: a segment, the way its jet goes, and how long it has leaked. */
    static final class Spot {
        final PipeNodeEntity node;
        final float angle;
        int steps;

        Spot(PipeNodeEntity node, float angle) {
            this.node = node;
            this.angle = angle;
        }
    }

    private PipeLeak() {}

    /** The leak threshold, kPa: {@code [pipes] leak_kpa}; 0 when leaks are off. */
    public static double threshold() {
        return AtmosServerConfig.get(AtmosServerConfig.PIPE_LEAK_KPA);
    }

    /** The share a second of what would even it out that a leak lets out: {@code [pipes] leak_rate}. */
    public static double rate() {
        return AtmosServerConfig.get(AtmosServerConfig.PIPE_LEAK_RATE);
    }

    /**
     * What a leak lets out in one atmos tick, per gas: the open end's outward flow at the leak's
     * ratio.
     *
     * @param pipe moles per gas in the network
     * @param pipeVolume the network's volume, L
     * @param cell the reading of the leaking segment's cell
     * @param ratePerSecond the leak's rate, a share a second
     * @param seconds the step's length
     * @return moles per gas leaving the network; empty if nothing would
     */
    public static Map<ResourceLocation, Double> flow(
            Map<ResourceLocation, Double> pipe,
            double pipeVolume,
            AtmosphereSample cell,
            double ratePerSecond,
            double seconds) {
        double r = Math.max(0, Math.min(1, ratePerSecond));
        double ratio = 1 - Math.pow(1 - r, seconds);
        return PipeBlockEntity.openEndFlow(
                        pipe, pipeVolume, cell.mixture(), cell.state() == AtmosphereState.AMBIENT, ratio)
                .out();
    }

    /**
     * A network's leak for this atmos tick, after its burst check: finds a leaking segment, if the
     * network is over the threshold, and lets its gas out.
     *
     * @param level the level
     * @param net the network
     * @return the moles that left; 0 if it didn't leak
     */
    static double step(ServerLevel level, PipeNetwork net) {
        double threshold = threshold();
        double pressure = net.gas().pressure();
        if (!(threshold > 0) || pressure <= threshold) {
            net.leak(null);
            return 0;
        }
        Spot spot = net.leak();
        AtmosphereSample cell = spot == null ? null : over(level, spot.node, pressure, threshold);
        if (cell == null) {
            spot = null;
            List<PipeNode> members = net.members();
            RandomSource random = level.getRandom();
            for (int i = 0; i < TRIES && spot == null && !members.isEmpty(); i++) {
                PipeNode m = members.get(random.nextInt(members.size()));
                cell = over(level, m, pressure, threshold);
                if (cell != null) {
                    spot = new Spot((PipeNodeEntity) m, random.nextFloat() * Mth.TWO_PI);
                }
            }
            net.leak(spot);
            if (spot == null) {
                return 0;
            }
        }
        Map<ResourceLocation, Double> out =
                flow(net.gas().moles(), net.gas().volume(), cell, rate(), DeviceTicks.SECONDS);
        if (out.isEmpty()) {
            return 0;
        }
        GasParcel gas = net.gas().take(out);
        PipeNodeEntity node = spot.node;
        BlockPos pos = node.getBlockPos();
        Vec3 at = Vec3.atCenterOf(pos);
        GasMutations.add(level, at, gas, SOURCE).whenComplete((result, failure) -> {
            if (failure == null) {
                node.deliver(new GasParcel(ZonePort.untaken(gas, result), gas.temperature()));
            }
        });
        if (spot.steps++ % HISS_EVERY == 0) {
            AtmosSounds.play(level, pos, AtmosSounds.PIPE_LEAK);
        }
        GasJets.leak(level, at, gas.totalMoles(), spot.angle);
        return gas.totalMoles();
    }

    /** A member's cell if the member can leak and the network is over the threshold there; else null. */
    private static @Nullable AtmosphereSample over(
            ServerLevel level, PipeNode member, double pressure, double threshold) {
        AtmosphereSample cell = PipeBurst.cell(level, member);
        return cell != null && PipeBurst.exceeds(pressure, cell.mixture().pressure(), threshold) ? cell : null;
    }
}
