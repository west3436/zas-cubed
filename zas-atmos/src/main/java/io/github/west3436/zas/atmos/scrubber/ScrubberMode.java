package io.github.west3436.zas.atmos.scrubber;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * What a scrubber does (CONT-079): nothing, keep only the selected gases of its sample, keep its
 * whole sample, or keep its whole sample at the panic rate.
 */
public enum ScrubberMode implements StringRepresentable {
    OFF(0),
    SCRUB(ScrubberMath.SAMPLE_LITRES),
    SIPHON(ScrubberMath.SAMPLE_LITRES),
    PANIC(ScrubberMath.PANIC_LITRES);

    /** The block state shows what the scrubber is doing: its mode while it runs, else off. */
    public static final EnumProperty<ScrubberMode> PROPERTY = EnumProperty.create("mode", ScrubberMode.class);

    private final double litres;

    ScrubberMode(double litres) {
        this.litres = litres;
    }

    /** The volume it samples per second, in L (CONT-080). */
    public double litres() {
        return litres;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mode with a saved name, or {@link #OFF} for an unknown one. */
    public static ScrubberMode byName(String name) {
        for (ScrubberMode m : values()) {
            if (m.getSerializedName().equals(name)) {
                return m;
            }
        }
        return OFF;
    }

    /** The mode with an ordinal, or {@link #OFF} for one out of range. */
    public static ScrubberMode of(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : OFF;
    }
}
