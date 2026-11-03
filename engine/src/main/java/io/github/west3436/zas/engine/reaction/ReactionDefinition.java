package io.github.west3436.zas.engine.reaction;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * One gas reaction, as a datapack defines it (FIRE-001). The format is documented in {@code
 * docs/datapacks.md}.
 *
 * <p>The reaction as written, {@code Σ νᵢ reactantᵢ → Σ μⱼ productⱼ}, happens some number of times
 * per step: its <em>extent</em>, in mol. Each unit of extent consumes νᵢ mol of each reactant,
 * produces μⱼ mol of each product and releases {@code -enthalpy} J of heat.
 *
 * @param id the reaction id, {@code namespace:path}
 * @param type {@link #STANDARD}, or the id of a reaction type implemented in code (API-020), whose
 *     {@code code} computes the change instead of the stoichiometry
 * @param reactants moles of each reactant per unit of extent, by gas id; all positive. Empty only
 *     for a code type
 * @param products moles of each product per unit of extent, by gas id; all positive
 * @param enthalpy the enthalpy of reaction, J per mol of extent: negative releases heat, as in
 *     chemistry's ΔH
 * @param conditions when it can happen
 * @param rate how fast it goes, per second (CORE-046); empty means it goes to completion in one step.
 *     A reaction that needs ignition has none: it burns its parcel to completion (FIRE-011)
 * @param incomplete the incomplete-combustion form (FIRE-005), blended in by the share of carbon
 *     that forms CO at the mixture's O₂ fraction
 * @param code the implementation of a code type (API-020); empty for {@link #STANDARD}
 */
public record ReactionDefinition(
        String id,
        String type,
        Map<String, Double> reactants,
        Map<String, Double> products,
        double enthalpy,
        ReactionConditions conditions,
        Optional<ReactionRate> rate,
        Optional<Stoichiometry> incomplete,
        Optional<CodeReaction> code) {

    /** The type of a reaction given wholly by its stoichiometry. */
    public static final String STANDARD = "zas:standard";

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public ReactionDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(conditions, "conditions");
        Objects.requireNonNull(rate, "rate");
        Objects.requireNonNull(incomplete, "incomplete");
        Objects.requireNonNull(code, "code");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("reaction id must be namespace:path, got '" + id + "'");
        }
        if (!ID.matcher(type).matches()) {
            throw new IllegalArgumentException("type: must be namespace:path, got '" + type + "'");
        }
        reactants = coefficients("reactants", reactants);
        products = coefficients("products", products);
        boolean standard = type.equals(STANDARD);
        if (standard && code.isPresent()) {
            throw new IllegalArgumentException("type: " + STANDARD + " has no code");
        }
        if (!standard && code.isEmpty()) {
            throw new IllegalArgumentException("type: no reaction type " + type + " is registered");
        }
        if (standard && reactants.isEmpty()) {
            throw new IllegalArgumentException("reactants: must name at least one gas");
        }
        if (!Double.isFinite(enthalpy)) {
            throw new IllegalArgumentException("enthalpy: must be finite, got " + enthalpy);
        }
        if (conditions.requiresIgnition() && rate.isPresent()) {
            throw new IllegalArgumentException(
                    "rate: a reaction that requires ignition burns its parcel to completion and has no rate");
        }
        if (!standard && rate.isPresent()) {
            throw new IllegalArgumentException("rate: a code type works out its own rate");
        }
        if (!standard && incomplete.isPresent()) {
            throw new IllegalArgumentException("incomplete_combustion: only a " + STANDARD + " reaction has one");
        }
    }

    /** A standard reaction. */
    public static ReactionDefinition standard(
            String id,
            Map<String, Double> reactants,
            Map<String, Double> products,
            double enthalpy,
            ReactionConditions conditions,
            Optional<ReactionRate> rate,
            Optional<Stoichiometry> incomplete) {
        return new ReactionDefinition(
                id, STANDARD, reactants, products, enthalpy, conditions, rate, incomplete, Optional.empty());
    }

    /** Every gas id the definition names, in id order. */
    public List<String> gases() {
        TreeMap<String, Boolean> ids = new TreeMap<>();
        reactants.keySet().forEach(g -> ids.put(g, true));
        products.keySet().forEach(g -> ids.put(g, true));
        incomplete.ifPresent(s -> {
            s.reactants().keySet().forEach(g -> ids.put(g, true));
            s.products().keySet().forEach(g -> ids.put(g, true));
        });
        conditions.concentrations().forEach(c -> ids.put(c.gas(), true));
        conditions.catalysts().forEach(c -> ids.put(c.gas(), true));
        conditions.flammability().ifPresent(f -> ids.put(f.fuel(), true));
        return List.copyOf(ids.keySet());
    }

    static Map<String, Double> coefficients(String field, Map<String, Double> in) {
        Objects.requireNonNull(in, field);
        TreeMap<String, Double> out = new TreeMap<>();
        in.forEach((gas, n) -> {
            if (!ID.matcher(gas).matches()) {
                throw new IllegalArgumentException(field + ": gas id must be namespace:path, got '" + gas + "'");
            }
            if (n == null || !Double.isFinite(n) || n <= 0) {
                throw new IllegalArgumentException(field + ": " + gas + " must be finite and positive, got " + n);
            }
            out.put(gas, n);
        });
        return Collections.unmodifiableMap(out);
    }

    /**
     * Another form of a reaction's stoichiometry: the incomplete combustion of FIRE-005.
     *
     * @param reactants moles of each reactant per unit of extent
     * @param products moles of each product per unit of extent
     * @param enthalpy J per mol of extent; negative releases heat
     */
    public record Stoichiometry(Map<String, Double> reactants, Map<String, Double> products, double enthalpy) {
        public Stoichiometry {
            reactants = coefficients("incomplete_combustion: reactants", reactants);
            products = coefficients("incomplete_combustion: products", products);
            if (reactants.isEmpty()) {
                throw new IllegalArgumentException("incomplete_combustion: reactants: must name at least one gas");
            }
            if (!Double.isFinite(enthalpy)) {
                throw new IllegalArgumentException("incomplete_combustion: enthalpy: must be finite, got " + enthalpy);
            }
        }
    }
}
