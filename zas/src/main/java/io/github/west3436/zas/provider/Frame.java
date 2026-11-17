package io.github.west3436.zas.provider;

import org.jetbrains.annotations.Nullable;

/**
 * Where part of a level shows, for a mod that keeps blocks in one place in their level and shows
 * them in another (COMPAT-101, API-068): Sable keeps a sub-level's blocks in its plot and shows them
 * at the ship's pose. A frame is rigid: a turn, a move and maybe a scale.
 *
 * <p>A point "in the level" is where blocks sit, as zones, cells and airflow's openings name them; a
 * "shown" point is where the world shows it, as players and entities are. Each method returns a new
 * {x, y, z}. Platform-free, so the client's airflow plans can take one ({@link Frames}).
 */
public interface Frame {
    /** Where a point of the level shows. */
    double[] toShown(double x, double y, double z);

    /** The point of the level that shows at a shown point. */
    double[] toLevel(double x, double y, double z);

    /** A direction in the level, turned as the frame shows it; its length is kept. */
    double[] turnToShown(double x, double y, double z);

    /** A shown direction, turned into the level's; its length is kept. */
    double[] turnToLevel(double x, double y, double z);

    /** How far a point of the level moved where it shows in the last game tick, blocks. */
    double[] shownVelocity(double x, double y, double z);

    /** The frame a block column of a level is shown by. */
    @FunctionalInterface
    interface Lookup {
        /** Every block where it is. */
        Lookup NONE = (x, z) -> null;

        /**
         * The frame that shows a block column.
         *
         * @param blockX the column's x
         * @param blockZ its z
         * @return the frame, or null where the column shows where it is
         */
        @Nullable
        Frame at(int blockX, int blockZ);
    }
}
