package io.github.west3436.zas.provider;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.geometry.SubCells;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves a point or a block position to the one cell a query or a mutation there acts on
 * (API-015, D-44).
 *
 * <ul>
 *   <li><b>An exact point</b> uses the sub-cell of its block that contains it ({@link SubCells}).
 *       A point inside the block's collision shape uses the block's nearest cell.
 *   <li><b>A point inside a block with no cells</b> (a full block, which holds no gas) uses the
 *       nearest cell next to it: of the six neighbours, in order of the point's distance to the
 *       face they share (ties in {@link Face} order), the first that has a cell, and of that
 *       neighbour's cells the one nearest the point. With no cell in any neighbour, it stays at its
 *       own block, which has no zone, so it reads as the dimension's ambient (or unresolved, in an
 *       unloaded chunk or a held region) and gas sent there goes to the ambient.
 *   <li><b>A block position</b> uses its largest cell, the first of equals.
 * </ul>
 *
 * <p>Only loaded chunks are read, and none is ever loaded. A point whose block can't be read (an
 * unloaded chunk, or the geometry table being rebuilt) resolves to its block's largest cell.
 * Any thread: off the server thread, the block is read from the chunk the server last made
 * visible, which may be a tick stale.
 */
public final class PointResolver {
    private static final Direction[] DIRECTIONS = Direction.values();

    private PointResolver() {}

    /**
     * One cell: sub-cell {@code sub} of block {@code pos}, or with {@code sub} -1 the block's
     * largest cell.
     *
     * @param pos the block holding the cell
     * @param sub its index among the block's cells (GEO-004), or -1
     * @param cells how many cells the block has, or -1 if it couldn't be read
     */
    public record Cell(BlockPos pos, int sub, int cells) {
        static Cell largestOf(BlockPos pos) {
            return new Cell(pos, -1, -1);
        }
    }

    /** The cell an exact point resolves to. */
    public static Cell resolve(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        GeometryTable table = Geometries.tableIfReady();
        BlockState state = table == null ? null : blockState(level, pos);
        if (state == null) {
            return Cell.largestOf(pos);
        }
        Geometry g = table.geometry(state);
        if (g.cellCount() > 0) {
            return new Cell(pos, cellNear(state, g, pos, point), g.cellCount());
        }
        // No cells here: the nearest neighbour with one.
        double fx = point.x - pos.getX();
        double fy = point.y - pos.getY();
        double fz = point.z - pos.getZ();
        Direction[] order = DIRECTIONS.clone();
        double[] distance = new double[order.length];
        for (Direction d : order) {
            distance[d.get3DDataValue()] = faceDistance(d, fx, fy, fz);
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int c = Double.compare(distance[a.get3DDataValue()], distance[b.get3DDataValue()]);
            return c != 0 ? c : Integer.compare(a.get3DDataValue(), b.get3DDataValue());
        });
        for (Direction d : order) {
            BlockPos next = pos.relative(d);
            BlockState there = blockState(level, next);
            if (there == null) {
                continue;
            }
            Geometry ng = table.geometry(there);
            if (ng.cellCount() > 0) {
                return new Cell(next, cellNear(there, ng, next, point), ng.cellCount());
            }
        }
        return new Cell(pos, -1, 0);
    }

    /** The cell a block position resolves to: its largest. */
    public static Cell resolve(ServerLevel level, BlockPos pos) {
        GeometryTable table = Geometries.tableIfReady();
        BlockState state = table == null ? null : blockState(level, pos);
        if (state == null) {
            return Cell.largestOf(pos);
        }
        Geometry g = table.geometry(state);
        return new Cell(pos, SubCells.largest(g), g.cellCount());
    }

    /**
     * An exact point in a block position's largest cell, which {@link #resolve(ServerLevel, Vec3)}
     * resolves back to that cell: the block's centre, or for a block with several cells (a pane,
     * whose centre is in the glass) the centre of a voxel of its largest cell. For calls that take a
     * point on behalf of a block position, such as the {@code /zas gas} commands.
     */
    public static Vec3 pointIn(ServerLevel level, BlockPos pos) {
        GeometryTable table = Geometries.tableIfReady();
        BlockState state = table == null ? null : blockState(level, pos);
        if (state == null) {
            return Vec3.atCenterOf(pos);
        }
        Geometry g = table.geometry(state);
        int voxel = SubCells.voxelOf(state, g, SubCells.largest(g));
        if (voxel < 0) {
            return Vec3.atCenterOf(pos);
        }
        double n = io.github.west3436.zas.engine.GeometryDeriver.N;
        return new Vec3(
                pos.getX() + ((voxel & 15) + 0.5) / n,
                pos.getY() + ((voxel >>> 8) + 0.5) / n,
                pos.getZ() + (((voxel >>> 4) & 15) + 0.5) / n);
    }

    /** The cell of a block nearest a point, which may lie outside it; -1 for the largest. */
    private static int cellNear(BlockState state, Geometry g, BlockPos pos, Vec3 point) {
        return SubCells.at(
                state,
                g,
                SubCells.voxel(point.x - pos.getX()),
                SubCells.voxel(point.y - pos.getY()),
                SubCells.voxel(point.z - pos.getZ()));
    }

    /** The distance from a point within a block (each coordinate 0 to 1) to one of its faces. */
    private static double faceDistance(Direction d, double fx, double fy, double fz) {
        return switch (d) {
            case DOWN -> fy;
            case UP -> 1 - fy;
            case NORTH -> fz;
            case SOUTH -> 1 - fz;
            case WEST -> fx;
            case EAST -> 1 - fx;
        };
    }

    /**
     * The block at a position if its chunk is loaded, without loading it; null otherwise. Any
     * thread.
     */
    static BlockState blockState(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos)) {
            return null;
        }
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        LevelChunk chunk;
        if (level.getServer().isSameThread()) {
            chunk = level.getChunkSource().getChunkNow(cx, cz);
        } else {
            ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(cx, cz));
            ChunkAccess access = holder == null ? null : holder.getChunkIfPresent(ChunkStatus.FULL);
            chunk = access instanceof LevelChunk c ? c : null;
        }
        return chunk == null ? null : chunk.getBlockState(pos);
    }
}
