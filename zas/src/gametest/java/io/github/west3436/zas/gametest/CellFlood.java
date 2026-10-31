package io.github.west3436.zas.gametest;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.GeometryPalette;
import io.github.west3436.zas.geometry.GeometryTable;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A test-only flood fill over cells, using the geometry table's seal test. It stands in for the
 * enclosure detection that comes later (GEO-020) so GameTests can check that a room is sealed.
 */
final class CellFlood {
    /** A cell: a position (relative to the test) and the cell's index in its geometry. */
    record Node(BlockPos pos, int cell) {}

    /**
     * @param reached cells reached inside the bounds
     * @param escaped whether gas could leave the bounds
     */
    record Result(Set<Node> reached, boolean escaped) {
        boolean reachedPos(BlockPos pos) {
            return reached.stream().anyMatch(n -> n.pos().equals(pos));
        }
    }

    private CellFlood() {}

    /** Floods from cell {@code cell} at {@code start}, staying inside {@code bounds} (relative). */
    static Result flood(GameTestHelper h, GeometryTable table, BlockPos start, int cell, BoundingBox bounds) {
        GeometryPalette palette = table.palette();
        Set<Node> reached = new HashSet<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Node first = new Node(start, cell);
        reached.add(first);
        queue.add(first);
        boolean escaped = false;
        while (!queue.isEmpty()) {
            Node n = queue.poll();
            int id = table.geometryId(h.getBlockState(n.pos()));
            Geometry.Cell c = palette.geometry(id).cell(n.cell());
            for (Direction d : Direction.values()) {
                Face face = Face.of(d.get3DDataValue());
                if (!c.reaches(face)) {
                    continue;
                }
                BlockPos next = n.pos().relative(d);
                int nid = table.geometryId(h.getBlockState(next));
                for (int j = 0; j < palette.geometry(nid).cellCount(); j++) {
                    if (!palette.connects(id, n.cell(), face, nid, j)) {
                        continue;
                    }
                    if (!bounds.isInside(next)) {
                        escaped = true;
                        continue;
                    }
                    Node m = new Node(next, j);
                    if (reached.add(m)) {
                        queue.add(m);
                    }
                }
            }
        }
        return new Result(reached, escaped);
    }
}
