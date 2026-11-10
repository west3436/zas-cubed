package io.github.west3436.zas.compat.viewer;

import java.util.List;
import java.util.Objects;

/**
 * A piece of text before it is a component: a translation key with its arguments, or text that is
 * the same in every language, such as a number with its unit's symbol or a formula from data.
 *
 * @param key the translation key, or empty if {@code fallback} is the text
 * @param fallback the text itself when there is no key; with a key, what shows if no language has
 *     the key, or empty to show the key
 * @param args the key's arguments, already formatted
 */
public record Line(String key, String fallback, List<String> args) {
    /** No text. */
    public static final Line NONE = new Line("", "", List.of());

    public Line {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(fallback, "fallback");
        args = List.copyOf(args);
    }

    /** Text that needs no translation. */
    public static Line plain(String text) {
        return new Line("", text, List.of());
    }

    /** A translated text. */
    public static Line of(String key, String... args) {
        return new Line(key, "", List.of(args));
    }

    /** A translated text that may have no translation, such as a datapack's tag. */
    public static Line keyOr(String key, String fallback) {
        return new Line(key, fallback, List.of());
    }

    /** Whether there is nothing to show. */
    public boolean isEmpty() {
        return key.isEmpty() && fallback.isEmpty();
    }

    /** Whether the text comes from a translation key. */
    public boolean translated() {
        return !key.isEmpty();
    }
}
