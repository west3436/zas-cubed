package io.github.west3436.zas.compat.viewer;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.VersionGate;
import java.util.Objects;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The version gate of a viewer plugin (COMPAT-002). A viewer finds its plugins by annotation and
 * starts them itself, so no loader stands in front of them: each plugin asks this before it
 * registers anything. Outside the supported range it registers nothing and one warning is logged,
 * however often the viewer reloads.
 */
public final class ViewerGate {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final String modId;
    private final String range;
    private final Supplier<String> installed;
    private Boolean on;
    private int warnings;

    /**
     * A gate on the installed version of a mod.
     *
     * @param modId the viewer's mod id
     * @param range the versions the plugin supports, written as in {@code neoforge.mods.toml}
     * @throws IllegalArgumentException if {@code range} isn't a range with bounds
     */
    public ViewerGate(String modId, String range) {
        this(modId, range, () -> Adapters.installedVersion(modId).orElse(null));
    }

    /**
     * A gate on a version read some other way.
     *
     * @param installed gives the installed version, or null if the mod isn't installed
     */
    public ViewerGate(String modId, String range, Supplier<String> installed) {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.range = Objects.requireNonNull(range, "range");
        this.installed = Objects.requireNonNull(installed, "installed");
        // A range that can't be read fails here, when the plugin is made, not on some later reload.
        VersionGate.check(null, range);
    }

    /** Whether the plugin may register. Decided the first time it is asked. */
    public synchronized boolean on() {
        if (on == null) {
            on = decide(installed.get());
        }
        return on;
    }

    /** How many warnings this gate has logged: at most one. */
    public synchronized int warnings() {
        return warnings;
    }

    /** The versions the plugin supports. */
    public String range() {
        return range;
    }

    private boolean decide(@Nullable String version) {
        switch (VersionGate.check(version, range)) {
            case SUPPORTED:
                LOGGER.info("The adapter for {} {} is on", modId, version);
                return true;
            case UNSUPPORTED:
                warnings++;
                LOGGER.warn(
                        "The adapter for {} is off: it supports versions {}, and {} is installed",
                        modId,
                        range,
                        version);
                return false;
            default:
                return false;
        }
    }
}
