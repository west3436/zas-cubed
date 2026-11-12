package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.script.ScriptType;
import io.github.west3436.zas.api.ThirdPartyFaults;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells a pack maker that a script's hazard handler or ambient provider threw (COMPAT-052,
 * COMPAT-053, NFR-013).
 *
 * <p>ZAS³ contains such a fault itself, as it does a Java one: it catches what the handler or
 * provider throws, skips it for that check or that atmos tick, and logs it under the registered id
 * at most once a minute. So the adapter lets the exception through to ZAS³. Before it does, it
 * writes the fault to KubeJS's server console, where pack makers look: KubeJS names the script and
 * the line that threw where it knows them, in the game's log and in its own {@code
 * logs/kubejs/server.log}. That line too is written at most once a minute for each id.
 */
final class ScriptFaults {
    private ScriptFaults() {}

    /**
     * Writes a fault to the server scripts' console, unless it wrote one for the same code within
     * the last minute. Server thread.
     *
     * @param kind what threw: "Hazard handler" or "Ambient provider"
     * @param id the id it was registered under
     * @param skipped what it is skipped for, to finish the sentence "skipped for ..."
     * @param fault what it threw
     */
    static void report(String kind, ResourceLocation id, String skipped, Throwable fault) {
        if (ThirdPartyFaults.shouldLog("KubeJS " + kind + " " + id)) {
            ScriptType.SERVER.console.error(
                    kind + " " + id + " threw; skipped for " + skipped + " (logged at most once a minute)", fault);
        }
    }
}
