package io.github.west3436.zas.engine.reaction;

import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasMixture;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * A server's reactions, compiled against its gases (FIRE-001), and the pure functions that react a
 * mixture with them.
 *
 * <p>Two kinds of step change a mixture:
 *
 * <ul>
 *   <li>{@link #react}: the reactions that don't need ignition, over the whole mixture, for some
 *       seconds. Zones run it in phase 7 (ZONE_UPDATES).
 *   <li>{@link #burn}: the reactions that need ignition (FIRE-006), run to completion on the gas in
 *       a parcel of the mixture (FIRE-011, FIRE-031). Hotspots run it in phase 6.
 * </ul>
 *
 * <p>In both, every condition is read from the mixture at the start of the step, each reaction's
 * extent is worked out on its own, and reactions that together want more of a gas than there is
 * are scaled back, each by the worst ratio among its own reactants, so a reaction that competes
 * with no other keeps its extent (FIRE-002). Products are added after every reaction has consumed,
 * so one reaction's products don't feed another in the same step. The heat goes into the whole
 * mixture through its gases' heat capacities ({@link GasMixture#applyReaction}), conserving energy.
 * Burning a parcel is therefore the same as taking the parcel out, burning it, and mixing it back.
 *
 * <p><b>Threads.</b> Immutable; any thread may use it on a mixture that thread owns. Reactions are
 * visited in id order and nothing reads a clock or a random source, so results are deterministic
 * (architecture §3.5).
 */
public final class ReactionSet {
    /** No reactions. */
    public static final ReactionSet EMPTY = new ReactionSet(GasRegistry.EMPTY, List.of());

    /** A fire's parcel, litres of the mixture per second (FIRE-011, FIRE-031): ZAS's 0.75 cell per 2 s. */
    public static final double PARCEL_LITRES_PER_SECOND = 375;

    private final GasRegistry registry;
    private final List<ReactionDefinition> definitions;
    /** Reactions that don't need ignition, in id order. */
    private final Compiled[] zoneWide;
    /** Reactions that need ignition, in id order. */
    private final Compiled[] ignited;

    private final int oxygen;

    private ReactionSet(GasRegistry registry, List<Compiled> compiled) {
        this.registry = registry;
        this.definitions = compiled.stream().map(c -> c.def).toList();
        this.zoneWide = compiled.stream()
                .filter(c -> !c.def.conditions().requiresIgnition())
                .toArray(Compiled[]::new);
        this.ignited = compiled.stream()
                .filter(c -> c.def.conditions().requiresIgnition())
                .toArray(Compiled[]::new);
        this.oxygen = registry.indexOf(FireSettings.OXYGEN);
    }

    /**
     * Compiles definitions against a server's gases. A definition that names a gas the registry
     * doesn't hold, or repeats an id, is passed to {@code skipped} with the reason and left out.
     *
     * @param registry the gases
     * @param definitions the reactions, in any order
     * @param skipped told of each definition left out, and why
     */
    public static ReactionSet compile(
            GasRegistry registry,
            Collection<ReactionDefinition> definitions,
            BiConsumer<ReactionDefinition, String> skipped) {
        Objects.requireNonNull(registry, "registry");
        List<ReactionDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(Comparator.comparing(ReactionDefinition::id));
        List<Compiled> compiled = new ArrayList<>();
        String last = null;
        for (ReactionDefinition def : sorted) {
            if (def.id().equals(last)) {
                skipped.accept(def, "duplicate reaction id " + def.id());
                continue;
            }
            last = def.id();
            List<String> missing =
                    def.gases().stream().filter(g -> registry.indexOf(g) < 0).toList();
            if (!missing.isEmpty()) {
                skipped.accept(def, "no such gas: " + String.join(", ", missing));
                continue;
            }
            compiled.add(new Compiled(registry, def));
        }
        return new ReactionSet(registry, compiled);
    }

    /** The gases it was compiled against. */
    public GasRegistry registry() {
        return registry;
    }

    /** The compiled reactions, in id order. */
    public List<ReactionDefinition> definitions() {
        return definitions;
    }

    /** Whether it has no reactions. */
    public boolean isEmpty() {
        return definitions.isEmpty();
    }

    // ---- Steps --------------------------------------------------------------------------------

    /**
     * Burns a parcel of a mixture (FIRE-011, FIRE-031): the reactions that need ignition, run to
     * completion on the gas in {@code min(parcelLitres, volume)} litres of it, scaled back where they
     * compete (FIRE-002). A reaction burns if an ignition source is present or the mixture is at or
     * above its autoignition temperature, its other conditions hold, and, for a fire, the mixture
     * passes the fire oxygen threshold (FIRE-030). Its incomplete form, if any, is blended in by the
     * share of carbon that forms CO at the mixture's O₂ fraction (FIRE-005).
     *
     * @param mixture the mixture, changed in place; its registry must be this set's
     * @param parcelLitres the parcel, L: {@link #PARCEL_LITRES_PER_SECOND} times the step's seconds
     * @param ignition whether an ignition source is present; a hotspot always is one
     * @param fire the fire oxygen threshold and incomplete-combustion settings
     * @return what burned
     */
    public ReactionResult burn(GasMixture mixture, double parcelLitres, boolean ignition, FireSettings fire) {
        requireRegistry(mixture);
        if (!(parcelLitres > 0) || ignited.length == 0 || mixture.isEmpty()) {
            return ReactionResult.none(registry.size());
        }
        double share = Math.min(1, parcelLitres / mixture.volume());
        return step(mixture, ignited, share, 0, ignition, fire);
    }

    /**
     * Runs the reactions that don't need ignition over a whole mixture for a time (phase 7,
     * FIRE-001, CORE-046), scaled back where they compete (FIRE-002).
     *
     * @param mixture the mixture, changed in place; its registry must be this set's
     * @param seconds the step's length
     * @param fire the fire oxygen threshold, for a reaction with flammability limits
     * @return what reacted
     */
    public ReactionResult react(GasMixture mixture, double seconds, FireSettings fire) {
        requireRegistry(mixture);
        if (!(seconds > 0) || zoneWide.length == 0 || mixture.isEmpty()) {
            return ReactionResult.none(registry.size());
        }
        return step(mixture, zoneWide, 1, seconds, false, fire);
    }

    /**
     * Reacts a mixture held outside the world for a time step (API-019, FIRE-031), as a zone's atmos
     * tick would: first {@link #burn}, as one hotspot whose parcel is {@link
     * #PARCEL_LITRES_PER_SECOND} times the seconds, if it ignites by the source or by autoignition;
     * then {@link #react} over the whole mixture.
     *
     * @param mixture the mixture, changed in place; its registry must be this set's
     * @param seconds the step's length
     * @param ignition whether an ignition source acts on it
     * @param fire the fire oxygen threshold and incomplete-combustion settings
     * @return both steps' changes together
     */
    public ReactionResult applyStandalone(GasMixture mixture, double seconds, boolean ignition, FireSettings fire) {
        ReactionResult burned = burn(mixture, PARCEL_LITRES_PER_SECOND * seconds, ignition, fire);
        ReactionResult reacted = react(mixture, seconds, fire);
        if (!burned.reacted()) {
            return reacted;
        }
        if (!reacted.reacted()) {
            return burned;
        }
        double[] moles = burned.moles().clone();
        for (int i = 0; i < moles.length; i++) {
            moles[i] += reacted.moles()[i];
        }
        return new ReactionResult(moles, burned.heat() + reacted.heat(), burned.reactions() + reacted.reactions());
    }

    /**
     * Whether a mixture can catch fire, and at what temperature by itself (FIRE-010, FIRE-014,
     * FIRE-017): see {@link Flammability}.
     *
     * @param mixture the mixture; its registry must be this set's
     * @param fire the fire oxygen threshold
     */
    public Flammability flammability(GasMixture mixture, FireSettings fire) {
        requireRegistry(mixture);
        if (ignited.length == 0 || mixture.isEmpty()) {
            return Flammability.NONE;
        }
        boolean flammable = false;
        double autoignition = Double.POSITIVE_INFINITY;
        int fireOk = UNKNOWN;
        for (Compiled c : ignited) {
            if (c.fire) {
                if (fireOk == UNKNOWN) {
                    fireOk = fire.sustains(mixture) ? YES : NO;
                }
                if (fireOk == NO) {
                    continue;
                }
            }
            if (c.holds(mixture)) {
                flammable = true;
                autoignition = Math.min(autoignition, c.autoignition);
            }
        }
        return flammable ? new Flammability(true, autoignition) : Flammability.NONE;
    }

    private static final int UNKNOWN = 0;
    private static final int YES = 1;
    private static final int NO = 2;

    private ReactionResult step(
            GasMixture mixture,
            Compiled[] reactions,
            double share,
            double seconds,
            boolean ignition,
            FireSettings fire) {
        int n = registry.size();
        double temperature = mixture.temperature();
        int fireOk = UNKNOWN;
        List<Active> active = null;
        double[] available = null;
        for (Compiled c : reactions) {
            if (!c.reactantsPresent(mixture)) {
                continue;
            }
            if (c.def.conditions().requiresIgnition() && !ignition && !(temperature >= c.autoignition)) {
                continue;
            }
            if (c.fire) {
                if (fireOk == UNKNOWN) {
                    fireOk = fire.sustains(mixture) ? YES : NO;
                }
                if (fireOk == NO) {
                    continue;
                }
            }
            if (!c.holds(mixture)) {
                continue;
            }
            if (available == null) {
                available = new double[n];
                for (int i = 0; i < n; i++) {
                    available[i] = share == 1 ? mixture.moles(i) : mixture.moles(i) * share;
                }
            }
            Active a = c.code != null
                    ? codeStep(c, mixture, available, seconds, ignition)
                    : standardStep(c, mixture, available, seconds, fire);
            if (a != null && a.extent > 0) {
                if (active == null) {
                    active = new ArrayList<>();
                }
                active.add(a);
            }
        }
        if (active == null) {
            return ReactionResult.none(n);
        }

        // Fair allocation (FIRE-002): a gas wanted beyond what is there scales back only the
        // reactions that want it, each by the worst ratio among its own reactants.
        double[] demand = new double[n];
        for (Active a : active) {
            for (int i = 0; i < n; i++) {
                demand[i] += a.consumed[i] * a.extent;
            }
        }
        double[] ratio = new double[n];
        for (int i = 0; i < n; i++) {
            ratio[i] = demand[i] > available[i] ? available[i] / demand[i] : 1;
        }
        double[] consumed = new double[n];
        double[] produced = new double[n];
        double heat = 0;
        for (Active a : active) {
            double scale = 1;
            for (int i = 0; i < n; i++) {
                if (a.consumed[i] > 0) {
                    scale = Math.min(scale, ratio[i]);
                }
            }
            double extent = a.extent * scale;
            for (int i = 0; i < n; i++) {
                consumed[i] += a.consumed[i] * extent;
                produced[i] += a.produced[i] * extent;
            }
            heat += a.heat * extent;
        }
        double[] delta = new double[n];
        for (int i = 0; i < n; i++) {
            double used = consumed[i];
            // A reactant used up to rounding is used up exactly.
            if (used > available[i] * (1 - 1e-12)) {
                used = available[i];
            }
            delta[i] = produced[i] - used;
        }
        if (!mixture.applyReaction(delta, heat)) {
            return ReactionResult.none(n);
        }
        return new ReactionResult(delta, heat, active.size());
    }

    private Active standardStep(Compiled c, GasMixture mixture, double[] available, double seconds, FireSettings fire) {
        double[] nu = c.nu;
        double[] mu = c.mu;
        double heat = c.heat;
        if (c.nuIncomplete != null) {
            double s = oxygen < 0 ? 0 : fire.coShare(mixture.moles(oxygen) / mixture.totalMoles());
            if (s > 0) {
                nu = blend(c.nu, c.nuIncomplete, s);
                mu = blend(c.mu, c.muIncomplete, s);
                heat = (1 - s) * c.heat + s * c.heatIncomplete;
            }
        }
        double extent = maxExtent(nu, available, Double.POSITIVE_INFINITY);
        if (c.def.rate().isPresent()) {
            extent *= c.def.rate().get().fraction(mixture.temperature(), seconds);
        }
        return new Active(nu, mu, heat, extent);
    }

    private Active codeStep(Compiled c, GasMixture mixture, double[] available, double seconds, boolean ignition) {
        int n = registry.size();
        double[] moles = new double[n];
        for (int i = 0; i < n; i++) {
            moles[i] = mixture.moles(i);
        }
        CodeReaction.Change change;
        try {
            change = c.code.compute(new CodeReaction.Input(
                    registry, moles, available.clone(), mixture.temperature(), mixture.volume(), seconds, ignition));
        } catch (RuntimeException | LinkageError e) {
            c.fail(mixture, "threw " + e);
            return null;
        }
        if (change == null) {
            return null;
        }
        String invalid = invalid(change, n);
        if (invalid != null) {
            c.fail(mixture, invalid);
            return null;
        }
        double[] consumed = change.consumed().clone();
        double[] produced = change.produced().clone();
        // The change as asked is extent 1; less if it asks for more than is there.
        double extent = maxExtent(consumed, available, 1);
        return new Active(consumed, produced, change.heat(), extent);
    }

    private static String invalid(CodeReaction.Change change, int n) {
        if (change.consumed() == null || change.produced() == null) {
            return "returned a change without consumed or produced moles";
        }
        if (change.consumed().length != n || change.produced().length != n) {
            return "returned arrays of length " + change.consumed().length + " and " + change.produced().length
                    + " for " + n + " gases";
        }
        for (int i = 0; i < n; i++) {
            if (!(change.consumed()[i] >= 0)
                    || !(change.produced()[i] >= 0)
                    || !Double.isFinite(change.consumed()[i])
                    || !Double.isFinite(change.produced()[i])) {
                return "returned a negative or non-finite amount";
            }
        }
        if (!Double.isFinite(change.heat())) {
            return "returned non-finite heat " + change.heat();
        }
        return null;
    }

    /** The most extent the reactants allow, at most {@code cap}. */
    private static double maxExtent(double[] nu, double[] available, double cap) {
        double extent = cap;
        for (int i = 0; i < nu.length; i++) {
            if (nu[i] > 0) {
                extent = Math.min(extent, available[i] / nu[i]);
            }
        }
        return extent == Double.POSITIVE_INFINITY ? 0 : extent;
    }

    private static double[] blend(double[] complete, double[] incomplete, double s) {
        double[] out = new double[complete.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = (1 - s) * complete[i] + s * incomplete[i];
        }
        return out;
    }

    private void requireRegistry(GasMixture mixture) {
        if (mixture.registry() != registry && !mixture.registry().equals(registry)) {
            throw new IllegalArgumentException("the mixture's gases aren't the ones the reactions were compiled for");
        }
    }

    /** One reaction's wanted change in a step, per unit of extent, and its extent. */
    private record Active(double[] consumed, double[] produced, double heat, double extent) {}

    /** A definition resolved to gas indices. */
    private static final class Compiled {
        final ReactionDefinition def;
        final CodeReaction code;
        final double[] nu;
        final double[] mu;
        /** J released per mol of extent. */
        final double heat;

        final double[] nuIncomplete;
        final double[] muIncomplete;
        final double heatIncomplete;
        /** Reactant indices, for a quick test that every reactant is there. */
        final int[] reactants;

        final boolean fire;
        final int fuel;
        final double lower;
        final double upper;
        final double autoignition;
        final int[] concentrationGas;
        final double[] concentrationMin;
        final double[] concentrationMax;
        final int[] catalystGas;
        final double[] catalystMin;
        final AtomicBoolean failed = new AtomicBoolean();

        Compiled(GasRegistry registry, ReactionDefinition def) {
            this.def = def;
            this.code = def.code().orElse(null);
            int n = registry.size();
            this.nu = dense(registry, def.reactants(), n);
            this.mu = dense(registry, def.products(), n);
            this.heat = -def.enthalpy();
            if (def.incomplete().isPresent()) {
                ReactionDefinition.Stoichiometry s = def.incomplete().get();
                this.nuIncomplete = dense(registry, s.reactants(), n);
                this.muIncomplete = dense(registry, s.products(), n);
                this.heatIncomplete = -s.enthalpy();
            } else {
                this.nuIncomplete = null;
                this.muIncomplete = null;
                this.heatIncomplete = 0;
            }
            this.reactants = def.reactants().keySet().stream()
                    .mapToInt(registry::indexOf)
                    .toArray();
            ReactionConditions cond = def.conditions();
            this.fire = cond.flammability().isPresent();
            this.fuel = cond.flammability().map(f -> registry.indexOf(f.fuel())).orElse(-1);
            this.lower = cond.flammability().map(f -> f.lower()).orElse(0.0);
            this.upper = cond.flammability().map(f -> f.upper()).orElse(1.0);
            this.autoignition = cond.flammability().map(f -> f.autoignition()).orElse(Double.POSITIVE_INFINITY);
            this.concentrationGas = cond.concentrations().stream()
                    .mapToInt(c -> registry.indexOf(c.gas()))
                    .toArray();
            this.concentrationMin = cond.concentrations().stream()
                    .mapToDouble(ReactionConditions.Concentration::min)
                    .toArray();
            this.concentrationMax = cond.concentrations().stream()
                    .mapToDouble(ReactionConditions.Concentration::max)
                    .toArray();
            this.catalystGas = cond.catalysts().stream()
                    .mapToInt(c -> registry.indexOf(c.gas()))
                    .toArray();
            this.catalystMin = cond.catalysts().stream()
                    .mapToDouble(ReactionConditions.Concentration::min)
                    .toArray();
        }

        boolean reactantsPresent(GasMixture m) {
            for (int i : reactants) {
                if (m.moles(i) <= 0) {
                    return false;
                }
            }
            return true;
        }

        /** Whether the conditions other than ignition and the fire oxygen threshold hold. */
        boolean holds(GasMixture m) {
            ReactionConditions cond = def.conditions();
            double t = m.temperature();
            if (t < cond.minTemperature() || t > cond.maxTemperature()) {
                return false;
            }
            double total = m.totalMoles();
            if (total <= 0) {
                return false;
            }
            if (fuel >= 0) {
                double x = m.moles(fuel) / total;
                if (x < lower || x > upper) {
                    return false;
                }
            }
            for (int k = 0; k < concentrationGas.length; k++) {
                double x = m.moles(concentrationGas[k]) / total;
                if (x < concentrationMin[k] || x > concentrationMax[k]) {
                    return false;
                }
            }
            for (int k = 0; k < catalystGas.length; k++) {
                double moles = m.moles(catalystGas[k]);
                if (moles <= 0 || moles / total < catalystMin[k]) {
                    return false;
                }
            }
            return true;
        }

        void fail(GasMixture m, String why) {
            if (failed.compareAndSet(false, true)) {
                m.config()
                        .log()
                        .warn("Reaction " + def.id() + " of type " + def.type() + " " + why
                                + "; skipped (further failures aren't logged)");
            }
        }

        private static double[] dense(GasRegistry registry, Map<String, Double> coefficients, int n) {
            double[] out = new double[n];
            coefficients.forEach((gas, v) -> out[registry.indexOf(gas)] = v);
            return out;
        }
    }
}
