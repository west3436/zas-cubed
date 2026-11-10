package io.github.west3436.zas.engine.zone;

import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Gas levels that keep a sealed zone from dissolving into the ambient (CORE-052).
 *
 * <p>A level is a partial pressure of one gas at which something changes for whatever is in the
 * room, such as a threshold of the gas's hazard profile. The equivalence test (CORE-006) lets each
 * gas's molar density differ by 0.0021 mol/L, about 5 kPa at room temperature, which is hundreds
 * of times the partial pressure at which some gases harm. So a sealed zone that passes that test is
 * still kept while its gas and the ambient's lie on different sides of any level: dissolving it
 * would change which levels the room has reached.
 *
 * <p>Immutable, and indexed like its gas registry, so a batch can carry it to the atmos worker.
 * The test costs a few comparisons for each gas with levels, whatever the zone's size.
 */
public final class DissolveGuard {
    /** No levels: the equivalence test alone decides. */
    public static final DissolveGuard NONE =
            new DissolveGuard(GasRegistry.EMPTY, new int[0], new double[0][], new boolean[0][]);

    private final GasRegistry registry;
    /** The registry indices of the gases with levels, in order. */
    private final int[] gases;
    /** Each of those gases' levels, in kPa, lowest first. */
    private final double[][] levels;
    /** Whether each level is reached only strictly above it, rather than at it. */
    private final boolean[][] above;

    private DissolveGuard(GasRegistry registry, int[] gases, double[][] levels, boolean[][] above) {
        this.registry = registry;
        this.gases = gases;
        this.levels = levels;
        this.above = above;
    }

    /** Starts a guard for the gases of a registry. */
    public static Builder builder(GasRegistry registry) {
        return new Builder(registry);
    }

    /** The registry the levels are indexed by; {@link GasRegistry#EMPTY} for {@link #NONE}. */
    public GasRegistry registry() {
        return registry;
    }

    /** Whether no gas has a level, so nothing is kept. */
    public boolean isEmpty() {
        return gases.length == 0;
    }

    /**
     * Whether a zone must be kept: whether, for any gas with levels, the zone's partial pressure has
     * reached a level the ambient's hasn't, or the other way round.
     *
     * @param zone the zone's gas, of this guard's registry
     * @param ambient the ambient, of the same registry, at any volume
     */
    public boolean keeps(GasMixture zone, GasMixture ambient) {
        for (int i = 0; i < gases.length; i++) {
            double inZone = zone.partialPressure(gases[i]);
            double inAmbient = ambient.partialPressure(gases[i]);
            double[] at = levels[i];
            if (inZone < at[0] && inAmbient < at[0]) {
                continue; // under every level, as nearly every zone is
            }
            boolean[] strict = above[i];
            for (int k = 0; k < at.length; k++) {
                if (reached(inZone, at[k], strict[k]) != reached(inAmbient, at[k], strict[k])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean reached(double kPa, double level, boolean strictlyAbove) {
        return strictlyAbove ? kPa > level : kPa >= level;
    }

    @Override
    public boolean equals(Object o) {
        return o == this
                || (o instanceof DissolveGuard other
                        && registry.equals(other.registry)
                        && Arrays.equals(gases, other.gases)
                        && Arrays.deepEquals(levels, other.levels)
                        && Arrays.deepEquals(above, other.above));
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(gases) + Arrays.deepHashCode(levels)) + Arrays.deepHashCode(above);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DissolveGuard[");
        for (int i = 0; i < gases.length; i++) {
            sb.append(i == 0 ? "" : ", ")
                    .append(registry.definition(gases[i]).id())
                    .append(' ')
                    .append(Arrays.toString(levels[i]));
        }
        return sb.append(']').toString();
    }

    /** Collects levels, in any order. */
    public static final class Builder {
        private record Level(double kPa, boolean above) {}

        private final GasRegistry registry;
        private final TreeMap<Integer, List<Level>> byGas = new TreeMap<>();

        private Builder(GasRegistry registry) {
            this.registry = Objects.requireNonNull(registry, "registry");
        }

        /**
         * Adds a level of one gas.
         *
         * @param gas the gas's registry index
         * @param kPa the partial pressure, in kPa; finite and above 0
         * @param above whether the level is reached only strictly above {@code kPa}, rather than at it
         * @throws IllegalArgumentException if the registry has no such gas or the pressure isn't one
         */
        public Builder level(int gas, double kPa, boolean above) {
            if (gas < 0 || gas >= registry.size()) {
                throw new IllegalArgumentException("no gas " + gas + " in a registry of " + registry.size());
            }
            if (!Double.isFinite(kPa) || kPa <= 0) {
                throw new IllegalArgumentException("a level must be finite and above 0 kPa, got " + kPa);
            }
            List<Level> levels = byGas.computeIfAbsent(gas, g -> new ArrayList<>());
            Level level = new Level(kPa, above);
            if (!levels.contains(level)) {
                levels.add(level);
            }
            return this;
        }

        /** The guard, or {@link #NONE} if no level was added. */
        public DissolveGuard build() {
            if (byGas.isEmpty()) {
                return NONE;
            }
            int[] gases = new int[byGas.size()];
            double[][] levels = new double[gases.length][];
            boolean[][] above = new boolean[gases.length][];
            int i = 0;
            for (var entry : byGas.entrySet()) {
                List<Level> sorted = new ArrayList<>(entry.getValue());
                sorted.sort(Comparator.comparingDouble(Level::kPa).thenComparing(Level::above));
                gases[i] = entry.getKey();
                levels[i] = new double[sorted.size()];
                above[i] = new boolean[sorted.size()];
                for (int k = 0; k < sorted.size(); k++) {
                    levels[i][k] = sorted.get(k).kPa();
                    above[i][k] = sorted.get(k).above();
                }
                i++;
            }
            return new DissolveGuard(registry, gases, levels, above);
        }
    }
}
