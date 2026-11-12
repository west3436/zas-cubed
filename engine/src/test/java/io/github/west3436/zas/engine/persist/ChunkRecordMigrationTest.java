package io.github.west3436.zas.engine.persist;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.GasOp;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import io.github.west3436.zas.engine.zone.Pins;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;

/**
 * Every earlier format of a chunk's saved atmosphere reads, and saves again as the current one
 * (DATA-012). Formats 1 to 4 were never in a release, so there is no saved world to take them from:
 * these records are written by hand, a byte at a time, as each format laid them out. The first
 * release, 0.1.0, wrote format 5; a world it saved is read by {@code SavedWorldTest} in the mod's
 * tests, and loaded in a running game by the content mod's {@code SavedWorldTests}.
 */
class ChunkRecordMigrationTest {
    private static final GasRegistry GASES =
            GasRegistry.of(List.of(gas("zas:nitrogen", 0.028, 20.8), gas("zas:oxygen", 0.032, 21.0)));
    private static final long CHUNK = SectionKeys.chunk(-3, 7);
    private static final long HELD = SectionKeys.block(-41, 64, 120);
    private static final long TARGET = SectionKeys.block(-40, 64, 120);
    private static final String OWNER = "othermod:habitat";

    private static GasDefinition gas(String id, double molarMass, double heatCapacity) {
        return new GasDefinition(
                id, "gas." + id.replace(':', '.'), id.substring(4), molarMass, heatCapacity, Optional.empty());
    }

    /** One pinned zone with one cell at (-48, 70, 112), sub-cell 1, a held block and an addition. */
    private static byte[] record(int version) throws Exception {
        if (version < 1 || version > 4) {
            throw new AssertionError("add format " + version + " to this test, as DATA-012 needs every one");
        }
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
        out.writeInt(1);
        // Formats 1 and 2 pack 24 bits of x and z, 12 of y and 4 of sub-cell.
        out.writeLong(
                version <= 2
                        ? ((long) (-48 & 0xFFFFFF) << 40) | ((long) 112 << 16) | ((long) 70 << 4) | 1
                        : CellIds.of(-48, 70, 112, 1));
        out.writeDouble(3);
        out.writeDouble(1);
        out.writeDouble(80);
        out.writeInt(1);
        out.writeLong(CHUNK);
        if (version == 2 || version == 3) {
            out.writeBoolean(true); // pinned, with no owner
        } else if (version == 4) {
            out.writeInt(1);
            out.writeUTF(OWNER);
        }
        out.writeInt(1);
        out.writeLong(HELD);
        out.writeInt(1); // one queued addition
        out.writeLong(17);
        out.writeLong(TARGET);
        out.writeDouble(2);
        out.writeDouble(0);
        out.writeDouble(300);
        out.writeUTF(LedgerCategory.API.name());
        out.writeBoolean(true);
        out.writeUTF("zas_atmos:vent");
        out.flush();
        CRC32 crc = new CRC32();
        crc.update(bytes.toByteArray());
        out.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }

    @Test
    void data_012_every_earlier_chunk_format_reads_and_saves_as_the_current_one() throws Exception {
        for (int version = 1; version < ChunkRecord.FORMAT_VERSION; version++) {
            String name = "format " + version;
            byte[] old = record(version);
            assertEquals(version, ChunkRecordCodec.version(old), name);
            ChunkRecord read = ChunkRecordCodec.decode(old, CHUNK, GASES, m -> {
                throw new AssertionError(m);
            });

            ZoneShare zone = read.zones().get(0);
            assertEquals(11, zone.zoneId(), name);
            assertArrayEquals(new long[] {CellIds.of(-48, 70, 112, 1)}, zone.cells(), name);
            assertEquals(3, zone.moles(0), name);
            assertEquals(1, zone.moles(1), name);
            assertEquals(80, zone.temperature(), name);
            assertArrayEquals(new long[] {CHUNK}, zone.zoneChunks(), name);
            List<String> pins = version == 1 ? Pins.NONE : version <= 3 ? Pins.ANONYMOUS_ONLY : List.of(OWNER);
            assertEquals(pins, zone.pins(), name);
            assertArrayEquals(new long[] {HELD}, read.heldBlocks(), name);

            GasOp op = read.queuedAdditions().get(0);
            assertEquals(TARGET, op.block(), name);
            assertEquals(2, op.moles(0), name);
            assertEquals(300, op.temperature(), name);
            assertEquals(-1, op.sub(), name + ": no format before the current one saves a sub-cell");

            byte[] again = ChunkRecordCodec.encode(read, GASES);
            assertEquals(ChunkRecord.FORMAT_VERSION, ChunkRecordCodec.version(again), name);
            assertEquals(read, ChunkRecordCodec.decode(again, CHUNK, GASES, m -> {}), name);
        }
    }
}
