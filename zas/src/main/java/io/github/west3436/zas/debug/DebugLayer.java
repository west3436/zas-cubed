package io.github.west3436.zas.debug;

import java.util.Locale;

/** A layer of the operator debug overlay, switched on and off with {@code /zas debug <layer>}. */
public enum DebugLayer {
    /** Zoned cells tinted per zone; paused zones and unresolved regions apart (CLIENT-005). */
    ZONES,
    /** Faces between zones and to the ambient, by type, with pressure differences (CLIENT-011). */
    EDGES,
    /** The seal mask and sub-cells of the block under the crosshair (CLIENT-012). */
    CELLS,
    /** Hotspots (CLIENT-013). */
    HOTSPOTS;

    /** Every layer's bit. */
    public static final int ALL = (1 << values().length) - 1;

    /** This layer's bit in a layer mask. */
    public int bit() {
        return 1 << ordinal();
    }

    /** Whether a layer mask holds this layer. */
    public boolean in(int layers) {
        return (layers & bit()) != 0;
    }

    /** The name used in commands: {@code zones}, {@code edges}, {@code cells}, {@code hotspots}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
