package io.github.west3436.zas.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.ss13.compat.Adapters.Row;
import io.github.west3436.ss13.compat.VersionGate;
import io.github.west3436.ss13.compat.VersionGate.Verdict;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * ZAS³'s adapter table (COMPAT-002, SPACE-003, MEK-001), which needs no game: its shape, and its
 * rows' ranges against the versions their targets really carry. The check itself is SS13
 * Framework's, tested there.
 */
class ZasAdaptersTest {
    /**
     * The Curios and Accessories rows against the versions those mods really carry, which end in
     * the Minecraft version and, for Accessories, are betas throughout (COMPAT-040, COMPAT-041).
     * Accessories' compatibility layer says it is Curios in a version of its own choosing.
     */
    @Test
    void compat_002_the_curios_and_accessories_ranges_fit_their_real_versions() {
        String curios = range("curios");
        for (String version : List.of("9.2.0+1.21.1", "9.2.3+1.21.1", "9.3.1+1.21.1", "9.5.1+1.21.1", "10.0.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, curios), version);
        }
        for (String version : List.of("9.0.12+1.21.1", "9.1.4+1.21.1", "8.1.0+1.20.6")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, curios), version);
        }
        String accessories = range("accessories");
        for (String version : List.of(
                "1.1.0-beta.3+1.21.1", "1.1.0-beta.22+1.21.1", "1.1.0-beta.54+1.21.1", "1.1.0+1.21.1", "1.2.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, accessories), version);
        }
        for (String version : List.of("1.0.0-beta.35+1.21", "1.1.0-beta.2+1.21.1", "1.0.0")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, accessories), version);
        }
    }

    /**
     * AE2's row (COMPAT-002): from its first release for this Minecraft not marked alpha or beta, and
     * every build after it, betas too. The beta just before it is the one the unsupported run loads.
     */
    @Test
    void compat_002_the_ae2_range_fits_its_real_versions() {
        String ae2 = range("ae2");
        for (String version : List.of("19.0.24", "19.0.27", "19.1.0-beta", "19.2.0-beta", "19.2.19", "20.0.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, ae2), version);
        }
        for (String version : List.of("19.0.23-beta", "19.0.5-alpha", "19.0.20-beta", "18.0.0")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, ae2), version);
        }
    }

    /**
     * The PneumaticCraft row against the versions its builds for 1.21.1 carry: 8.0.3 is the oldest
     * it supports, and 8.0.2, the last build for 1.21.0, is below it.
     */
    @Test
    void compat_002_the_pneumaticcraft_range_fits_its_real_versions() {
        String pneumaticcraft = range("pneumaticcraft");
        for (String version : List.of("8.0.3", "8.1.0", "8.2.24", "9.0.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, pneumaticcraft), version);
        }
        for (String version : List.of("8.0.2", "8.0.0", "7.0.0")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, pneumaticcraft), version);
        }
    }

    /**
     * The Create Aeronautics row against the versions its builds carry: 1.3.0, the first on Sable 2,
     * is the oldest it supports, and 1.2.1, the last on Sable 1.2, is below it.
     */
    @Test
    void compat_002_the_aeronautics_range_fits_its_real_versions() {
        String aeronautics = range("aeronautics");
        for (String version : List.of("1.3.0", "1.3.1", "1.3.2", "1.4.0", "2.0.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, aeronautics), version);
        }
        for (String version : List.of("1.2.1", "1.1.3", "1.0.2")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, aeronautics), version);
        }
    }

    /**
     * The recipe viewer plugins' ranges, which their viewers start themselves behind a gate of
     * their own (COMPAT-002), against the version texts the viewers write in their mod metadata.
     * JEI's starts at one build, the fourth number of its version, and EMI's carries the game's
     * version too.
     */
    @Test
    void compat_002_the_viewer_ranges_hold_the_versions_as_the_viewers_write_them() {
        String jei = "[19.21.0.247,20)"; // ZasJeiPlugin.SUPPORTED
        String emi = "[1.1.13,2)"; // ZasEmiPlugin.SUPPORTED
        for (String version :
                List.of("19.21.0.247", "19.21.0.248", "19.21.1.312", "19.21.2.313", "19.27.0.340", "19.70.0.469")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, jei), version);
        }
        for (String version : List.of("19.21.0.246", "19.21.0.9", "19.20.0.241", "19.8.2.99", "20.0.0.1", "21.3.0.5")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, jei), version);
        }
        for (String version : List.of("1.1.13+1.21.1+neoforge", "1.1.18+1.21.1+neoforge", "1.1.24+1.21.1+neoforge")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, emi), version);
        }
        for (String version : List.of("1.1.12+1.21+neoforge", "1.0.30+1.20.4+neoforge", "2.0.0+1.21.1+neoforge")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, emi), version);
        }
    }

    private static String range(String modId) {
        return ZasAdapters.ADAPTERS.rows().stream()
                .filter(row -> row.modId().equals(modId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no row for " + modId))
                .range();
    }

    /** The table that ships: it loads, which checks every range, and names each target once. */
    @Test
    void compat_002_the_shipped_table_is_well_formed() {
        Set<String> targets = new HashSet<>();
        for (Row row : ZasAdapters.ADAPTERS.rows()) {
            assertEquals(true, targets.add(row.modId()), row.modId() + " has two rows");
            assertEquals(false, row.entryClass().isBlank(), row.modId() + " has no entry class");
        }
        assertEquals(List.of(), ZasAdapters.ADAPTERS.statuses(), "nothing is loaded outside the game");
    }
}
