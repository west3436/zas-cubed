package io.github.west3436.zas.engine.gas;

/**
 * How a gas shows in the world (CORE-010).
 *
 * @param rgb the colour, as {@code 0xRRGGBB}
 * @param densityThreshold the molar density, in mol/L, above which the gas is visible
 */
public record GasVisibility(int rgb, double densityThreshold) {
    public GasVisibility {
        if ((rgb & ~0xFFFFFF) != 0) {
            throw new IllegalArgumentException("rgb must be 0xRRGGBB, got 0x" + Integer.toHexString(rgb));
        }
        if (!Double.isFinite(densityThreshold) || densityThreshold <= 0) {
            throw new IllegalArgumentException("densityThreshold must be finite and positive, got " + densityThreshold);
        }
    }
}
