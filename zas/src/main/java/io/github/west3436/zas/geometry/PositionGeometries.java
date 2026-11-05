package io.github.west3436.zas.geometry;

import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The geometry of positions whose geometry isn't simply their block state's (architecture §4.1):
 * blocks with a seal provider (API-023).
 *
 * <p><b>When.</b> It is worked out on the server thread when a section is copied for an atmos tick,
 * and travels with the copy as the snapshot's overrides (API-028). It is kept per position until the
 * block changes (GEO-010), {@code markDirty} is called (GEO-014) or the geometry table is rebuilt, so
 * a section copied again asks nothing again. A chunk's entries go when it unloads, since a chunk
 * loading is copied afresh.
 *
 * <p>Any thread may read what is kept ({@link #geometry}); only the server thread works anything
 * out.
 */
public final class PositionGeometries {
    /** The overrides of one section copy: section indices, ascending, and the geometry at each. */
    public record Overrides(int[] indices, Geometry[] geometries) {
        static final Overrides NONE = new Overrides(new int[0], new Geometry[0]);
    }

    /** What was worked out at a position, and from which table; a null geometry means its block state's. */
    private record Worked(GeometryTable table, Geometry geometry) {}

    /** By level, then chunk, then packed position. */
    private static final Map<ServerLevel, Map<Long, Map<Long, Worked>>> LEVELS = new ConcurrentHashMap<>();

    private PositionGeometries() {}

    /** Registers the chunk and server lifecycle listeners on the game event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(PositionGeometries::onChunkUnload);
        gameBus.addListener(PositionGeometries::onLevelUnload);
        gameBus.addListener(PositionGeometries::onServerStopped);
    }

    /**
     * Works out the overrides of a section being copied: every position of it whose state {@code
     * table} flags and whose geometry isn't its state's. Nothing is asked twice while what was worked
     * out still holds. A section with no flagged state costs one look at its palette. Server thread.
     */
    public static Overrides capture(
            ServerLevel level, LevelChunk chunk, LevelChunkSection section, long sectionKey, GeometryTable table) {
        if (section.hasOnlyAir() || !section.maybeHas(table::perPosition)) {
            return Overrides.NONE;
        }
        Map<Long, Worked> held = LEVELS.computeIfAbsent(level, l -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunk.getPos().toLong(), c -> new ConcurrentHashMap<>());
        PalettedContainer<BlockState> states = section.getStates();
        int x0 = SectionKeys.x(sectionKey) << 4;
        int y0 = SectionKeys.y(sectionKey) << 4;
        int z0 = SectionKeys.z(sectionKey) << 4;
        int[] indices = new int[8];
        Geometry[] geometries = new Geometry[8];
        int n = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = states.get(x, y, z);
                    if (!table.perPosition(state)) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x0 + x, y0 + y, z0 + z);
                    Worked worked = held.get(pos.asLong());
                    if (worked == null || worked.table() != table) {
                        worked = work(level, chunk, pos, state, table);
                        held.put(pos.asLong(), worked);
                    }
                    if (worked.geometry() != null) {
                        if (n == indices.length) {
                            indices = Arrays.copyOf(indices, n * 2);
                            geometries = Arrays.copyOf(geometries, n * 2);
                        }
                        indices[n] = SectionKeys.index(x, y, z);
                        geometries[n++] = worked.geometry();
                    }
                }
            }
        }
        return n == 0 ? Overrides.NONE : new Overrides(Arrays.copyOf(indices, n), Arrays.copyOf(geometries, n));
    }

    /** Works out one position: a seal provider's answer, or else its block state's geometry. */
    private static Worked work(
            ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state, GeometryTable table) {
        Geometry base = table.geometry(state);
        BlockEntity blockEntity = state.hasBlockEntity() ? chunk.getBlockEntity(pos) : null;
        return SealProviders.ask(level, pos, state, blockEntity)
                .map(seal -> new Worked(table, SealProviders.geometry(seal, base)))
                .orElseGet(() -> new Worked(table, null));
    }

    /**
     * A position's geometry as far as it is known: what was last worked out there, or its block
     * state's if nothing has been yet, since no zone can hold its cells before its section is copied.
     * Any thread.
     */
    public static Geometry geometry(ServerLevel level, BlockPos pos, BlockState state, GeometryTable table) {
        Geometry base = table.geometry(state);
        if (!base.hasFlag(Geometry.FLAG_POSITIONAL)) {
            return base;
        }
        Map<Long, Map<Long, Worked>> chunks = LEVELS.get(level);
        Map<Long, Worked> held = chunks == null ? null : chunks.get(chunkOf(pos));
        Worked worked = held == null ? null : held.get(pos.asLong());
        return worked == null || worked.geometry() == null ? base : worked.geometry();
    }

    /** Forgets what was worked out at a position, because it changed. Any thread. */
    public static void forget(ServerLevel level, BlockPos pos) {
        Map<Long, Map<Long, Worked>> chunks = LEVELS.get(level);
        Map<Long, Worked> held = chunks == null ? null : chunks.get(chunkOf(pos));
        if (held != null) {
            held.remove(pos.asLong());
        }
    }

    private static long chunkOf(BlockPos pos) {
        return ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Map<Long, Map<Long, Worked>> chunks = LEVELS.get(level);
            if (chunks != null) {
                chunks.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LEVELS.remove(level);
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LEVELS.clear();
    }
}
