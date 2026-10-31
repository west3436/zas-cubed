package io.github.west3436.zas.engine.gas;

/** Physical constants in the engine's units: kPa, L, mol, K. */
public final class GasConstants {
    /** The molar gas constant, R, in kPa·L/(mol·K) (CODATA 2018, exact). */
    public static final double R = 8.314462618;

    /** One standard atmosphere, in kPa. */
    public static final double ONE_ATMOSPHERE = 101.325;

    private GasConstants() {}
}
