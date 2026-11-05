package io.github.west3436.zas.persistence;

import java.util.Objects;

/**
 * Why a dimension's simulation is paused (OPS-005), saved with the dimension so it lasts until the
 * dimension resumes, and shown to operators.
 *
 * @param by who or what paused it, as a source id: {@code command:<player>} for an operator, or a
 *     name of the code that did
 * @param reason why, in a few words
 */
public record DimensionPause(String by, String reason) {
    public DimensionPause {
        Objects.requireNonNull(by, "by");
        Objects.requireNonNull(reason, "reason");
    }
}
