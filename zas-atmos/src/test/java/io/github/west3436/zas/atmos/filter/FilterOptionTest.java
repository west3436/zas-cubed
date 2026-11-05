package io.github.west3436.zas.atmos.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.scrubber.GasSelection;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** What a filter's GUI offers, and turning an entry on and off (CONT-013, CONT-074). */
class FilterOptionTest {
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation PACK_GAS = ResourceLocation.fromNamespaceAndPath("pack", "krypton");
    private static final GasTag PACK_TAG = GasTag.of("pack", "noble");
    private static final GasSelection NOTHING = GasSelection.of(List.of(), List.of());

    private static FilterOption gas(ResourceLocation id) {
        return new FilterOption(id, false);
    }

    private static FilterOption tag(GasTag tag) {
        return new FilterOption(tag.id(), true);
    }

    @Test
    void cont_013_a_filter_offers_the_shipped_tags_and_standard_air() {
        List<FilterOption> offered = FilterOption.offered(NOTHING, List.of());
        assertTrue(offered.contains(tag(GasTags.FLAMMABLE)));
        assertTrue(offered.contains(tag(GasTags.OXIDIZER)));
        assertTrue(offered.contains(tag(GasTags.TOXIC)));
        assertTrue(offered.contains(gas(StandardAir.NITROGEN)));
        assertTrue(offered.contains(gas(StandardAir.OXYGEN)));
        assertFalse(offered.contains(gas(PACK_GAS)), "a datapack gas that isn't in the line or selected");
    }

    @Test
    void cont_013_a_filter_offers_what_is_in_its_line_and_what_is_selected() {
        assertTrue(FilterOption.offered(NOTHING, List.of(PACK_GAS)).contains(gas(PACK_GAS)));
        List<FilterOption> offered =
                FilterOption.offered(GasSelection.of(List.of(PACK_GAS), List.of(PACK_TAG)), List.of());
        assertTrue(offered.contains(gas(PACK_GAS)), "a selected gas stays offered, so it can be turned off");
        assertTrue(offered.contains(tag(PACK_TAG)), "and so does a selected tag");
    }

    @Test
    void cont_013_tags_come_first_then_gases_each_sorted_and_with_no_repeats() {
        List<FilterOption> offered =
                FilterOption.offered(GasSelection.of(List.of(CO2), List.of(GasTags.TOXIC)), List.of(CO2, CO2));
        List<FilterOption> tags = offered.stream().filter(FilterOption::tag).toList();
        List<FilterOption> gases = offered.stream().filter(o -> !o.tag()).toList();
        assertEquals(offered.subList(0, tags.size()), tags, "tags first");
        assertEquals(1, gases.stream().filter(o -> o.id().equals(CO2)).count());
        assertEquals(
                gases.stream().map(FilterOption::id).sorted().toList(),
                gases.stream().map(FilterOption::id).toList());
        assertEquals(
                tags.stream().map(FilterOption::id).sorted().toList(),
                tags.stream().map(FilterOption::id).toList());
    }

    @Test
    void cont_013_an_entry_turns_on_and_off() {
        GasSelection on = gas(CO2).toggled(NOTHING);
        assertTrue(gas(CO2).in(on));
        assertTrue(on.test(CO2));
        assertTrue(gas(CO2).toggled(on).gases().isEmpty());

        GasSelection tagged = tag(GasTags.TOXIC).toggled(on);
        assertTrue(tag(GasTags.TOXIC).in(tagged));
        assertTrue(gas(CO2).in(tagged), "turning on a tag leaves the gases alone");
        assertFalse(tag(GasTags.TOXIC).in(tag(GasTags.TOXIC).toggled(tagged)));
    }

    @Test
    void cont_013_a_gas_selected_through_a_tag_is_not_named() {
        GasSelection tagged = tag(GasTags.OXIDIZER).toggled(NOTHING);
        assertTrue(tagged.test(StandardAir.OXYGEN), "selected through the tag");
        assertFalse(gas(StandardAir.OXYGEN).in(tagged), "but its own entry stays off");
    }
}
