package io.github.west3436.zas.compat.pneumaticcraft;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Heat between zones and PneumaticCraft's heat blocks (COMPAT-013): a hot Compressed Iron Block or a
 * working Advanced Air Compressor warms the room it stands in, and a cold Vortex Tube end cools it,
 * by exactly what the block loses or gains.
 *
 * <ul>
 *   <li><b>Which blocks.</b> Any block entity that exposes PneumaticCraft's own heat exchanger
 *       ({@code PNCCapabilities.HEAT_EXCHANGER_BLOCK}), as {@link PncBlocks} learns them. Each atmos
 *       tick only those a zone touches ({@link Atmospheres#zoneTouches}) are looked at further.
 *   <li><b>Which gas.</b> Each side on which the block exposes an exchanger that PneumaticCraft has
 *       connected to something, and whose neighbouring position holds a cell of a zone, is a face
 *       shared with that zone: the zone of the cell that touches the middle of the face. Open air,
 *       a paused zone, and gas too thin to carry heat (HEAT-008) take no part.
 *   <li><b>How much.</b> {@link HeatExchange}: each face conducts the server's {@code [heat]
 *       face_conductance}, in series with the exchanger's own thermal resistance, and the heat that
 *       flows in an atmos tick is the exact course of the exchanger and the zone's gas toward one
 *       temperature, so it never passes it (HEAT-006). PneumaticCraft's heat is turned into joules
 *       by {@code [heat] joules_per_heat_unit}.
 *   <li><b>Once.</b> Faces are gathered by exchanger before anything moves, so a block whose sides
 *       all expose one exchanger is one body with many faces, and its heat capacity is counted
 *       once; a Vortex Tube's hot and cold ends are two. Flows within one atmos tick are worked out
 *       one after another on running temperatures, so several blocks in one room can't together
 *       carry its gas past any of them.
 *   <li><b>Conserved.</b> Heat that enters a zone leaves the exchanger at once ({@code addHeat}) and
 *       goes through {@link HeatMutations#add}. Heat that leaves a zone is asked for through {@link
 *       HeatMutations#remove} and reaches the exchanger when it is delivered, in the amount
 *       delivered. A zone with a mutation of ours still on its way sits the step out, so nothing is
 *       worked out twice from one reading, and makes the time up when it next takes part.
 * </ul>
 *
 * <p><b>PneumaticCraft's own loss.</b> PneumaticCraft has each heat block lose heat to the air
 * beside it, as to a store of fixed temperature, and that heat goes nowhere. The exchange leaves
 * that as it is, and adds none of it to a zone, so no joule is counted twice; a block in a zone has
 * both ways out.
 *
 * <p>Server thread.
 */
public final class PncHeat {
    /** Who the adapter's heat mutations are made by. */
    public static final SourceId SOURCE = SourceId.of("zas", "pneumaticcraft_heat");

    private static final Direction[] SIDES = Direction.values();

    /** The most steps a zone that sat out makes up for when it next takes part. */
    private static final int MOST_STEPS_MADE_UP = 10;

    /** PneumaticCraft treats an exchanger of less thermal capacity than this as having none. */
    private static final double LEAST_CAPACITY = 0.1;

    /** What the exchange keeps of each level. */
    private static final Map<ServerLevel, State> LEVELS = new WeakHashMap<>();

    private PncHeat() {}

    /** What the exchange keeps of one level between steps. */
    private static final class State {
        /** How many mutations of ours each zone still has on their way. */
        final Map<ZoneId, Integer> onItsWay = new HashMap<>();

        /** How many steps each of those zones has sat out. */
        Map<ZoneId, Integer> satOut = new HashMap<>();

        /** The game time of the last step, or -1. */
        long stepped = -1;
    }

    /**
     * Heat that one step sent to a zone or asked of it.
     *
     * @param zone the zone
     * @param joules the heat, in J: above 0 into the zone, below 0 out of it
     * @param result what ZAS³ made of it, complete once the atmos tick that applies it has been
     */
    public record Flow(ZoneId zone, double joules, CompletableFuture<HeatResult> result) {}

    /** A level has ticked. */
    static void levelTicked(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ticked(level, level.getGameTime());
        }
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void forget() {
        LEVELS.clear();
    }

    /**
     * Once an atmos tick, a level's heat blocks exchange heat with their zones, for the length of an
     * atmos tick. A frozen game's time stands still, and so does the exchange.
     *
     * @param level the level, which has just ticked
     * @param gameTime the level's game time
     * @return the heat sent to each zone and asked of it; empty when this isn't the tick for it
     */
    @ServerThread
    public static List<Flow> ticked(ServerLevel level, long gameTime) {
        int period = Atmospheres.periodTicks();
        if (period <= 0 || gameTime % period != 0) {
            return List.of();
        }
        State state = LEVELS.computeIfAbsent(level, l -> new State());
        if (gameTime == state.stepped) {
            return List.of();
        }
        state.stepped = gameTime;
        return step(level, period / 20.0);
    }

    /** One exchanger, with the faces it shares with each zone. */
    private static final class Body {
        final BlockEntity owner;
        final IHeatExchangerLogic logic;
        final double capacity;
        double temperature;
        final Map<ZoneId, Double> conductance = new LinkedHashMap<>();

        Body(BlockEntity owner, IHeatExchangerLogic logic, double capacity, double temperature) {
            this.owner = owner;
            this.logic = logic;
            this.capacity = capacity;
            this.temperature = temperature;
        }
    }

    /** A zone's gas as the step runs, or that it takes no part in it. */
    private static final class Gas {
        final Vec3 point;
        final double capacity;
        final boolean takesPart;
        final double seconds;
        double temperature;
        double added;

        Gas(Vec3 point, GasMixture gas, boolean takesPart, double seconds) {
            this.point = point;
            this.capacity = GasProperties.heatCapacity(gas.moles());
            this.takesPart = takesPart && capacity > 0;
            this.seconds = seconds;
            this.temperature = gas.temperature();
        }
    }

    /**
     * Exchanges heat between a level's heat blocks and the zones they touch, for a length of time.
     * {@link #ticked} calls it once an atmos tick; GameTests call it directly.
     *
     * @param level the level
     * @param seconds the time the exchange covers
     * @return the heat sent to each zone and asked of it, for whoever wants to follow it
     */
    @ServerThread
    public static List<Flow> step(ServerLevel level, double seconds) {
        State state = LEVELS.computeIfAbsent(level, l -> new State());
        double face = PncSettings.faceConductance();
        List<BlockEntity> blocks = PncBlocks.heat(level);
        if (!(seconds > 0) || !(face > 0) || blocks.isEmpty()) {
            return List.of();
        }
        double joulesPerUnit = PncSettings.joulesPerHeatUnit();
        double nearVacuum = Atmospheres.nearVacuumDensity();

        // Gather: every exchanger with the faces it shares with each zone.
        Map<IHeatExchangerLogic, Body> bodies = new IdentityHashMap<>();
        List<Body> order = new ArrayList<>();
        Map<ZoneId, Gas> zones = new HashMap<>();
        Map<ZoneId, Integer> satOut = new HashMap<>();
        for (BlockEntity entity : blocks) {
            BlockPos pos = entity.getBlockPos();
            if (!Atmospheres.zoneTouches(level, pos)) {
                continue;
            }
            BlockState blockState = entity.getBlockState();
            for (Direction side : SIDES) {
                IHeatExchangerLogic logic =
                        level.getCapability(PNCCapabilities.HEAT_EXCHANGER_BLOCK, pos, blockState, entity, side);
                if (!PncBlocks.own(logic) || !logic.isSideConnected(side)) {
                    continue;
                }
                // The position's own answer first: a full block holds no cell, and a point in one
                // would be answered by whatever cell is nearest, through the block.
                BlockPos next = pos.relative(side);
                if (!level.isLoaded(next) || Atmospheres.at(level, next).state() != AtmosphereState.ZONED) {
                    continue;
                }
                Vec3 across = PncLeaks.across(pos, side);
                ZoneId zone = zone(state, zones, satOut, Atmospheres.at(level, across), across, seconds, nearVacuum);
                if (zone == null) {
                    continue;
                }
                Body body = bodies.get(logic);
                if (body == null) {
                    double units = logic.getThermalCapacity();
                    double temperature = logic.getTemperature();
                    if (!(units >= LEAST_CAPACITY) || !Double.isFinite(units) || !Double.isFinite(temperature)) {
                        continue;
                    }
                    body = new Body(entity, logic, units * joulesPerUnit, temperature);
                    bodies.put(logic, body);
                    order.add(body);
                }
                double conductance = HeatExchange.conductance(face, joulesPerUnit, logic.getThermalResistance());
                if (conductance > 0) {
                    body.conductance.merge(zone, conductance, Double::sum);
                }
            }
        }
        state.satOut = satOut;

        // Move: each exchanger with each of its zones in turn, on running temperatures.
        List<Flow> flows = new ArrayList<>();
        for (Body body : order) {
            double lost = 0;
            for (Map.Entry<ZoneId, Double> shared : body.conductance.entrySet()) {
                Gas gas = zones.get(shared.getKey());
                if (!gas.takesPart) {
                    continue;
                }
                double joules = HeatExchange.exchange(
                        body.capacity, body.temperature, gas.capacity, gas.temperature, shared.getValue(), gas.seconds);
                if (joules == 0) {
                    continue;
                }
                body.temperature -= joules / body.capacity;
                gas.temperature += joules / gas.capacity;
                if (joules > 0) {
                    lost += joules;
                    gas.added += joules;
                } else {
                    // The exchanger gains what the zone delivers, when it delivers it. If the block
                    // has gone by then, the heat goes back where it came from.
                    Vec3 point = gas.point;
                    CompletableFuture<HeatResult> result = HeatMutations.remove(level, point, -joules, SOURCE)
                            .thenApply(delivered -> {
                                if (delivered.joules() > 0) {
                                    if (body.owner.isRemoved()) {
                                        HeatMutations.add(level, point, delivered.joules(), SOURCE);
                                    } else {
                                        body.logic.addHeat(delivered.joules() / joulesPerUnit);
                                    }
                                }
                                return delivered;
                            });
                    flows.add(follow(state, new Flow(shared.getKey(), joules, result)));
                }
            }
            if (lost > 0) {
                body.logic.addHeat(-lost / joulesPerUnit);
            }
        }
        zones.forEach((zone, gas) -> {
            if (gas.added > 0) {
                flows.add(follow(
                        state, new Flow(zone, gas.added, HeatMutations.add(level, gas.point, gas.added, SOURCE))));
            }
        });
        return flows;
    }

    /**
     * The zone a sample is of, if it takes part in the exchange: it isn't paused and its gas isn't
     * too thin to carry heat. Null otherwise. A zone with heat of ours still on its way to it sits
     * this step out: it is counted, once a step, and makes the time up when it next takes part.
     */
    private static @Nullable ZoneId zone(
            State state,
            Map<ZoneId, Gas> zones,
            Map<ZoneId, Integer> satOut,
            AtmosphereSample sample,
            Vec3 point,
            double seconds,
            double nearVacuum) {
        if (sample.state() != AtmosphereState.ZONED || sample.zone().isEmpty()) {
            return null;
        }
        ZoneId zone = sample.zone().get();
        Gas gas = zones.get(zone);
        if (gas == null) {
            int before = state.satOut.getOrDefault(zone, 0);
            boolean waits = state.onItsWay.containsKey(zone);
            if (waits) {
                satOut.put(zone, Math.min(MOST_STEPS_MADE_UP, before + 1));
            }
            GasMixture mixture = sample.mixture();
            boolean thin = thin(mixture.totalMoles(), mixture.volume(), nearVacuum);
            gas = new Gas(point, mixture, !waits && !thin, seconds * (1 + before));
            zones.put(zone, gas);
        }
        return gas.takesPart ? zone : null;
    }

    /** Whether gas is too thin to carry heat (HEAT-008): below the near-vacuum density, or empty. */
    static boolean thin(double moles, double litres, double nearVacuum) {
        return !(moles > 0) || !(litres > 0) || moles < nearVacuum * litres;
    }

    /** Keeps a zone out of later steps until the heat sent to it, or asked of it, has landed. */
    private static Flow follow(State state, Flow flow) {
        state.onItsWay.merge(flow.zone(), 1, Integer::sum);
        flow.result()
                .whenComplete((result, failure) ->
                        state.onItsWay.computeIfPresent(flow.zone(), (zone, n) -> n > 1 ? n - 1 : null));
        return flow;
    }
}
