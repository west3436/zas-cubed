package io.github.west3436.zas.atmos.filter;

import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * One entry a filter's GUI offers: a gas tag or a gas (CONT-013). The server makes the list when the
 * GUI opens, and the client sends back only an entry's place in it, so a client can't select
 * anything the list doesn't hold (CONT-074).
 *
 * <p>The API can't list every gas or tag, so the list is the tags ZAS³ ships, their gases, standard
 * air's gases, the gases in the filter's input line and whatever is already selected. A datapack gas
 * in none of those appears once it is in the line.
 *
 * @param id the gas's or tag's id
 * @param tag whether it is a tag
 */
public record FilterOption(ResourceLocation id, boolean tag) {
    /** The tags ZAS³ ships, which a filter always offers. */
    public static final List<GasTag> SHIPPED_TAGS = List.of(GasTags.FLAMMABLE, GasTags.OXIDIZER, GasTags.TOXIC);

    /** The same, over the network. */
    public static final StreamCodec<ByteBuf, FilterOption> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FilterOption::id, ByteBufCodecs.BOOL, FilterOption::tag, FilterOption::new);

    /**
     * What a filter offers: tags first, then gases, each sorted by id.
     *
     * @param selection what it selects now
     * @param inLine the gases in its input line
     * @return the entries
     */
    public static List<FilterOption> offered(GasSelection selection, Collection<ResourceLocation> inLine) {
        TreeSet<ResourceLocation> tags = new TreeSet<>();
        TreeSet<ResourceLocation> gases = new TreeSet<>(inLine);
        gases.add(StandardAir.NITROGEN);
        gases.add(StandardAir.OXYGEN);
        gases.addAll(selection.gases());
        selection.tags().forEach(t -> tags.add(t.id()));
        for (GasTag t : SHIPPED_TAGS) {
            tags.add(t.id());
            gases.addAll(GasTags.gases(t));
        }
        List<FilterOption> out = new ArrayList<>(tags.size() + gases.size());
        tags.forEach(id -> out.add(new FilterOption(id, true)));
        gases.forEach(id -> out.add(new FilterOption(id, false)));
        return List.copyOf(out);
    }

    /** The entry's name: a gas's, or a tag's translation, {@code gas_tag.<namespace>.<path>}, or its id. */
    public Component name() {
        return tag
                ? Component.translatableWithFallback("gas_tag." + id.getNamespace() + "." + id.getPath(), "#" + id)
                : GasCodecs.name(id);
    }

    /** Whether a selection names this entry. A gas selected only through a tag doesn't count. */
    public boolean in(GasSelection selection) {
        return tag
                ? selection.tags().contains(new GasTag(id))
                : selection.gases().contains(id);
    }

    /** A selection with this entry turned on if it was off, or off if it was on. */
    public GasSelection toggled(GasSelection selection) {
        Set<ResourceLocation> gases = new HashSet<>(selection.gases());
        Set<GasTag> tags = new HashSet<>(selection.tags());
        if (tag) {
            GasTag group = new GasTag(id);
            if (!tags.remove(group)) {
                tags.add(group);
            }
        } else if (!gases.remove(id)) {
            gases.add(id);
        }
        return GasSelection.of(gases, tags);
    }
}
