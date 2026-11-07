package io.github.west3436.zas.atmos.device;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * The format version of what this mod's data components save on an item (DATA-003, DATA-012). A
 * value saves as {@code {FormatVersion: 1, value: ...}}, with the value as its component's own codec
 * writes it. A value saved before versions were added is the value alone, and reads as the first
 * format, so a component's old saves, recipes and commands all still read.
 *
 * <p>The version is only in the saved form. A component's value in memory has none, so an item read
 * from an old save and the same item made now are equal and stack. What's synced to clients goes
 * through the component's stream codec, which this doesn't touch.
 */
public final class ItemData {
    /** The key of a saved value, beside {@link DeviceData#VERSION_KEY}. */
    public static final String VALUE_KEY = "value";

    /** The steps of a format that hasn't changed: a value with no version is already in it. */
    private static final List<Consumer<CompoundTag>> FIRST_FORMAT = List.of(tag -> {});

    private ItemData() {}

    /**
     * A component's persistent codec, for a format that hasn't changed since versions were added.
     *
     * @param format what writes and reads the value itself
     * @return the codec that saves it with a version
     */
    public static <A> Codec<A> versioned(Codec<A> format) {
        return versioned(format, FIRST_FORMAT);
    }

    /**
     * A component's persistent codec. To change its format, give it steps, and the version written
     * follows from the list.
     *
     * @param format what writes and reads the value in the current format
     * @param steps what brings a save forward, oldest first: entry {@code i} changes version {@code
     *     i}'s tag, in place, into version {@code i + 1}'s, with the value at {@link #VALUE_KEY}.
     *     Version 0 is a value with no version
     * @return the codec that saves it with a version
     */
    public static <A> Codec<A> versioned(Codec<A> format, List<Consumer<CompoundTag>> steps) {
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encode(A value, DynamicOps<T> ops, T prefix) {
                return format.encodeStart(ops, value)
                        .flatMap(saved -> ops.mapBuilder()
                                .add(DeviceData.VERSION_KEY, ops.createInt(steps.size()))
                                .add(VALUE_KEY, saved)
                                .build(prefix));
            }

            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                CompoundTag tag = saved(ops.convertTo(NbtOps.INSTANCE, input));
                DeviceData.migrate(tag, steps);
                Tag value = tag.get(VALUE_KEY);
                if (value == null) {
                    return DataResult.error(() -> "No " + VALUE_KEY + " in saved item data: " + tag);
                }
                return format.parse(ops, NbtOps.INSTANCE.convertTo(ops, value)).map(a -> Pair.of(a, ops.empty()));
            }
        };
    }

    /** A saved value as the steps read it; one with no version has its value wrapped. */
    private static CompoundTag saved(Tag input) {
        if (input instanceof CompoundTag tag && tag.contains(DeviceData.VERSION_KEY)) {
            return tag;
        }
        CompoundTag tag = new CompoundTag();
        tag.put(VALUE_KEY, input);
        return tag;
    }
}
