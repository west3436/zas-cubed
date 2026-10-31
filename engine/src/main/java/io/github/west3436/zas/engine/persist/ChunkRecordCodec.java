package io.github.west3436.zas.engine.persist;

import io.github.west3436.zas.engine.EngineLog;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.GasOp;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * The saved form of a {@link ChunkRecord} (DATA-002, DATA-003, DATA-004): a versioned,
 * checksummed byte string the platform stores in the chunk's data.
 *
 * <p>Layout, big-endian, as {@link DataOutputStream} writes it:
 *
 * <ol>
 *   <li>magic {@code "ZAS3"} and the format version ({@link ChunkRecord#FORMAT_VERSION});
 *   <li>the gas table: a count, then each gas id; every amount below is listed in this order, so
 *       saves don't depend on the order of the gas registry;
 *   <li>the zones: a count, then per zone its id, its cells, its moles per gas, its temperature,
 *       the chunks it spans and, from version 2, whether it is pinned (D-64);
 *   <li>the held block positions (DATA-010);
 *   <li>the queued additions (DATA-011): per operation its original sequence number, block,
 *       moles per gas, temperature, ledger category, whether it triggers a zone, and its source;
 *   <li>a CRC-32 of everything before it.
 * </ol>
 *
 * <p>Only what can't be rebuilt is written: no edges, seal masks, sub-cell shapes or hotspots
 * (DATA-009). Version 1, which has no pins, is still read, its zones unpinned. Reading checks the
 * magic, the version, the checksum and every value; anything wrong
 * throws {@link CorruptRecordException}, and the caller resets the chunk to unzoned (DATA-004).
 * Gases the registry no longer has are dropped with a warning (CORE-013).
 */
public final class ChunkRecordCodec {
    private static final int MAGIC = 0x5A415333; // "ZAS3"
    private static final int MAX_COUNT = 1 << 24;
    /** The first format version, without zone pins. */
    private static final int VERSION_1 = 1;

    /**
     * The chunk key of the level-wide record, which holds queued additions whose target chunk
     * wasn't loaded at shutdown (DATA-011, architecture §4.5).
     */
    public static final long LEVEL_WIDE = Long.MIN_VALUE;

    private ChunkRecordCodec() {}

    /** Thrown when saved bytes can't be read (DATA-004). */
    public static final class CorruptRecordException extends Exception {
        public CorruptRecordException(String message) {
            super(message);
        }
    }

    /** Encodes a record, naming gases by their ids in {@code registry}. */
    public static byte[] encode(ChunkRecord record, GasRegistry registry) {
        Objects.requireNonNull(record, "record");
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(MAGIC);
            out.writeInt(ChunkRecord.FORMAT_VERSION);
            out.writeLong(record.chunk());
            int gases = registry.size();
            out.writeInt(gases);
            for (int i = 0; i < gases; i++) {
                out.writeUTF(registry.definition(i).id());
            }
            out.writeInt(record.zones().size());
            for (ZoneShare z : record.zones()) {
                out.writeLong(z.zoneId());
                writeLongs(out, z.cells());
                writeMoles(out, z.moles(), gases);
                out.writeDouble(z.temperature());
                writeLongs(out, z.zoneChunks());
                out.writeBoolean(z.pinned());
            }
            writeLongs(out, record.heldBlocks());
            out.writeInt(record.queuedAdditions().size());
            for (GasOp op : record.queuedAdditions()) {
                out.writeLong(op.seq());
                out.writeLong(op.block());
                writeMoles(out, op.moles(), gases);
                out.writeDouble(op.temperature());
                out.writeUTF(op.category().name());
                out.writeBoolean(op.triggersZone());
                out.writeUTF(op.source());
            }
            out.flush();
            CRC32 crc = new CRC32();
            crc.update(bytes.toByteArray());
            out.writeInt((int) crc.getValue());
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e); // a ByteArrayOutputStream doesn't throw
        }
    }

    /** The format version saved bytes carry, or -1 if they don't start like a record. */
    public static int version(byte[] data) {
        if (data == null || data.length < 8) {
            return -1;
        }
        int magic = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        if (magic != MAGIC) {
            return -1;
        }
        return ((data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16) | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
    }

    /**
     * Decodes a record, mapping gas ids to {@code registry}'s indices.
     *
     * @param chunk the chunk the data was read from; it must match the saved one
     * @param log receives a warning per gas the registry lacks, whose amounts are dropped
     * @throws CorruptRecordException if the data is damaged, of another chunk, or of a version this
     *     code can't read
     */
    public static ChunkRecord decode(byte[] data, long chunk, GasRegistry registry, EngineLog log)
            throws CorruptRecordException {
        Objects.requireNonNull(registry, "registry");
        if (data == null || data.length < 12) {
            throw new CorruptRecordException("too short");
        }
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length - 4);
        int stored = ((data[data.length - 4] & 0xFF) << 24)
                | ((data[data.length - 3] & 0xFF) << 16)
                | ((data[data.length - 2] & 0xFF) << 8)
                | (data[data.length - 1] & 0xFF);
        if (stored != (int) crc.getValue()) {
            throw new CorruptRecordException("checksum mismatch");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, data.length - 4));
            if (in.readInt() != MAGIC) {
                throw new CorruptRecordException("not a zas atmos record");
            }
            int version = in.readInt();
            if (version != ChunkRecord.FORMAT_VERSION && version != VERSION_1) {
                throw new CorruptRecordException("unknown format version " + version);
            }
            long savedChunk = in.readLong();
            if (savedChunk != chunk) {
                throw new CorruptRecordException("saved for another chunk");
            }
            int gases = count(in);
            int[] index = new int[gases];
            for (int i = 0; i < gases; i++) {
                String id = in.readUTF();
                index[i] = registry.indexOf(id);
                if (index[i] < 0) {
                    log.warn("Saved atmosphere holds " + id + ", which the gas registry lacks; it is dropped");
                }
            }
            int zoneCount = count(in);
            List<ZoneShare> zones = new ArrayList<>(Math.min(zoneCount, 64));
            for (int z = 0; z < zoneCount; z++) {
                long id = in.readLong();
                long[] cells = readLongs(in);
                double[] moles = readMoles(in, gases, index, registry.size());
                double temperature = in.readDouble();
                long[] chunks = readLongs(in);
                boolean pinned = version != VERSION_1 && in.readBoolean();
                zones.add(new ZoneShare(id, cells, moles, temperature, chunks, pinned));
            }
            long[] held = readLongs(in);
            int opCount = count(in);
            List<GasOp> ops = new ArrayList<>(Math.min(opCount, 64));
            for (int o = 0; o < opCount; o++) {
                long seq = in.readLong();
                long block = in.readLong();
                double[] moles = readMoles(in, gases, index, registry.size());
                double temperature = in.readDouble();
                LedgerCategory category = LedgerCategory.valueOf(in.readUTF());
                boolean triggers = in.readBoolean();
                String source = in.readUTF();
                ops.add(new GasOp(seq, GasOp.Kind.ADD, block, moles, temperature, category, triggers, source));
            }
            if (in.available() != 0) {
                throw new CorruptRecordException("trailing bytes");
            }
            return new ChunkRecord(chunk, zones, held, ops);
        } catch (EOFException e) {
            throw new CorruptRecordException("truncated");
        } catch (IOException e) {
            throw new CorruptRecordException("unreadable: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new CorruptRecordException("invalid value: " + e.getMessage());
        }
    }

    private static int count(DataInputStream in) throws IOException, CorruptRecordException {
        int n = in.readInt();
        if (n < 0 || n > MAX_COUNT) {
            throw new CorruptRecordException("bad count " + n);
        }
        return n;
    }

    private static void writeLongs(DataOutputStream out, long[] values) throws IOException {
        out.writeInt(values.length);
        for (long v : values) {
            out.writeLong(v);
        }
    }

    private static long[] readLongs(DataInputStream in) throws IOException, CorruptRecordException {
        int n = count(in);
        if (n > in.available() / 8) {
            throw new CorruptRecordException("truncated array");
        }
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = in.readLong();
        }
        return out;
    }

    private static void writeMoles(DataOutputStream out, double[] moles, int gases) throws IOException {
        for (int i = 0; i < gases; i++) {
            out.writeDouble(i < moles.length ? moles[i] : 0);
        }
    }

    private static double[] readMoles(DataInputStream in, int gases, int[] index, int size) throws IOException {
        double[] out = new double[size];
        for (int i = 0; i < gases; i++) {
            double n = in.readDouble();
            if (index[i] >= 0) {
                out[index[i]] = n;
            }
        }
        return out;
    }
}
