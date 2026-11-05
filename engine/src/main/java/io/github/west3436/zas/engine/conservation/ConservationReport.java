package io.github.west3436.zas.engine.conservation;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One period of the conservation ledger (CORE-073): what each category changed,
 * and every change no category explains.
 *
 * @param tick the atmos tick checked
 * @param byCategory moles recorded per category (by ordinal) and gas index; sinks are negative
 * @param voided per gas index, moles voided at paused zones and unresolved regions (GEO-036);
 *     they never entered a zone, so they explain no change
 * @param supplied per gas index, moles supplied by paused zones and unresolved regions without
 *     depleting them (GEO-038)
 * @param byOperator per operator, in source order, and per gas index, the moles their commands
 *     removed (OPS-013): who {@link LedgerCategory#OPERATOR}'s sinks are attributed to
 * @param violations unexplained changes, in gas index order; empty when conserved
 */
public record ConservationReport(
        long tick,
        double[][] byCategory,
        double[] voided,
        double[] supplied,
        Map<String, double[]> byOperator,
        List<Violation> violations) {

    public ConservationReport {
        byOperator = Collections.unmodifiableMap(new TreeMap<>(byOperator));
        violations = List.copyOf(violations);
    }

    /** The moles of one gas that an operator's commands removed (OPS-013). */
    public double removedBy(String operator, int gas) {
        double[] moles = byOperator.get(operator);
        return moles == null ? 0 : moles[gas];
    }

    /** Whether every change was explained. */
    public boolean conserved() {
        return violations.isEmpty();
    }

    /** The moles of one gas voided at paused zones and unresolved regions (GEO-036). */
    public double voided(int gas) {
        return voided[gas];
    }

    /** The moles of one gas supplied by paused zones and unresolved regions (GEO-038). */
    public double supplied(int gas) {
        return supplied[gas];
    }

    /** The moles recorded against a category for one gas. */
    public double recorded(LedgerCategory category, int gas) {
        return byCategory[category.ordinal()][gas];
    }

    /**
     * A change in one gas's zoned moles that no category explains.
     *
     * @param tick the atmos tick
     * @param gas the gas's registry index
     * @param gasId the gas's id
     * @param expected the moles the start total plus the recorded changes predicts
     * @param actual the moles found in zones
     * @param unexplained {@code actual - expected}
     */
    public record Violation(long tick, int gas, String gasId, double expected, double actual, double unexplained) {
        @Override
        public String toString() {
            return "tick " + tick + ": " + gasId + " changed by " + unexplained + " mol that no source or sink explains"
                    + " (expected " + expected + ", found " + actual + ")";
        }
    }
}
