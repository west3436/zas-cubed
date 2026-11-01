package io.github.west3436.zas.engine.persist;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.GasOp;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/** The saved form of a chunk's atmosphere (DATA-003, DATA-004). */
class ChunkRecordCodecTest {
    private static final GasRegistry GASES =
            GasRegistry.of(List.of(gas("zas:nitrogen", 0.028, 20.8), gas("zas:oxygen", 0.032, 21.0)));
    private static final long CHUNK = SectionKeys.chunk(-3, 7);

    private static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(4), molarMass, heatCapacity, Optional.empty());
    }

    private static ChunkRecord sample() {
        ZoneShare a = new ZoneShare(
                4, new long[] {5, 3, 9}, new double[] {1.25, 0.5}, 293.15, new long[] {CHUNK, SectionKeys.chunk(-2, 7)
                });
        ZoneShare b = new ZoneShare(11, new long[] {42}, new double[] {0, 1e-7}, 80, new long[] {CHUNK}, true);
        GasOp op = new GasOp(
                17,
                GasOp.Kind.ADD,
                SectionKeys.block(-40, 64, 120),
                new double[] {2, 0},
                300,
                LedgerCategory.DEVICE,
                true,
                "zas_atmos:vent");
        return new ChunkRecord(CHUNK, List.of(a, b), new long[] {SectionKeys.block(-41, 64, 120)}, List.of(op));
    }

    /** The format version is written, and a record reads back exactly as it was (DATA-003). */
    @Test
    void data_003_version_is_written_and_read() throws Exception {
        byte[] bytes = ChunkRecordCodec.encode(sample(), GASES);
        assertEquals(ChunkRecord.FORMAT_VERSION, ChunkRecordCodec.version(bytes));
        ChunkRecord back = ChunkRecordCodec.decode(bytes, CHUNK, GASES, m -> {
            throw new AssertionError(m);
        });
        assertEquals(sample(), back);
    }

    /** A version this code doesn't know is refused, not misread (DATA-003, DATA-004). */
    @Test
    void data_003_unknown_version_is_refused() {
        byte[] bytes = ChunkRecordCodec.encode(sample(), GASES);
        bytes[7] = 99;
        assertEquals(99, ChunkRecordCodec.version(bytes));
        assertThrows(
                ChunkRecordCodec.CorruptRecordException.class,
                () -> ChunkRecordCodec.decode(bytes, CHUNK, GASES, m -> {}));
    }

    /** Damage anywhere, truncation, or another chunk's data is detected (DATA-004). */
    @Test
    void data_004_damage_is_detected() {
        byte[] bytes = ChunkRecordCodec.encode(sample(), GASES);
        for (int i = 0; i < bytes.length; i += 7) {
            byte[] damaged = bytes.clone();
            damaged[i] ^= 0x10;
            assertThrows(
                    ChunkRecordCodec.CorruptRecordException.class,
                    () -> ChunkRecordCodec.decode(damaged, CHUNK, GASES, m -> {}),
                    "byte " + i);
        }
        byte[] truncated = java.util.Arrays.copyOf(bytes, bytes.length / 2);
        assertThrows(
                ChunkRecordCodec.CorruptRecordException.class,
                () -> ChunkRecordCodec.decode(truncated, CHUNK, GASES, m -> {}));
        assertThrows(
                ChunkRecordCodec.CorruptRecordException.class,
                () -> ChunkRecordCodec.decode(bytes, CHUNK + 1, GASES, m -> {}));
        assertThrows(
                ChunkRecordCodec.CorruptRecordException.class,
                () -> ChunkRecordCodec.decode(new byte[0], CHUNK, GASES, m -> {}));
    }

    /** Gases are saved by id, so a reordered registry reads them right, and a missing one is dropped. */
    @Test
    void gases_are_saved_by_id() throws Exception {
        byte[] bytes = ChunkRecordCodec.encode(sample(), GASES);
        GasRegistry reordered = GasRegistry.of(List.of(gas("zas:oxygen", 0.032, 21.0), gas("zas:argon", 0.040, 12.5)));
        List<String> warnings = new ArrayList<>();
        ChunkRecord back = ChunkRecordCodec.decode(bytes, CHUNK, reordered, warnings::add);
        assertEquals(0.5, back.zones().get(0).moles(reordered.indexOf("zas:oxygen")));
        assertEquals(0, back.zones().get(0).moles(reordered.indexOf("zas:argon")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("zas:nitrogen"));
    }

    /**
     * A gas the registry no longer defines is dropped from every zone and queued operation, with
     * one warning that names the gas, the total amount, the chunk and each place it was; the other
     * gases read back unchanged (CORE-013, CORE-016).
     */
    @Test
    void core_016_removed_gas_is_dropped_with_a_warning() throws Exception {
        byte[] bytes = ChunkRecordCodec.encode(sample(), GASES);
        GasRegistry oxygenOnly = GasRegistry.of(List.of(gas("zas:oxygen", 0.032, 21.0)));
        List<String> warnings = new ArrayList<>();
        ChunkRecord back = ChunkRecordCodec.decode(bytes, CHUNK, oxygenOnly, warnings::add);

        assertEquals(1, warnings.size(), warnings.toString());
        String warning = warnings.get(0);
        assertTrue(warning.startsWith("Dropped 3.25 mol of zas:nitrogen saved in chunk [-3, 7]"), warning);
        assertTrue(warning.contains("1.25 mol in zone 4 at "), warning);
        assertTrue(warning.contains("2 mol in a queued addition at -40 64 120"), warning);
        // Zone 11 held none of it, so it isn't named.
        assertFalse(warning.contains("zone 11"), warning);

        assertEquals(2, back.zones().size());
        assertEquals(1, back.zones().get(0).moles().length);
        assertEquals(0.5, back.zones().get(0).moles(0));
        assertEquals(1e-7, back.zones().get(1).moles(0));
        assertEquals(293.15, back.zones().get(0).temperature());
        assertEquals(1, back.queuedAdditions().size());
        assertEquals(0, back.queuedAdditions().get(0).moles()[0]);

        // Saved again with the registry it now has, the record holds no trace of the gas.
        byte[] resaved = ChunkRecordCodec.encode(back, oxygenOnly);
        List<String> again = new ArrayList<>();
        assertEquals(back, ChunkRecordCodec.decode(resaved, CHUNK, oxygenOnly, again::add));
        assertEquals(List.of(), again);
    }

    /** A removed gas that no zone held any of is dropped without a warning (CORE-013). */
    @Test
    void core_013_removed_gas_with_no_moles_is_silent() throws Exception {
        ZoneShare zone = new ZoneShare(4, new long[] {5}, new double[] {0, 0.5}, 293.15, new long[] {CHUNK});
        ChunkRecord record = new ChunkRecord(CHUNK, List.of(zone), new long[0], List.of());
        GasRegistry oxygenOnly = GasRegistry.of(List.of(gas("zas:oxygen", 0.032, 21.0)));
        List<String> warnings = new ArrayList<>();
        ChunkRecord back =
                ChunkRecordCodec.decode(ChunkRecordCodec.encode(record, GASES), CHUNK, oxygenOnly, warnings::add);
        assertEquals(List.of(), warnings);
        assertEquals(0.5, back.zones().get(0).moles(0));
    }

    /** A zone's pin is saved and read back (D-64, API-018). */
    @Test
    void d_064_pin_is_saved() throws Exception {
        ChunkRecord back = ChunkRecordCodec.decode(ChunkRecordCodec.encode(sample(), GASES), CHUNK, GASES, m -> {
            throw new AssertionError(m);
        });
        assertFalse(back.zones().get(0).pinned());
        assertTrue(back.zones().get(1).pinned());
    }

    /** A record in format 1 or 2, by hand: one zone with these cells, in the old cell-id layout. */
    private static byte[] oldRecord(int version, long[] cells, boolean pinned) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x5A415333);
        out.writeInt(version);
        out.writeLong(CHUNK);
        out.writeInt(2);
        out.writeUTF("zas:nitrogen");
        out.writeUTF("zas:oxygen");
        out.writeInt(1); // one zone
        out.writeLong(11);
        out.writeInt(cells.length);
        for (long c : cells) {
            out.writeLong(c);
        }
        out.writeDouble(3);
        out.writeDouble(1);
        out.writeDouble(80);
        out.writeInt(1);
        out.writeLong(CHUNK);
        if (version >= 2) {
            out.writeBoolean(pinned);
        }
        out.writeInt(0); // no held blocks
        out.writeInt(0); // no queued additions
        out.flush();
        CRC32 crc = new CRC32();
        crc.update(bytes.toByteArray());
        out.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }

    /** An id in the cell-id layout of formats 1 and 2: 24 bits of x and z, 12 of y, 4 of sub-cell. */
    private static long format2(int x, int y, int z, int sub) {
        return ((long) (x & 0xFFFFFF) << 40) | ((long) (z & 0xFFFFFF) << 16) | ((long) (y & 0xFFF) << 4) | sub;
    }

    /** Version 1 records, saved before pins, still read, their zones unpinned (DATA-003, D-64). */
    @Test
    void d_064_version_1_reads_unpinned() throws Exception {
        byte[] bytes = oldRecord(1, new long[] {format2(-48, 70, 112, 1)}, false);
        ChunkRecord back = ChunkRecordCodec.decode(bytes, CHUNK, GASES, m -> {
            throw new AssertionError(m);
        });
        ZoneShare expected = new ZoneShare(
                11, new long[] {CellIds.of(-48, 70, 112, 1)}, new double[] {3, 1}, 80, new long[] {CHUNK});
        assertEquals(List.of(expected), back.zones());
        assertFalse(back.zones().get(0).pinned());
    }

    /**
     * Version 2 records still read, their cells converted to the current layout (#105); cells it
     * can't name are left out, and the share's gas is kept (DATA-003, architecture §4.5).
     */
    @Test
    void data_003_version_2_cells_convert() throws Exception {
        long[] old = {
            format2(-48, 70, 112, 0),
            format2(-48, 70, 112, 3),
            format2(-8_388_608, -64, 8_388_607, 1),
            format2(-48, 1500, 112, 0), // above the current y range
            format2(-47, 70, 112, 4) // a fifth sub-cell
        };
        byte[] bytes = oldRecord(2, old, true);
        assertEquals(2, ChunkRecordCodec.version(bytes));
        ChunkRecord back = ChunkRecordCodec.decode(bytes, CHUNK, GASES, m -> {
            throw new AssertionError(m);
        });
        ZoneShare share = back.zones().get(0);
        long[] expected = {
            CellIds.of(-48, 70, 112, 0), CellIds.of(-48, 70, 112, 3), CellIds.of(-8_388_608, -64, 8_388_607, 1)
        };
        Arrays.sort(expected);
        assertArrayEquals(expected, share.cells());
        assertTrue(share.pinned());
        assertEquals(3, share.moles(0));
        assertEquals(1, share.moles(1));
        // Written again, it is a current record and reads back the same.
        byte[] again = ChunkRecordCodec.encode(back, GASES);
        assertEquals(ChunkRecord.FORMAT_VERSION, ChunkRecordCodec.version(again));
        assertEquals(back, ChunkRecordCodec.decode(again, CHUNK, GASES, m -> {}));
    }

    /** Cells at the world border are saved and read back as they are (#105). */
    @Test
    void data_003_cells_at_the_world_border() throws Exception {
        long[] cells = {
            CellIds.of(29_999_984, 319, -29_999_984, 3),
            CellIds.of(-29_999_984, -64, 29_999_984, 0),
            CellIds.of(CellIds.MAX_XZ, CellIds.MAX_Y, CellIds.MIN_XZ, CellIds.MAX_SUB_CELLS - 1)
        };
        ChunkRecord record = new ChunkRecord(
                CHUNK,
                List.of(new ZoneShare(7, cells, new double[] {1, 2}, 290, new long[] {CHUNK})),
                new long[0],
                List.of());
        ChunkRecord back = ChunkRecordCodec.decode(ChunkRecordCodec.encode(record, GASES), CHUNK, GASES, m -> {});
        assertEquals(record, back);
    }
}
