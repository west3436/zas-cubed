package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.HeatMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.mutation.ZonePins;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The KubeJS adapter's entry point (COMPAT-050 to COMPAT-053). KubeJS finds it through {@code
 * kubejs.plugins.txt} and creates it while KubeJS itself is constructed; nothing in ZAS³ names
 * this class or any other in its package but {@link KubeJsGate}, so without KubeJS none of them is
 * loaded (COMPAT-001, NFR-022).
 *
 * <p>Everything it gives is for server scripts, and is the public API:
 *
 * <ul>
 *   <li>the event group {@code ZasEvents} ({@link ZasEvents});
 *   <li>the API's six entry points under their own names. {@code Breathing}, {@code GasMutations},
 *       {@code HeatMutations}, {@code ZonePins} and {@code Fires} are the classes themselves, so a
 *       script calls their static methods as a mod does. {@code Atmospheres} is the class behind
 *       {@link ScriptAtmospheres}, which only merges two pairs of overloads that KubeJS's script
 *       engine can't tell apart. A call that needs the server thread says so and throws from any
 *       other, as it does for a mod;
 *   <li>{@code HazardHandlers} and {@code AmbientProviders}, the API's two registrations as a
 *       script needs them ({@link ScriptHazardHandlers}, {@link ScriptAmbientProviders});
 *   <li>the three API classes a script builds arguments with: {@code GasParcel}, {@code
 *       AmbientDefinition} and {@code StandardAir};
 *   <li>and two conversions, so that a script writes a mutation's source as {@code 'pack:name'}
 *       and a gas tag as {@code '#zas:toxic'}. KubeJS and Rhino convert the rest: positions from
 *       {@code [x, y, z]}, ids from strings, and the API's records from objects with the records'
 *       own field names.
 * </ul>
 *
 * <p>Outside the supported range of KubeJS versions it registers nothing ({@link KubeJsGate}).
 */
public final class ZasKubeJsPlugin implements KubeJSPlugin {
    static {
        // For the check that nothing here runs without KubeJS.
        KubeJsGate.notePluginStarted();
    }

    private static final AtomicBoolean CREATED = new AtomicBoolean();

    /**
     * Whether this is the first plugin KubeJS created. It reads {@code kubejs.plugins.txt} once
     * for each mod of the jar that holds it, and the release jar holds two mods, so there it
     * creates two. Only the first acts, or every event would reach a script twice.
     */
    private final boolean first = CREATED.compareAndSet(false, true);

    /** Whether this plugin registers: it is the first, and KubeJS's version is supported. */
    private boolean acts() {
        return first && KubeJsGate.open();
    }

    @Override
    public void init() {
        if (!acts()) {
            return;
        }
        ZasEvents.listen(NeoForge.EVENT_BUS);
        // A stopped server's scripts are gone with it, and so is what they registered.
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> clearRegistrations());
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        if (acts()) {
            registry.register(ZasEvents.GROUP);
        }
    }

    @Override
    public void registerBindings(BindingRegistry bindings) {
        if (bindings.type() != ScriptType.SERVER || !acts()) {
            return;
        }
        bindings.add("Atmospheres", ScriptAtmospheres.INSTANCE);
        bindings.add("Breathing", Breathing.class);
        bindings.add("GasMutations", GasMutations.class);
        bindings.add("HeatMutations", HeatMutations.class);
        bindings.add("ZonePins", ZonePins.class);
        bindings.add("Fires", Fires.class);
        bindings.add("HazardHandlers", ScriptHazardHandlers.INSTANCE);
        bindings.add("AmbientProviders", ScriptAmbientProviders.INSTANCE);
        bindings.add("GasParcel", GasParcel.class);
        bindings.add("AmbientDefinition", AmbientDefinition.class);
        bindings.add("StandardAir", StandardAir.class);
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
        if (registry.scriptType() != ScriptType.SERVER || !acts()) {
            return;
        }
        // Only for text and ids: an object or an array still becomes the record, as Rhino has it.
        registry.register(SourceId.class, (from, target) -> isId(from), from -> new SourceId(id(from, "")));
        registry.register(GasTag.class, (from, target) -> isId(from), from -> new GasTag(id(from, "#")));
    }

    /** Before server scripts load, at the server's start and at every reload: what they registered goes. */
    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (first && manager.scriptType == ScriptType.SERVER) {
            clearRegistrations();
        }
    }

    private static void clearRegistrations() {
        ScriptHazardHandlers.INSTANCE.clear();
        ScriptAmbientProviders.INSTANCE.clear();
    }

    private static boolean isId(Object from) {
        return from instanceof CharSequence || from instanceof ResourceLocation;
    }

    /** An id from text or from an id, with an optional prefix dropped from text. */
    private static ResourceLocation id(Object from, String prefix) {
        if (from instanceof ResourceLocation id) {
            return id;
        }
        String text = from.toString();
        return ResourceLocation.parse(!prefix.isEmpty() && text.startsWith(prefix) ? text.substring(1) : text);
    }
}
