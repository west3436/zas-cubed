package io.github.west3436.zas.compat.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** COMPAT-002 for a plugin its viewer starts: it gates itself, and warns once. */
class ViewerGateTest {
    /** The JEI plugin's range and the EMI plugin's. */
    private static final String JEI = "[19.21,20)";

    private static final String EMI = "[1.1.13,2)";

    @Test
    void compat_002_a_viewer_outside_the_range_is_off_with_one_warning_however_often_it_is_asked() {
        AtomicInteger asked = new AtomicInteger();
        ViewerGate gate = new ViewerGate("jei", JEI, () -> {
            asked.incrementAndGet();
            return "19.8.2.99";
        });
        assertEquals(0, gate.warnings(), "nothing is decided before the viewer starts the plugin");
        // A viewer calls its plugin several times each time it loads, and loads again on each reload.
        for (int i = 0; i < 5; i++) {
            assertFalse(gate.on());
        }
        assertEquals(1, gate.warnings());
        assertEquals(1, asked.get(), "the version is read once");
    }

    @Test
    void compat_002_a_viewer_inside_the_range_is_on_without_a_warning() {
        ViewerGate gate = new ViewerGate("jei", JEI, () -> "19.21.0.247");
        assertTrue(gate.on());
        assertTrue(gate.on());
        assertEquals(0, gate.warnings());
        assertEquals(JEI, gate.range());
    }

    @Test
    void compat_002_a_viewer_that_is_not_installed_is_off_without_a_warning() {
        ViewerGate gate = new ViewerGate("emi", EMI, () -> null);
        assertFalse(gate.on());
        assertEquals(0, gate.warnings());
    }

    @Test
    void compat_002_a_range_without_bounds_is_refused_when_the_gate_is_made() {
        assertThrows(IllegalArgumentException.class, () -> new ViewerGate("jei", "19.21", () -> "19.21.0.247"));
    }

    /** The version text is each viewer's own, from its mod metadata. EMI's carries the game's version too. */
    @Test
    void compat_002_the_ranges_hold_the_versions_as_the_viewers_write_them() {
        for (String version : List.of("19.21.0.247", "19.21.2.313", "19.27.0.340", "19.70.0.469")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, JEI), version);
        }
        for (String version : List.of("19.20.0.241", "19.8.2.99", "20.0.0.1", "21.3.0.5")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, JEI), version);
        }
        for (String version : List.of("1.1.13+1.21.1+neoforge", "1.1.18+1.21.1+neoforge", "1.1.24+1.21.1+neoforge")) {
            assertEquals(Verdict.SUPPORTED, VersionGate.check(version, EMI), version);
        }
        for (String version : List.of("1.1.12+1.21+neoforge", "1.0.30+1.20.4+neoforge", "2.0.0+1.21.1+neoforge")) {
            assertEquals(Verdict.UNSUPPORTED, VersionGate.check(version, EMI), version);
        }
    }
}
