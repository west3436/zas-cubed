package io.github.west3436.zas.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

/** What a dimension saves for itself, and bringing older saves forward (DATA-003, DATA-012). */
class DimensionDataTest {
    private static final byte[] QUEUED = {1, 2, 3};

    /** A save holding every field, at this version. */
    private static CompoundTag full(int version) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        tag.putLong("next_zone_id", 42);
        tag.putByteArray("queued", QUEUED);
        ListTag owners = new ListTag();
        owners.add(StringTag.valueOf("othermod:habitat"));
        tag.put("released_pins", owners);
        CompoundTag pause = new CompoundTag();
        pause.putString("by", "an operator");
        pause.putString("reason", "testing");
        tag.put("pause", pause);
        return tag;
    }

    private static void holdsEverything(DimensionData d) {
        assertEquals(42, d.nextZoneId());
        assertEquals(Set.of("othermod:habitat"), d.releasedPins());
        assertEquals(new DimensionPause("an operator", "testing"), d.pause());
        assertArrayEquals(QUEUED, d.takeQueued());
    }

    @Test
    void data_012_a_save_reads_back_as_it_was_written() {
        DimensionData d = DimensionData.load(full(1), null);
        CompoundTag written = d.save(new CompoundTag(), null);
        assertEquals(1, written.getInt("version"));
        holdsEverything(DimensionData.load(written, null));
    }

    /** Version 1's saves from before these optional fields were added read with none of them. */
    @Test
    void data_012_a_save_without_the_optional_fields_reads_with_none() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putLong("next_zone_id", 42);
        DimensionData d = DimensionData.load(tag, null);
        assertEquals(42, d.nextZoneId());
        assertEquals(Set.of(), d.releasedPins());
        assertNull(d.pause());
        assertNull(d.takeQueued());
    }

    /** Steps that rename the counter, as a real format change would. */
    private static List<Consumer<CompoundTag>> steps(List<String> ran) {
        return List.of(
                tag -> {
                    ran.add("1 to 2");
                    tag.putLong("next_zone_id", tag.getLong("zone_counter"));
                    tag.remove("zone_counter");
                },
                tag -> ran.add("2 to 3"));
    }

    @Test
    void data_012_steps_run_in_order_from_the_saved_version() {
        List<String> ran = new ArrayList<>();
        CompoundTag oldest = new CompoundTag();
        oldest.putInt("version", 1);
        oldest.putLong("zone_counter", 9);
        DimensionData.migrate(oldest, steps(ran));
        assertEquals(List.of("1 to 2", "2 to 3"), ran);
        assertEquals(9, oldest.getLong("next_zone_id"));
        assertFalse(oldest.contains("zone_counter"));
        assertEquals(3, oldest.getInt("version"));

        ran.clear();
        CompoundTag middle = new CompoundTag();
        middle.putInt("version", 2);
        DimensionData.migrate(middle, steps(ran));
        assertEquals(List.of("2 to 3"), ran);

        ran.clear();
        CompoundTag current = new CompoundTag();
        current.putInt("version", 3);
        DimensionData.migrate(current, steps(ran));
        assertEquals(List.of(), ran);
    }

    /**
     * A save from a newer mod keeps everything this code can read, the zone counter above all:
     * starting it again would reissue ids that chunks have saved (CORE-026).
     */
    @Test
    void data_012_a_newer_version_keeps_what_this_code_can_read() {
        holdsEverything(DimensionData.load(full(99), null));
    }

    /** So does a save whose version is missing, which no code has written. */
    @Test
    void data_012_a_missing_version_keeps_what_this_code_can_read() {
        CompoundTag tag = full(1);
        tag.remove("version");
        holdsEverything(DimensionData.load(tag, null));
    }

    /** No step runs on a version it doesn't lead from. */
    @Test
    void data_012_no_step_runs_on_an_unknown_version() {
        List<String> ran = new ArrayList<>();
        CompoundTag newer = new CompoundTag();
        newer.putInt("version", 4);
        DimensionData.migrate(newer, steps(ran));
        assertEquals(List.of(), ran);
        assertEquals(4, newer.getInt("version"));
    }

    /** Saving after a newer version was read writes the version this code knows (DATA-003). */
    @Test
    void data_003_saving_writes_the_current_version() {
        CompoundTag written = DimensionData.load(full(99), null).save(new CompoundTag(), null);
        assertEquals(1, written.getInt("version"));
    }
}
