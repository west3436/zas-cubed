package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * The registered {@link HazardHandler}s (LIFE-032), each for one gas or one gas tag.
 *
 * <p>A handler for a tag is called once for each gas of the tag that reaches the entity, so it can
 * treat them apart. To treat several gases or tags alike, register the same handler under several ids.
 * At a check the handlers run in order of id, namespace first, each going through its gases in order of
 * id.
 *
 * <p>Register during mod loading, such as in common setup, or at any time: a change counts from the
 * next check. The gases come from datapacks, so a handler for a gas the server doesn't define is kept
 * and never called.
 *
 * <p>Without ZAS³ installed, registered handlers are kept but never called (D-17).
 */
public final class HazardHandlers {
    private static final Comparator<Registration> ORDER = Comparator.<Registration, String>comparing(
                    r -> r.id().getNamespace())
            .thenComparing(r -> r.id().getPath());

    /** Immutable and in {@link #ORDER}; replaced as a whole, so a check reads it without a lock. */
    private static volatile List<Registration> registered = List.of();

    private HazardHandlers() {}

    /** A handler and what it was registered for. */
    @AnyThread
    public static final class Registration {
        private final ResourceLocation id;
        private final ResourceLocation gas;
        private final GasTag tag;
        private final HazardHandler handler;

        private Registration(ResourceLocation id, ResourceLocation gas, GasTag tag, HazardHandler handler) {
            this.id = Objects.requireNonNull(id, "id");
            this.gas = gas;
            this.tag = tag;
            this.handler = Objects.requireNonNull(handler, "handler");
        }

        /**
         * Its id, which names it when its faults are logged.
         *
         * <p>Thread: any.
         *
         * @return the id, whose namespace is the registering mod's id
         */
        public ResourceLocation id() {
            return id;
        }

        /**
         * The handler.
         *
         * <p>Thread: any.
         *
         * @return the handler
         */
        public HazardHandler handler() {
            return handler;
        }

        /**
         * Whether it's registered for a gas, by naming it or a tag that holds it.
         *
         * <p>Thread: any.
         *
         * @param gas the gas id
         * @return true if the handler is called for that gas
         */
        public boolean appliesTo(ResourceLocation gas) {
            return this.gas != null ? this.gas.equals(gas) : GasTags.contains(tag, gas);
        }
    }

    /**
     * Registers a handler for a gas.
     *
     * <p>Thread: any.
     *
     * @param id the handler's id, in the registering mod's namespace
     * @param gas the gas id
     * @param handler the handler
     * @throws IllegalArgumentException if a handler with that id is already registered
     */
    @AnyThread
    public static void register(ResourceLocation id, ResourceLocation gas, HazardHandler handler) {
        add(new Registration(id, Objects.requireNonNull(gas, "gas"), null, handler));
    }

    /**
     * Registers a handler for each gas in a tag, whichever gases the tag holds on the running server.
     *
     * <p>Thread: any.
     *
     * @param id the handler's id, in the registering mod's namespace
     * @param tag the gas tag
     * @param handler the handler
     * @throws IllegalArgumentException if a handler with that id is already registered
     */
    @AnyThread
    public static void register(ResourceLocation id, GasTag tag, HazardHandler handler) {
        add(new Registration(id, null, Objects.requireNonNull(tag, "tag"), handler));
    }

    private static synchronized void add(Registration added) {
        List<Registration> next = new ArrayList<>(registered);
        for (Registration r : next) {
            if (r.id().equals(added.id())) {
                throw new IllegalArgumentException("hazard handler " + added.id() + " is already registered");
            }
        }
        next.add(added);
        next.sort(ORDER);
        registered = List.copyOf(next);
    }

    /**
     * Removes a handler. It's not called from the next check on.
     *
     * <p>Thread: any.
     *
     * @param id the handler's id
     * @return whether it was registered
     */
    @AnyThread
    public static synchronized boolean unregister(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        List<Registration> next = new ArrayList<>(registered);
        boolean removed = next.removeIf(r -> r.id().equals(id));
        registered = List.copyOf(next);
        return removed;
    }

    /**
     * The registered handlers.
     *
     * <p>Thread: any.
     *
     * @return them in the order they run; immutable, and empty if there are none
     */
    @AnyThread
    public static List<Registration> registered() {
        return registered;
    }
}
