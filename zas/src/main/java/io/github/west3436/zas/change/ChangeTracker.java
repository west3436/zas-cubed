package io.github.west3436.zas.change;

import io.github.west3436.zas.config.ZoneTrigger;
import io.github.west3436.zas.engine.ChunkGenerations;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.DirtySections;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Block-change tracking for server levels (architecture §4.2, GEO-010 to GEO-012).
 *
 * <p>The change hook ({@code LevelChunkMixin}) calls {@link #onBlockWrite} for every block state
 * written into a server chunk, whatever wrote it. Per write it does constant work:
 *
 * <ul>
 *   <li>bumps the chunk's block generation ({@link ChunkGenerations}), on every write;
 *   <li>if the geometry id changed, marks the position in the dimension's {@link DirtySections};
 *       a write that keeps the geometry id (stone to cobblestone, air to a torch) stops there
 *       (GEO-011);
 *   <li>if the write starts a Hardcore zone trigger that is active, a fire or a lit furnace
 *       ({@link ZoneTrigger#COMBUSTION}) or a plant ({@link ZoneTrigger#PLANTING}), marks the
 *       position in that trigger's set (CORE-056). Which states start one is a table read per
 *       state ({@link TriggerStates}); the preset is read only for a write that starts one.
 * </ul>
 *
 * <p>Repeated changes to a position coalesce into one dirty bit until the scheduler drains them
 * with {@link #drain(ServerLevel, LongPredicate)} at batch close (GEO-012); so do triggers, which
 * the scheduler takes with {@link #drainTriggers(ServerLevel)} and turns into zone-forming
 * operations. Writes made off the server thread are queued and folded in, in arrival order, on the
 * server thread before any query and at the end of every server tick.
 *
 * <p>Everything except {@link #onBlockWrite} must be called on the server thread.
 */
public final class ChangeTracker {
    private static final Map<ServerLevel, Dimension> LEVELS = new IdentityHashMap<>();
    private static final ConcurrentLinkedQueue<OffThreadWrite> OFF_THREAD = new ConcurrentLinkedQueue<>();

    // A one-entry cache for the hot path; almost every write hits the same level as the last.
    private static ServerLevel lastLevel;
    private static Dimension lastDimension;

    private ChangeTracker() {}

    /** One dimension's tracking state. */
    private static final class Dimension {
        final DirtySections dirty = new DirtySections();
        final ChunkGenerations generations = new ChunkGenerations();
        /** Positions where combustion started since the last drain (CORE-056). */
        final DirtySections combustion = new DirtySections();
        /** Positions where planting happened since the last drain (CORE-056). */
        final DirtySections planting = new DirtySections();

        void record(int x, int y, int z, boolean geometryChanged, int triggers) {
            generations.bumpBlock(SectionKeys.chunk(x >> 4, z >> 4));
            if (geometryChanged) {
                dirty.mark(x, y, z);
            }
            if (triggers != 0) {
                if ((triggers & TriggerStates.COMBUSTION_FLAG) != 0) {
                    combustion.mark(x, y, z);
                }
                if ((triggers & TriggerStates.PLANTING_FLAG) != 0) {
                    planting.mark(x, y, z);
                }
            }
        }
    }

    private record OffThreadWrite(ServerLevel level, long pos, boolean geometryChanged, int triggers) {}

    /**
     * A Hardcore zone trigger that started at a block position (CORE-056): combustion or planting
     * there, under a preset or override that lets it form a zone.
     */
    public record Trigger(BlockPos pos, ZoneTrigger trigger) {}

    /** Registers the chunk and server lifecycle listeners on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ChangeTracker::onChunkLoad);
        gameBus.addListener(ChangeTracker::onChunkUnload);
        gameBus.addListener(ChangeTracker::onLevelUnload);
        gameBus.addListener(ChangeTracker::onServerTick);
        gameBus.addListener(ChangeTracker::onServerStopped);
    }

    /**
     * Records that {@code chunk} now holds {@code now} at {@code pos} instead of {@code old}. Called
     * by the change hook for every write that changed the stored state; client chunks are ignored.
     * Safe on any thread.
     */
    public static void onBlockWrite(LevelChunk chunk, BlockPos pos, BlockState old, BlockState now) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        int oldId = Block.getId(old);
        int nowId = Block.getId(now);
        boolean geometryChanged = geometryChanged(oldId, nowId);
        int triggers = TriggerStates.started(oldId, nowId);
        if (level.getServer().isSameThread()) {
            dimension(level).record(pos.getX(), pos.getY(), pos.getZ(), geometryChanged, triggers);
        } else {
            OFF_THREAD.add(new OffThreadWrite(level, pos.asLong(), geometryChanged, triggers));
        }
    }

    /**
     * Whether a write from state id {@code oldId} to {@code nowId} ({@link Block#getId}) can change
     * gas geometry. While the geometry table is being rebuilt the answer is yes, so nothing is
     * missed.
     */
    static boolean geometryChanged(int oldId, int nowId) {
        GeometryTable table = Geometries.tableIfReady();
        return table == null || table.geometryId(oldId) != table.geometryId(nowId);
    }

    // ---- queries and drains (server thread) ----

    /**
     * Removes every dirty position in {@code level} and returns them by section, sorted by
     * section x, then z, then y ({@link SectionKeys#compare}).
     */
    public static List<DirtySection> drain(ServerLevel level) {
        return drain(level, key -> true);
    }

    /**
     * Removes every dirty position in {@code level} and returns the sections {@code interest}
     * accepts (section keys, {@link SectionKeys}), sorted as {@link #drain(ServerLevel)} does. The
     * rest are dropped without further work: the scheduler passes the sections that hold or border
     * a zone or hold a pending classification (architecture §4.2, GEO-011).
     */
    public static List<DirtySection> drain(ServerLevel level, LongPredicate interest) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        return d == null ? List.of() : d.dirty.drain(interest);
    }

    /**
     * Removes the Hardcore zone triggers that started in {@code level} since the last drain
     * (CORE-056) and returns them: combustion, then planting, each sorted by section as {@link
     * #drain(ServerLevel)} sorts and by position within a section. Repeated triggers at a position
     * come out once. Triggers in chunks that unloaded since are gone. Server thread.
     */
    public static List<Trigger> drainTriggers(ServerLevel level) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        if (d == null || (d.combustion.isEmpty() && d.planting.isEmpty())) {
            return List.of();
        }
        List<Trigger> out = new ArrayList<>();
        addTriggers(d.combustion.drain(), ZoneTrigger.COMBUSTION, out);
        addTriggers(d.planting.drain(), ZoneTrigger.PLANTING, out);
        return out;
    }

    private static void addTriggers(List<DirtySection> sections, ZoneTrigger trigger, List<Trigger> out) {
        for (DirtySection s : sections) {
            int x0 = s.sectionX() << 4;
            int y0 = s.sectionY() << 4;
            int z0 = s.sectionZ() << 4;
            s.forEachIndex(i ->
                    out.add(new Trigger(new BlockPos(x0 + (i & 15), y0 + (i >> 8), z0 + ((i >> 4) & 15)), trigger)));
        }
    }

    /**
     * Removes the dirty positions inside {@code box} and returns them by section. Positions
     * outside the box stay dirty.
     */
    public static List<DirtySection> drainWithin(ServerLevel level, BoundingBox box) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        return d == null
                ? List.of()
                : d.dirty.drainWithin(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /**
     * Marks a position dirty as if its geometry had changed, so the next batch captures its section
     * again if the worker mirrors it: the geometry audit's repair (GEO-032). Server thread.
     */
    public static void markDirty(ServerLevel level, BlockPos pos) {
        flushOffThread();
        dimension(level).dirty.mark(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether any position of section {@code sectionKey} ({@link SectionKeys}) is waiting to be drained. */
    public static boolean isSectionDirty(ServerLevel level, long sectionKey) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        return d != null && d.dirty.isSectionDirty(sectionKey);
    }

    /** Whether {@code pos} has changed geometry since it was last drained. */
    public static boolean isDirty(ServerLevel level, BlockPos pos) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        return d != null && d.dirty.isDirty(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The block generation of chunk {@code pos}, or {@link ChunkGenerations#UNLOADED}. */
    public static long blockGeneration(ServerLevel level, ChunkPos pos) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        return d == null ? ChunkGenerations.UNLOADED : d.generations.block(pos.toLong());
    }

    /** The lifecycle generation of chunk {@code pos}, or {@link ChunkGenerations#UNLOADED}. */
    public static long lifecycleGeneration(ServerLevel level, ChunkPos pos) {
        flushOffThread();
        Dimension d = LEVELS.get(level);
        return d == null ? ChunkGenerations.UNLOADED : d.generations.lifecycle(pos.toLong());
    }

    /**
     * Gives a loaded chunk a new lifecycle generation, as if it had unloaded and loaded again,
     * without touching the chunk. For tests of stale results (CORE-070), which can't unload a
     * chunk the test holds. Server thread.
     */
    public static void markReloaded(ServerLevel level, ChunkPos pos) {
        flushOffThread();
        Dimension d = dimension(level);
        d.generations.unload(pos.toLong());
        d.generations.load(pos.toLong());
    }

    // ---- internals ----

    private static Dimension dimension(ServerLevel level) {
        if (level == lastLevel) {
            return lastDimension;
        }
        Dimension d = LEVELS.computeIfAbsent(level, l -> new Dimension());
        lastLevel = level;
        lastDimension = d;
        return d;
    }

    private static void flushOffThread() {
        OffThreadWrite w;
        while ((w = OFF_THREAD.poll()) != null) {
            dimension(w.level())
                    .record(
                            BlockPos.getX(w.pos()),
                            BlockPos.getY(w.pos()),
                            BlockPos.getZ(w.pos()),
                            w.geometryChanged(),
                            w.triggers());
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            dimension(level).generations.load(event.getChunk().getPos().toLong());
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && level.getServer().isSameThread()) {
            flushOffThread();
            ChunkPos pos = event.getChunk().getPos();
            Dimension d = dimension(level);
            d.generations.unload(pos.toLong());
            d.dirty.removeChunk(pos.x, pos.z);
            d.combustion.removeChunk(pos.x, pos.z);
            d.planting.removeChunk(pos.x, pos.z);
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            OFF_THREAD.removeIf(w -> w.level() == level);
            LEVELS.remove(level);
            if (lastLevel == level) {
                lastLevel = null;
                lastDimension = null;
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        flushOffThread();
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        OFF_THREAD.clear();
        LEVELS.clear();
        lastLevel = null;
        lastDimension = null;
    }
}
