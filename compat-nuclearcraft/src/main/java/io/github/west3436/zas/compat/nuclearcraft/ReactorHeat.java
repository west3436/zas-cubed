package io.github.west3436.zas.compat.nuclearcraft;

import igentuman.nc.block_entity.fission.FissionReactorControllerBE;
import igentuman.nc.util.HeatBuffer;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.HeatResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * A fission reactor's heat in the rooms round it (COMPAT-153). The heat its heat sinks remove,
 * which NuclearCraft deletes, warms the gas the reactor stands in, so a reactor in a sealed room
 * heats the room unless the room is cooled.
 *
 * <ul>
 *   <li><b>Which reactors.</b> Each fission reactor controller, learned as its chunk loads and as
 *       it is placed, never by searching the world. Only a formed reactor gives heat.
 *   <li><b>How much.</b> Each tick, what the controller shows of its heat buffer, its public {@code
 *       heatBuffer}, says how much its sinks removed ({@link SinkHeat#removedPerTick}). Once an
 *       atmos tick, that is turned into joules by the server's {@code [heat]
 *       joules_per_heat_unit}, held to {@code [heat] max_watts}, and sent. No mixin: NuclearCraft
 *       is read, never changed, and keeps its own heat as before.
 *   <li><b>Where.</b> To the zones beside the reactor's outer casing, shared by how many of the
 *       casing's outer faces each touches. A face against open air gives its share to the open air,
 *       which takes it all and doesn't change. A face against a solid block, a paused zone or gas
 *       too thin to carry heat counts for nothing, so the rest share its part. A reactor with no
 *       gas round it gives none, as without ZAS³.
 * </ul>
 *
 * <p>Heat added to a zone leaves through {@link HeatMutations#add}, so nothing is made but what
 * NuclearCraft's sinks removed. Server thread.
 */
public final class ReactorHeat {
    /** Who the adapter's heat mutations are made by. */
    public static final SourceId SOURCE = SourceId.of("zas", "nuclearcraft_reactor_heat");

    /** What {@code [heat] joules_per_heat_unit} is unless set. */
    public static final double DEFAULT_JOULES_PER_HEAT_UNIT = 1.0;

    /** The most {@code [heat] joules_per_heat_unit} may be set to. */
    public static final double MAX_JOULES_PER_HEAT_UNIT = 1.0e6;

    /** What {@code [heat] max_watts} is unless set: the Mekanism adapter's ceiling for one body. */
    public static final double DEFAULT_MAX_WATTS = 50_000.0;

    /** The most {@code [heat] max_watts} may be set to. */
    public static final double HIGHEST_MAX_WATTS = 1.0e12;

    /** What is known of each level's reactors. Server thread only. */
    private static final Map<ServerLevel, Known> LEVELS = new WeakHashMap<>();

    private ReactorHeat() {}

    /** What is known of one level. */
    private static final class Known {
        /** The reactor controllers, each with the heat its sinks have removed since its last step. */
        final Map<BlockPos, Removed> reactors = new LinkedHashMap<>();

        /** Positions whose block entity hasn't been looked at yet. */
        final Set<BlockPos> waiting = new HashSet<>();

        /** The game time of the last tick counted, or -1. */
        long counted = -1;
    }

    /** The heat a reactor's sinks have removed since its last step, and over how many ticks. */
    private static final class Removed {
        double heat;
        int ticks;
    }

    /**
     * Heat that one step sent to a zone.
     *
     * @param reactor the reactor's controller
     * @param zone the zone
     * @param joules the heat, in J
     * @param result what ZAS³ made of it, complete once the atmos tick that applies it has been
     */
    public record Flow(BlockPos reactor, ZoneId zone, double joules, CompletableFuture<HeatResult> result) {}

    private static Known known(ServerLevel level) {
        return LEVELS.computeIfAbsent(level, l -> new Known());
    }

    // ---- Learning the reactors -----------------------------------------------------------------

    /**
     * A chunk has loaded: its block entities are looked at at the next tick.
     *
     * @param event the load
     */
    static void chunkLoaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            Map<BlockPos, BlockEntity> entities = chunk.getBlockEntities();
            if (!entities.isEmpty()) {
                known(level).waiting.addAll(entities.keySet());
            }
        }
    }

    /**
     * A block was set: if it has a block entity, it is looked at at the next tick.
     *
     * @param event the change
     */
    static void blockChanged(BlockEvent.NeighborNotifyEvent event) {
        if (event.getState().hasBlockEntity() && event.getLevel() instanceof ServerLevel level) {
            known(level).waiting.add(event.getPos().immutable());
        }
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void forget() {
        LEVELS.clear();
    }

    /**
     * Whether a position holds one of a level's reactor controllers, once the positions waiting have
     * been looked at.
     *
     * @param level the level
     * @param pos the controller's position
     * @return true if the controller there is counted
     */
    @ServerThread
    public static boolean knows(ServerLevel level, BlockPos pos) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return false;
        }
        admit(level, known);
        return known.reactors.containsKey(pos);
    }

    private static void admit(ServerLevel level, Known known) {
        if (known.waiting.isEmpty()) {
            return;
        }
        for (BlockPos pos : known.waiting) {
            if (level.isLoaded(pos)
                    && level.getBlockEntity(pos) instanceof FissionReactorControllerBE controller
                    && !controller.isRemoved()) {
                known.reactors.putIfAbsent(pos, new Removed());
            }
        }
        known.waiting.clear();
    }

    // ---- Counting and sending ------------------------------------------------------------------

    /** A level has ticked: its reactors have too. */
    static void levelTicked(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ticked(level, level.getGameTime());
        }
    }

    /**
     * Counts what each reactor's sinks removed in the tick just run, and once an atmos tick sends
     * what they removed since the last one. A frozen game's time stands still, and so does this.
     *
     * @param level the level, which has just ticked
     * @param gameTime the level's game time
     * @return the heat sent to each zone; empty when this isn't the tick for it
     */
    @ServerThread
    public static List<Flow> ticked(ServerLevel level, long gameTime) {
        Known known = LEVELS.get(level);
        if (known == null || gameTime == known.counted) {
            return List.of();
        }
        known.counted = gameTime;
        count(level);
        int period = Atmospheres.periodTicks();
        return period > 0 && gameTime % period == 0 ? step(level) : List.of();
    }

    /**
     * Counts what each reactor's sinks removed in its last tick. {@link #ticked} calls it once a game
     * tick.
     *
     * @param level the level
     */
    @ServerThread
    public static void count(ServerLevel level) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return;
        }
        admit(level, known);
        Iterator<Map.Entry<BlockPos, Removed>> it = known.reactors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Removed> entry = it.next();
            FissionReactorControllerBE controller = controller(level, entry.getKey());
            if (controller == null) {
                it.remove(); // learned again when its chunk loads, or when it is placed again
                continue;
            }
            Removed removed = entry.getValue();
            removed.ticks++;
            if (controller.formed) {
                HeatBuffer buffer = controller.heatBuffer;
                removed.heat += SinkHeat.removedPerTick(
                        buffer.currentHeat, buffer.heatPerTick, buffer.cooldownPerTick, controller.reactivity);
            }
        }
    }

    private static FissionReactorControllerBE controller(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof FissionReactorControllerBE controller && !controller.isRemoved()
                ? controller
                : null;
    }

    /**
     * Sends what each reactor's sinks removed since its last step to the rooms round it. {@link
     * #ticked} calls it once an atmos tick.
     *
     * @param level the level
     * @return the heat sent to each zone, for whoever wants to follow it
     */
    @ServerThread
    public static List<Flow> step(ServerLevel level) {
        Known known = LEVELS.get(level);
        if (known == null) {
            return List.of();
        }
        double joulesPerUnit = NuclearCraftSettings.joulesPerHeatUnit();
        double maxWatts = NuclearCraftSettings.maxWatts();
        List<Flow> flows = new ArrayList<>();
        for (Map.Entry<BlockPos, Removed> entry : known.reactors.entrySet()) {
            Removed removed = entry.getValue();
            double joules = SinkHeat.joules(removed.heat, removed.ticks, joulesPerUnit, maxWatts);
            removed.heat = 0;
            removed.ticks = 0;
            FissionReactorControllerBE controller = controller(level, entry.getKey());
            if (joules > 0 && controller != null && controller.formed) {
                send(level, entry.getKey(), controller, joules, flows);
            }
        }
        return flows;
    }

    /** A zone beside the reactor, with how many of its casing's faces touch it. */
    private static final class Side {
        final Vec3 point;
        int faces;

        Side(Vec3 point) {
            this.point = point;
        }
    }

    /** Shares a reactor's heat out among the zones beside its casing, by faces. */
    private static void send(
            ServerLevel level, BlockPos at, FissionReactorControllerBE controller, double joules, List<Flow> flows) {
        long minPacked = controller.aabbMinPacked;
        long maxPacked = controller.aabbMaxPacked;
        BlockPos min = minPacked == 0 && maxPacked == 0 ? at : BlockPos.of(minPacked);
        BlockPos max = minPacked == 0 && maxPacked == 0 ? at : BlockPos.of(maxPacked);
        double nearVacuum = Atmospheres.nearVacuumDensity();
        Map<ZoneId, Side> sides = new LinkedHashMap<>();
        int open = 0;
        for (BlockPos pos : outside(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            AtmosphereSample sample = Atmospheres.at(level, pos);
            if (sample.state() == AtmosphereState.AMBIENT) {
                open++;
            } else if (sample.state() == AtmosphereState.ZONED
                    && sample.zone().isPresent()
                    && !thin(sample.mixture(), nearVacuum)) {
                sides.computeIfAbsent(sample.zone().get(), zone -> new Side(Vec3.atCenterOf(pos))).faces++;
            }
        }
        int faces = open;
        for (Side side : sides.values()) {
            faces += side.faces;
        }
        if (faces == 0) {
            return;
        }
        for (Map.Entry<ZoneId, Side> zone : sides.entrySet()) {
            Side side = zone.getValue();
            double part = joules * side.faces / faces;
            flows.add(new Flow(at, zone.getKey(), part, HeatMutations.add(level, side.point, part, SOURCE)));
        }
    }

    /** Gas too thin to carry heat (HEAT-008), or none. */
    private static boolean thin(GasMixture gas, double nearVacuum) {
        double moles = gas.totalMoles();
        double litres = gas.volume();
        return !(moles > 0) || !(litres > 0) || moles < nearVacuum * litres;
    }

    /** The positions just outside each face of a box: one beside each outer face of its blocks. */
    static List<BlockPos> outside(BlockPos min, BlockPos max) {
        int x0 = Math.min(min.getX(), max.getX());
        int y0 = Math.min(min.getY(), max.getY());
        int z0 = Math.min(min.getZ(), max.getZ());
        int x1 = Math.max(min.getX(), max.getX());
        int y1 = Math.max(min.getY(), max.getY());
        int z1 = Math.max(min.getZ(), max.getZ());
        List<BlockPos> found = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                found.add(new BlockPos(x0 - 1, y, z));
                found.add(new BlockPos(x1 + 1, y, z));
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                found.add(new BlockPos(x, y0 - 1, z));
                found.add(new BlockPos(x, y1 + 1, z));
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                found.add(new BlockPos(x, y, z0 - 1));
                found.add(new BlockPos(x, y, z1 + 1));
            }
        }
        return found;
    }
}
