package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The chemical map's two lookups (MEK-010). */
class ChemicalMapTest {
    private static final ResourceLocation OXYGEN = id("mekanism:oxygen");
    private static final ResourceLocation WATER_VAPOR = id("mekanism:water_vapor");
    private static final ResourceLocation STEAM = id("mekanism:steam");
    private static final ResourceLocation FUSION_FUEL = id("mekanismgenerators:fusion_fuel");
    private static final ResourceLocation DEUTERIUM = id("mekanismgenerators:deuterium");
    private static final ResourceLocation BRINE = id("mekanism:brine");

    private static final ResourceLocation O2 = id("zas:oxygen");
    private static final ResourceLocation H2O = id("zas:water_vapor");
    private static final ResourceLocation D2 = id("zas:deuterium");
    private static final ResourceLocation T2 = id("zas:tritium");

    private static final ChemicalMapping STEAM_MAPPING = new ChemicalMapping(STEAM, Map.of(H2O, 1.0), 373.15, false);
    private static final ChemicalMap MAP = ChemicalMap.of(List.of(
            ChemicalMapping.of(OXYGEN, O2),
            ChemicalMapping.of(WATER_VAPOR, H2O),
            STEAM_MAPPING,
            ChemicalMapping.of(DEUTERIUM, D2),
            new ChemicalMapping(FUSION_FUEL, Map.of(D2, 0.5, T2, 0.5), 0, false)));

    private static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }

    private static Optional<ResourceLocation> chemicalFor(ChemicalMap map, ResourceLocation gas, double temperature) {
        return map.forGas(gas, temperature).map(ChemicalMapping::chemical);
    }

    /** The map is the allow-list: a chemical without a mapping is no gas. */
    @Test
    void mek_010_an_unmapped_chemical_is_no_gas() {
        assertEquals(
                Optional.of(O2),
                MAP.ofChemical(OXYGEN).map(m -> m.gases().keySet().iterator().next()));
        assertEquals(Optional.empty(), MAP.ofChemical(BRINE));
        assertEquals(Optional.empty(), ChemicalMap.EMPTY.ofChemical(OXYGEN));
        assertEquals(Set.of(OXYGEN, WATER_VAPOR, STEAM, DEUTERIUM, FUSION_FUEL), MAP.chemicals());
    }

    /** Water vapour leaves as steam at 373.15 K and above, and as water vapour below. */
    @Test
    void mek_010_temperature_picks_between_steam_and_water_vapor_on_the_way_out() {
        assertEquals(Optional.of(WATER_VAPOR), chemicalFor(MAP, H2O, 293.15));
        assertEquals(Optional.of(WATER_VAPOR), chemicalFor(MAP, H2O, 373.14));
        assertEquals(Optional.of(STEAM), chemicalFor(MAP, H2O, 373.15));
        assertEquals(Optional.of(STEAM), chemicalFor(MAP, H2O, 800));
        assertEquals(Optional.of(OXYGEN), chemicalFor(MAP, O2, 3));
    }

    /** A gas that has reached no chemical's least temperature leaves as the coolest of them. */
    @Test
    void mek_010_a_gas_below_every_least_temperature_leaves_as_the_coolest() {
        ChemicalMap steamOnly = ChemicalMap.of(List.of(STEAM_MAPPING));
        assertEquals(Optional.of(STEAM), chemicalFor(steamOnly, H2O, 293.15));
    }

    /** A chemical that splits never comes back: deuterium leaves as deuterium, tritium not at all. */
    @Test
    void mek_010_a_split_chemical_never_leads_back() {
        assertEquals(Optional.of(DEUTERIUM), chemicalFor(MAP, D2, 300));
        assertEquals(Optional.empty(), chemicalFor(MAP, T2, 300));
        assertEquals(Set.of(O2, H2O, D2), MAP.gases());
        ChemicalMap oneWay = ChemicalMap.of(List.of(new ChemicalMapping(OXYGEN, Map.of(O2, 1.0), 0, true)));
        assertEquals(Optional.empty(), chemicalFor(oneWay, O2, 300));
        assertTrue(oneWay.ofChemical(OXYGEN).isPresent());
    }

    /** Two chemicals of one gas at the same least temperature are a tie, which the lower id wins. */
    @Test
    void mek_010_a_tie_goes_to_the_lower_chemical_id_and_is_reported() {
        ResourceLocation other = id("another:oxygen");
        ChemicalMap tied = ChemicalMap.of(List.of(ChemicalMapping.of(OXYGEN, O2), ChemicalMapping.of(other, O2)));
        assertEquals(Optional.of(other), chemicalFor(tied, O2, 300));
        assertEquals(Map.of(O2, List.of(other, OXYGEN)), tied.ties());
        assertEquals(Map.of(), MAP.ties());
    }

    /** A chemical has one mapping, and a map can be narrowed. */
    @Test
    void mek_010_a_chemical_has_one_mapping() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ChemicalMap.of(List.of(ChemicalMapping.of(OXYGEN, O2), ChemicalMapping.of(OXYGEN, H2O))));
        assertSame(ChemicalMap.EMPTY, ChemicalMap.of(List.of()));
        ChemicalMap noSteam = MAP.filter(m -> !m.chemical().equals(STEAM));
        assertEquals(Optional.of(WATER_VAPOR), chemicalFor(noSteam, H2O, 800));
        assertEquals(4, noSteam.mappings().size());
    }
}
