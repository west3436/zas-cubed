package io.github.west3436.zas.compat.kubejs;

import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.life.HazardHandler;
import io.github.west3436.zas.api.life.HazardHandlers;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * What a server script sees as {@code HazardHandlers} (COMPAT-052): the API's own registration
 * ({@link HazardHandlers}), with the two things a script needs that a mod doesn't.
 *
 * <p><b>Reloads.</b> A mod registers once; a script runs again at every {@code /reload} and {@code
 * /kubejs reload server-scripts}. So this remembers the ids scripts registered, and the plugin
 * removes them all just before server scripts load. What is registered afterwards is what the
 * scripts register now: nothing is left from a script that was edited or deleted, and nothing is
 * registered twice.
 *
 * <p><b>Faults.</b> A handler that throws is contained by ZAS³ as a mod's is (NFR-013): skipped
 * for the rest of that check, called again at the next. On its way there the fault is written to
 * KubeJS's server console ({@link ScriptFaults}).
 */
public final class ScriptHazardHandlers {
    static final ScriptHazardHandlers INSTANCE = new ScriptHazardHandlers();

    /** The ids scripts have registered since server scripts last loaded. */
    private final Set<ResourceLocation> registered = new LinkedHashSet<>();

    private ScriptHazardHandlers() {}

    /**
     * Registers a handler for a gas, or for each gas in a gas tag.
     *
     * @param id the handler's id, in the pack's own namespace
     * @param gasOrTag a gas id such as {@code 'zas:carbon_monoxide'}, or a gas tag as {@code
     *     '#zas:toxic'}
     * @param handler called at each breath check of an entity that gas reaches
     * @throws IllegalArgumentException if a handler with that id is already registered, or {@code
     *     gasOrTag} is neither
     */
    public synchronized void register(ResourceLocation id, Object gasOrTag, HazardHandler handler) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(handler, "handler");
        HazardHandler guarded = new Guarded(id, handler);
        if (gasOrTag instanceof GasTag tag) {
            HazardHandlers.register(id, tag, guarded);
        } else if (gasOrTag instanceof ResourceLocation gas) {
            HazardHandlers.register(id, gas, guarded);
        } else if (gasOrTag instanceof CharSequence text && text.toString().startsWith("#")) {
            HazardHandlers.register(
                    id, new GasTag(ResourceLocation.parse(text.toString().substring(1))), guarded);
        } else if (gasOrTag instanceof CharSequence text) {
            HazardHandlers.register(id, ResourceLocation.parse(text.toString()), guarded);
        } else {
            throw new IllegalArgumentException(
                    "A hazard handler is for a gas id, or a gas tag written '#namespace:path', not " + gasOrTag);
        }
        registered.add(id);
    }

    /**
     * Removes a handler a script registered.
     *
     * @param id the handler's id
     * @return whether a script had registered it
     */
    public synchronized boolean unregister(ResourceLocation id) {
        return registered.remove(id) && HazardHandlers.unregister(id);
    }

    /** Removes every handler scripts registered. */
    synchronized void clear() {
        registered.forEach(HazardHandlers::unregister);
        registered.clear();
    }

    /** A script's handler, reporting what it throws before ZAS³ contains it. */
    private record Guarded(ResourceLocation id, HazardHandler script) implements HazardHandler {
        @Override
        public void apply(HazardExposure exposure) {
            try {
                script.apply(exposure);
            } catch (Exception | LinkageError e) {
                ScriptFaults.report("Hazard handler", id, "this check", e);
                throw e;
            }
        }
    }
}
