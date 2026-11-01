package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What one chunk saves of the atmosphere (DATA-001, DATA-002, D-54): only what can't be rebuilt
 * from the blocks. Edges, seal masks and sub-cell shapes are derived again on load (DATA-009).
 *
 * <p>The server thread builds a record when the chunk is saved, from the published snapshot
 * ({@link DimensionPipeline#recordFor}), and hands the record read back from a loaded chunk to the
 * pipeline ({@link DimensionPipeline#chunkLoaded(long, ChunkRecord)}), which passes it to the
 * worker in the next {@link Batch}. Immutable.
 *
 * @param chunk the chunk ({@link SectionKeys#chunk})
 * @param zones the chunk's share of each zone with cells in it, in ascending id order
 * @param heldBlocks block positions in the chunk whose changes were held for a paused zone and not
 *     yet processed (GEO-037, DATA-010), ascending
 * @param queuedAdditions gas additions targeting the chunk that were queued but not applied when
 *     the server stopped (DATA-011), in their original sequence order; empty in every other save
 */
public record ChunkRecord(long chunk, List<ZoneShare> zones, long[] heldBlocks, List<GasOp> queuedAdditions) {

    /**
     * The current format version (DATA-003), written by {@code ChunkRecordCodec}. Version 2 adds
     * each zone's pin (D-64); version 3 stores cells in the wider cell-id layout that reaches the
     * world border (#105). Versions 1 and 2 are still read: version 1's zones unpinned, and both
     * versions' cells converted.
     */
    public static final int FORMAT_VERSION = 3;

    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if zones repeat or aren't in id order, or a queued
     *     operation isn't an addition
     */
    public ChunkRecord {
        zones = List.copyOf(zones);
        for (int i = 1; i < zones.size(); i++) {
            if (zones.get(i).zoneId() <= zones.get(i - 1).zoneId()) {
                throw new IllegalArgumentException("zones must be in ascending id order without repeats");
            }
        }
        heldBlocks = Membership.sortedDistinct(heldBlocks);
        List<GasOp> ops = new ArrayList<>(queuedAdditions);
        for (GasOp op : ops) {
            if (op.kind() != GasOp.Kind.ADD) {
                throw new IllegalArgumentException("only additions are saved (DATA-011): " + op);
            }
        }
        ops.sort((a, b) -> Long.compare(a.seq(), b.seq()));
        queuedAdditions = List.copyOf(ops);
    }

    /** An empty record. */
    public static ChunkRecord empty(long chunk) {
        return new ChunkRecord(chunk, List.of(), new long[0], List.of());
    }

    /** Whether the record holds nothing, so the chunk needs no saved atmosphere. */
    public boolean isEmpty() {
        return zones.isEmpty() && heldBlocks.length == 0 && queuedAdditions.isEmpty();
    }

    /** This record without its queued additions. */
    public ChunkRecord withoutQueued() {
        return queuedAdditions.isEmpty() ? this : new ChunkRecord(chunk, zones, heldBlocks, List.of());
    }

    /** The share of a zone, or null. */
    public ZoneShare share(long zoneId) {
        for (ZoneShare z : zones) {
            if (z.zoneId() == zoneId) {
                return z;
            }
        }
        return null;
    }

    @Override
    public long[] heldBlocks() {
        return heldBlocks.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChunkRecord r
                && chunk == r.chunk
                && zones.equals(r.zones)
                && Arrays.equals(heldBlocks, r.heldBlocks)
                && queuedAdditions.equals(r.queuedAdditions);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(chunk) * 31 + zones.hashCode();
    }

    @Override
    public String toString() {
        return "ChunkRecord[" + SectionKeys.chunkX(chunk) + ", " + SectionKeys.chunkZ(chunk) + ": " + zones.size()
                + " zones, " + heldBlocks.length + " held blocks, " + queuedAdditions.size() + " queued additions]";
    }
}
