package io.github.west3436.zas.atmos.device;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import org.slf4j.Logger;

/**
 * The format version of what a block entity of this mod saves, and how an older save is brought
 * forward (DATA-003, DATA-012). {@link AtmosDevice} does both for nearly every block entity, before
 * any subclass reads the tag, so a subclass only reads and writes its own fields.
 *
 * <p>To change the format, add a step to {@link #STEPS}. The version written follows from the list.
 * A new field that reads as a default when it's missing needs no step.
 *
 * <p>What else this mod saves keeps a list of its own, under the same key, and brings itself forward
 * with {@link #migrate(CompoundTag, List)}: the primed TTV, the multitool's selection and {@link
 * ItemData}.
 */
public final class DeviceData {
    /** The key of the format version, in a block entity's tag. */
    public static final String VERSION_KEY = "FormatVersion";

    /**
     * What brings a save forward, oldest first: entry {@code i} changes version {@code i}'s tag, in
     * place, into version {@code i + 1}'s. Version 0 is a save with no version, from before versions
     * were added, and its fields are those of version 1.
     */
    private static final List<Consumer<CompoundTag>> STEPS = List.of(tag -> {});

    /** The format version this code writes. */
    public static final int VERSION = STEPS.size();

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private DeviceData() {}

    /** Writes the current format version into a tag being saved. */
    public static void writeVersion(CompoundTag tag) {
        tag.putInt(VERSION_KEY, VERSION);
    }

    /** Brings a block entity's saved tag forward to the current format, in place. */
    public static void migrate(CompoundTag tag) {
        migrate(tag, STEPS);
    }

    /**
     * Brings a saved tag forward with the given steps, in place. A newer version, from a newer mod,
     * is left as it is and read by name, as the fields are read one at a time and each has a default.
     * One warning is logged the first time, since a station holds thousands of block entities.
     *
     * @param tag the saved tag
     * @param steps entry {@code i} takes version {@code i} to {@code i + 1}
     * @return whether the tag's version is newer than the steps reach, so it was left as it is
     */
    public static boolean migrate(CompoundTag tag, List<Consumer<CompoundTag>> steps) {
        int version = tag.getInt(VERSION_KEY);
        if (version > steps.size()) {
            if (WARNED.compareAndSet(false, true)) {
                LOGGER.warn(
                        "Something was saved in format {}, and this version of zas_atmos only knows up to {}. "
                                + "It reads the fields it recognises, and any others are lost at the next save",
                        version,
                        steps.size());
            }
            return true;
        }
        for (int v = Math.max(0, version); v < steps.size(); v++) {
            steps.get(v).accept(tag);
        }
        tag.putInt(VERSION_KEY, steps.size());
        return false;
    }
}
