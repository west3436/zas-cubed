package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.ss13.compat.PluginGate;
import io.github.west3436.zas.atmos.ZasAtmosMod;

/**
 * Whether the installed Jade is one the adapter supports (COMPAT-002). Jade finds the plugin by its
 * annotation and loads it whatever its own version, so the plugin asks here before it registers
 * anything: outside the range it registers nothing, one warning says so, and the game goes on.
 *
 * <p>The check is SS13 Framework's ({@link PluginGate}), the same as {@code zas}'s adapter loader
 * makes for the adapters it starts. This adapter, like {@code zas_atmos}, compiles against {@code
 * zas-api} and SS13 Framework, never {@code zas} internals (CONT-095).
 *
 * <p>This is the adapter's one class that names nothing of Jade's, so it also keeps the note of
 * whether Jade ever created the plugin, which a game without Jade can ask for.
 */
public final class JadeGate {
    static final String MOD_ID = "jade";

    /**
     * The versions supported, written as in {@code neoforge.mods.toml}. 15.1.6 is the first Jade for
     * Minecraft 1.21.1 and the one the adapter is compiled against; the builds before it were for
     * 1.21, though the loader lets them in. Jade 16 is for later versions of the game.
     */
    static final String SUPPORTED = "[15.1.6,16)";

    /** The version gate, from SS13 Framework, since no loader stands in front of the plugin. */
    private static final PluginGate GATE = new PluginGate(ZasAtmosMod.MOD_ID, MOD_ID, SUPPORTED);

    private static volatile boolean pluginStarted;

    private JadeGate() {}

    /** The plugin's note that Jade has created it. */
    static void notePluginStarted() {
        pluginStarted = true;
    }

    /**
     * Whether Jade has created the plugin, the adapter's only way in. Without Jade it never has, and
     * so none of the adapter's classes that name Jade's has run (NFR-022): the release jar's check
     * asks here, on a server without Jade. Any thread.
     *
     * @return true once the plugin class has been initialised
     */
    public static boolean pluginStarted() {
        return pluginStarted;
    }

    /**
     * Whether the adapter may register with the Jade that is installed. Decided at the first call,
     * which logs the one line that says so: a warning if it may not. Any thread.
     */
    static boolean open() {
        return GATE.on();
    }
}
