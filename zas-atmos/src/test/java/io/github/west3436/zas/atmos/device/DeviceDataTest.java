package io.github.west3436.zas.atmos.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The format version of what block entities save, and bringing older saves forward (DATA-012). */
class DeviceDataTest {
    private static CompoundTag saved(int version) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(DeviceData.VERSION_KEY, version);
        return tag;
    }

    /** Steps that rename a field, as a real format change would. */
    private static List<Consumer<CompoundTag>> renames(List<String> ran) {
        return List.of(
                tag -> ran.add("0 to 1"),
                tag -> {
                    ran.add("1 to 2");
                    tag.putDouble("Setting", tag.getDouble("OldSetting"));
                    tag.remove("OldSetting");
                },
                tag -> ran.add("2 to 3"));
    }

    @Test
    void data_012_the_version_is_written_and_a_current_save_is_unchanged() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Setting", 5);
        DeviceData.writeVersion(tag);
        assertEquals(DeviceData.VERSION, tag.getInt(DeviceData.VERSION_KEY));
        CompoundTag copy = tag.copy();
        DeviceData.migrate(tag);
        assertEquals(copy, tag);
    }

    /** Everything saved before versions were added has none, and its fields still read. */
    @Test
    void data_012_a_save_with_no_version_reads_as_the_first_format() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("Setting", 5);
        DeviceData.migrate(tag);
        assertEquals(5, tag.getDouble("Setting"));
        assertEquals(DeviceData.VERSION, tag.getInt(DeviceData.VERSION_KEY));
    }

    @Test
    void data_012_steps_run_in_order_from_the_saved_version() {
        List<String> ran = new ArrayList<>();
        CompoundTag oldest = new CompoundTag();
        oldest.putDouble("OldSetting", 7);
        assertFalse(DeviceData.migrate(oldest, renames(ran)));
        assertEquals(List.of("0 to 1", "1 to 2", "2 to 3"), ran);
        assertEquals(7, oldest.getDouble("Setting"));
        assertFalse(oldest.contains("OldSetting"));
        assertEquals(3, oldest.getInt(DeviceData.VERSION_KEY));

        ran.clear();
        assertFalse(DeviceData.migrate(saved(2), renames(ran)));
        assertEquals(List.of("2 to 3"), ran);

        ran.clear();
        assertFalse(DeviceData.migrate(saved(3), renames(ran)));
        assertEquals(List.of(), ran);
    }

    /** A negative version can only be damage: it runs every step, as a save with none does. */
    @Test
    void data_012_a_negative_version_runs_every_step() {
        List<String> ran = new ArrayList<>();
        assertFalse(DeviceData.migrate(saved(-4), renames(ran)));
        assertEquals(3, ran.size());
    }

    /** A save from a newer mod is left as it is, for its fields to be read by name. */
    @Test
    void data_012_a_newer_version_is_left_as_it_is() {
        List<String> ran = new ArrayList<>();
        CompoundTag newer = saved(4);
        newer.putDouble("Setting", 9);
        CompoundTag copy = newer.copy();
        assertTrue(DeviceData.migrate(newer, renames(ran)));
        assertEquals(List.of(), ran);
        assertEquals(copy, newer);
    }
}
