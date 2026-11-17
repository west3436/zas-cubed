package io.github.west3436.zas.compat.sable;

import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The air on board (COMPAT-101): a point inside a sub-level, as the world shows it, stands for the
 * point of the sub-level's plot that Sable shows there. ZAS³ asks this wherever it resolves a point
 * to a cell, so an entity on board breathes the cabin, and its HUD, its hazards, its fire and the
 * multitool's readout read it; where the plot's point is in the open air, the point is where it is.
 *
 * <p>Server thread.
 */
final class Aboard {
    /** How far around the point to look for a sub-level's bounds, in blocks. */
    private static final double REACH = 1.0E-3;

    private Aboard() {}

    /**
     * The plot points a point in the world stands for, one for each sub-level whose bounds hold it.
     *
     * @param level the level
     * @param point the point, as the world shows it
     * @return the points, maybe none
     */
    static List<Vec3> project(ServerLevel level, Vec3 point) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.getLoadedCount() == 0 || container.inBounds(BlockPos.containing(point))) {
            // No sub-levels, or the point is in the plots' own grid already.
            return List.of();
        }
        BoundingBox3d around = new BoundingBox3d(
                point.x - REACH, point.y - REACH, point.z - REACH, point.x + REACH, point.y + REACH, point.z + REACH);
        List<Vec3> out = null;
        for (SubLevel sub : container.queryIntersecting(around)) {
            if (sub.isRemoved()) {
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(1);
            }
            out.add(sub.logicalPose().transformPositionInverse(point));
        }
        return out == null ? List.of() : out;
    }
}
