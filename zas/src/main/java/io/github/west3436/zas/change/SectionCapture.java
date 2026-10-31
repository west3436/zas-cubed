package io.github.west3436.zas.change;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.SectionSnapshot;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;

/**
 * A private copy of one section's block states, taken on the server thread and decoded into a
 * {@link SectionSnapshot} on any thread (architecture §3.4).
 *
 * <p>Taking the copy is the server thread's only cost ({@link PalettedContainer#copy()}, about
 * 0.3 µs per section in spike S5); mapping 4096 states to geometry ids is left to {@link
 * #decode()}, which the atmos worker calls. The copy is never shared, so reading it off-thread
 * doesn't touch the live world (CORE-049).
 */
public final class SectionCapture {
    private final long key;
    private final long lifecycleGeneration;
    private final long blockGeneration;
    private final PalettedContainer<BlockState> states;
    private final GeometryTable table;

    private SectionCapture(
            long key,
            long lifecycleGeneration,
            long blockGeneration,
            PalettedContainer<BlockState> states,
            GeometryTable table) {
        this.key = key;
        this.lifecycleGeneration = lifecycleGeneration;
        this.blockGeneration = blockGeneration;
        this.states = states;
        this.table = table;
    }

    /**
     * Copies section {@code sectionKey} ({@link SectionKeys}) of {@code level}, with its chunk's
     * generations from {@link ChangeTracker}. Returns {@code null} if the chunk isn't loaded or the
     * section is outside the level's height. Never loads a chunk. Server thread only.
     */
    public static SectionCapture capture(ServerLevel level, long sectionKey) {
        int sx = SectionKeys.x(sectionKey);
        int sz = SectionKeys.z(sectionKey);
        LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
        if (chunk == null) {
            return null;
        }
        int index = chunk.getSectionIndexFromSectionY(SectionKeys.y(sectionKey));
        if (index < 0 || index >= chunk.getSectionsCount()) {
            return null;
        }
        ChunkPos pos = chunk.getPos();
        return new SectionCapture(
                sectionKey,
                ChangeTracker.lifecycleGeneration(level, pos),
                ChangeTracker.blockGeneration(level, pos),
                chunk.getSection(index).getStates().copy(),
                Geometries.table());
    }

    /** The section key ({@link SectionKeys}). */
    public long key() {
        return key;
    }

    /** Maps the copied states to geometry ids. Safe on any thread; may be called more than once. */
    public SectionSnapshot decode() {
        short[] ids = new short[SectionSnapshot.SIZE];
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    ids[SectionKeys.index(x, y, z)] = (short) table.geometryId(Block.getId(states.get(x, y, z)));
                }
            }
        }
        return SectionSnapshot.of(key, lifecycleGeneration, blockGeneration, ids);
    }
}
