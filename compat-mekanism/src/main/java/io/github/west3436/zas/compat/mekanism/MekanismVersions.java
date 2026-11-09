package io.github.west3436.zas.compat.mekanism;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Which Mekanism the adapter supports (MEK-001).
 *
 * <p>The oldest is 10.7.15. Mekanism's API last changed there (its {@code MekanismAPI.API_VERSION}
 * still reads 10.7.15 in every later build for Minecraft 1.21.1): that build added the radiation
 * and heat methods the adapter's hooks call. There is no newest: ZAS³ runs on Minecraft 1.21.1
 * only, where Mekanism is the 10.7 line, and its API hasn't changed since. Hooks into Mekanism's
 * internals can stop matching in any build, which is why each has its own GameTest (MEK-003) and
 * none is required to apply.
 *
 * <p>This class names nothing of Mekanism's or of the game's, so the mixin plugin can use it before
 * the game's classes load.
 */
public final class MekanismVersions {
    /** Mekanism's mod id. */
    public static final String MOD_ID = "mekanism";

    /** The mod id of Mekanism Generators, which registers deuterium, tritium and fusion fuel. */
    public static final String GENERATORS_MOD_ID = "mekanismgenerators";

    /** The oldest Mekanism the adapter supports, as Mekanism's own mod metadata writes its version. */
    public static final String FLOOR = "10.7.15";

    /**
     * The versions the adapter supports, written as {@code neoforge.mods.toml} writes a range. It
     * is the range of the adapter's row in {@code zas}'s adapter table.
     */
    public static final String SUPPORTED = "[" + FLOOR + ",)";

    private MekanismVersions() {}

    /**
     * Whether the adapter supports a Mekanism version. Versions compare by Maven's rules, as they
     * do in {@code neoforge.mods.toml}.
     *
     * @param version the version from Mekanism's mod metadata, such as {@code 10.7.19}
     * @return true if it is in {@link #SUPPORTED}
     */
    public static boolean supports(String version) {
        try {
            return VersionRange.createFromVersionSpec(SUPPORTED).containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalStateException("Not a version range: " + SUPPORTED, e);
        }
    }
}
