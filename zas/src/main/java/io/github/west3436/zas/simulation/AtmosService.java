package io.github.west3436.zas.simulation;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.ZasCommonConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.scheduler.AtmosWorkers;
import io.github.west3436.zas.engine.scheduler.SchedulerSettings;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The atmos scheduler on a running server (architecture §3): one {@link DimensionAtmos} per
 * level (CORE-042), the worker pool, and the timing of every atmos tick.
 *
 * <p><b>Timing.</b> At the end of each server tick ({@link ServerTickEvent.Post}), for each level
 * in the server's order: a batch that closed {@code apply_delay_ticks} ago is applied, then, every
 * {@code period_ticks}, the next batch closes (CORE-041). Batch work on the server thread shares
 * one budget per game tick (CORE-047); applying is never split. Results are applied at a fixed
 * game tick whatever the worker's timing, so threaded and single-threaded runs agree (CORE-071).
 *
 * <p><b>Threads.</b> Mutations and {@link #tickNow} are server-thread only; {@link #dimension}
 * and the published tables may be read from any thread (API-046).
 */
public final class AtmosService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile AtmosService current;

    private final MinecraftServer server;
    private final ExecutorService workers;
    private final Map<ResourceKey<Level>, DimensionAtmos> dimensions = new ConcurrentHashMap<>();

    private AtmosService(MinecraftServer server) {
        this.server = server;
        this.workers = AtmosWorkers.create(ZasCommonConfig.workerThreads());
    }

    /** Registers the lifecycle and tick listeners. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((ServerStartedEvent e) -> current = new AtmosService(e.getServer()));
        gameBus.addListener((ServerStoppingEvent e) -> {
            AtmosService s = current;
            current = null;
            if (s != null) {
                s.stop();
            }
        });
        gameBus.addListener((ServerTickEvent.Post e) -> {
            AtmosService s = current;
            if (s != null) {
                s.tick();
            }
        });
        gameBus.addListener((ChunkEvent.Load e) -> {
            AtmosService s = current;
            if (s != null
                    && e.getLevel() instanceof ServerLevel level
                    && level.getServer().isSameThread()) {
                DimensionAtmos d = s.dimensions.get(level.dimension());
                if (d != null) {
                    d.chunkLoaded(e.getChunk().getPos().toLong());
                }
            }
        });
        gameBus.addListener((ChunkEvent.Unload e) -> {
            AtmosService s = current;
            if (s != null
                    && e.getLevel() instanceof ServerLevel level
                    && level.getServer().isSameThread()) {
                DimensionAtmos d = s.dimensions.get(level.dimension());
                if (d != null) {
                    d.chunkUnloaded(e.getChunk().getPos().toLong());
                }
            }
        });
        gameBus.addListener((LevelEvent.Unload e) -> {
            AtmosService s = current;
            if (s != null && e.getLevel() instanceof ServerLevel level) {
                DimensionAtmos d = s.dimensions.remove(level.dimension());
                if (d != null) {
                    d.shutdown("level unloaded");
                }
            }
        });
    }

    /** The service of the running server, or null between servers. Any thread. */
    public static AtmosService current() {
        return current;
    }

    /** The atmos state of a level's dimension, or null if it has had none yet. Any thread. */
    public DimensionAtmos dimension(Level level) {
        return level instanceof ServerLevel ? dimensions.get(level.dimension()) : null;
    }

    /** The atmos state of a level, created if needed. Server thread. */
    public DimensionAtmos dimensionFor(ServerLevel level) {
        return dimensions.computeIfAbsent(level.dimension(), k -> new DimensionAtmos(level, workers));
    }

    // ---- Mutations (server thread) ---------------------------------------------------

    /** Queues an addition for the next atmos tick (API-012, API-016). */
    public CompletableFuture<MutationResult> add(ServerLevel level, BlockPos pos, GasParcel gas, SourceId source) {
        return dimensionFor(level).add(pos, gas, source);
    }

    /** Queues a removal for the next atmos tick (API-012, API-016). */
    public CompletableFuture<MutationResult> remove(
            ServerLevel level, BlockPos pos, Map<ResourceLocation, Double> moles, SourceId source) {
        return dimensionFor(level).remove(pos, moles, source);
    }

    // ---- Ticking ------------------------------------------------------------------------

    private void tick() {
        long gameTick = server.getTickCount();
        SchedulerSettings timing = ZasServerConfig.scheduler();
        long start = System.nanoTime();
        long budget = ZasServerConfig.budgetNanos();
        for (ServerLevel level : server.getAllLevels()) {
            DimensionAtmos d = dimensionFor(level);
            if (d.dueAt(gameTick, timing)) {
                d.apply();
            }
            if (timing.closesAt(gameTick)) {
                d.close(gameTick, timing, () -> System.nanoTime() - start > budget);
            }
        }
    }

    /**
     * Runs one whole atmos tick of a level now: applies a batch in flight, closes the next and
     * applies it at once, waiting for the worker. For GameTests and commands, which need an atmos
     * tick at a known moment. Server thread.
     */
    public void tickNow(ServerLevel level) {
        DimensionAtmos d = dimensionFor(level);
        SchedulerSettings timing = ZasServerConfig.scheduler();
        if (d.isInFlight()) {
            d.apply();
        }
        d.close(server.getTickCount(), timing, () -> false);
        d.apply();
    }

    /** Closes a batch of a level now, leaving it in flight. For tests of stale results. Server thread. */
    public void closeNow(ServerLevel level) {
        dimensionFor(level).close(server.getTickCount(), ZasServerConfig.scheduler(), () -> false);
    }

    /** Applies a level's batch in flight now, if any. For tests of stale results. Server thread. */
    public void applyNow(ServerLevel level) {
        DimensionAtmos d = dimensionFor(level);
        if (d.isInFlight()) {
            d.apply();
        }
    }

    /**
     * Stops a level's atmosphere as a server stop would, and returns what the given chunks write in
     * the final save (DATA-011), by chunk; chunks that save nothing are left out. For GameTests of
     * save and reload, which can't restart the server. Server thread.
     */
    public Map<Long, byte[]> stopForTest(ServerLevel level, long... chunks) {
        DimensionAtmos d = dimensions.remove(level.dimension());
        Map<Long, byte[]> out = new java.util.TreeMap<>();
        if (d == null) {
            return out;
        }
        if (d.isInFlight()) {
            d.apply();
        }
        d.stop();
        for (long chunk : chunks) {
            byte[] bytes = d.finalSave(chunk);
            if (bytes != null) {
                out.put(chunk, bytes);
            }
        }
        d.detach();
        return out;
    }

    /**
     * Starts a level's atmosphere again after {@link #stopForTest}, as after a restart: the given
     * bytes are what its loaded chunks saved. Server thread.
     */
    public DimensionAtmos startForTest(ServerLevel level, Map<Long, byte[]> saved) {
        saved.forEach(
                (chunk, bytes) -> io.github.west3436.zas.persistence.AtmosPersistence.restore(level, chunk, bytes));
        DimensionAtmos d = dimensionFor(level);
        d.start();
        return d;
    }

    private void stop() {
        // Never wait for a worker without bound: abandon ticks in flight, then give the pool a
        // short grace period. Its threads are daemons, so a stuck one can't keep the JVM alive.
        for (DimensionAtmos d : dimensions.values()) {
            d.stop(); // keeps its published table for the final save, which follows (DATA-011)
        }
        dimensions.clear();
        workers.shutdownNow();
        try {
            if (!workers.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warn("Atmos workers didn't stop within 2 s; leaving them to the JVM");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
