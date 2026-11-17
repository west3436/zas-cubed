package io.github.west3436.zas.persistence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Chunks another mod keeps in a level outside its chunk map, and saves in files of its own (COMPAT-100):
 * Sable's sub-level plots. Such a chunk fires {@link ChunkEvent.Load} and {@link
 * net.neoforged.neoforge.event.level.ChunkDataEvent.Load} when it loads, as any chunk does, so its
 * atmosphere comes back through {@link AtmosPersistence} unchanged. It fires neither {@link
 * net.neoforged.neoforge.event.level.ChunkDataEvent.Save} nor {@link ChunkEvent.Unload}, so the mod's
 * adapter asks here instead:
 *
 * <ul>
 *   <li>{@link #saving} when the mod saves the chunk: the bytes to keep with it, under {@link
 *       AtmosPersistence#TAG} in the chunk's own tag, so that its load event hands them back;
 *   <li>{@link #unloading} when the mod drops the chunk: ZAS³ forgets it as at an unload, and the
 *       bytes are what the unload saved.
 * </ul>
 *
 * <p>ZAS³'s own listeners for {@link ChunkEvent.Unload} register through {@link #onUnload}, which
 * adds them to the game bus and keeps them here, so that {@link #unloading} runs them in the same
 * order without posting an unload that other mods would see. Server thread.
 */
public final class HostedChunks {
    private record Listener(EventPriority priority, int order, Consumer<ChunkEvent.Unload> handler) {}

    private static final List<Listener> UNLOAD = new ArrayList<>();

    private HostedChunks() {}

    /**
     * Registers one of ZAS³'s chunk-unload listeners on the game bus, and for {@link #unloading}.
     *
     * @param gameBus the game bus
     * @param priority its priority on the bus, which {@link #unloading} keeps
     * @param handler the listener
     */
    public static synchronized void onUnload(
            IEventBus gameBus, EventPriority priority, Consumer<ChunkEvent.Unload> handler) {
        gameBus.addListener(priority, false, ChunkEvent.Unload.class, handler);
        UNLOAD.add(new Listener(priority, UNLOAD.size(), handler));
        UNLOAD.sort(Comparator.comparing(Listener::priority).thenComparingInt(Listener::order));
    }

    /**
     * What a loaded chunk the mod is saving keeps under {@link AtmosPersistence#TAG}, as {@link
     * net.neoforged.neoforge.event.level.ChunkDataEvent.Save} would write it. Server thread.
     *
     * @return the bytes, or null for nothing
     */
    public static byte @Nullable [] saving(ServerLevel level, ChunkPos pos) {
        return AtmosPersistence.bytesToSave(level, pos.toLong());
    }

    /**
     * The mod is dropping a chunk from the level: ZAS³ forgets it as it would a chunk that unloads,
     * pausing what reaches it and keeping its zones' record (DATA-002). Server thread.
     *
     * @return what its unload saved, to keep under {@link AtmosPersistence#TAG} with the chunk, or
     *     null for nothing
     */
    public static byte @Nullable [] unloading(ServerLevel level, LevelChunk chunk) {
        List<Listener> listeners;
        synchronized (HostedChunks.class) {
            listeners = List.copyOf(UNLOAD);
        }
        ChunkEvent.Unload event = new ChunkEvent.Unload(chunk);
        for (Listener l : listeners) {
            l.handler().accept(event);
        }
        return AtmosPersistence.bytesToSave(level, chunk.getPos().toLong());
    }
}
