package io.github.west3436.zas.engine.persist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.LedgerCategory;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.GasOp;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        ZoneShare b = new ZoneShare(11, new long[] {42}, new double[] {0, 1e-7}, 80, new long[] {CHUNK});
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
}
