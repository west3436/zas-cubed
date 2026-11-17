package io.github.west3436.zas.simulation;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.Pins;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A zone on its way to where a mover put its blocks (API-065, DATA-020): it has left its dimension's
 * graph and not yet arrived. Kept in the destination's {@link
 * io.github.west3436.zas.persistence.DimensionData} from the apply that took it out of the source to
 * the apply that builds it at the destination, so a save in between holds its gas exactly once: the
 * source's chunks no longer list it, and the destination's don't yet.
 *
 * @param key its number in the destination's data
 * @param keepId the id it keeps at the destination, or 0 for a new one
 * @param fromId its id where it left
 * @param from the dimension it left
 * @param mover who moved it
 * @param blocks the block positions of its cells at the destination ({@link SectionKeys#block})
 * @param hints each cell's hint ({@link io.github.west3436.zas.engine.scheduler.Relocation})
 * @param moles its moles by gas id, sorted
 * @param temperature its temperature in K
 * @param pins the owners of its pins
 * @param breached whether it had an ambient edge where it left
 */
record PendingArrival(
        long key,
        long keepId,
        long fromId,
        ResourceKey<Level> from,
        String mover,
        long[] blocks,
        int[] hints,
        Map<String, Double> moles,
        double temperature,
        List<String> pins,
        boolean breached) {
    PendingArrival {
        blocks = blocks.clone();
        hints = hints.clone();
        moles = Collections.unmodifiableMap(new TreeMap<>(moles));
        pins = Pins.of(pins);
        if (blocks.length != hints.length) {
            throw new IllegalArgumentException("one hint per cell");
        }
    }

    @Override
    public long[] blocks() {
        return blocks.clone();
    }

    @Override
    public int[] hints() {
        return hints.clone();
    }

    /** This arrival under another key. */
    PendingArrival withKey(long key) {
        return new PendingArrival(key, keepId, fromId, from, mover, blocks, hints, moles, temperature, pins, breached);
    }

    /** The chunks its cells are in, ascending. */
    long[] chunks() {
        TreeSet<Long> out = new TreeSet<>();
        for (long b : blocks) {
            out.add(SectionKeys.chunkOfBlockKey(b));
        }
        long[] a = new long[out.size()];
        int i = 0;
        for (long c : out) {
            a[i++] = c;
        }
        return a;
    }

    CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.putLong("key", key);
        t.putLong("keep_id", keepId);
        t.putLong("from_id", fromId);
        t.putString("from", from.location().toString());
        t.putString("mover", mover);
        t.putLongArray("blocks", blocks);
        t.putIntArray("hints", hints);
        CompoundTag gas = new CompoundTag();
        moles.forEach(gas::putDouble);
        t.put("moles", gas);
        t.putDouble("temperature", temperature);
        ListTag owners = new ListTag();
        for (String owner : pins) {
            owners.add(StringTag.valueOf(owner));
        }
        t.put("pins", owners);
        t.putBoolean("breached", breached);
        return t;
    }

    /** Reads one back; throws if it can't be. */
    static PendingArrival fromTag(CompoundTag t) {
        CompoundTag gas = t.getCompound("moles");
        Map<String, Double> moles = new TreeMap<>();
        for (String id : gas.getAllKeys()) {
            moles.put(id, gas.getDouble(id));
        }
        ListTag owners = t.getList("pins", Tag.TAG_STRING);
        List<String> pins = new ArrayList<>();
        for (int i = 0; i < owners.size(); i++) {
            pins.add(owners.getString(i));
        }
        return new PendingArrival(
                t.getLong("key"),
                t.getLong("keep_id"),
                t.getLong("from_id"),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("from"))),
                t.getString("mover"),
                t.getLongArray("blocks"),
                t.getIntArray("hints"),
                moles,
                t.getDouble("temperature"),
                pins,
                t.getBoolean("breached"));
    }
}
