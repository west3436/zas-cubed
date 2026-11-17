package io.github.west3436.zas.compat.aeronautics;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * COMPAT-002: the builds the adapter's GameTests run with sit where they should against its range,
 * as Aeronautics' mod metadata writes their versions (the maven's names add {@code +mc1.21.1}).
 */
class AeronauticsVersionsTest {
    private static String metadataVersion(String property) {
        String v = System.getProperty(property);
        assertTrue(v != null && !v.isBlank(), property + " is not set by the build");
        int plus = v.indexOf('+');
        return plus < 0 ? v : v.substring(0, plus);
    }

    @Test
    void compat_002_the_floor_and_the_newest_are_supported() {
        assertTrue(AeronauticsVersions.supports(metadataVersion("zas.aeronautics.floorVersion")));
        assertTrue(AeronauticsVersions.supports(metadataVersion("zas.aeronautics.newestVersion")));
        assertTrue(AeronauticsVersions.supports(AeronauticsVersions.FLOOR));
    }

    @Test
    void compat_002_the_unsupported_build_is_below_the_floor() {
        assertFalse(AeronauticsVersions.supports(metadataVersion("zas.aeronautics.unsupportedVersion")));
        assertFalse(AeronauticsVersions.supports("1.2.1"));
        assertFalse(AeronauticsVersions.supports("1.0.2"));
    }
}
