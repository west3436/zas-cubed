package io.github.west3436.zas.provider;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryDeriver;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.geometry.Geometries;
import io.github.west3436.zas.geometry.GeometryTable;
import io.github.west3436.zas.geometry.PositionGeometries;
import io.github.west3436.zas.geometry.SubCells;
import io.github.west3436.zas.simulation.AtmosService;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
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
 *   <li><b>An exact point</b> uses the sub-cell of its block that contains it ({@link SubCells}, or
 *       for a block with a shape of its own at that position, {@link PositionGeometries#cellOf}). A
 *       point inside the block's collision shape uses the block's nearest cell.
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

    /**
     * Where a point in a level may really be, for a mod that keeps blocks somewhere other than where
     * they show (COMPAT-101): Sable shows a sub-level's blocks in the world while they sit in the
     * level at its plot, so a point inside a sub-level's cabin is a point in that plot. Server thread.
     */
    @FunctionalInterface
    public interface Projection {
        /**
         * The points a point may stand for, first the likeliest; empty when it stands for none.
         *
         * @param level the level
         * @param point the point, as the world shows it
         */
        List<Vec3> project(ServerLevel level, Vec3 point);
    }

    private static final List<Projection> PROJECTIONS = new CopyOnWriteArrayList<>();

    /**
     * Adds a projection (COMPAT-101). From then on, on the server thread, an exact point that a
     * projection maps onto a cell of a zone, or onto a cell whose atmosphere isn't known yet,
     * resolves to that cell; a point it maps onto the open air resolves where it is.
     */
    public static void addProjection(Projection projection) {
        PROJECTIONS.add(Objects.requireNonNull(projection, "projection"));
    }

    /**
     * The cell an exact point resolves to. On the server thread a {@link Projection} may say the
     * point stands for one elsewhere in the level: the first such cell that is in a zone, or whose
     * atmosphere isn't known yet, is taken.
     */
    public static Cell resolve(ServerLevel level, Vec3 point) {
        if (!PROJECTIONS.isEmpty() && level.getServer().isSameThread()) {
            for (Projection projection : PROJECTIONS) {
                for (Vec3 there : projection.project(level, point)) {
                    Cell cell = resolveHere(level, there);
                    if (!inOpenAir(level, cell)) {
                        return cell;
                    }
                }
            }
        }
        return resolveHere(level, point);
    }

    /**
     * Whether a cell is plainly in the open air: in no zone, in a loaded chunk whose zones are known,
     * and not held at a breach. True as well where no chunk is loaded, since no block stands there,
     * and without an engine for the level.
     */
    private static boolean inOpenAir(ServerLevel level, Cell cell) {
        AtmosService service = AtmosService.current();
        DimensionAtmos d = service == null ? null : service.dimension(level);
        AtmosSnapshot table = d == null ? null : d.published();
        if (table == null) {
            return true;
        }
        long block = cell.pos().asLong();
        if (table.zoneAt(block, cell.sub()) != null) {
            return false;
        }
        long chunk = ChunkPos.asLong(cell.pos().getX() >> 4, cell.pos().getZ() >> 4);
        if (!d.isChunkLoaded(chunk)) {
            return true;
        }
        return !d.isSettling(chunk) && !table.heldAt(block, cell.sub());
    }

    /** The cell an exact point resolves to where it is. */
    private static Cell resolveHere(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        GeometryTable table = Geometries.tableIfReady();
        BlockState state = table == null ? null : blockState(level, pos);
        if (state == null) {
            return Cell.largestOf(pos);
        }
        Geometry g = PositionGeometries.geometry(level, pos, state, table);
        if (g.cellCount() > 0) {
            return new Cell(pos, cellNear(level, table, state, g, pos, point), g.cellCount());
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
            Geometry ng = PositionGeometries.geometry(level, next, there, table);
            if (ng.cellCount() > 0) {
                return new Cell(next, cellNear(level, table, there, ng, next, point), ng.cellCount());
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
        Geometry g = PositionGeometries.geometry(level, pos, state, table);
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
        Geometry g = PositionGeometries.geometry(level, pos, state, table);
        int largest = SubCells.largest(g);
        byte[] own = g.cellCount() > 1 ? PositionGeometries.cellOf(level, pos, state, table) : null;
        int voxel = own != null ? SubCells.voxelOf(own, largest) : SubCells.voxelOf(state, g, largest);
        if (voxel < 0) {
            return Vec3.atCenterOf(pos);
        }
        double n = GeometryDeriver.N;
        return new Vec3(
                pos.getX() + ((voxel & 15) + 0.5) / n,
                pos.getY() + ((voxel >>> 8) + 0.5) / n,
                pos.getZ() + (((voxel >>> 4) & 15) + 0.5) / n);
    }

    /** The cell of a block nearest a point, which may lie outside it; -1 for the largest. */
    private static int cellNear(
            ServerLevel level, GeometryTable table, BlockState state, Geometry g, BlockPos pos, Vec3 point) {
        int x = SubCells.voxel(point.x - pos.getX());
        int y = SubCells.voxel(point.y - pos.getY());
        int z = SubCells.voxel(point.z - pos.getZ());
        byte[] own = g.cellCount() > 1 ? PositionGeometries.cellOf(level, pos, state, table) : null;
        if (own == null) {
            return SubCells.at(state, g, x, y, z);
        }
        // Off the server thread the geometry and the map are two reads, which a change may fall between.
        int cell = own[GeometryDeriver.index(x, y, z)];
        return cell < g.cellCount() ? cell : -1;
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
