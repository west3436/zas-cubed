package io.github.west3436.zas.compat.mekanism;

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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.heat.IMekanismHeatHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Heat between zones and the blocks that expose Mekanism's heat capability (MEK-040): a Resistive
 * Heater warms the room it stands in, and a cold block cools it, by exactly what the block loses or
 * gains.
 *
 * <ul>
 *   <li><b>Which blocks.</b> Any block entity that exposes {@code mekanism:heat_handler}. They are
 *       learned as their chunk loads and as a block with a block entity is set, never by searching
 *       the world: a chunk's block entities are looked at once when it loads, and a set block
 *       costs one flag read unless it has a block entity, when it and the block entities next to
 *       it are looked at. Each atmos tick only the blocks learned are visited, at a handful of
 *       lookups each.
 *   <li><b>Which gas.</b> Each side on which the block exposes the capability, and whose
 *       neighbouring position holds a cell of a zone, is a face shared with that zone: the zone of
 *       the cell that touches the middle of the face. A side against a full block is no face,
 *       whatever is beyond it. A Thermodynamic Conductor exposes the capability only on the sides
 *       it connects on, so those are its faces. Open air, a paused zone, and gas too thin to carry
 *       heat (HEAT-008) take no part: Mekanism has its own loss to the open air.
 *   <li><b>How much.</b> {@link HeatFlow}: each face conducts the server's {@code [heat]
 *       face_conductance}, in series with the capacitor's own insulation, and the heat that flows
 *       in an atmos tick is the exact course of the capacitor and the zone's gas toward one
 *       temperature, so it never passes it (HEAT-006). Mekanism's heat is turned into joules by
 *       {@code [heat] joules_per_heat_unit}.
 *   <li><b>Once.</b> Faces are gathered by capacitor before anything moves, so a multiblock whose
 *       casing blocks all expose one capacitor is one body with many faces, and its heat capacity
 *       is counted once. Flows within one atmos tick are worked out one after another on running
 *       temperatures, so several blocks in one room can't together carry its gas past any of them.
 *   <li><b>Conserved.</b> Heat that enters a zone leaves the capacitor at once and goes through
 *       {@link HeatMutations#add}. Heat that leaves a zone is asked for through {@link
 *       HeatMutations#remove} and reaches the capacitor when it is delivered, in the amount
 *       delivered.
 *   <li><b>Never on a stale reading.</b> ZAS³ applies a mutation at a later atmos tick. A zone
 *       with a mutation of ours still on its way sits the step out, and makes the time up when it
 *       next takes part. Heat is written straight into Mekanism's capacitors ({@link
 *       IHeatCapacitor#setHeat}), not handed over for the block's next tick to take in, so a
 *       capacitor always reads what it holds, whether or not its chunk ticks. So nothing is worked
 *       out twice from one reading. Only a block of another mod's, which exposes the capability
 *       without Mekanism's capacitors, is handed its heat ({@link IHeatHandler#handleHeat}).
 * </ul>
 *
 * <p><b>Mekanism's own loss.</b> A Mekanism heat block also loses heat each tick to an ambient
 * temperature it reads from the biome, and that heat goes nowhere. This class leaves that as it is,
 * and adds none of it to a zone, so no joule is counted twice; a block in a zone has both ways out.
 * Replacing the biome's ambient is MEK-041.
 *
 * <p>The capability is found by its name, and capacitors through Mekanism's API, so nothing here
 * names a class of Mekanism's outside it. Server thread.
 */
public final class MekanismHeat {
    /** Who the adapter's heat mutations are made by. */
    public static final SourceId SOURCE = SourceId.of("zas", "mekanism_heat");

    /** Mekanism's heat capability on blocks, by its name: the registry gives back the one Mekanism made. */
    private static final BlockCapability<IHeatHandler, @Nullable Direction> HEAT = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "heat_handler"), IHeatHandler.class);

    private static final Direction[] SIDES = Direction.values();

    /** The most steps a zone that sat out makes up for when it next takes part. */
    private static final int MOST_STEPS_MADE_UP = 10;

    /** What is known of each level's heat blocks. Server thread only. */
    private static final Map<ServerLevel, Known> LEVELS = new WeakHashMap<>();

    private MekanismHeat() {}

    /** What is known of one level. */
    private static final class Known {
        /** The heat blocks. */
        final Set<BlockPos> blocks = new LinkedHashSet<>();

        /** Positions whose block entity hasn't been looked at yet. */
        final Set<BlockPos> waiting = new HashSet<>();

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

    private static Known known(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new Known());
    }

    // ---- Learning the blocks -------------------------------------------------------------------

    /**
     * A chunk has loaded: its block entities are looked at at the next step.
     *
     * @param event the load
     */
    public static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Map<BlockPos, BlockEntity> entities = chunk.getBlockEntities();
            if (!entities.isEmpty()) {
                known(level).waiting.addAll(entities.keySet());
            }
        }
    }

    /**
     * A block was set: if it has a block entity, it is looked at at the next step. So are the block
     * entities next to it, since what a block exposes can turn on its neighbours: a conductor exposes
     * heat on the sides it connects on.
     */
    static void blockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().hasBlockEntity() && event.getLevel() instanceof ServerLevel level) {
            Set<BlockPos> waiting = known(level).waiting;
            BlockPos pos = event.getPos();
            waiting.add(pos.immutable());
            for (Direction side : SIDES) {
                BlockPos next = pos.relative(side);
                if (level.isLoaded(next) && level.getBlockState(next).hasBlockEntity()) {
                    waiting.add(next);
                }
            }
        }
    }

    /** A level has ticked. */
    static void levelTicked(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ticked(level, level.getGameTime());
        }
    }

    /**
     * Once an atmos tick, a level's heat blocks exchange heat with their zones, for the length of
     * an atmos tick. A frozen game's time stands still, and so does the exchange.
     *
     * @param level the level, which has just ticked
     * @param gameTime the level's game time
     * @return the heat sent to each zone and asked of it; empty when this isn't the tick for it
     */
    @ServerThread
    public static List<Flow> ticked(ServerLevel level, long gameTime) {
        Known known = LEVELS.get(level);
        int period = Atmospheres.periodTicks();
        if (known == null || period <= 0 || gameTime % period != 0 || gameTime == known.stepped) {
            return List.of();
        }
        known.stepped = gameTime;
        return step(level, period / 20.0);
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void forget() {
        LEVELS.clear();
    }

    /**
     * Whether a position is among a level's heat blocks, once the positions waiting have been looked
     * at.
     *
     * @param level the level
     * @param pos the block's position
     * @return true if the block there takes part in the exchange
     */
    @ServerThread
    public static boolean knows(ServerLevel level, BlockPos pos) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return false;
        }
        admit(level, known);
        return known.blocks.contains(pos);
    }

    /** Looks at the positions waiting: those whose block entity exposes heat join the level's heat blocks. */
    private static void admit(ServerLevel level, Known known) {
        if (known.waiting.isEmpty()) {
            return;
        }
        for (BlockPos pos : known.waiting) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity != null && !entity.isRemoved() && exposesHeat(level, pos, entity)) {
                known.blocks.add(pos);
            }
        }
        known.waiting.clear();
    }

    /**
     * Whether a block entity can expose heat: any of Mekanism's own, which may expose its capacitors
     * only later, as a multiblock's casing does once it forms; or anything that exposes the
     * capability now, on a side or on none, as a conductor that connects to nothing yet does.
     */
    private static boolean exposesHeat(ServerLevel level, BlockPos pos, BlockEntity entity) {
        if (entity instanceof IMekanismHeatHandler) {
            return true;
        }
        BlockState state = entity.getBlockState();
        if (level.getCapability(HEAT, pos, state, entity, null) != null) {
            return true;
        }
        for (Direction side : SIDES) {
            if (level.getCapability(HEAT, pos, state, entity, side) != null) {
                return true;
            }
        }
        return false;
    }

    // ---- The exchange --------------------------------------------------------------------------

    /** One capacitor, or one block's capacitors, with the faces it shares with each zone. */
    private static final class Body {
        final BlockEntity owner;
        final IHeatHandler handler;

        /** Mekanism's capacitors behind the handler, or null for a block of another mod's. */
        final @Nullable List<IHeatCapacitor> capacitors;

        final double capacity;

        /** Its temperature as the step's flows are worked out. */
        double temperature;

        final Map<ZoneId, Double> conductance = new LinkedHashMap<>();

        Body(
                BlockEntity owner,
                IHeatHandler handler,
                @Nullable List<IHeatCapacitor> capacitors,
                double capacity,
                double temperature) {
            this.owner = owner;
            this.handler = handler;
            this.capacitors = capacitors;
            this.capacity = capacity;
            this.temperature = temperature;
        }

        /** Gives the body heat, in Mekanism's units; less than 0 takes it. */
        void give(double heat) {
            if (capacitors == null) {
                handler.handleHeat(heat);
            } else if (capacitors.size() == 1) {
                IHeatCapacitor only = capacitors.getFirst();
                only.setHeat(only.getHeat() + heat);
            } else {
                // Shared out by heat capacity, as Mekanism shares heat a block is handed.
                double total = 0;
                for (IHeatCapacitor capacitor : capacitors) {
                    total += capacitor.getHeatCapacity();
                }
                for (IHeatCapacitor capacitor : capacitors) {
                    capacitor.setHeat(capacitor.getHeat() + heat * capacitor.getHeatCapacity() / total);
                }
            }
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
     * {@link #ticked} calls it once an atmos tick.
     *
     * @param level the level
     * @param seconds the time the exchange covers
     * @return the heat sent to each zone and asked of it, for whoever wants to follow it
     */
    @ServerThread
    public static List<Flow> step(ServerLevel level, double seconds) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return List.of();
        }
        admit(level, known);
        double face = MekanismSettings.heatFaceConductance();
        if (known.blocks.isEmpty() || !(face > 0) || !(seconds > 0)) {
            return List.of();
        }
        double joulesPerUnit = MekanismSettings.joulesPerHeatUnit();
        double nearVacuum = Atmospheres.nearVacuumDensity();

        // Gather: every capacitor with the faces it shares with each zone.
        Map<Object, Body> bodies = new IdentityHashMap<>();
        List<Body> order = new ArrayList<>();
        Map<ZoneId, Gas> zones = new HashMap<>();
        Map<ZoneId, Integer> satOut = new HashMap<>();
        Iterator<BlockPos> blocks = known.blocks.iterator();
        while (blocks.hasNext()) {
            BlockPos pos = blocks.next();
            if (!level.isLoaded(pos)) {
                blocks.remove(); // learned again when its chunk loads
                continue;
            }
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity == null || entity.isRemoved()) {
                blocks.remove();
                continue;
            }
            IMekanismHeatHandler own = entity instanceof IMekanismHeatHandler h ? h : null;
            if (own != null && !own.canHandleHeat()) {
                continue;
            }
            BlockState state = entity.getBlockState();
            for (Direction side : SIDES) {
                IHeatHandler handler = level.getCapability(HEAT, pos, state, entity, side);
                if (handler == null || handler.getHeatCapacitorCount() == 0) {
                    continue;
                }
                // The position's own answer first: a full block holds no cell, and a point in one
                // would be answered by whatever cell is nearest, through the block.
                if (Atmospheres.at(level, pos.relative(side)).state() != AtmosphereState.ZONED) {
                    continue;
                }
                Vec3 across = across(pos, side);
                ZoneId zone = zone(known, zones, satOut, Atmospheres.at(level, across), across, seconds, nearVacuum);
                if (zone == null) {
                    continue;
                }
                List<IHeatCapacitor> capacitors = own == null ? null : own.getHeatCapacitors(side);
                // One capacitor is one body, however many blocks of a multiblock expose it.
                Object key = capacitors != null && capacitors.size() == 1 ? capacitors.getFirst() : entity;
                Body body = bodies.get(key);
                if (body == null) {
                    double capacity = handler.getTotalHeatCapacity() * joulesPerUnit;
                    double temperature = handler.getTotalTemperature();
                    if (!(capacity > 0) || !Double.isFinite(capacity) || !Double.isFinite(temperature)) {
                        continue;
                    }
                    body = new Body(entity, handler, capacitors, capacity, temperature);
                    bodies.put(key, body);
                    order.add(body);
                }
                double inverse = handler.getTotalInverseConduction();
                if (own != null) {
                    inverse += own.getTotalInverseInsulation(side);
                }
                body.conductance.merge(zone, HeatFlow.conductance(face, body.capacity, inverse), Double::sum);
            }
        }
        known.satOut = satOut;

        // Move: each capacitor with each of its zones in turn, on running temperatures.
        List<Flow> flows = new ArrayList<>();
        for (Body body : order) {
            double lost = 0;
            for (Map.Entry<ZoneId, Double> shared : body.conductance.entrySet()) {
                Gas gas = zones.get(shared.getKey());
                double joules = HeatFlow.exchange(
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
                    // The capacitor gains what the zone delivers, when it delivers it. If the block
                    // has gone by then, the heat goes back where it came from.
                    Vec3 point = gas.point;
                    CompletableFuture<HeatResult> result = HeatMutations.remove(level, point, -joules, SOURCE)
                            .thenApply(delivered -> {
                                if (delivered.joules() > 0) {
                                    if (body.owner.isRemoved()) {
                                        HeatMutations.add(level, point, delivered.joules(), SOURCE);
                                    } else {
                                        body.give(delivered.joules() / joulesPerUnit);
                                    }
                                }
                                return delivered;
                            });
                    flows.add(follow(known, new Flow(shared.getKey(), joules, result)));
                }
            }
            if (lost > 0) {
                body.give(-lost / joulesPerUnit);
            }
        }
        zones.forEach((zone, gas) -> {
            if (gas.added > 0) {
                flows.add(follow(
                        known, new Flow(zone, gas.added, HeatMutations.add(level, gas.point, gas.added, SOURCE))));
            }
        });
        return flows;
    }

    /**
     * The zone a sample is of, if it takes part in this step: it isn't paused, its gas isn't too thin
     * to carry heat, and no heat of ours is still on its way to it. Null otherwise. A zone that sits
     * the step out for the last reason is counted, once a step, and makes the time up when it next
     * takes part.
     */
    private static @Nullable ZoneId zone(
            Known known,
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
            int before = known.satOut.getOrDefault(zone, 0);
            boolean waits = known.onItsWay.containsKey(zone);
            if (waits) {
                satOut.put(zone, Math.min(MOST_STEPS_MADE_UP, before + 1));
            }
            GasMixture mixture = sample.mixture();
            boolean thin = HeatFlow.thin(mixture.totalMoles(), mixture.volume(), nearVacuum);
            gas = new Gas(point, mixture, !waits && !thin, seconds * (1 + before));
            zones.put(zone, gas);
        }
        return gas.takesPart ? zone : null;
    }

    /** A point just across one side of a block: in the cell that touches the middle of that face. */
    private static Vec3 across(BlockPos pos, Direction side) {
        double reach = 0.5 + 1.0 / 32;
        return new Vec3(
                pos.getX() + 0.5 + side.getStepX() * reach,
                pos.getY() + 0.5 + side.getStepY() * reach,
                pos.getZ() + 0.5 + side.getStepZ() * reach);
    }

    /** Keeps a zone out of later steps until the heat sent to it, or asked of it, has landed. */
    private static Flow follow(Known known, Flow flow) {
        known.onItsWay.merge(flow.zone(), 1, Integer::sum);
        flow.result()
                .whenComplete((result, failure) ->
                        known.onItsWay.computeIfPresent(flow.zone(), (zone, n) -> n > 1 ? n - 1 : null));
        return flow;
    }
}
