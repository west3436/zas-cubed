package io.github.west3436.zas.compat.viewer;

import java.util.List;
import java.util.Objects;

/**
 * A reaction as a viewer shows it (COMPAT-060). A reaction with an incomplete form makes two
 * entries, since the two forms take and give different gases.
 *
 * @param id the reaction's id, {@code namespace:path}
 * @param incomplete whether this is the reaction's incomplete form
 * @param reactants what it takes, in the order of their ids
 * @param products what it gives
 * @param catalysts what must be present and isn't used up, each with an amount of 1
 * @param chips its conditions, rate and heat, in the order they are shown
 */
public record ReactionEntry(
        String id,
        boolean incomplete,
        List<GasStack> reactants,
        List<GasStack> products,
        List<GasStack> catalysts,
        List<Chip> chips) {
    public ReactionEntry {
        Objects.requireNonNull(id, "id");
        reactants = List.copyOf(reactants);
        products = List.copyOf(products);
        catalysts = List.copyOf(catalysts);
        chips = List.copyOf(chips);
    }

    /** The namespace of the reaction's id. */
    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    /** The path of this entry's own id: the reaction's, with a suffix for the incomplete form. */
    public String path() {
        String path = id.substring(id.indexOf(':') + 1);
        return incomplete ? path + "/incomplete" : path;
    }
}
