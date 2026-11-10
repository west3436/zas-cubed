package io.github.west3436.zas.compat.viewer;

import java.util.List;
import java.util.Objects;

/**
 * A gas as a viewer lists it (COMPAT-061).
 *
 * @param id the gas's id, {@code namespace:path}
 * @param nameKey the translation key of its name
 * @param formula its formula, as its definition writes it
 * @param molarMass kg/mol, or NaN if the client has no definition of the gas
 * @param molarHeatCapacity J/(mol·K), or NaN likewise
 * @param colour the icon's tint, 0xRRGGBB
 * @param tags the ids of the gas tags it is in, in order
 */
public record GasEntry(
        String id,
        String nameKey,
        String formula,
        double molarMass,
        double molarHeatCapacity,
        int colour,
        List<String> tags) {
    public GasEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(formula, "formula");
        tags = List.copyOf(tags);
    }

    /** Whether the client has this gas's definition. A reaction can name a gas it doesn't have. */
    public boolean known() {
        return !Double.isNaN(molarMass);
    }

    /** The namespace of the gas's id. */
    public String namespace() {
        return id.substring(0, id.indexOf(':'));
    }

    /** The path of the gas's id. */
    public String path() {
        return id.substring(id.indexOf(':') + 1);
    }
}
