package io.github.west3436.zas.compat.viewer;

import java.util.List;
import java.util.Objects;

/**
 * A piece of text before it is a component. It is one of two kinds, kept apart so that only a real
 * translation key is ever looked up as one: {@link Text}, which reads the same in every language,
 * such as a number with its unit's symbol or a formula from data, and {@link Key}, a translation key
 * with its arguments.
 */
public sealed interface Line {
    /** No text. */
    Line NONE = new Text("");

    /**
     * Text that needs no translation.
     *
     * @param text the text itself
     */
    record Text(String text) implements Line {
        public Text {
            Objects.requireNonNull(text, "text");
        }
    }

    /**
     * A translated text.
     *
     * @param key the translation key
     * @param fallback what shows if no language has the key, or empty to show the key
     * @param args the key's arguments, already formatted
     */
    record Key(String key, String fallback, List<String> args) implements Line {
        public Key {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(fallback, "fallback");
            args = List.copyOf(args);
        }
    }

    /** Text that needs no translation. */
    static Line plain(String text) {
        return new Text(text);
    }

    /** A translated text. */
    static Line of(String key, String... args) {
        return new Key(key, "", List.of(args));
    }

    /** A translated text that may have no translation, such as a datapack's tag. */
    static Line keyOr(String key, String fallback) {
        return new Key(key, fallback, List.of());
    }

    /** Whether there is nothing to show. */
    default boolean isEmpty() {
        return this instanceof Text plain && plain.text().isEmpty();
    }
}
