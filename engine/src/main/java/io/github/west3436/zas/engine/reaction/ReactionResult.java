package io.github.west3436.zas.engine.reaction;

/**
 * What one step of reactions did to a mixture.
 *
 * @param moles the change in moles per gas, indexed like the registry: what a conservation ledger
 *     records under {@code REACTION}. Pruning afterwards is the mixture's own (CORE-007)
 * @param heat the heat released, J; negative if the reactions absorbed heat
 * @param reactions how many reactions happened
 */
public record ReactionResult(double[] moles, double heat, int reactions) {
    /** Whether any reaction happened. */
    public boolean reacted() {
        return reactions > 0;
    }

    static ReactionResult none(int gases) {
        return new ReactionResult(new double[gases], 0, 0);
    }
}
