package io.github.west3436.zas.compat.kubejs;

import io.github.west3436.ss13.compat.PluginGate;

/**
 * Whether the installed KubeJS is one the adapter supports (COMPAT-002). KubeJS finds the plugin
 * through {@code kubejs.plugins.txt} and creates it whatever its own version, and may do so before
 * {@code zas} is constructed, so no loader stands in front of the plugin: it asks here before it
 * registers anything. Outside the range it registers nothing, one warning says so, and the game
 * goes on.
 *
 * <p>This is the adapter's one class that names nothing of KubeJS's, so it also keeps the note of
 * whether KubeJS ever created the plugin, which a game without KubeJS can ask for.
 */
public final class KubeJsGate {
    /** KubeJS's mod id. */
    public static final String MOD_ID = "kubejs";

    /**
     * The versions supported, written as in {@code neoforge.mods.toml}. The first is the oldest
     * build the adapter is compiled against and tested with, which is also the first one published
     * for this Minecraft. No upper bound: every KubeJS for this Minecraft is a 2101 build, and the
     * adapter's GameTests run with the newest one named in {@code gradle.properties} (COMPAT-004).
     */
    public static final String SUPPORTED = "[2101.7.0-build.131,)";

    /**
     * The version gate, from SS13 Framework, since no loader stands in front of the plugin. The
     * adapter is part of {@code zas}, which its log lines name.
     */
    private static final PluginGate GATE = new PluginGate("zas", MOD_ID, SUPPORTED);

    private static volatile boolean pluginStarted;

    private KubeJsGate() {}

    /** The plugin's note that KubeJS has created it. */
    static void notePluginStarted() {
        pluginStarted = true;
    }

    /**
     * Whether KubeJS has created the plugin, the adapter's only way in. Without KubeJS it never
     * has, and so none of the adapter's classes that name KubeJS's has run (NFR-022): the release
     * jar's check asks here, on a server without KubeJS. Any thread.
     *
     * @return true once the plugin class has been initialised
     */
    public static boolean pluginStarted() {
        return pluginStarted;
    }

    /**
     * Whether scripts have ZAS³'s events and calls: KubeJS created the plugin, and its version is
     * one the adapter supports. Any thread.
     *
     * @return true if the adapter is on
     */
    public static boolean isActive() {
        return pluginStarted && open();
    }

    /**
     * Whether the adapter may register with the KubeJS that is installed. Decided at the first
     * call, which logs the one line that says so: a warning if it may not. Any thread.
     */
    static boolean open() {
        return GATE.on();
    }
}
