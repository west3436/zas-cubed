package io.github.west3436.zas.compat.mekanism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Which Mekanism the adapter supports (MEK-001). */
class MekanismVersionsTest {
    /** 10.7.15 and every later version, by Maven's rules, as Mekanism's mod metadata writes them. */
    @Test
    void mek_001_supports_10_7_15_and_newer() {
        assertEquals("[10.7.15,)", MekanismVersions.SUPPORTED);
        assertTrue(MekanismVersions.supports("10.7.15"));
        assertTrue(MekanismVersions.supports("10.7.16"));
        assertTrue(MekanismVersions.supports("10.7.19"));
        assertTrue(MekanismVersions.supports("10.7.100"));
        assertTrue(MekanismVersions.supports("10.8.0"));
        assertFalse(MekanismVersions.supports("10.7.14"));
        assertFalse(MekanismVersions.supports("10.7.9"));
        assertFalse(MekanismVersions.supports("10.6.9"));
        assertFalse(MekanismVersions.supports("9.99.99"));
    }

    /** The oldest Mekanism the build runs the GameTests with is the oldest the adapter supports (MEK-003). */
    @Test
    void mek_001_the_builds_floor_is_the_adapters() {
        String floor = System.getProperty("zas.mekanism.floorVersion");
        assertTrue(floor != null && !floor.isBlank(), "zas.mekanism.floorVersion is not set by the build");
        // A published build is <minecraft>-<mod version>.<build number>.
        assertTrue(
                floor.matches("1\\.21\\.1-" + MekanismVersions.FLOOR.replace(".", "\\.") + "\\.\\d+"),
                "gradle.properties' mekanism_floor_version is " + floor + ", and the adapter supports "
                        + MekanismVersions.SUPPORTED);
    }
}
