package io.github.west3436.zas.engine.gas;

import io.github.west3436.zas.engine.EngineLog;
import java.util.Objects;

/**
 * Settings shared by the mixtures of one engine.
 *
 * @param heatCapacityFloor the least heat capacity a mixture reports, J/K (CORE-009)
 * @param temperatureFloor removing energy never cools a mixture below this, K (CORE-062)
 * @param pruneEpsilon amounts below this many moles are pruned (CORE-007)
 * @param equivalence default tolerances of the equivalence test (CORE-006)
 * @param log where rejected operations are reported (CORE-063)
 */
public record GasConfig(
        double heatCapacityFloor,
        double temperatureFloor,
        double pruneEpsilon,
        EquivalenceTolerances equivalence,
        EngineLog log) {

    /** ZAS's floors (0.0003 J/K, 2.7 K), a 1e-9 mol pruning epsilon and ZAS's tolerances. */
    public static final GasConfig DEFAULT =
            new GasConfig(0.0003, 2.7, 1e-9, EquivalenceTolerances.DEFAULT, EngineLog.system());

    public GasConfig {
        Objects.requireNonNull(equivalence, "equivalence");
        Objects.requireNonNull(log, "log");
        if (!Double.isFinite(heatCapacityFloor) || heatCapacityFloor <= 0) {
            throw new IllegalArgumentException(
                    "heatCapacityFloor must be finite and positive, got " + heatCapacityFloor);
        }
        if (!Double.isFinite(temperatureFloor) || temperatureFloor < 0) {
            throw new IllegalArgumentException(
                    "temperatureFloor must be finite and not negative, got " + temperatureFloor);
        }
        if (!Double.isFinite(pruneEpsilon) || pruneEpsilon < 0) {
            throw new IllegalArgumentException("pruneEpsilon must be finite and not negative, got " + pruneEpsilon);
        }
    }

    public GasConfig withTemperatureFloor(double floor) {
        return new GasConfig(heatCapacityFloor, floor, pruneEpsilon, equivalence, log);
    }

    public GasConfig withPruneEpsilon(double epsilon) {
        return new GasConfig(heatCapacityFloor, temperatureFloor, epsilon, equivalence, log);
    }

    public GasConfig withEquivalence(EquivalenceTolerances tolerances) {
        return new GasConfig(heatCapacityFloor, temperatureFloor, pruneEpsilon, tolerances, log);
    }

    public GasConfig withLog(EngineLog engineLog) {
        return new GasConfig(heatCapacityFloor, temperatureFloor, pruneEpsilon, equivalence, engineLog);
    }
}
