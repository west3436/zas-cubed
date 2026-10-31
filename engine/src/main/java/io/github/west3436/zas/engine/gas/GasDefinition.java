package io.github.west3436.zas.engine.gas;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One gas type, as a datapack defines it (CORE-010).
 *
 * @param id the gas id, {@code namespace:path}, as in a Minecraft resource location
 * @param nameKey the translation key of the display name
 * @param formula the formula label, such as {@code O₂}
 * @param molarMass molar mass, in kg/mol
 * @param molarHeatCapacity molar heat capacity, in J/(mol·K)
 * @param visibility how the gas shows in the world, if it does
 */
public record GasDefinition(
        String id,
        String nameKey,
        String formula,
        double molarMass,
        double molarHeatCapacity,
        Optional<GasVisibility> visibility) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public GasDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(visibility, "visibility");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("gas id must be namespace:path, got '" + id + "'");
        }
        if (nameKey.isBlank()) {
            throw new IllegalArgumentException(id + ": nameKey is blank");
        }
        if (formula.isBlank()) {
            throw new IllegalArgumentException(id + ": formula is blank");
        }
        if (!Double.isFinite(molarMass) || molarMass <= 0) {
            throw new IllegalArgumentException(id + ": molarMass must be finite and positive, got " + molarMass);
        }
        if (!Double.isFinite(molarHeatCapacity) || molarHeatCapacity <= 0) {
            throw new IllegalArgumentException(
                    id + ": molarHeatCapacity must be finite and positive, got " + molarHeatCapacity);
        }
    }
}
