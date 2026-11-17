package io.github.west3436.zas.compat.sable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * COMPAT-002: the builds the adapter's GameTests run with sit where they should against its range,
 * as Sable's mod metadata writes their versions (the maven's names add {@code +mc1.21.1}).
 */
class SableVersionsTest {
    private static String metadataVersion(String property) {
        String v = System.getProperty(property);
        assertTrue(v != null && !v.isBlank(), property + " is not set by the build");
        int plus = v.indexOf('+');
        return plus < 0 ? v : v.substring(0, plus);
    }

    @Test
    void compat_002_the_floor_and_the_newest_are_supported() {
        assertTrue(SableVersions.supports(metadataVersion("zas.sable.floorVersion")));
        assertTrue(SableVersions.supports(metadataVersion("zas.sable.newestVersion")));
        assertTrue(SableVersions.supports(SableVersions.FLOOR));
    }

    @Test
    void compat_002_the_unsupported_build_is_below_the_floor() {
        assertFalse(SableVersions.supports(metadataVersion("zas.sable.unsupportedVersion")));
        assertFalse(SableVersions.supports("1.2.2"));
        assertFalse(SableVersions.supports("1.9.9"));
    }
}
