package io.github.west3436.zas.compat.sable;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Which Sable the adapter supports (COMPAT-002).
 *
 * <p>The oldest is 2.0.0, Sable 2's first build. What the adapter uses of Sable (its sub-level
 * container and observers, its plots' save and its block mover) is the same in every Sable 2 build
 * so far, so the range has no top: the adapter's GameTests run with the newest Sable every night
 * (COMPAT-004), and its hooks aren't required to apply.
 *
 * <p>This class names nothing of the game's or of Sable's, so the mixin plugin can use it before the
 * game's classes load.
 */
public final class SableVersions {
    /** Sable's mod id. */
    public static final String MOD_ID = "sable";

    /** The oldest Sable the adapter supports, as its own mod metadata writes its version. */
    public static final String FLOOR = "2.0.0";

    /**
     * The versions the adapter supports, written as {@code neoforge.mods.toml} writes a range. It
     * is the range of the adapter's row in {@code zas}'s adapter table.
     */
    public static final String SUPPORTED = "[" + FLOOR + ",)";

    private SableVersions() {}

    /**
     * Whether the adapter supports a Sable version. Versions compare by Maven's rules, as they do in
     * {@code neoforge.mods.toml}.
     *
     * @param version the version from Sable's mod metadata, such as {@code 2.0.6}
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
