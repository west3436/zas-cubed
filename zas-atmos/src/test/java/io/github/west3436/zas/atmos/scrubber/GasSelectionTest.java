package io.github.west3436.zas.atmos.scrubber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A scrubber's gas selection (CONT-015). Without ZAS³ the only tag with a member is the oxidizer tag. */
class GasSelectionTest {
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    @Test
    void cont_015_the_default_selection_is_co2_and_toxic() {
        assertEquals(Set.of(CO2), GasSelection.DEFAULT.gases());
        assertEquals(Set.of(GasTags.TOXIC), GasSelection.DEFAULT.tags());
        assertTrue(GasSelection.DEFAULT.test(CO2));
        assertFalse(GasSelection.DEFAULT.test(StandardAir.OXYGEN));
    }

    @Test
    void cont_015_a_gas_is_selected_by_name_or_through_a_tag() {
        GasSelection byName = GasSelection.of(List.of(StandardAir.NITROGEN), List.of());
        assertTrue(byName.test(StandardAir.NITROGEN));
        assertFalse(byName.test(StandardAir.OXYGEN));
        GasSelection byTag = GasSelection.of(List.of(), List.of(GasTags.OXIDIZER));
        assertTrue(byTag.test(StandardAir.OXYGEN));
        assertFalse(byTag.test(StandardAir.NITROGEN));
    }

    @Test
    void cont_015_a_selection_saves_and_loads() {
        GasSelection s = GasSelection.of(List.of(CO2, StandardAir.OXYGEN), List.of(GasTags.TOXIC, GasTags.OXIDIZER));
        assertEquals(s, GasSelection.load(s.save()));
        GasSelection none = GasSelection.of(List.of(), List.of());
        assertEquals(none, GasSelection.load(none.save()), "an empty selection stays empty");
        assertEquals(GasSelection.DEFAULT, GasSelection.load(new CompoundTag()), "a tag with none is the default");
    }

    @Test
    void cont_015_an_invalid_saved_id_is_skipped() {
        CompoundTag tag = new CompoundTag();
        ListTag gases = new ListTag();
        gases.add(StringTag.valueOf("zas:carbon_dioxide"));
        gases.add(StringTag.valueOf("Not An Id"));
        tag.put("Gases", gases);
        assertEquals(Tag.TAG_LIST, tag.getTagType("Gases"));
        assertEquals(GasSelection.of(List.of(CO2), List.of()), GasSelection.load(tag));
    }
}
