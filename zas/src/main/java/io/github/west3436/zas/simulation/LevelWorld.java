package io.github.west3436.zas.simulation;

import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.change.SectionCapture;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.DirtySection;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.scheduler.DimensionPipeline;
import io.github.west3436.zas.engine.scheduler.SectionSource;
import io.github.west3436.zas.environment.Ambients;
import java.util.List;
import java.util.function.LongPredicate;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * A server level as the atmos pipeline sees it (architecture §3.4). Every method runs on the
 * server thread and checks it, so no simulation code can reach the live world from a worker
 * (CORE-049); nothing here loads a chunk (GEO-034) or scans the world (CORE-054).
 */
final class LevelWorld implements DimensionPipeline.World {
    private final ServerLevel level;
    private final Predicate<Long> simulatedUnloaded;

    /**
     * @param simulatedUnloaded chunks GameTests treat as unloaded ({@link
     *     DimensionAtmos#simulateUnload})
     */
    LevelWorld(ServerLevel level, Predicate<Long> simulatedUnloaded) {
        this.level = level;
        this.simulatedUnloaded = simulatedUnloaded;
    }

    @Override
    public long[] drainDirty(LongPredicate interest) {
        requireServerThread();
        List<DirtySection> dirty =
                ChangeTracker.drain(level, k -> interest.test(k) && !simulatedUnloaded.test(SectionKeys.chunkOf(k)));
        long[] keys = new long[dirty.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = dirty.get(i).key();
        }
        return keys;
    }

    @Override
    public boolean isLoaded(long chunk) {
        requireServerThread();
        return !simulatedUnloaded.test(chunk)
                && level.getChunkSource().getChunkNow(SectionKeys.chunkX(chunk), SectionKeys.chunkZ(chunk)) != null;
    }

    @Override
    public SectionSource capture(long section) {
        requireServerThread();
        if (simulatedUnloaded.test(SectionKeys.chunkOf(section))) {
            return null;
        }
        return SectionCapture.capture(level, section);
    }

    @Override
    public long lifecycleGeneration(long chunk) {
        requireServerThread();
        if (simulatedUnloaded.test(chunk)) {
            return io.github.west3436.zas.engine.ChunkGenerations.UNLOADED;
        }
        return ChangeTracker.lifecycleGeneration(
                level, new ChunkPos(SectionKeys.chunkX(chunk), SectionKeys.chunkZ(chunk)));
    }

    @Override
    public Ambient ambient() {
        requireServerThread();
        return Ambients.ambient(level.dimension());
    }

    private void requireServerThread() {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException("live world access off the server thread from "
                    + Thread.currentThread().getName());
        }
    }
}
