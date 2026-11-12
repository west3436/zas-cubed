package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.LevelThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * The registered {@link ExtraSlotProvider}s: the equipment slots other mods add to an entity beside
 * its armor slots, which ZAS³ can't see by itself.
 *
 * <p><b>What it changes.</b> ZAS³ takes the items the providers give as worn:
 *
 * <ul>
 *   <li>Internals look for a supply ({@link io.github.west3436.zas.api.capability.IInternalSupply})
 *       in them after the armor slots and before the offhand (LIFE-028), so a tank on the back or
 *       the belt is breathed from.
 *   <li>An item with life support ({@link io.github.west3436.zas.api.capability.ILifeSupport}, or
 *       protection data) in a {@link ExtraSlotKind#HEAD head} slot seals the breathing and filters
 *       the breath as it would in the armor head slot, so a mask works under a helmet. The helmet
 *       and the mask each count for what they give.
 * </ul>
 *
 * <p>Nothing else counts from an extra slot: no pressure or thermal protection, as an extra slot
 * covers none of the body, and no airflow resistance.
 *
 * <p>The providers are asked in order of id, namespace first, and each gives its items in its own
 * order.
 *
 * <p>Register during mod loading, such as in common setup, or at any time: a change counts from the
 * next check. Without ZAS³ installed, registered providers are kept but never asked (D-17).
 *
 * @since 1.1.0
 */
public final class ExtraSlots {
    private static final Comparator<Registration> ORDER = Comparator.<Registration, String>comparing(
                    r -> r.id().getNamespace())
            .thenComparing(r -> r.id().getPath());

    /** Immutable and in {@link #ORDER}; replaced as a whole, so a check reads it without a lock. */
    private static volatile List<Registration> registered = List.of();

    private ExtraSlots() {}

    /**
     * A provider and its id.
     *
     * @param id its id, which names it when its faults are logged; its namespace is the registering
     *     mod's id
     * @param provider the provider
     */
    public record Registration(ResourceLocation id, ExtraSlotProvider provider) {
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
     * Registers a provider.
     *
     * <p>Thread: any.
     *
     * @param id the provider's id, in the registering mod's namespace
     * @param provider the provider
     * @throws IllegalArgumentException if a provider with that id is already registered
     */
    @AnyThread
    public static synchronized void register(ResourceLocation id, ExtraSlotProvider provider) {
        Registration added = new Registration(id, provider);
        List<Registration> next = new ArrayList<>(registered);
        for (Registration r : next) {
            if (r.id().equals(id)) {
                throw new IllegalArgumentException("extra slot provider " + id + " is already registered");
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

    /**
     * Gives a visitor each item an entity wears in extra slots: every provider's, in the order ZAS³
     * itself takes them. For something that acts on worn gear wherever it is worn, such as an item
     * that ticks while carried.
     *
     * <p>With no provider registered it does nothing, and costs one read. Without ZAS³ it does
     * nothing either (D-17). A provider that throws is skipped from there on for this call
     * (NFR-013), and so is one whose items the visitor throws on: don't throw from a visitor.
     *
     * <p>Thread: the main thread of the entity's logical side.
     *
     * @param entity the entity, server or client side
     * @param visitor takes each worn item; the stacks are the ones in the slots, and one may be
     *     empty
     * @throws IllegalStateException if the entity is in a server level and this isn't its server's
     *     thread
     */
    @LevelThread
    public static void forEach(LivingEntity entity, ExtraSlotVisitor visitor) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(visitor, "visitor");
        if (registered.isEmpty()) {
            return;
        }
        if (entity.level() instanceof ServerLevel level) {
            ServerThreads.require(level, "ExtraSlots.forEach");
        }
        ZasApi.provider().extraSlotItems(entity, visitor);
    }
}
