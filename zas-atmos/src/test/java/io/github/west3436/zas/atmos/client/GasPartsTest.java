package io.github.west3436.zas.atmos.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The colour a gas has in the windows: its square in a list and its part of a bar (CONT-034). */
class GasPartsTest {
    /**
     * The gases the Mekanism adapter adds, each in the tint Mekanism 10.7.19 gives the chemical it
     * is: {@code ChemicalConstants} for the first four, {@code GeneratorsChemicalConstants} for
     * deuterium, and {@code GeneratorsChemicals} for tritium, which it registers as 0x64FF70.
     */
    @Test
    void mek_010_a_gas_of_the_mekanism_adapter_has_its_chemicals_tint() {
        assertEquals(0xFFCFE800, GasParts.colour("zas:chlorine"));
        assertEquals(0xFFA8F1E9, GasParts.colour("zas:hydrogen_chloride"));
        assertEquals(0xFFA99D90, GasParts.colour("zas:sulfur_dioxide"));
        assertEquals(0xFFEACCF9, GasParts.colour("zas:ethene"));
        assertEquals(0xFFFF3232, GasParts.colour("zas:deuterium"));
        assertEquals(0xFF64FF70, GasParts.colour("zas:tritium"));
    }

    /** Every gas the framework ships has a colour of its own, and none is the colour of "other". */
    @Test
    void cont_034_every_gas_we_ship_has_a_colour_of_its_own() throws IOException {
        Path gases = Path.of(System.getProperty("zas.resources"), "data", "zas", "zas", "gas");
        List<String> ids;
        try (Stream<Path> files = Files.list(gases)) {
            ids = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> "zas:" + name.substring(0, name.length() - ".json".length()))
                    .sorted()
                    .toList();
        }
        assertFalse(ids.isEmpty(), "no gas found in " + gases);
        Set<Integer> seen = new HashSet<>();
        for (String id : ids) {
            int colour = GasParts.colour(id);
            assertNotEquals(GasParts.OTHER, colour, id + " has no colour");
            assertEquals(0xFF, colour >>> 24, id + " is opaque");
            assertTrue(seen.add(colour), id + " has another gas's colour");
        }
    }

    @Test
    void cont_034_a_gas_with_no_colour_has_the_colour_of_other() {
        assertEquals(GasParts.OTHER, GasParts.colour("somepack:xenon"));
        assertEquals(GasParts.OTHER, GasParts.colour("others"));
    }
}
