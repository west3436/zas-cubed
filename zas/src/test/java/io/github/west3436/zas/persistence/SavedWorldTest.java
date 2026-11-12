package io.github.west3436.zas.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.west3436.zas.engine.EngineLog;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.persist.ChunkRecordCodec;
import io.github.west3436.zas.engine.scheduler.ChunkRecord;
import io.github.west3436.zas.engine.scheduler.ZoneShare;
import io.github.west3436.zas.reaction.ReactionFormatTest;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * The framework's part of a world saved by each release reads with this code (DATA-012): the
 * atmosphere saved in each chunk, and the dimension's own data. The content mod's part is read by
 * its {@code SavedWorldDataTest}, and its GameTest {@code SavedWorldTests} loads the same world in a
 * running game. {@link SavedWorld} says what a fixture holds and how to make the next release's.
 *
 * <p>The amounts are compared with what the release's console printed, which has six significant
 * digits. 0.1.0's station has three zones: R, pinned by its area air controller, and Q, both in one
 * chunk, and P across two.
 */
class SavedWorldTest {
    private static final SavedWorld RELEASE_0_1_0 = SavedWorld.of("0.1.0");

    /** The chunk format 0.1.0 wrote. */
    private static final int CHUNK_FORMAT_0_1_0 = 5;

    private static final EngineLog NO_WARNINGS = message -> {
        throw new AssertionError(message);
    };

    /** A number as a release's console prints it: six significant digits. */
    private static String printed(double value) {
        return new BigDecimal(value)
                .round(new MathContext(6))
                .stripTrailingZeros()
                .toPlainString();
    }

    @Test
    void data_012_chunk_records_saved_by_0_1_0_read_with_what_their_zones_held() throws Exception {
        GasRegistry gases = ReactionFormatTest.shippedGases();
        JsonObject expected = RELEASE_0_1_0.expected();
        List<ChunkRecord> records = new ArrayList<>();
        for (JsonElement c : expected.getAsJsonArray("chunks")) {
            int x = c.getAsJsonArray().get(0).getAsInt();
            int z = c.getAsJsonArray().get(1).getAsInt();
            String name = "chunk " + x + ", " + z;
            long chunk = SectionKeys.chunk(x, z);
            byte[] saved = RELEASE_0_1_0.chunk(x, z).getByteArray(AtmosPersistence.TAG);
            assertEquals(CHUNK_FORMAT_0_1_0, ChunkRecordCodec.version(saved), name);
            ChunkRecord read = ChunkRecordCodec.decode(saved, chunk, gases, NO_WARNINGS);
            assertEquals(0, read.heldBlocks().length, name);
            assertEquals(List.of(), read.queuedAdditions(), name);

            byte[] again = ChunkRecordCodec.encode(read, gases);
            assertEquals(ChunkRecord.FORMAT_VERSION, ChunkRecordCodec.version(again), name);
            assertEquals(read, ChunkRecordCodec.decode(again, chunk, gases, NO_WARNINGS), name);
            records.add(read);
        }

        Set<Long> saved = new TreeSet<>();
        records.forEach(r -> r.zones().forEach(z -> saved.add(z.zoneId())));
        Set<Long> told = new TreeSet<>();
        Map<String, Integer> spans = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e :
                expected.getAsJsonObject("zones").entrySet()) {
            String name = "zone " + e.getKey();
            JsonObject zone = e.getValue().getAsJsonObject();
            long id = zone.get("id").getAsLong();
            told.add(id);
            List<String> pins = new ArrayList<>();
            zone.getAsJsonArray("pins").forEach(p -> pins.add(p.getAsString()));

            List<ZoneShare> shares = new ArrayList<>();
            Set<Long> chunks = new TreeSet<>();
            for (ChunkRecord r : records) {
                ZoneShare share = r.share(id);
                if (share != null) {
                    shares.add(share);
                    chunks.add(r.chunk());
                }
            }
            spans.put(e.getKey(), chunks.size());
            int cells = 0;
            double[] moles = new double[gases.size()];
            for (ZoneShare share : shares) {
                cells += share.cellCount();
                for (int i = 0; i < moles.length; i++) {
                    moles[i] += share.moles(i);
                }
                assertEquals(zone.get("temperature").getAsString(), printed(share.temperature()), name);
                assertEquals(pins, share.pins(), name);
                assertArrayEquals(
                        chunks.stream().mapToLong(Long::longValue).toArray(), share.zoneChunks(), name + "'s chunks");
            }
            assertEquals(zone.get("cells").getAsInt(), cells, name);

            Map<String, String> held = new LinkedHashMap<>();
            double total = 0;
            for (int i = 0; i < moles.length; i++) {
                if (moles[i] > 0) {
                    held.put(gases.definition(i).id(), printed(moles[i]));
                    total += moles[i];
                }
            }
            Map<String, String> gasesTold = new LinkedHashMap<>();
            zone.getAsJsonObject("gases")
                    .entrySet()
                    .forEach(g -> gasesTold.put(g.getKey(), g.getValue().getAsString()));
            assertEquals(new TreeMap<>(gasesTold), new TreeMap<>(held), name + "'s gases");
            assertEquals(zone.get("moles").getAsString(), printed(total), name + "'s moles");
        }
        assertEquals(told, saved, "the zones saved in the chunks");
        assertEquals(Map.of("P", 2, "Q", 1, "R", 1), spans, "the chunks each zone is saved across");
    }

    @Test
    void data_012_the_dimension_data_saved_by_0_1_0_reads() {
        CompoundTag saved = RELEASE_0_1_0.data(DimensionData.NAME);
        assertEquals(1, saved.getInt("version"));
        long highest = 0;
        for (JsonElement zone :
                RELEASE_0_1_0.expected().getAsJsonObject("zones").asMap().values()) {
            highest = Math.max(highest, zone.getAsJsonObject().get("id").getAsLong());
        }

        DimensionData read = DimensionData.load(saved.copy(), null);
        assertEquals(highest + 1, read.nextZoneId(), "the next zone id follows the ids saved in the chunks");
        assertEquals(Set.of(), read.releasedPins());
        assertNull(read.pause());
        assertNull(read.takeQueued());

        // Saved again, it is this code's version and holds the same.
        CompoundTag again = read.save(new CompoundTag(), null);
        assertEquals(highest + 1, DimensionData.load(again, null).nextZoneId());
    }
}
