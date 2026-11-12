package io.github.west3436.zas.compat.kubejs;

import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProvider;
import io.github.west3436.zas.api.environment.AmbientProviders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * What a server script sees as {@code AmbientProviders} (COMPAT-053): the API's own registration
 * ({@link AmbientProviders}), with the two things a script needs that a mod doesn't.
 *
 * <p><b>Reloads.</b> As for {@link ScriptHazardHandlers}: this remembers what scripts registered,
 * and the plugin removes it all just before server scripts load, so a reload leaves exactly what
 * the scripts register now.
 *
 * <p><b>Faults.</b> A provider that throws is contained by ZAS³ as a mod's is (NFR-013): skipped
 * for that atmos tick, with its last good answer left standing, and asked again at the next. On
 * its way there the fault is written to KubeJS's server console ({@link ScriptFaults}).
 */
public final class ScriptAmbientProviders {
    static final ScriptAmbientProviders INSTANCE = new ScriptAmbientProviders();

    /** What scripts have registered since server scripts last loaded. */
    private final List<Registered> registered = new ArrayList<>();

    private ScriptAmbientProviders() {}

    private record Registered(ResourceLocation id, ResourceKey<Level> dimension) {}

    /**
     * Registers a provider for a dimension with the default priority, 0.
     *
     * @param id the provider's id, in the pack's own namespace
     * @param dimension the dimension's id, such as {@code 'minecraft:the_end'}
     * @param provider called once per atmos tick of that dimension; answers an ambient, or null
     * @throws IllegalArgumentException if the dimension already has a provider with that id
     */
    public void register(ResourceLocation id, ResourceLocation dimension, AmbientProvider provider) {
        register(id, dimension, AmbientProviders.DEFAULT_PRIORITY, provider);
    }

    /**
     * Registers a provider for a dimension with a priority: of several, the highest applies.
     *
     * @param id the provider's id, in the pack's own namespace
     * @param dimension the dimension's id, such as {@code 'minecraft:the_end'}
     * @param priority its priority
     * @param provider called once per atmos tick of that dimension; answers an ambient, or null
     * @throws IllegalArgumentException if the dimension already has a provider with that id
     */
    public synchronized void register(
            ResourceLocation id, ResourceLocation dimension, int priority, AmbientProvider provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(provider, "provider");
        ResourceKey<Level> key =
                ResourceKey.create(Registries.DIMENSION, Objects.requireNonNull(dimension, "dimension"));
        AmbientProviders.register(id, key, priority, new Guarded(id, provider));
        registered.add(new Registered(id, key));
    }

    /**
     * Removes a provider a script registered.
     *
     * @param id the provider's id
     * @param dimension the dimension it supplied
     * @return whether a script had registered it
     */
    public synchronized boolean unregister(ResourceLocation id, ResourceLocation dimension) {
        Registered removed = new Registered(id, ResourceKey.create(Registries.DIMENSION, dimension));
        return registered.remove(removed) && AmbientProviders.unregister(id, removed.dimension());
    }

    /** Removes every provider scripts registered. */
    synchronized void clear() {
        registered.forEach(r -> AmbientProviders.unregister(r.id(), r.dimension()));
        registered.clear();
    }

    /** A script's provider, reporting what it throws before ZAS³ contains it. */
    private record Guarded(ResourceLocation id, AmbientProvider script) implements AmbientProvider {
        @Override
        public AmbientDefinition ambient(ServerLevel level) {
            try {
                return script.ambient(level);
            } catch (Exception | LinkageError e) {
                ScriptFaults.report(
                        "Ambient provider", id, "the atmos tick, and its last good answer stands if it has one", e);
                throw e;
            }
        }
    }
}
