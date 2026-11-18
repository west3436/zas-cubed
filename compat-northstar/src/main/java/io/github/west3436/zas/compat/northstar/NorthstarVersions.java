package io.github.west3436.zas.compat.northstar;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Which Create: Northstar – Redux the adapter supports (COMPAT-002).
 *
 * <p>The adapter uses classes outside Northstar's {@code api} package: its oxygen and temperature
 * per level, their providers, its planet data and the two checks its mixins stop. These changed
 * between 0.6.0 and 0.6.6, so the range starts at 0.6.6. No upper bound: the adapter's GameTests run
 * with the newest Northstar every night (COMPAT-004).
 *
 * <p>This class names nothing of the game's or of Northstar's, so the mixin plugin can use it before
 * the game's classes load.
 */
public final class NorthstarVersions {
    /** Northstar's mod id. */
    public static final String MOD_ID = "northstar";

    /** The oldest Northstar the adapter supports, as its own mod metadata writes its version. */
    public static final String FLOOR = "0.6.6";

    /**
     * The versions the adapter supports, written as {@code neoforge.mods.toml} writes a range. It
     * is the range of the adapter's row in {@code zas}'s adapter table.
     */
    public static final String SUPPORTED = "[" + FLOOR + ",)";

    private NorthstarVersions() {}

    /**
     * Whether the adapter supports a Northstar version. Versions compare by Maven's rules, as they
     * do in {@code neoforge.mods.toml}.
     *
     * @param version the version from Northstar's mod metadata, such as {@code 0.6.6+1.21.1}
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
