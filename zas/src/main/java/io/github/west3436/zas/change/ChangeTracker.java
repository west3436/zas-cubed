package io.github.west3436.zas.change;

import io.github.west3436.zas.engine.ChunkGenerations;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.DirtySections;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.LongPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
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
 *       (GEO-011).
 * </ul>
 *
 * <p>Repeated changes to a position coalesce into one dirty bit until the scheduler drains them
 * with {@link #drain(ServerLevel, LongPredicate)} at batch close (GEO-012). Writes made off the
 * server thread are queued and folded in, in arrival order, on the server thread before any query
 * and at the end of every server tick.
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

        void record(int x, int y, int z, boolean geometryChanged) {
            generations.bumpBlock(SectionKeys.chunk(x >> 4, z >> 4));
            if (geometryChanged) {
                dirty.mark(x, y, z);
            }
        }
    }

    private record OffThreadWrite(ServerLevel level, long pos, boolean geometryChanged) {}

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
        boolean geometryChanged = geometryChanged(old, now);
        if (level.getServer().isSameThread()) {
            dimension(level).record(pos.getX(), pos.getY(), pos.getZ(), geometryChanged);
        } else {
            OFF_THREAD.add(new OffThreadWrite(level, pos.asLong(), geometryChanged));
        }
    }

    /**
     * Whether a write from {@code old} to {@code now} can change gas geometry. While the geometry
     * table is being rebuilt the answer is yes, so nothing is missed.
     */
    static boolean geometryChanged(BlockState old, BlockState now) {
        GeometryTable table = Geometries.tableIfReady();
        return table == null || table.geometryId(old) != table.geometryId(now);
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
                            w.geometryChanged());
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
