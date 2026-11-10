package io.github.west3436.zas.compat.viewer;

import java.util.Objects;

/**
 * One condition or property as a viewer shows it: an icon, then a value.
 *
 * @param icon what kind of thing it is
 * @param text the value, or {@link Line#NONE} when the icon says it all
 * @param hint the name shown when the pointer is over it
 */
public record Chip(Icon icon, Line text, Line hint) {
    public Chip {
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(hint, "hint");
    }
}
