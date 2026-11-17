package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.Pins;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Carrying whole zones along when another mod moves their blocks (API-065, GEO-046, GEO-047): the
 * operations a batch brings and what they produced. Plain data, immutable.
 *
 * <p>A move is a hand-over in three steps. {@link Begin} suspends every zone wholly inside a box:
 * the zones pause (GEO-030), so they keep their gas, and block changes touching them are held
 * (GEO-037) while the mover clears and places blocks. {@link Complete} takes them out of the graph,
 * each as a {@link Departure} whose cells are already where they arrive. The server hands each
 * departure to the destination's engine as an {@link Arrive}, which rebuilds the zone there as a zone
 * back from chunk storage is rebuilt (DATA-002): it is classified again before it resumes, so a
 * destination that doesn't enclose it opens it as any opening would. {@link Cancel} lets the zones
 * resume where they are.
 *
 * <p><b>Cells and rotation.</b> A departure lists each cell by the block position it arrives at and a
 * <i>hint</i> that tells sub-cells apart: -1 where the block had one cell, which arrives as the
 * destination block's largest cell; otherwise one bit of a face the sub-cell reaches ({@link
 * #hint}), turned with the move. The destination's engine takes the sub-cell that reaches that bit,
 * so the inside half of a pane stays inside, whichever way the block was turned.
 */
public final class Relocation {
    private Relocation() {}

    /** An operation of a move; it shares the sequence numbers of {@link GasOp}s. */
    public sealed interface Op permits Begin, Complete, Cancel, Arrive {
        long seq();
    }

    /**
     * Suspends the zones wholly inside a box under a ticket. It fails whole if any of them is paused
     * already, is another ticket's, or if together they have more than {@code maxCells} cells.
     *
     * @param seq the sequence number
     * @param ticket the ticket, unique within the server
     * @param box the box
     * @param maxCells the most cells the zones may have between them
     */
    public record Begin(long seq, long ticket, Box box, int maxCells) implements Op {
        public Begin {
            Objects.requireNonNull(box, "box");
            if (maxCells < 0) {
                throw new IllegalArgumentException("maxCells must be 0 or more: " + maxCells);
            }
        }
    }

    /**
     * Takes a ticket's suspended zones out of the graph, as departures moved by {@code transform}.
     *
     * @param seq the sequence number
     * @param ticket the ticket
     * @param transform where the cells go
     */
    public record Complete(long seq, long ticket, Transform transform) implements Op {
        public Complete {
            Objects.requireNonNull(transform, "transform");
        }
    }

    /**
     * Lets a ticket's zones resume where they are.
     *
     * @param seq the sequence number
     * @param ticket the ticket
     */
    public record Cancel(long seq, long ticket) implements Op {}

    /**
     * A zone arriving: rebuilt from a departure in this engine.
     *
     * @param seq the sequence number
     * @param keepId the id to give the zone if it is free in this graph and was issued by it, as when
     *     a zone moves within its dimension; 0 for a new id
     * @param fromId the zone's id where it came from, for its event
     * @param blocks the block positions of its cells ({@link SectionKeys#block}), one per cell
     * @param hints each cell's {@linkplain Relocation hint}
     * @param moles its moles per gas, indexed by this engine's registry
     * @param temperature its temperature in K
     * @param pins the owners of its pins (D-64)
     * @param breached whether it had an ambient edge when it left, so a destination that leaves it
     *     open is no news
     */
    public record Arrive(
            long seq,
            long keepId,
            long fromId,
            long[] blocks,
            int[] hints,
            double[] moles,
            double temperature,
            List<String> pins,
            boolean breached)
            implements Op {
        public Arrive {
            blocks = blocks.clone();
            hints = hints.clone();
            if (blocks.length != hints.length) {
                throw new IllegalArgumentException("one hint per cell: " + blocks.length + " cells, " + hints.length);
            }
            moles = moles.clone();
            for (double n : moles) {
                if (!Double.isFinite(n) || n < 0) {
                    throw new IllegalArgumentException("moles must be finite and >= 0: " + n);
                }
            }
            if (!Double.isFinite(temperature) || temperature < 0) {
                throw new IllegalArgumentException("temperature must be finite and >= 0 K: " + temperature);
            }
            pins = Pins.of(pins);
        }

        @Override
        public long[] blocks() {
            return blocks.clone();
        }

        @Override
        public int[] hints() {
            return hints.clone();
        }

        @Override
        public double[] moles() {
            return moles.clone();
        }

        /** The number of cells, without copying. */
        public int cellCount() {
            return blocks.length;
        }

        /** Cell {@code i}'s block position, without copying. */
        public long block(int i) {
            return blocks[i];
        }

        /** Cell {@code i}'s hint, without copying. */
        public int hint(int i) {
            return hints[i];
        }
    }

    /**
     * A box of block positions, both corners inside.
     *
     * @param minX the least x
     * @param minY the least y
     * @param minZ the least z
     * @param maxX the greatest x
     * @param maxY the greatest y
     * @param maxZ the greatest z
     */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (maxX < minX || maxY < minY || maxZ < minZ) {
                throw new IllegalArgumentException("a box's greatest corner can't be below its least");
            }
        }

        /** Whether a block position ({@link SectionKeys#block}) is inside. */
        public boolean contains(long block) {
            int x = SectionKeys.blockX(block);
            int y = SectionKeys.blockY(block);
            int z = SectionKeys.blockZ(block);
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        /** Whether a chunk ({@link SectionKeys#chunk}) holds any of the box. */
        public boolean touchesChunk(long chunk) {
            int cx = SectionKeys.chunkX(chunk);
            int cz = SectionKeys.chunkZ(chunk);
            return cx >= minX >> 4 && cx <= maxX >> 4 && cz >= minZ >> 4 && cz <= maxZ >> 4;
        }
    }

    /**
     * Where a move takes block positions: {@code to + R(p - from)}, with R a turn of {@code
     * quarterTurns} quarter turns clockwise seen from above, about the vertical axis, as Minecraft's
     * {@code BlockPos.rotate} turns a position. So the block at {@code from} goes to {@code to}.
     *
     * @param fromX the pivot's x
     * @param fromY the pivot's y
     * @param fromZ the pivot's z
     * @param toX where the pivot goes, x
     * @param toY y
     * @param toZ z
     * @param quarterTurns clockwise quarter turns, 0 to 3
     */
    public record Transform(int fromX, int fromY, int fromZ, int toX, int toY, int toZ, int quarterTurns) {
        /** The move that changes nothing. */
        public static final Transform IDENTITY = new Transform(0, 0, 0, 0, 0, 0, 0);

        public Transform {
            if (quarterTurns < 0 || quarterTurns > 3) {
                throw new IllegalArgumentException("quarterTurns must be 0 to 3: " + quarterTurns);
            }
        }

        /** A move by an offset, without turning. */
        public static Transform offset(int dx, int dy, int dz) {
            return new Transform(0, 0, 0, dx, dy, dz, 0);
        }

        /** Where a block position ({@link SectionKeys#block}) goes. */
        public long block(long block) {
            int dx = SectionKeys.blockX(block) - fromX;
            int dy = SectionKeys.blockY(block) - fromY;
            int dz = SectionKeys.blockZ(block) - fromZ;
            int rx;
            int rz;
            switch (quarterTurns) {
                case 1 -> {
                    rx = -dz;
                    rz = dx;
                }
                case 2 -> {
                    rx = -dx;
                    rz = -dz;
                }
                case 3 -> {
                    rx = dz;
                    rz = -dx;
                }
                default -> {
                    rx = dx;
                    rz = dz;
                }
            }
            return SectionKeys.block(toX + rx, toY + dy, toZ + rz);
        }

        /** A cell's hint turned with the move; -1 stays -1. */
        public int hint(int hint) {
            if (hint < 0 || quarterTurns == 0) {
                return hint;
            }
            int[] voxel = voxel(hint);
            int x = voxel[0];
            int y = voxel[1];
            int z = voxel[2];
            for (int i = 0; i < quarterTurns; i++) {
                int nx = 15 - z;
                z = x;
                x = nx;
            }
            return hintAt(turn(Face.of(hint >>> 8), quarterTurns), x, y, z);
        }
    }

    /** The hint naming bit {@code (u, v)} of face {@code face} of a block's cell. */
    public static int hint(Face face, int u, int v) {
        if (u < 0 || u > 15 || v < 0 || v > 15) {
            throw new IndexOutOfBoundsException("(" + u + ", " + v + ")");
        }
        return face.ordinal() << 8 | v << 4 | u;
    }

    /** The face a hint names. */
    public static Face hintFace(int hint) {
        return Face.of(hint >>> 8);
    }

    /** The u coordinate of a hint's bit. */
    public static int hintU(int hint) {
        return hint & 15;
    }

    /** The v coordinate of a hint's bit. */
    public static int hintV(int hint) {
        return (hint >>> 4) & 15;
    }

    /** The voxel on the face a hint names, at depth 0, as {x, y, z}. */
    private static int[] voxel(int hint) {
        int u = hintU(hint);
        int v = hintV(hint);
        return switch (hintFace(hint)) {
            case DOWN -> new int[] {u, 0, v};
            case UP -> new int[] {u, 15, v};
            case NORTH -> new int[] {u, v, 0};
            case SOUTH -> new int[] {u, v, 15};
            case WEST -> new int[] {0, v, u};
            case EAST -> new int[] {15, v, u};
        };
    }

    /** The hint of the voxel at depth 0 of a face. */
    private static int hintAt(Face face, int x, int y, int z) {
        return switch (face) {
            case DOWN, UP -> hint(face, x, z);
            case NORTH, SOUTH -> hint(face, x, y);
            case WEST, EAST -> hint(face, z, y);
        };
    }

    /** A face turned clockwise, seen from above, by quarter turns. */
    static Face turn(Face face, int quarterTurns) {
        Face f = face;
        for (int i = 0; i < quarterTurns; i++) {
            f = switch (f) {
                case NORTH -> Face.EAST;
                case EAST -> Face.SOUTH;
                case SOUTH -> Face.WEST;
                case WEST -> Face.NORTH;
                default -> f;
            };
        }
        return f;
    }

    /** What an operation of a move did. */
    public enum Status {
        /** {@link Begin}: the zones are suspended. */
        BEGUN,
        /** {@link Complete}: the zones left; {@link Outcome#departed()} lists them. */
        DEPARTED,
        /** {@link Cancel}: the zones resume where they are. */
        CANCELLED,
        /** {@link Arrive}: the zone is rebuilt here, as {@link Outcome#arrivedId()}. */
        ARRIVED,
        /**
         * {@link Arrive}: no cell of the zone could be had here, so its gas was added at its first
         * cell's position instead, as a gas addition there would be.
         */
        ADDED,
        /** {@link Begin}: a zone in the box is paused, or the whole dimension is. Nothing changed. */
        PAUSED,
        /** {@link Begin}: a zone in the box is another ticket's. Nothing changed. */
        BUSY,
        /** {@link Begin}: the zones have more cells than the operation allows. Nothing changed. */
        TOO_LARGE,
        /**
         * {@link Complete}: a zone of the ticket has a chunk unloaded, or has left memory. Nothing
         * left, and the ticket stays open.
         */
        UNLOADED,
        /** {@link Complete}, {@link Cancel}: the ticket isn't open here. Nothing changed. */
        ENDED
    }

    /**
     * A zone that left: everything needed to rebuild it where it arrives.
     *
     * @param zoneId its id here
     * @param blocks the block positions its cells arrive at, one per cell
     * @param hints each cell's hint, turned with the move
     * @param moles its moles per gas, by this engine's registry
     * @param temperature its temperature in K
     * @param pins the owners of its pins
     * @param breached whether it had an ambient edge
     */
    public record Departure(
            long zoneId,
            long[] blocks,
            int[] hints,
            double[] moles,
            double temperature,
            List<String> pins,
            boolean breached) {
        public Departure {
            blocks = blocks.clone();
            hints = hints.clone();
            moles = moles.clone();
            pins = Pins.of(pins);
        }

        @Override
        public long[] blocks() {
            return blocks.clone();
        }

        @Override
        public int[] hints() {
            return hints.clone();
        }

        @Override
        public double[] moles() {
            return moles.clone();
        }

        /** The number of cells, without copying. */
        public int cellCount() {
            return blocks.length;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Departure d
                    && zoneId == d.zoneId
                    && Arrays.equals(blocks, d.blocks)
                    && Arrays.equals(hints, d.hints)
                    && Arrays.equals(moles, d.moles)
                    && Double.compare(temperature, d.temperature) == 0
                    && pins.equals(d.pins)
                    && breached == d.breached;
        }

        @Override
        public int hashCode() {
            return Long.hashCode(zoneId) * 31 + Arrays.hashCode(blocks);
        }
    }

    /**
     * What an operation of a move did.
     *
     * @param seq its sequence number
     * @param status what happened
     * @param zones {@link Begin}: the zones suspended, ascending; {@link Cancel}: those resuming;
     *     {@link Complete}: those that left, or with {@link Status#UNLOADED} those still suspended;
     *     {@link Arrive}: the zone here; otherwise empty
     * @param straddling {@link Begin}: the zones with cells both inside and outside the box, which stay,
     *     ascending; otherwise empty
     * @param departed {@link Complete}: the zones that left, in id order
     * @param arrivedId {@link Arrive}: the zone's id here, or 0
     * @param component the exchange component the operation belongs to, or -1 (architecture §3.3): a
     *     departure is taken back with its component if that is rejected
     */
    public record Outcome(
            long seq,
            Status status,
            long[] zones,
            long[] straddling,
            List<Departure> departed,
            long arrivedId,
            int component) {
        public Outcome {
            Objects.requireNonNull(status, "status");
            zones = zones.clone();
            straddling = straddling.clone();
            departed = List.copyOf(departed);
        }

        /** An outcome that lists nothing. */
        static Outcome of(long seq, Status status) {
            return new Outcome(seq, status, new long[0], new long[0], List.of(), 0, -1);
        }

        @Override
        public long[] zones() {
            return zones.clone();
        }

        @Override
        public long[] straddling() {
            return straddling.clone();
        }

        /** This outcome in an exchange component. */
        Outcome inComponent(int index) {
            return new Outcome(seq, status, zones, straddling, departed, arrivedId, index);
        }
    }
}
