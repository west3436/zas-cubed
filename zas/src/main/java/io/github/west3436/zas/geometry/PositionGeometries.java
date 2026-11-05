package io.github.west3436.zas.geometry;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.capability.SealGeometry;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.SectionKeys;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * The geometry of positions whose geometry isn't simply their block state's (architecture §4.1):
 *
 * <ul>
 *   <li>blocks with a seal provider (API-023), which answer for themselves;
 *   <li>a moving piston, which has the geometry of the state it settles into, from the start of the
 *       motion to its end (GEO-042);
 *   <li>blocks whose collision shape depends on position or data, such as bamboo's offset, whose
 *       shape is read in the level (GEO-043).
 * </ul>
 *
 * <p><b>When.</b> It is worked out on the server thread when a section is copied for an atmos tick,
 * and travels with the copy as the snapshot's overrides (API-028). It is kept per position until the
 * block changes (GEO-010), {@code markDirty} is called (GEO-014) or the geometry table is rebuilt, so
 * a section copied again asks nothing again. A chunk's entries go when it unloads, since a chunk
 * loading is copied afresh.
 *
 * <p>A shape that animates isn't followed: what is read is kept until one of those happens. A moving
 * piston is the one case handled for vanilla; a shulker box settles closed, which is the geometry it
 * has from its block state (GEO-042).
 *
 * <p>Any thread may read what is kept ({@link #geometry}, {@link #cellOf}); only the server thread
 * works anything out.
 */
public final class PositionGeometries {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The overrides of one section copy: section indices, ascending, and the geometry at each. */
    public record Overrides(int[] indices, Geometry[] geometries) {
        static final Overrides NONE = new Overrides(new int[0], new Geometry[0]);
    }

    /**
     * What was worked out at a position, and from which table; a null geometry means its block state's.
     *
     * @param cellOf the geometry's voxel to cell map if it has several cells ({@link
     *     GeometryDeriver#cellOfVoxels}), or else null
     */
    private record Worked(GeometryTable table, Geometry geometry, byte[] cellOf) {}

    /** A collision shape's geometry, and its voxel map if it has several cells. */
    private record Shape(Geometry geometry, byte[] cellOf) {
        static Shape of(BitSet voxels) {
            Geometry geometry = GeometryDeriver.derive(voxels);
            return new Shape(geometry, geometry.cellCount() > 1 ? GeometryDeriver.cellOfVoxels(voxels) : null);
        }
    }

    /** By level, then chunk, then packed position. */
    private static final Map<ServerLevel, Map<Long, Map<Long, Worked>>> LEVELS = new ConcurrentHashMap<>();

    /** Shapes by their voxels: bamboo has a few shapes among all its positions. Worked out once each. */
    private static final Map<BitSet, Shape> SHAPES = new ConcurrentHashMap<>();

    private static volatile boolean warnedOfBrokenShape;

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

    /**
     * Works out one position: the state a moving piston settles into, a seal provider's answer, or
     * else the shape the block has here.
     */
    private static Worked work(
            ServerLevel level, LevelChunk chunk, BlockPos pos, BlockState state, GeometryTable table) {
        Geometry base = table.geometry(state);
        BlockEntity blockEntity = state.hasBlockEntity() ? chunk.getBlockEntity(pos) : null;
        if (blockEntity instanceof PistonMovingBlockEntity moving) {
            BlockState settled = moving.getMovedState();
            Geometry geometry = table.geometry(settled);
            return new Worked(table, geometry, SubCells.cellOf(settled, geometry));
        }
        Optional<SealGeometry> seal = SealProviders.ask(level, pos, state, blockEntity);
        if (seal.isPresent()) {
            return new Worked(table, SealProviders.geometry(seal.get(), base), null);
        }
        return shaped(level, pos, state, base, table);
    }

    /**
     * Works out the shape a block has at a position (GEO-041, GEO-043), with the heat, fire and
     * boundary data of its block state's geometry. A shape that comes out as the block state's has,
     * which is every position of most blocks flagged, needs no override. A fluid seals whatever the
     * shape (GEO-006).
     */
    private static Worked shaped(
            ServerLevel level, BlockPos pos, BlockState state, Geometry base, GeometryTable table) {
        if (base.hasFlag(Geometry.FLAG_FLUID)) {
            return new Worked(table, null, null);
        }
        Shape shape;
        try {
            BitSet voxels = GeometryDerivation.rasterize(state.getCollisionShape(level, pos, CollisionContext.empty()));
            shape = SHAPES.computeIfAbsent(voxels, Shape::of);
        } catch (RuntimeException e) {
            // A broken modded shape mustn't stop the server; its block state's geometry stands.
            if (!warnedOfBrokenShape) {
                warnedOfBrokenShape = true;
                LOGGER.warn("Couldn't work out the geometry of {} at {}; using its block state's", state, pos, e);
            }
            return new Worked(table, null, null);
        }
        int flags = Geometry.FLAG_POSITIONAL;
        for (Face f : Face.values()) {
            if (base.hasFlag(Geometry.boundaryFlag(f))) {
                flags |= Geometry.boundaryFlag(f);
            }
        }
        if (shape.geometry().equals(Geometry.AIR)) {
            flags |= Geometry.FLAG_OCCUPIED;
        }
        Geometry geometry = shape.geometry().withDataOf(base).withFlags(flags);
        return geometry.equals(base) ? new Worked(table, null, null) : new Worked(table, geometry, shape.cellOf());
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
        Worked worked = worked(level, pos);
        return worked == null || worked.geometry() == null ? base : worked.geometry();
    }

    /**
     * The voxel to cell map of a position's own geometry ({@link GeometryDeriver#cellOfVoxels}) if it
     * has several cells of its own (API-015). Otherwise null, and its cells, if any, are its block
     * state's ({@link SubCells}). Any thread.
     */
    public static byte[] cellOf(ServerLevel level, BlockPos pos, BlockState state, GeometryTable table) {
        if (!table.perPosition(state)) {
            return null;
        }
        Worked worked = worked(level, pos);
        return worked == null ? null : worked.cellOf();
    }

    private static Worked worked(ServerLevel level, BlockPos pos) {
        Map<Long, Map<Long, Worked>> chunks = LEVELS.get(level);
        Map<Long, Worked> held = chunks == null ? null : chunks.get(chunkOf(pos));
        return held == null ? null : held.get(pos.asLong());
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
        SHAPES.clear();
    }
}
