package io.github.west3436.zas.s10;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.IEventBus;

/**
 * Times the mods' game-bus listeners (NFR-001). Both mods register every listener with {@code
 * addListener}, which the bus keeps as a {@code ConsumerEventHandler} around the mod's consumer;
 * this swaps each consumer of a {@code zas} or {@code zas_atmos} class for a {@link Timed} one, in
 * place, so the bus's order and priorities don't change. A part is named by the listener's class
 * and the event it was handed.
 *
 * <p>The bus's classes are in a package its module doesn't export, so they're reached by name and
 * their fields through {@link Unsafes}. That ties the bench to the bus's internals (bus 8.0.5, with
 * NeoForge 21.1): {@link #install} fails loudly if they change.
 */
final class BusTiming {
    static final String ZAS = "io.github.west3436.zas.";
    static final String BENCH = "io.github.west3436.zas.s10.";

    private BusTiming() {}

    /**
     * Wraps the mods' listeners on a bus.
     *
     * @return how many it wrapped
     */
    @SuppressWarnings("unchecked")
    static int install(IEventBus bus) {
        Class<?> busType = Unsafes.type("net.neoforged.bus.EventBus");
        Class<?> handler = Unsafes.type("net.neoforged.bus.ConsumerEventHandler");
        Class<?> withPredicate = Unsafes.type("net.neoforged.bus.ConsumerEventHandler$WithPredicate");
        if (!busType.isInstance(bus)) {
            throw new IllegalStateException("the game bus isn't an EventBus: " + bus.getClass());
        }
        long listenersOffset = Unsafes.offset(busType, "listeners");
        long consumerOffset = Unsafes.offset(handler, "consumer");
        long withoutCheckOffset = Unsafes.offset(withPredicate, "withoutCheck");
        Map<Object, List<EventListener>> listeners =
                (Map<Object, List<EventListener>>) Unsafes.get(bus, listenersOffset);
        int wrapped = 0;
        for (List<EventListener> list : listeners.values()) {
            for (EventListener l : List.copyOf(list)) {
                wrapped += wrap(l, handler, withPredicate, consumerOffset, withoutCheckOffset);
            }
        }
        return wrapped;
    }

    @SuppressWarnings("unchecked")
    private static int wrap(
            EventListener l, Class<?> handler, Class<?> withPredicate, long consumerOffset, long withoutCheckOffset) {
        if (!handler.isInstance(l)) {
            return 0;
        }
        int n = 0;
        Consumer<Event> c = (Consumer<Event>) Unsafes.get(l, consumerOffset);
        String owner = owner(c.getClass());
        if (!(c instanceof Timed) && owner.startsWith(ZAS) && !owner.startsWith(BENCH)) {
            Unsafes.put(l, consumerOffset, new Timed(c, owner));
            n++;
        }
        // The bus may dispatch to the handler's unchecked copy instead, which holds the same consumer.
        if (withPredicate.isInstance(l)) {
            n += wrap(
                    (EventListener) Unsafes.get(l, withoutCheckOffset),
                    handler,
                    withPredicate,
                    consumerOffset,
                    withoutCheckOffset);
        }
        return n;
    }

    /** The class a lambda or method reference was written in. */
    static String owner(Class<?> c) {
        String n = c.getName();
        int lambda = n.indexOf("$$Lambda");
        return lambda < 0 ? n : n.substring(0, lambda);
    }

    private static String shortName(String className) {
        return className.substring(className.lastIndexOf('.') + 1);
    }

    /** A listener's consumer, timed. */
    private static final class Timed implements Consumer<Event> {
        private final Consumer<Event> inner;
        private final String owner;
        private final String ownerShort;
        private Class<?> lastEvent;
        private Clock.Part lastPart;

        Timed(Consumer<Event> inner, String owner) {
            this.inner = inner;
            this.owner = owner;
            this.ownerShort = shortName(owner);
        }

        @Override
        public void accept(Event event) {
            long t = Clock.enter();
            if (t == Clock.OFF) {
                inner.accept(event);
                return;
            }
            try {
                inner.accept(event);
            } finally {
                Class<?> type = event.getClass();
                Clock.Part part = lastPart;
                if (type != lastEvent) {
                    part = Clock.part(ownerShort + " · " + shortName(type.getName()), owner);
                    lastEvent = type;
                    lastPart = part;
                }
                Clock.exit(part, t);
            }
        }
    }
}
