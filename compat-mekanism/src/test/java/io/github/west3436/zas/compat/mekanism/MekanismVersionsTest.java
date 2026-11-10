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

    /**
     * The builds the GameTests run with (MEK-003): the oldest is the oldest the adapter supports, and
     * the one named unsupported is outside its range.
     */
    @Test
    void mek_001_the_builds_floor_is_the_adapters() {
        assertEquals(MekanismVersions.FLOOR, modVersion("zas.mekanism.floorVersion"));
        assertTrue(MekanismVersions.supports(modVersion("zas.mekanism.floorVersion")));
        assertFalse(
                MekanismVersions.supports(modVersion("zas.mekanism.unsupportedVersion")),
                "gradle.properties' mekanism_unsupported_version is one the adapter supports");
    }

    /** The mod version in a published build's name, which is {@code <minecraft>-<mod version>.<build number>}. */
    private static String modVersion(String property) {
        String build = System.getProperty(property);
        assertTrue(
                build != null && build.matches("1[.]21[.]1-[0-9]+[.][0-9]+[.][0-9]+[.][0-9]+"),
                property + " is " + build);
        return build.substring(build.indexOf('-') + 1, build.lastIndexOf('.'));
    }
}
