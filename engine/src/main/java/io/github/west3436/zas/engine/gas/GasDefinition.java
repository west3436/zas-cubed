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
 * @param molarHeatCapacity molar heat capacity at constant volume, in J/(mol·K): the engine's
 *     thermal energy is this times moles times temperature, at the fixed volume of a mixture
 * @param visibility how the gas shows in the world, if it does
 * @param phase the gas's phase data, if it has any (CORE-017); nothing in R1 reads it
 */
public record GasDefinition(
        String id,
        String nameKey,
        String formula,
        double molarMass,
        double molarHeatCapacity,
        Optional<GasVisibility> visibility,
        Optional<GasPhaseData> phase) {

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public GasDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameKey, "nameKey");
        Objects.requireNonNull(formula, "formula");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(phase, "phase");
        if (!isValidId(id)) {
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

    /** A definition without phase data. */
    public GasDefinition(
            String id,
            String nameKey,
            String formula,
            double molarMass,
            double molarHeatCapacity,
            Optional<GasVisibility> visibility) {
        this(id, nameKey, formula, molarMass, molarHeatCapacity, visibility, Optional.empty());
    }

    /** Whether a string is a valid gas or gas tag id, {@code namespace:path}. */
    static boolean isValidId(String id) {
        return ID.matcher(id).matches();
    }
}
