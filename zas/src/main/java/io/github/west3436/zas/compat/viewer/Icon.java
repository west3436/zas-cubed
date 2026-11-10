package io.github.west3436.zas.compat.viewer;

/**
 * The small icons that stand for a condition or a property, in the order they sit on the sheet
 * {@code textures/gui/viewer/icons.png}: six to a row, each in a cell of 10 pixels.
 */
public enum Icon {
    /** The reaction needs something to light it. */
    SPARK,
    /** The temperature at which a fuel lights itself, and the flammable tag. */
    FLAME,
    /** A temperature range. */
    THERMOMETER,
    /** A gas's share of the mixture. */
    SHARE,
    /** A pressure. */
    GAUGE,
    /** How fast the reaction runs. */
    CLOCK,
    /** Heat released. */
    HEAT_OUT,
    /** Heat absorbed. */
    HEAT_IN,
    /** The activation energy. */
    BARRIER,
    /** A reaction type implemented in code. */
    GEAR,
    /** Molar mass. */
    MASS,
    /** Heat capacity. */
    CAPACITY,
    /** A gas tag with no icon of its own. */
    TAG,
    /** The oxidizer tag. */
    OXIDIZER,
    /** The toxic tag. */
    TOXIC;

    /** An icon's width and height. */
    public static final int SIZE = 9;

    /** The sheet's width. */
    public static final int SHEET_WIDTH = 64;

    /** The sheet's height. */
    public static final int SHEET_HEIGHT = 32;

    private static final int PER_ROW = 6;
    private static final int PITCH = 10;

    /** The icon's left edge on the sheet. */
    public int u() {
        return ordinal() % PER_ROW * PITCH;
    }

    /** The icon's top edge on the sheet. */
    public int v() {
        return ordinal() / PER_ROW * PITCH;
    }
}
