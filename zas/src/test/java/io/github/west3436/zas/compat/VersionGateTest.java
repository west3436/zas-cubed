package io.github.west3436.zas.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.west3436.zas.compat.Adapters.Row;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** COMPAT-002's decision (SPACE-003, MEK-001), which needs no game, and the shape of the table. */
class VersionGateTest {
    /** SPACE-003's range for Ad Astra. */
    private static final String BOUNDED = "[1.16.26,1.17)";
    /** MEK-001's: 10.7.15 or newer. */
    private static final String OPEN_ENDED = "[10.7.15,)";

    @Test
    void compat_002_a_target_that_is_not_installed_is_absent() {
        assertEquals(Verdict.ABSENT, VersionGate.check(null, BOUNDED));
        assertEquals(Verdict.ABSENT, VersionGate.check(null, OPEN_ENDED));
    }

    @Test
    void compat_002_a_version_inside_the_range_is_supported() {
        for (String version : List.of("1.16.26", "1.16.27", "1.16.26.1")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, BOUNDED), version);
        }
        for (String version : List.of("10.7.15", "10.7.15.81", "10.8.0", "11.0")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, OPEN_ENDED), version);
        }
        assertEquals(Verdict.SUPPORTED, VersionGate.check("1.21.1", "[1.21.1]"));
    }

    @Test
    void compat_002_a_version_outside_the_range_is_unsupported() {
        for (String version : List.of("1.16.25", "1.15", "1.17", "1.17.0", "1.17.1", "2.0")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, BOUNDED), version);
        }
        for (String version : List.of("10.7.14", "10.6.9", "9.99")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, OPEN_ENDED), version);
        }
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("1.21.2", "[1.21.1]"));
    }

    @Test
    void compat_002_versions_compare_as_numbers_not_as_text() {
        assertEquals(Verdict.SUPPORTED, VersionGate.check("1.16.100", BOUNDED));
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("1.16.3", BOUNDED));
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("10.7.9", OPEN_ENDED));
        assertEquals(Verdict.SUPPORTED, VersionGate.check("10.10.0", OPEN_ENDED));
    }

    /** Maven's order, which mod metadata uses too: a pre-release comes before its release. */
    @Test
    void compat_002_a_pre_release_of_the_upper_bound_is_below_it() {
        assertEquals(Verdict.SUPPORTED, VersionGate.check("1.17-beta.1", BOUNDED));
        assertEquals(Verdict.UNSUPPORTED, VersionGate.check("1.16.26-beta.1", BOUNDED));
    }

    /** A version that isn't numbers still gets a verdict, never an exception. */
    @Test
    void compat_002_a_version_that_is_not_a_number_is_unsupported() {
        for (String version : List.of("", "NONE", "${file.jarVersion}", "dev")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, BOUNDED), version);
        }
    }

    /**
     * A bare version isn't a range. Maven would let every version through one, so a row written
     * with one by mistake would gate nothing.
     */
    @Test
    void compat_002_a_range_needs_bounds() {
        for (String spec : List.of("1.16.26", "", "[1.16.26", "1.16.26,1.17", "[1.17,1.16]")) {
            assertThrows(IllegalArgumentException.class, () -> VersionGate.check("1.16.26", spec), spec);
            assertThrows(IllegalArgumentException.class, () -> VersionGate.check(null, spec), spec);
            assertThrows(IllegalArgumentException.class, () -> new Row("target", spec, "some.Entry"), spec);
        }
    }

    @Test
    void compat_002_a_target_has_one_row() {
        Row first = new Row("target", BOUNDED, "some.Entry");
        Row other = new Row("other", OPEN_ENDED, "some.OtherEntry");
        assertEquals(List.of(first, other), Adapters.table(List.of(first, other)));
        Row again = new Row("target", OPEN_ENDED, "some.LaterEntry");
        assertThrows(IllegalArgumentException.class, () -> Adapters.table(List.of(first, other, again)));
    }

    /** The table that ships: it loads, which checks every range, and names each target once. */
    @Test
    void compat_002_the_shipped_table_is_well_formed() {
        Set<String> targets = new HashSet<>();
        for (Row row : Adapters.rows()) {
            assertEquals(true, targets.add(row.modId()), row.modId() + " has two rows");
            assertEquals(false, row.entryClass().isBlank(), row.modId() + " has no entry class");
        }
        assertEquals(List.of(), Adapters.statuses(), "nothing is loaded outside the game");
    }
}
