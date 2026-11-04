package io.github.west3436.zas.atmos.scrubber;

import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The gases a scrubber keeps from its sample, named one by one or by gas tag (CONT-015). A tag is
 * read when a gas is tested, so gases a datapack adds to it are selected too (CORE-012).
 *
 * @param gases the gases selected by name
 * @param tags the gas tags selected
 */
public record GasSelection(Set<ResourceLocation> gases, Set<GasTag> tags) implements Predicate<ResourceLocation> {
    /** A scrubber's default selection: CO₂ and {@code zas:toxic} (CONT-015). */
    public static final GasSelection DEFAULT = of(ControllerPreset.SCRUB_GASES, ControllerPreset.SCRUB_TAGS);

    private static final String GASES = "Gases";
    private static final String TAGS = "GasTags";

    public GasSelection {
        gases = Set.copyOf(gases);
        tags = Set.copyOf(tags);
    }

    /** A selection of these gases and tags. */
    public static GasSelection of(Collection<ResourceLocation> gases, Collection<GasTag> tags) {
        return new GasSelection(Set.copyOf(gases), Set.copyOf(tags));
    }

    /** Whether a gas is selected, by name or through a tag. */
    @Override
    public boolean test(ResourceLocation gas) {
        if (gases.contains(gas)) {
            return true;
        }
        for (GasTag tag : tags) {
            if (GasTags.contains(tag, gas)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Writes the selection, in sorted order, under the keys {@code Gases} and {@code GasTags}.
     *
     * @return a tag for {@link #load}
     */
    public CompoundTag save() {
        CompoundTag out = new CompoundTag();
        ListTag names = new ListTag();
        new TreeSet<>(gases).forEach(id -> names.add(StringTag.valueOf(id.toString())));
        out.put(GASES, names);
        ListTag groups = new ListTag();
        tags.stream().map(GasTag::id).sorted().forEach(id -> groups.add(StringTag.valueOf(id.toString())));
        out.put(TAGS, groups);
        return out;
    }

    /**
     * Reads a selection written by {@link #save}. Invalid ids are skipped.
     *
     * @param in the tag
     * @return the selection, or {@link #DEFAULT} if the tag holds none
     */
    public static GasSelection load(CompoundTag in) {
        if (!in.contains(GASES, Tag.TAG_LIST) && !in.contains(TAGS, Tag.TAG_LIST)) {
            return DEFAULT;
        }
        return of(
                ids(in.getList(GASES, Tag.TAG_STRING)),
                ids(in.getList(TAGS, Tag.TAG_STRING)).stream().map(GasTag::new).toList());
    }

    private static List<ResourceLocation> ids(ListTag list) {
        return list.stream()
                .map(t -> ResourceLocation.tryParse(t.getAsString()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
