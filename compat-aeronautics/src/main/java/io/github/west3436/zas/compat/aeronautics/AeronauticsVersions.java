package io.github.west3436.zas.compat.aeronautics;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Which Create Aeronautics the adapter supports (COMPAT-002).
 *
 * <p>The oldest is 1.3.0, its first build on Sable 2, which every later build needs. The adapter's
 * one hook is into Sable ({@code DimensionPhysicsData.getAirPressure}), and that method is the same
 * in every Sable 2 build, so the range has no top: the adapter's GameTests run with the newest
 * Aeronautics and Sable every night (COMPAT-004), and the hook isn't required to apply.
 *
 * <p>This class names nothing of the game's, of Aeronautics' or of Sable's, so the mixin plugin can
 * use it before the game's classes load.
 */
public final class AeronauticsVersions {
    /** Create Aeronautics' mod id: the mod inside its bundled jar, not the bundle's own id. */
    public static final String MOD_ID = "aeronautics";

    /** Sable's mod id. Aeronautics needs it, and the hook is into it. */
    public static final String SABLE_MOD_ID = "sable";

    /** The oldest Aeronautics the adapter supports, as its own mod metadata writes its version. */
    public static final String FLOOR = "1.3.0";

    /**
     * The versions the adapter supports, written as {@code neoforge.mods.toml} writes a range. It
     * is the range of the adapter's row in {@code zas}'s adapter table.
     */
    public static final String SUPPORTED = "[" + FLOOR + ",)";

    private AeronauticsVersions() {}

    /**
     * Whether the adapter supports an Aeronautics version. Versions compare by Maven's rules, as
     * they do in {@code neoforge.mods.toml}.
     *
     * @param version the version from Aeronautics' mod metadata, such as {@code 1.3.2}
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
