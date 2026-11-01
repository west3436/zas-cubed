package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Whether an entity can breathe where it is, and whether it is protected (API-014): the answer of
 * {@link Breathing#of}. Its parts:
 *
 * <ul>
 *   <li>{@link #canBreathe()} and {@link #reason()}: whether it can breathe, and why;
 *   <li>{@link #source()} and {@link #breath()}: what it breathes from, and the gas it breathes;
 *   <li>{@link #isProtected()} and {@link #hazards()}: whether the environment harms it in any
 *       other way, and how;
 *   <li>{@link #protection()}: what protects it.
 * </ul>
 *
 * <p>It is an immutable value, the answer at one moment, and may be used from any thread. Two
 * verdicts are equal when every part is.
 *
 * <p>It is a class with a builder rather than a record so that later versions can add parts
 * without breaking the mods that read it or the providers that build it. A part a provider
 * doesn't set has the default its accessor names.
 */
@AnyThread
public final class Breathability {
    private final BreathReason reason;
    private final BreathSource source;
    private final GasMixture breath;
    private final Set<Hazard> hazards;
    private final Set<Protection> protection;

    private Breathability(Builder b) {
        this.reason = b.reason;
        this.source = b.source;
        this.breath = b.breath;
        this.hazards = Collections.unmodifiableSet(EnumSet.copyOf(b.hazards));
        this.protection = Collections.unmodifiableSet(EnumSet.copyOf(b.protection));
    }

    /**
     * A verdict with only a reason and a source: no breath gas, no hazards and no protection.
     *
     * <p>Thread: any.
     *
     * @param reason why it can or can't breathe
     * @param source what it breathes from
     * @return the verdict
     * @throws NullPointerException if an argument is null
     */
    @AnyThread
    public static Breathability of(BreathReason reason, BreathSource source) {
        return builder(reason, source).build();
    }

    /**
     * Starts a verdict. Providers use this; the builder sets the other parts.
     *
     * <p>Thread: any.
     *
     * @param reason why it can or can't breathe
     * @param source what it breathes from
     * @return a new builder
     * @throws NullPointerException if an argument is null
     */
    @AnyThread
    public static Builder builder(BreathReason reason, BreathSource source) {
        return new Builder(reason, source);
    }

    /**
     * Whether it can breathe: its air holds or refills rather than running out. The same as
     * {@code reason().canBreathe()}.
     *
     * <p>Thread: any.
     *
     * @return true if it can breathe
     */
    @AnyThread
    public boolean canBreathe() {
        return reason.canBreathe();
    }

    /**
     * Why it can or can't breathe.
     *
     * <p>Thread: any.
     *
     * @return the reason
     */
    @AnyThread
    public BreathReason reason() {
        return reason;
    }

    /**
     * What it breathes from.
     *
     * <p>Thread: any.
     *
     * @return the source
     */
    @AnyThread
    public BreathSource source() {
        return source;
    }

    /**
     * The gas it breathes, when the verdict looked at one: the atmosphere at its eyes, or what its
     * internals deliver. The pressures that matter, such as the O₂ partial pressure LIFE-003
     * compares with its minimum, are this mixture's.
     *
     * <p>Empty by default: when it breathes no gas ({@link BreathSource#isGas()} is false), and in
     * vanilla's verdict, which never looks at the gas.
     *
     * <p>Thread: any.
     *
     * @return the gas breathed, or empty
     */
    @AnyThread
    public Optional<GasMixture> breath() {
        return Optional.ofNullable(breath);
    }

    /**
     * Whether it is protected: nothing in the environment harms it here other than running out of
     * breath, because no hazard applies or its protection covers each one. The same as
     * {@code hazards().isEmpty()}.
     *
     * <p>Thread: any.
     *
     * @return true if no hazard harms it here
     */
    @AnyThread
    public boolean isProtected() {
        return hazards.isEmpty();
    }

    /**
     * The hazards that harm it here despite its protection. Running out of breath isn't one:
     * that is {@link #canBreathe()}.
     *
     * <p>Empty by default, as in vanilla's verdict: nothing in vanilla's air harms an entity.
     *
     * <p>Thread: any.
     *
     * @return an unmodifiable set, in declaration order
     */
    @AnyThread
    public Set<Hazard> hazards() {
        return hazards;
    }

    /**
     * What protects it here: the life support that applies to it, whether or not a hazard needs
     * it, and the rules that spare it.
     *
     * <p>Empty by default, as in vanilla's verdict.
     *
     * <p>Thread: any.
     *
     * @return an unmodifiable set, in declaration order
     */
    @AnyThread
    public Set<Protection> protection() {
        return protection;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Breathability b
                && reason == b.reason
                && source == b.source
                && Objects.equals(breath, b.breath)
                && hazards.equals(b.hazards)
                && protection.equals(b.protection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, source, breath, hazards, protection);
    }

    @Override
    public String toString() {
        return "Breathability[reason=" + reason + ", source=" + source + ", breath=" + breath + ", hazards=" + hazards
                + ", protection=" + protection + "]";
    }

    /**
     * Builds a {@link Breathability}. It is not thread-safe; build on one thread, then share the
     * verdict. Building doesn't change the builder, and later changes to it don't change verdicts
     * already built.
     */
    public static final class Builder {
        private final BreathReason reason;
        private final BreathSource source;
        private GasMixture breath;
        private final EnumSet<Hazard> hazards = EnumSet.noneOf(Hazard.class);
        private final EnumSet<Protection> protection = EnumSet.noneOf(Protection.class);

        private Builder(BreathReason reason, BreathSource source) {
            this.reason = Objects.requireNonNull(reason, "reason");
            this.source = Objects.requireNonNull(source, "source");
        }

        /**
         * Sets the gas it breathes ({@link Breathability#breath()}).
         *
         * <p>Thread: any, one at a time; a builder isn't thread-safe.
         *
         * @param breath the gas breathed
         * @return this builder
         * @throws NullPointerException if {@code breath} is null
         */
        public Builder breath(GasMixture breath) {
            this.breath = Objects.requireNonNull(breath, "breath");
            return this;
        }

        /**
         * Adds a hazard that harms it here despite its protection ({@link Breathability#hazards()}).
         *
         * <p>Thread: any, one at a time; a builder isn't thread-safe.
         *
         * @param hazard the hazard
         * @return this builder
         * @throws NullPointerException if {@code hazard} is null
         */
        public Builder hazard(Hazard hazard) {
            hazards.add(Objects.requireNonNull(hazard, "hazard"));
            return this;
        }

        /**
         * Adds something that protects it here ({@link Breathability#protection()}).
         *
         * <p>Thread: any, one at a time; a builder isn't thread-safe.
         *
         * @param protection the protection
         * @return this builder
         * @throws NullPointerException if {@code protection} is null
         */
        public Builder protection(Protection protection) {
            this.protection.add(Objects.requireNonNull(protection, "protection"));
            return this;
        }

        /**
         * Builds the verdict.
         *
         * <p>Thread: any, one at a time; a builder isn't thread-safe.
         *
         * @return the verdict
         * @throws IllegalArgumentException if a breath gas is set but the source supplies no gas
         */
        public Breathability build() {
            if (breath != null && !source.isGas()) {
                throw new IllegalArgumentException("a verdict breathing from " + source + " can't carry a breath gas");
            }
            return new Breathability(this);
        }
    }
}
