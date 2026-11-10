package io.github.west3436.zas.api.environment;

import io.github.west3436.zas.api.AnyThread;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * The registered {@link AmbientSeed}s: ambients seeded from another mod's data (ENV-014's third
 * source, SPACE-020).
 *
 * <p><b>Which one applies.</b> A dimension's ambient comes from the first source that defines it
 * (D-45): its datapack definition, then its {@link AmbientProviders provider}, then a seed, then
 * standard air. The seeds are asked in order of id, namespace first, and the first answer that isn't
 * {@code null} stands. Two seeds covering one dimension is no conflict and logs nothing: a seed is
 * registered once for every dimension, so it can't know which ones another covers.
 *
 * <p>Register during mod loading, such as in common setup, or at any time: a change takes effect at
 * each dimension's next atmos tick. Without ZAS³ installed, registered seeds are kept but never
 * asked, and every point stays standard air (D-17).
 */
public final class AmbientSeeds {
    private static final Comparator<Registration> ORDER = Comparator.<Registration, String>comparing(
                    r -> r.id().getNamespace())
            .thenComparing(r -> r.id().getPath());

    /** Immutable and in {@link #ORDER}; replaced as a whole, so an atmos tick reads it without a lock. */
    private static volatile List<Registration> registered = List.of();

    private AmbientSeeds() {}

    /**
     * A seed and its id.
     *
     * @param id its id, which names it when its faults are logged; its namespace is the registering
     *     mod's id
     * @param seed the seed
     */
    @AnyThread
    public record Registration(ResourceLocation id, AmbientSeed seed) {
        /**
         * Checks the arguments.
         *
         * @throws NullPointerException if the id or the seed is null
         */
        public Registration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(seed, "seed");
        }
    }

    /**
     * Registers a seed, for every dimension.
     *
     * <p>Thread: any.
     *
     * @param id the seed's id, in the registering mod's namespace
     * @param seed the seed
     * @throws IllegalArgumentException if a seed with that id is already registered
     */
    @AnyThread
    public static synchronized void register(ResourceLocation id, AmbientSeed seed) {
        Registration added = new Registration(id, seed);
        List<Registration> next = new ArrayList<>(registered);
        for (Registration r : next) {
            if (r.id().equals(id)) {
                throw new IllegalArgumentException("ambient seed " + id + " is already registered");
            }
        }
        next.add(added);
        next.sort(ORDER);
        registered = List.copyOf(next);
    }

    /**
     * Removes a seed. The next source applies from each dimension's next atmos tick.
     *
     * <p>Thread: any.
     *
     * @param id the seed's id
     * @return whether it was registered
     */
    @AnyThread
    public static synchronized boolean unregister(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        List<Registration> next = new ArrayList<>(registered);
        boolean removed = next.removeIf(r -> r.id().equals(id));
        if (removed) {
            registered = List.copyOf(next);
        }
        return removed;
    }

    /**
     * The registered seeds, in the order they are asked.
     *
     * <p>Thread: any.
     *
     * @return an immutable list
     */
    @AnyThread
    public static List<Registration> registered() {
        return registered;
    }
}
