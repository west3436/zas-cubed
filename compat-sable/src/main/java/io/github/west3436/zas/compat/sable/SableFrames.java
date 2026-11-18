package io.github.west3436.zas.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinhelpers.CanFallAtleastHelper;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import io.github.west3436.zas.provider.Frame;
import io.github.west3436.zas.provider.Frames;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

/**
 * Where the world shows a sub-level's plot (COMPAT-101, API-068): each sub-level is a {@link Frame}
 * at its pose. Airflow finds a cabin's openings near a player through it, pushes along the flow as
 * the ship turns it, and the client places the hiss, the wind and the puffs at the hole on the ship.
 * {@code Relocations.shownAt} gives players coordinates on the ship rather than in its plot, and
 * airflow's crawl sees the hull in the way.
 *
 * <p>Both sides: Sable keeps sub-levels in the client's level too, with their poses. The level's own
 * thread.
 */
final class SableFrames implements Frames.Source {
    @Override
    public @Nullable Frame at(Level level, int blockX, int blockZ) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        if (container == null || !container.inBounds(chunkX, chunkZ)) {
            return null;
        }
        LevelPlot plot = container.getPlot(chunkX, chunkZ);
        SubLevel sub = plot == null ? null : plot.getSubLevel();
        return sub == null || sub.isRemoved() ? null : new Placed(sub);
    }

    @Override
    public List<Frame> near(Level level, AABB shown) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.getLoadedCount() == 0) {
            return List.of();
        }
        BoundingBox3d box = new BoundingBox3d(shown.minX, shown.minY, shown.minZ, shown.maxX, shown.maxY, shown.maxZ);
        List<Frame> out = null;
        for (SubLevel sub : container.queryIntersecting(box)) {
            if (sub.isRemoved()) {
                continue;
            }
            if (out == null) {
                out = new ArrayList<>(1);
            }
            out.add(new Placed(sub));
        }
        return out == null ? List.of() : out;
    }

    /**
     * Whether the box meets a sub-level's blocks, by the check Sable itself makes when a player
     * stands up from crawling: Sable adds it to vanilla's {@code canPlayerFitWithinBlocksAndEntitiesWhen}
     * only, so airflow's crawl asks it here (FLOW-019).
     */
    @Override
    public boolean collides(Level level, AABB shown) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null || container.getLoadedCount() == 0) {
            return false;
        }
        return CanFallAtleastHelper.canFallAtleastWithSubLevels(level, shown) != null;
    }

    /** A sub-level, read at its pose as it is when asked. */
    private record Placed(SubLevel sub) implements Frame {
        @Override
        public double[] toShown(double x, double y, double z) {
            return array(sub.logicalPose().transformPosition(new Vector3d(x, y, z)));
        }

        @Override
        public double[] toLevel(double x, double y, double z) {
            return array(sub.logicalPose().transformPositionInverse(new Vector3d(x, y, z)));
        }

        @Override
        public double[] turnToShown(double x, double y, double z) {
            return array(sub.logicalPose().orientation().transform(new Vector3d(x, y, z)));
        }

        @Override
        public double[] turnToLevel(double x, double y, double z) {
            return array(sub.logicalPose().orientation().transformInverse(new Vector3d(x, y, z)));
        }

        @Override
        public double[] shownVelocity(double x, double y, double z) {
            Pose3dc now = sub.logicalPose();
            Pose3dc last = sub.lastPose();
            Vector3d at = new Vector3d(x, y, z);
            return array(now.transformPosition(at, new Vector3d()).sub(last.transformPosition(at, new Vector3d())));
        }

        private static double[] array(Vector3d v) {
            return new double[] {v.x, v.y, v.z};
        }
    }
}
