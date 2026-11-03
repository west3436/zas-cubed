package io.github.west3436.zas.api.reaction;

import io.github.west3436.zas.api.AnyThread;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/**
 * The change a {@link ReactionType} wants in one step (API-020). ZAS³ may scale it down as a whole
 * where reactions compete for a gas (FIRE-002), and never up: a change asking for more of a gas
 * than {@link ReactionContext#available} holds is scaled to fit.
 *
 * @param consumed moles of each gas used up, each finite and not negative; sorted by id
 * @param produced moles of each gas made, likewise. A gas the server doesn't define can't be
 *     produced: such a change is skipped
 * @param heat the heat released into the gas, in J; negative absorbs heat
 */
@AnyThread
public record ReactionChange(
        Map<ResourceLocation, Double> consumed, Map<ResourceLocation, Double> produced, double heat) {
    /**
     * Checks and copies the arguments.
     *
     * @throws IllegalArgumentException if an amount is negative or not finite, or the heat isn't
     *     finite
     * @throws NullPointerException if a map, a key or a value is null
     */
    public ReactionChange {
        consumed = amounts("consumed", consumed);
        produced = amounts("produced", produced);
        if (!Double.isFinite(heat)) {
            throw new IllegalArgumentException("heat must be finite: " + heat);
        }
    }

    private static Map<ResourceLocation, Double> amounts(String what, Map<ResourceLocation, Double> in) {
        Objects.requireNonNull(in, what);
        TreeMap<ResourceLocation, Double> out = new TreeMap<>();
        in.forEach((gas, n) -> {
            Objects.requireNonNull(gas, what + " gas");
            Objects.requireNonNull(n, what + " amount");
            if (!Double.isFinite(n) || n < 0) {
                throw new IllegalArgumentException(what + " " + gas + " must be finite and >= 0: " + n);
            }
            out.put(gas, n);
        });
        return Collections.unmodifiableMap(out);
    }
}
