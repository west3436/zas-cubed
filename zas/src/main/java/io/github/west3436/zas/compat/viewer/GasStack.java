package io.github.west3436.zas.compat.viewer;

import java.util.Objects;

/**
 * A gas with an amount: a reaction's coefficient, in moles for each mole of reaction. It is the
 * viewers' ingredient: two stacks of one gas are the same ingredient whatever their amounts.
 *
 * @param gas the gas
 * @param amount the coefficient; 1 for a gas listed on its own
 */
public record GasStack(GasEntry gas, double amount) {
    public GasStack {
        Objects.requireNonNull(gas, "gas");
    }

    /** One of a gas. */
    public static GasStack of(GasEntry gas) {
        return new GasStack(gas, 1);
    }
}
