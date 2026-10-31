package io.github.west3436.zas.engine.conservation;

import io.github.west3436.zas.engine.EngineLog;
import io.github.west3436.zas.engine.gas.GasConfig;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The per-gas conservation ledger and its checker (CORE-036, CORE-073).
 *
 * <p>Each atmos tick the scheduler calls {@link #begin(double[])} with the moles of
 * each gas held in zones, the engine {@linkplain #record records} every change it
 * makes against a {@link LedgerCategory}, and {@link #check(long, double[])} compares
 * the new totals with the old ones plus everything recorded. Any gas whose
 * change is larger than the pruning epsilon (plus floating-point rounding, see
 * {@link #relativeTolerance()}) and isn't explained is reported, through the
 * {@link EngineLog} and in the returned {@link ConservationReport}.
 *
 * <p>Sign convention: a recorded amount is the change it made to zoned gas, so
 * sinks are negative. Recording is a few additions and is always on; the
 * comparison is the debug part, and the scheduler calls it only while the
 * checker is switched on (OPS-010).
 *
 * <p>Not thread-safe; owned by the atmos worker like the zone graph.
 */
public final class ConservationLedger {
    /** The default relative tolerance for floating-point rounding, 1e-12. */
    public static final double DEFAULT_RELATIVE_TOLERANCE = 1e-12;

    private static final LedgerCategory[] CATEGORIES = LedgerCategory.values();

    private final GasRegistry registry;
    private final double epsilon;
    private final double relativeTolerance;
    private final EngineLog log;
    private final double[][] recorded;
    private final double[] start;
    private boolean begun;

    /**
     * @param registry the gases the ledger counts
     * @param epsilon the pruning epsilon (CORE-007): smaller unexplained changes are ignored
     * @param relativeTolerance rounding allowance, relative to the amounts involved
     * @param log where violations are reported
     */
    public ConservationLedger(GasRegistry registry, double epsilon, double relativeTolerance, EngineLog log) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.log = Objects.requireNonNull(log, "log");
        if (!Double.isFinite(epsilon) || epsilon < 0) {
            throw new IllegalArgumentException("epsilon must be finite and not negative, got " + epsilon);
        }
        if (!Double.isFinite(relativeTolerance) || relativeTolerance < 0) {
            throw new IllegalArgumentException(
                    "relativeTolerance must be finite and not negative, got " + relativeTolerance);
        }
        this.epsilon = epsilon;
        this.relativeTolerance = relativeTolerance;
        this.recorded = new double[CATEGORIES.length][registry.size()];
        this.start = new double[registry.size()];
    }

    /**
     * A ledger using a mixture config's pruning epsilon and log, with the
     * {@linkplain #DEFAULT_RELATIVE_TOLERANCE default rounding allowance}.
     */
    public static ConservationLedger of(GasRegistry registry, GasConfig config) {
        return new ConservationLedger(registry, config.pruneEpsilon(), DEFAULT_RELATIVE_TOLERANCE, config.log());
    }

    public GasRegistry registry() {
        return registry;
    }

    public double epsilon() {
        return epsilon;
    }

    /**
     * The allowance for rounding: an unexplained change is reported only if it
     * exceeds the epsilon plus this fraction of the larger total and of the
     * amounts recorded for that gas.
     */
    public double relativeTolerance() {
        return relativeTolerance;
    }

    /** Records a change of one gas's zoned moles; negative for a sink. */
    public void record(LedgerCategory category, int gas, double delta) {
        recorded[category.ordinal()][gas] += delta;
    }

    /** Records every gas of a parcel: {@code sign} 1 for gas added to zones, -1 for gas taken out. */
    public void record(LedgerCategory category, GasMixture parcel, double sign) {
        double[] row = recorded[category.ordinal()];
        for (int i = 0; i < row.length; i++) {
            row[i] += sign * parcel.moles(i);
        }
    }

    /** Records an array of per-gas changes, indexed like the registry. */
    public void record(LedgerCategory category, double[] deltas) {
        double[] row = recorded[category.ordinal()];
        for (int i = 0; i < row.length; i++) {
            row[i] += deltas[i];
        }
    }

    /** The moles recorded against a category for one gas since {@link #begin}. */
    public double recorded(LedgerCategory category, int gas) {
        return recorded[category.ordinal()][gas];
    }

    /** The moles recorded against every category for one gas since {@link #begin}. */
    public double recordedTotal(int gas) {
        double sum = 0;
        for (double[] row : recorded) {
            sum += row[gas];
        }
        return sum;
    }

    /** Starts a period: takes the zoned totals per gas and clears what was recorded. */
    public void begin(double[] totals) {
        requireLength(totals);
        System.arraycopy(totals, 0, start, 0, start.length);
        for (double[] row : recorded) {
            Arrays.fill(row, 0);
        }
        begun = true;
    }

    /**
     * Ends a period: compares the zoned totals with the totals at {@link #begin}
     * plus everything recorded, reports every unexplained change, then starts the
     * next period from these totals.
     *
     * @param tick the atmos tick, for the report
     * @param totals zoned moles per gas now
     * @throws IllegalStateException if {@link #begin} was never called
     */
    public ConservationReport check(long tick, double[] totals) {
        requireLength(totals);
        if (!begun) {
            throw new IllegalStateException("check before begin");
        }
        double[][] byCategory = new double[CATEGORIES.length][];
        for (int c = 0; c < CATEGORIES.length; c++) {
            byCategory[c] = recorded[c].clone();
        }
        List<ConservationReport.Violation> violations = new ArrayList<>();
        for (int i = 0; i < start.length; i++) {
            double explained = 0;
            double recordedMagnitude = 0;
            for (double[] row : recorded) {
                explained += row[i];
                recordedMagnitude += Math.abs(row[i]);
            }
            double expected = start[i] + explained;
            double unexplained = totals[i] - expected;
            double allowance = epsilon
                    + relativeTolerance * (Math.max(Math.abs(start[i]), Math.abs(totals[i])) + recordedMagnitude);
            if (!(Math.abs(unexplained) <= allowance)) {
                ConservationReport.Violation violation = new ConservationReport.Violation(
                        tick, i, registry.definition(i).id(), expected, totals[i], unexplained);
                violations.add(violation);
                log.warn("Conservation: " + violation);
            }
        }
        begin(totals);
        return new ConservationReport(tick, byCategory, violations);
    }

    private void requireLength(double[] totals) {
        if (totals.length != start.length) {
            throw new IllegalArgumentException("expected " + start.length + " per-gas totals, got " + totals.length);
        }
    }
}
