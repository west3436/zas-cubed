package io.github.west3436.zas.api.reaction;

import com.mojang.serialization.Dynamic;
import io.github.west3436.zas.api.AnyThread;

/**
 * A reaction type implemented in code (API-020), for reactions a datapack definition can't express
 * with stoichiometry alone. Register it with {@link Reactions#registerType}; a datapack reaction
 * definition then names it in its {@code "type"} field, and may give it {@code "parameters"}.
 *
 * <p>The definition's own conditions (temperatures, concentrations, catalysts, ignition and
 * flammability limits; see {@code docs/datapacks.md}) are checked first. Only where they all hold is
 * {@link #react} called, once per zone per atmos tick, or once per {@link
 * Reactions#applyStandalone} call. If several reactions want more of a gas than there is, ZAS³
 * scales the change it returns down, as a whole, like any reaction's (FIRE-002).
 *
 * <p><b>Threads.</b> {@link #react} runs on the atmos worker thread, or on whatever thread calls
 * {@link Reactions#applyStandalone}, possibly on several at once. It must be a pure function of
 * its context: no world access, no shared mutable state, no randomness and no clock, so the
 * simulation stays deterministic (architecture §3.5). If it throws, or returns something invalid,
 * ZAS³ skips it for that call, logs it, and carries on (fault isolation, §3.6).
 */
@FunctionalInterface
public interface ReactionType {
    /**
     * The change this reaction wants, given a mixture where the definition's conditions hold.
     *
     * <p>Thread: any; see the class description.
     *
     * @param context the mixture, the gas it may use, the time step and whether an ignition
     *     source is present
     * @return the change, or {@code null} for none
     */
    @AnyThread
    ReactionChange react(ReactionContext context);

    /**
     * This type configured with one definition's {@code "parameters"}. ZAS³ calls it when it loads
     * a definition naming this type, on the thread that loads datapacks, and calls {@link #react}
     * on what it returns.
     *
     * <p>The default ignores the parameters and returns this type. A type that takes parameters
     * overrides it, decoding them with a codec, for example {@code
     * MY_CODEC.parse(parameters).getOrThrow(IllegalArgumentException::new)}.
     *
     * @param parameters the definition's {@code "parameters"} value; an empty map if it gave none
     * @return the configured reaction
     * @throws IllegalArgumentException if the parameters are invalid; the definition is then
     *     skipped, and the message logged with its file
     */
    default ReactionType withParameters(Dynamic<?> parameters) {
        return this;
    }
}
