package io.github.west3436.zas.compat.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import io.github.west3436.zas.persistence.AtmosPersistence;
import io.github.west3436.zas.persistence.HostedChunks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * The atmosphere of a sub-level's plot kept with the sub-level (COMPAT-100). Sable keeps a plot's
 * chunks in the level, but saves them with the sub-level in files of its own, and drops them when
 * the sub-level unloads or is removed, without the chunk events a world chunk fires then. This
 * stands in for those events:
 *
 * <ul>
 *   <li>when Sable saves a plot, each chunk's tag gets the chunk's record under the key a world chunk
 *       keeps it under ({@link #saving}), so the load event Sable fires with the tag hands it back;
 *   <li>when a sub-level unloads, ZAS³ forgets its chunks as at a chunk unload, and the record that
 *       unload builds, with every change since the last save, replaces the one in the tag Sable
 *       holds for it;
 *   <li>when a sub-level is removed, ZAS³ forgets its chunks and their record goes with them, once
 *       the rooms of any move out of them, such as the one that put them back into the world, have
 *       arrived.
 * </ul>
 *
 * <p>Server thread.
 */
public final class PlotAir {
    /** The observer of every server level's sub-levels. */
    static final SubLevelObserver OBSERVER = new SubLevelObserver() {
        @Override
        public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
            if (subLevel instanceof ServerSubLevel sub) {
                removed(sub, reason);
            }
        }
    };

    private PlotAir() {}

    /**
     * Watches a server level's sub-levels, when it loads.
     *
     * @param level the level
     */
    static void watch(ServerLevel level) {
        ServerSubLevelContainer subLevels = SubLevelContainer.getContainer(level);
        if (subLevels != null) {
            subLevels.addObserver(OBSERVER);
        }
    }

    /**
     * Adds each chunk's record to the tag Sable saves a plot in.
     *
     * @param plot the plot
     * @param saved the tag Sable built
     * @return the same tag
     */
    public static CompoundTag saving(ServerLevelPlot plot, CompoundTag saved) {
        ServerLevel level = plot.getSubLevel().getLevel();
        if (!level.getServer().isSameThread() || !saved.contains("chunks", Tag.TAG_COMPOUND)) {
            return saved;
        }
        CompoundTag chunks = saved.getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            ChunkPos local = local(key);
            if (local == null || !chunks.contains(key, Tag.TAG_COMPOUND)) {
                continue;
            }
            put(chunks.getCompound(key), HostedChunks.saving(level, plot.toGlobal(local)));
        }
        return saved;
    }

    private static void removed(ServerSubLevel sub, SubLevelRemovalReason reason) {
        ServerLevel level = sub.getLevel();
        if (!level.getServer().isSameThread()) {
            return;
        }
        ServerLevelPlot plot = sub.getPlot();
        List<LevelChunk> chunks = new ArrayList<>();
        for (PlotChunkHolder holder : plot.getLoadedChunks()) {
            LevelChunk chunk = holder.getChunk();
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        if (reason == SubLevelRemovalReason.UNLOADED) {
            CompoundTag held = heldChunks(level, sub);
            for (LevelChunk chunk : chunks) {
                byte[] bytes = HostedChunks.unloading(level, chunk);
                String key = String.valueOf(plot.toLocal(chunk.getPos()).toLong());
                if (held != null && held.contains(key, Tag.TAG_COMPOUND)) {
                    put(held.getCompound(key), bytes);
                }
            }
            return;
        }
        // Removed: the blocks are gone, or back in the world. Wait for the rooms of a move out of
        // these chunks, such as the one that put them back, to arrive before forgetting them.
        List<ChunkPos> positions = chunks.stream().map(LevelChunk::getPos).toList();
        CompletableFuture<Void> settled = SableMoves.settled(level, positions);
        if (settled == null) {
            drop(level, chunks);
        } else {
            settled.whenComplete((v, failure) -> drop(level, chunks));
        }
    }

    /** Forgets a removed sub-level's chunks, but not one a new sub-level has taken since. */
    private static void drop(ServerLevel level, List<LevelChunk> chunks) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        for (LevelChunk chunk : chunks) {
            if (container != null && container.getChunk(chunk.getPos()) != null) {
                continue;
            }
            HostedChunks.unloading(level, chunk);
        }
    }

    /** The chunk tags of the plot Sable holds for a sub-level that has just unloaded, or null. */
    private static @Nullable CompoundTag heldChunks(ServerLevel level, ServerSubLevel sub) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        HoldingSubLevel holding =
                container == null ? null : container.getHoldingChunkMap().getHoldingSubLevel(sub.getUniqueId());
        if (holding == null) {
            return null;
        }
        CompoundTag full = holding.data().fullTag();
        if (!full.contains("plot", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag plot = full.getCompound("plot");
        return plot.contains("chunks", Tag.TAG_COMPOUND) ? plot.getCompound("chunks") : null;
    }

    private static void put(CompoundTag chunkTag, byte @Nullable [] bytes) {
        if (bytes == null) {
            chunkTag.remove(AtmosPersistence.TAG);
        } else {
            chunkTag.putByteArray(AtmosPersistence.TAG, bytes);
        }
    }

    private static @Nullable ChunkPos local(String key) {
        try {
            return new ChunkPos(Long.parseLong(key));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
