package io.github.west3436.zas.api.environment;

import io.github.west3436.zas.api.AnyThread;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * The registered {@link AmbientProvider}s (ENV-004).
 *
 * <p><b>Which one applies</b> (ENV-019). If several are registered for a dimension, the one with the
 * highest priority applies. A tie goes to the lexicographically smallest mod id, which is the
 * namespace of the provider's id, and then, between two providers of one mod, to the smallest path.
 * ZAS³ logs one warning naming them all and the one that applies (ENV-020). Only that one is called.
 *
 * <p>Register during mod loading, such as in common setup, or whenever a dimension appears: a change
 * takes effect at that dimension's next atmos tick.
 *
 * <p>Without ZAS³ installed, registered providers are kept but never called, and every point stays
 * standard air (D-17).
 */
public final class AmbientProviders {
    /** The priority of a provider that doesn't declare one (ENV-019). */
    public static final int DEFAULT_PRIORITY = 0;

    /** The order they apply in: the first applies (ENV-019). */
    private static final Comparator<Registration> ORDER = Comparator.comparingInt(Registration::priority)
            .reversed()
            .thenComparing(r -> r.id().getNamespace())
            .thenComparing(r -> r.id().getPath());

    /** By dimension, each list immutable and in {@link #ORDER}. Lookups only; never iterated. */
    private static final Map<ResourceKey<Level>, List<Registration>> REGISTERED = new ConcurrentHashMap<>();

    private AmbientProviders() {}

    /**
     * A provider and what it was registered with.
     *
     * @param id its id, whose namespace is the registering mod's id
     * @param priority its priority; the highest applies
     * @param provider the provider
     */
    @AnyThread
    public record Registration(ResourceLocation id, int priority, AmbientProvider provider) {
        /**
         * Checks the arguments.
         *
         * @throws NullPointerException if the id or the provider is null
         */
        public Registration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /**
     * Registers a provider for a dimension with the {@linkplain #DEFAULT_PRIORITY default priority}.
     *
     * <p>Thread: any.
     *
     * @param id the provider's id, in the registering mod's namespace
     * @param dimension the dimension it supplies
     * @param provider the provider
     * @throws IllegalArgumentException if the dimension already has a provider with that id
     */
    @AnyThread
    public static void register(ResourceLocation id, ResourceKey<Level> dimension, AmbientProvider provider) {
        register(id, dimension, DEFAULT_PRIORITY, provider);
    }

    /**
     * Registers a provider for a dimension with a priority (ENV-019).
     *
     * <p>Thread: any.
     *
     * @param id the provider's id, in the registering mod's namespace
     * @param dimension the dimension it supplies
     * @param priority its priority; the highest applies
     * @param provider the provider
     * @throws IllegalArgumentException if the dimension already has a provider with that id
     */
    @AnyThread
    public static void register(
            ResourceLocation id, ResourceKey<Level> dimension, int priority, AmbientProvider provider) {
        Objects.requireNonNull(dimension, "dimension");
        Registration added = new Registration(id, priority, provider);
        REGISTERED.compute(dimension, (key, current) -> {
            List<Registration> next = current == null ? new ArrayList<>() : new ArrayList<>(current);
            for (Registration r : next) {
                if (r.id().equals(id)) {
                    throw new IllegalArgumentException(
                            "ambient provider " + id + " is already registered for " + dimension.location());
                }
            }
            next.add(added);
            next.sort(ORDER);
            return List.copyOf(next);
        });
    }

    /**
     * Removes a provider from a dimension, such as one that no longer exists. The next source applies
     * from that dimension's next atmos tick, unless another provider does.
     *
     * <p>Thread: any.
     *
     * @param id the provider's id
     * @param dimension the dimension it supplied
     * @return whether it was registered
     */
    @AnyThread
    public static boolean unregister(ResourceLocation id, ResourceKey<Level> dimension) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimension, "dimension");
        boolean[] removed = {false};
        REGISTERED.computeIfPresent(dimension, (key, current) -> {
            List<Registration> next = new ArrayList<>(current);
            removed[0] = next.removeIf(r -> r.id().equals(id));
            return next.isEmpty() ? null : List.copyOf(next);
        });
        return removed[0];
    }

    /**
     * The providers registered for a dimension.
     *
     * <p>Thread: any.
     *
     * @param dimension the dimension
     * @return them in the order they apply, so the first is the one that applies; immutable, and
     *     empty if there are none
     */
    @AnyThread
    public static List<Registration> registered(ResourceKey<Level> dimension) {
        return REGISTERED.getOrDefault(Objects.requireNonNull(dimension, "dimension"), List.of());
    }
}
