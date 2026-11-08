package io.github.west3436.zas.atmos.radiator;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * A network's radiator pipe exchanging heat with what is around it, once per atmos tick, in the
 * network's own atmos tick (HEAT-020, HEAT-021). {@link RadiatorMath} holds the arithmetic.
 *
 * <p>Each segment reads its own cell through the API, and couples the network to it:
 *
 * <ul>
 *   <li><b>A zone, or the open air, with gas in it</b> (HEAT-020): its cell's share of that gas, at
 *       the configured conductance. Heat for a zone goes through API-013's heat mutation (CONT-095),
 *       one per zone however many segments are in it. Heat for the open air goes through it too, one
 *       per segment, so a sealed room with no zone yet forms one (CORE-055); heat under {@link
 *       #MIN_JOULES} isn't sent, as the ambient takes or gives any amount unchanged (HEAT-010).
 *   <li><b>The open air of a vacuum, or of gas too thin to carry heat</b> (HEAT-021): the ambient's
 *       temperature, at the radiative coupling. The heat leaves the world, so nothing is sent.
 *   <li><b>A zone too thin to carry heat, a paused zone, an unresolved cell, or a reading the cell
 *       watch doesn't trust</b>: nothing.
 * </ul>
 *
 * <p>"Too thin" is the line below which zones stop conducting, read from the API each atmos tick
 * (HEAT-008, API-064).
 *
 * <p>Heat leaves or enters the network at once. Heat added to a zone leaves the caller when it is
 * sent; heat drawn from a zone arrives later, and the API may deliver less than asked at the
 * temperature floor, so the network gives back the difference when the result comes (API-013). Only
 * heat, never gas, moves, so the conservation ledger has nothing to explain (CORE-073).
 */
public final class RadiatorExchange {
    /** Who a radiator's heat mutations are made by. */
    public static final SourceId SOURCE = SourceId.of(ZasAtmosMod.MOD_ID, "radiator_pipe");

    /** The least heat a segment sends to the open air in an atmos tick, J. */
    public static final double MIN_JOULES = 1;

    private RadiatorExchange() {}

    /**
     * A network's radiator pipe exchanges heat for one atmos tick. A network with no radiator pipe,
     * or no gas, does nothing.
     *
     * @param level the level
     * @param net the network
     */
    public static void step(ServerLevel level, PipeNetwork net) {
        List<PipeNode> radiators = net.radiators();
        if (radiators.isEmpty()) {
            return;
        }
        GasStore gas = net.gas();
        double c = gas.heatCapacity();
        if (!(c > 0)) {
            return;
        }
        double t = gas.temperature();
        double seconds = AtmosTicks.SECONDS;
        double conductance = AtmosServerConfig.get(AtmosServerConfig.RADIATOR_CONDUCTANCE);
        double emissivity = AtmosServerConfig.get(AtmosServerConfig.RADIATOR_EMISSIVITY);
        double nearVacuum = Atmospheres.nearVacuumDensity();
        int n = radiators.size();
        double[] coupling = new double[n];
        double[] body = new double[n];
        AtmosphereSample[] cells = new AtmosphereSample[n];
        double couplings = 0;
        double weighted = 0;
        for (int i = 0; i < n; i++) {
            if (!(radiators.get(i) instanceof RadiatorPipeBlockEntity segment)
                    || segment.isRemoved()
                    || !level.isLoaded(segment.getBlockPos())) {
                continue;
            }
            AtmosphereSample cell = Atmospheres.at(level, segment.getBlockPos());
            if (!segment.trusts(cell)) {
                continue;
            }
            GasMixture air = cell.mixture();
            boolean thin = RadiatorMath.nearVacuum(air.totalMoles(), air.volume(), nearVacuum);
            double a;
            if (!thin && (cell.state() == AtmosphereState.ZONED || cell.state() == AtmosphereState.AMBIENT)) {
                a = RadiatorMath.convection(
                        conductance,
                        seconds,
                        RadiatorMath.cellCapacity(GasProperties.heatCapacity(air.moles()), air.volume()));
                cells[i] = cell;
            } else if (cell.state() == AtmosphereState.AMBIENT) {
                a = RadiatorMath.radiation(emissivity, seconds, t, air.temperature());
            } else {
                continue;
            }
            coupling[i] = a;
            body[i] = air.temperature();
            couplings += a;
            weighted += a * body[i];
        }
        if (!(couplings > 0)) {
            return;
        }
        double after = RadiatorMath.settle(c, t, couplings, weighted);
        gas.setTemperature(Math.max(0, after));

        Map<ZoneId, Send> zones = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            AtmosphereSample cell = cells[i];
            if (cell == null || coupling[i] == 0) {
                continue; // nothing, or radiated away
            }
            RadiatorPipeBlockEntity segment = (RadiatorPipeBlockEntity) radiators.get(i);
            double q = coupling[i] * (after - body[i]);
            if (cell.state() == AtmosphereState.ZONED) {
                zones.computeIfAbsent(cell.zone().orElseThrow(), z -> new Send(segment)).joules += q;
            } else if (Math.abs(q) >= MIN_JOULES) {
                send(level, segment, q);
            }
        }
        zones.values().forEach(s -> send(level, s.at, s.joules));
    }

    /** A zone's heat, summed over the segments in it, and the segment it is sent from. */
    private static final class Send {
        final RadiatorPipeBlockEntity at;
        double joules;

        Send(RadiatorPipeBlockEntity at) {
            this.at = at;
        }
    }

    /**
     * Sends heat into a segment's cell, or draws it out, through the API. What a draw doesn't deliver
     * goes back out of the network the segment is in when the result comes.
     */
    private static void send(ServerLevel level, RadiatorPipeBlockEntity segment, double joules) {
        Vec3 point = Vec3.atCenterOf(segment.getBlockPos());
        if (joules > 0) {
            HeatMutations.add(level, point, joules, SOURCE);
        } else if (joules < 0) {
            double wanted = -joules;
            HeatMutations.remove(level, point, wanted, SOURCE)
                    .thenAccept(result -> segment.giveBack(wanted - result.joules()));
        }
    }
}
