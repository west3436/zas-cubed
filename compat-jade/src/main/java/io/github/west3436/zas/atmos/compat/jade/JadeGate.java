package io.github.west3436.zas.atmos.compat.jade;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Whether the installed Jade is one the adapter supports (COMPAT-002). Jade finds the plugin by its
 * annotation and loads it whatever its own version, so the plugin asks here before it registers
 * anything: outside the range it registers nothing, one warning says so, and the game goes on.
 *
 * <p>The framework's adapter loader has the same check for the adapters it starts. It is internal
 * to {@code zas}, which this mod may not name (CONT-095), so the few lines are repeated here.
 */
final class JadeGate {
    static final String MOD_ID = "jade";

    /**
     * The versions supported, written as in {@code neoforge.mods.toml}. 15.1.6 is the first Jade for
     * Minecraft 1.21.1 and the one the adapter is compiled against; the builds before it were for
     * 1.21, though the loader lets them in. Jade 16 is for later versions of the game.
     */
    static final String SUPPORTED = "[15.1.6,16)";

    private static final Logger LOGGER = LogUtils.getLogger();

    private static @Nullable Boolean open;

    private JadeGate() {}

    /**
     * Whether the adapter may register with the Jade that is installed. Decided at the first call,
     * which logs the one warning if it may not. Any thread.
     */
    static synchronized boolean open() {
        if (open == null) {
            String installed = ModList.get()
                    .getModContainerById(MOD_ID)
                    .map(mod -> mod.getModInfo().getVersion().toString())
                    .orElse(null);
            open = installed != null && supports(installed, SUPPORTED);
            if (!open) {
                LOGGER.warn(
                        "The adapter for {} is off: it supports versions {}, and {} is installed",
                        MOD_ID,
                        SUPPORTED,
                        installed);
            }
        }
        return open;
    }

    /**
     * The decision alone.
     *
     * @param installed Jade's version as its mod metadata gives it, such as {@code 15.10.6+neoforge}
     * @param range the versions supported; versions compare by Maven's rules, as the loader's do
     * @return whether the version is in the range
     * @throws IllegalArgumentException if {@code range} isn't a range with bounds
     */
    static boolean supports(String installed, String range) {
        VersionRange supported;
        try {
            supported = VersionRange.createFromVersionSpec(range);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException("Not a version range: " + range, e);
        }
        if (!supported.hasRestrictions()) {
            // Maven reads a bare version as a preference that every version satisfies.
            throw new IllegalArgumentException("Not a version range, as it has no bounds: " + range);
        }
        return supported.containsVersion(new DefaultArtifactVersion(installed));
    }
}
