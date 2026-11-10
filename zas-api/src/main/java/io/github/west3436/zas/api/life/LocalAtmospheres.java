package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * The registered {@link LocalAtmosphereProvider}s: the air living things are in at places outside
 * zones that another mod keeps breathable by its own means (SPACE-017, D-51).
 *
 * <p><b>What it changes.</b> Where an entity's eyes are outside every zone, in the dimension's
 * ambient, the breathing rules and the pressure and temperature hazards (LIFE), and so the verdict
 * of {@link Breathing#of}, take the first provider's air instead of the ambient. Inside a zone the
 * zone governs and no provider is asked. Nothing else changes: atmosphere queries ({@link
 * io.github.west3436.zas.api.atmosphere.Atmospheres}) still report the ambient there, no gas
 * exists there to take or to burn, and an entity breathing there uses none up.
 *
 * <p>The providers are asked in order of id, namespace first, and the first answer that isn't
 * {@code null} stands.
 *
 * <p>Register during mod loading, such as in common setup, or at any time: a change counts from the
 * next check. Without ZAS³ installed, registered providers are kept but never asked (D-17).
 */
public final class LocalAtmospheres {
    private static final Comparator<Registration> ORDER = Comparator.<Registration, String>comparing(
                    r -> r.id().getNamespace())
            .thenComparing(r -> r.id().getPath());

    /** Immutable and in {@link #ORDER}; replaced as a whole, so a check reads it without a lock. */
    private static volatile List<Registration> registered = List.of();

    private LocalAtmospheres() {}

    /**
     * A provider and its id.
     *
     * @param id its id, which names it when its faults are logged; its namespace is the registering
     *     mod's id
     * @param provider the provider
     */
    public record Registration(ResourceLocation id, LocalAtmosphereProvider provider) {
        /** @throws NullPointerException if an argument is null */
        public Registration {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
        }
    }

    /**
     * Registers a provider.
     *
     * <p>Thread: any.
     *
     * @param id the provider's id, in the registering mod's namespace
     * @param provider the provider
     * @throws IllegalArgumentException if a provider with that id is already registered
     */
    @AnyThread
    public static synchronized void register(ResourceLocation id, LocalAtmosphereProvider provider) {
        Registration added = new Registration(id, provider);
        List<Registration> next = new ArrayList<>(registered);
        for (Registration r : next) {
            if (r.id().equals(id)) {
                throw new IllegalArgumentException("local atmosphere provider " + id + " is already registered");
            }
        }
        next.add(added);
        next.sort(ORDER);
        registered = List.copyOf(next);
    }

    /**
     * Removes a provider. It isn't asked from the next check on.
     *
     * <p>Thread: any.
     *
     * @param id the provider's id
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
     * The registered providers, in the order they are asked.
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
