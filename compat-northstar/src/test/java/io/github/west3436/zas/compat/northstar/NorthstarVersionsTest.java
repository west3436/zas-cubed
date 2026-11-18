package io.github.west3436.zas.compat.northstar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * COMPAT-002: the builds the adapter's GameTests run with sit where they should against its range,
 * whether Northstar's mod metadata writes their versions with the maven's {@code +1.21.1} or without.
 */
class NorthstarVersionsTest {
    private static String version(String property) {
        String v = System.getProperty(property);
        assertTrue(v != null && !v.isBlank(), property + " is not set by the build");
        return v;
    }

    private static String bare(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }

    @Test
    void compat_002_the_newest_is_supported() {
        String newest = version("zas.northstar.newestVersion");
        assertTrue(NorthstarVersions.supports(newest));
        assertTrue(NorthstarVersions.supports(bare(newest)));
        assertTrue(NorthstarVersions.supports(NorthstarVersions.FLOOR));
        assertTrue(NorthstarVersions.supports("0.7.0+1.21.1"));
    }

    @Test
    void compat_002_the_unsupported_build_is_below_the_floor() {
        String unsupported = version("zas.northstar.unsupportedVersion");
        assertFalse(NorthstarVersions.supports(unsupported));
        assertFalse(NorthstarVersions.supports(bare(unsupported)));
        assertFalse(NorthstarVersions.supports("0.6.0+1.21.1"));
        assertFalse(NorthstarVersions.supports("0.5.4"));
    }
}
