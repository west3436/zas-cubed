package io.github.west3436.zas.s10;

import java.lang.reflect.Field;

/**
 * {@code sun.misc.Unsafe}, the bench's way into fields no module opens to it: the event bus's
 * listeners, the pipeline's requested sections and the heap walk's layouts. Nothing here ships.
 */
@SuppressWarnings({"deprecation", "removal"})
final class Unsafes {
    static final sun.misc.Unsafe U = load();

    private Unsafes() {}

    /** A declared field of a class or one of its superclasses. */
    static Field field(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // look in the superclass
            }
        }
        throw new IllegalStateException("no field " + name + " in " + type.getName());
    }

    /** A field's offset, for {@link #get} and {@link #put}. */
    static long offset(Class<?> type, String name) {
        return U.objectFieldOffset(field(type, name));
    }

    static Object get(Object owner, long offset) {
        return U.getObject(owner, offset);
    }

    static void put(Object owner, long offset, Object value) {
        U.putObject(owner, offset, value);
    }

    /** A class by name, without initialising it. */
    static Class<?> type(String name) {
        try {
            return Class.forName(name, false, Unsafes.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("no class " + name, e);
        }
    }

    private static sun.misc.Unsafe load() {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the S-10 bench needs sun.misc.Unsafe", e);
        }
    }
}
