package io.github.west3436.zas.engine.reaction;

import io.github.west3436.zas.engine.gas.GasRegistry;

/**
 * A reaction type implemented in code (API-020), bound to one datapack definition. The platform
 * adapts the API's {@code ReactionType} to this.
 *
 * <p>It runs on the atmos worker, or on whatever thread calls a standalone reaction (API-019), so it
 * must be a pure function of its input (architecture §3.6). The engine checks what it returns and
 * scales it back like any reaction that competes for gas (FIRE-002). If it throws or returns
 * something invalid, the engine skips it for that step and logs the first failure.
 */
@FunctionalInterface
public interface CodeReaction {
    /**
     * The change this reaction wants in one step, once the definition's conditions hold.
     *
     * @return the change, or {@code null} for none
     */
    Change compute(Input input);

    /**
     * What a code reaction sees.
     *
     * @param registry the gases; {@code moles} is indexed like it
     * @param moles moles per gas in the whole mixture; a copy the reaction may keep
     * @param available moles per gas it may use: all of {@code moles}, or a fire's parcel of it
     * @param temperature K
     * @param volume L
     * @param seconds the step's length; 0 for a fire's parcel, which burns at once
     * @param ignition whether an ignition source is present
     */
    record Input(
            GasRegistry registry,
            double[] moles,
            double[] available,
            double temperature,
            double volume,
            double seconds,
            boolean ignition) {}

    /**
     * A wanted change. The engine may scale it down as a whole, never up.
     *
     * @param consumed moles of each gas consumed, indexed like the registry, none negative
     * @param produced moles of each gas produced, likewise
     * @param heat J released; negative absorbs heat
     */
    record Change(double[] consumed, double[] produced, double heat) {}
}
