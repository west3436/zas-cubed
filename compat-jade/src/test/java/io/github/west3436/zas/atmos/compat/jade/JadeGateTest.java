package io.github.west3436.zas.atmos.compat.jade;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.ss13.compat.VersionGate;
import org.junit.jupiter.api.Test;

/**
 * The adapter's range (COMPAT-002), on the version texts Jade's builds really carry: its mod
 * metadata takes the jar's version, such as {@code 15.10.6+neoforge}. The check itself is SS13
 * Framework's, tested there.
 */
class JadeGateTest {
    @Test
    void compat_002_the_builds_for_1_21_1_are_supported() {
        // The first for 1.21.1, two from between, and the newest.
        for (String version :
                new String[] {"15.1.6+neoforge", "15.3.2+neoforge", "15.9.3+neoforge", "15.10.6+neoforge"}) {
            assertTrue(VersionGate.supports(version, JadeGate.SUPPORTED), version);
        }
    }

    /** Numbers compare as numbers: 15.10 is above 15.9 and 15.1. */
    @Test
    void compat_002_a_later_15_is_supported() {
        assertTrue(VersionGate.supports("15.11.0+neoforge", JadeGate.SUPPORTED));
        assertTrue(VersionGate.supports("15.100.0+neoforge", JadeGate.SUPPORTED));
    }

    @Test
    void compat_002_the_builds_for_1_21_are_not() {
        for (String version : new String[] {"15.1.5+neoforge", "15.1.0+neoforge", "15.0.4+neoforge"}) {
            assertFalse(VersionGate.supports(version, JadeGate.SUPPORTED), version);
        }
    }

    @Test
    void compat_002_jade_for_another_game_version_is_not() {
        for (String version :
                new String[] {"14.3.4+neoforge", "16.0.0+neoforge", "17.2.1+neoforge", "21.0.1+neoforge"}) {
            assertFalse(VersionGate.supports(version, JadeGate.SUPPORTED), version);
        }
    }
}
