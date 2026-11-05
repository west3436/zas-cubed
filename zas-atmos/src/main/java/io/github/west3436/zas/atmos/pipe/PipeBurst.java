package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.particle.GasJets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Pipe bursts (CONT-108, D-59). When a network's pressure is more than 100 atm over a segment's cell,
 * one such segment, chosen at random, is destroyed and the network's whole contents go into its cell.
 * A burst doesn't explode.
 *
 * <p>Pipes and caps are segments ({@link PipeNodeEntity#isSegment()}); device buffers aren't, so a
 * network with no pipe never bursts. Cost: one pressure test per network per atmos tick, and the
 * cells are read only above 100 atm, since no cell's pressure is below zero.
 */
public final class PipeBurst {
    /** The burst threshold over a segment's cell, kPa: 100 atm (D-59). */
    public static final double THRESHOLD_KPA = 10_132.5;

    /** Who a burst's gas is released by, for the conservation ledger. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "pipe_burst");

    private PipeBurst() {}

    /**
     * Whether a network's pressure is over a cell's by more than the threshold.
     *
     * @param networkKPa the network's pressure
     * @param cellKPa the cell's pressure; NaN never exceeds
     * @return true if a segment in that cell may burst
     */
    public static boolean exceeds(double networkKPa, double cellKPa) {
        return networkKPa - cellKPa > THRESHOLD_KPA;
    }

    /**
     * Chooses the member to burst, at random among those over the threshold.
     *
     * @param members the network's members
     * @param networkKPa the network's pressure
     * @param cellKPa a member's cell pressure, or NaN for a member that can't burst
     * @param random the source of the choice
     * @param <N> the members' type
     * @return the member, or null if none is over the threshold
     */
    static <N> N choose(List<N> members, double networkKPa, ToDoubleFunction<N> cellKPa, RandomSource random) {
        List<N> over = new ArrayList<>();
        for (N member : members) {
            if (exceeds(networkKPa, cellKPa.applyAsDouble(member))) {
                over.add(member);
            }
        }
        return over.isEmpty() ? null : over.get(random.nextInt(over.size()));
    }

    /**
     * Bursts a segment of a network if it is overpressured: takes all its gas, destroys the segment
     * and releases the gas into the segment's cell.
     *
     * @param level the level
     * @param net the network
     * @return whether a segment burst
     */
    static boolean check(ServerLevel level, PipeNetwork net) {
        double pressure = net.gas().pressure();
        if (pressure <= THRESHOLD_KPA) {
            return false;
        }
        PipeNode chosen = choose(net.members(), pressure, m -> cellPressure(level, m), level.getRandom());
        if (chosen == null) {
            return false;
        }
        BlockPos pos = ((PipeNodeEntity) chosen).getBlockPos();
        GasParcel all = net.gas().drain();
        level.destroyBlock(pos, false);
        Vec3 at = Vec3.atCenterOf(pos);
        GasMutations.add(level, at, all, SOURCE);
        GasJets.burst(level, at, all.totalMoles());
        return true;
    }

    /**
     * The pressure of a member's cell, or NaN if it can't burst now: it isn't a segment, its chunk
     * has left full status, or its cell is paused or unresolved, whose reading isn't its real
     * pressure.
     */
    private static double cellPressure(ServerLevel level, PipeNode member) {
        if (!(member instanceof PipeNodeEntity node) || !node.isSegment() || !level.isLoaded(node.getBlockPos())) {
            return Double.NaN;
        }
        AtmosphereSample cell = Atmospheres.at(level, node.getBlockPos());
        return cell.state() == AtmosphereState.ZONED || cell.state() == AtmosphereState.AMBIENT
                ? cell.mixture().pressure()
                : Double.NaN;
    }
}
