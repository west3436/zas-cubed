package io.github.west3436.zas.api.gas;

import io.github.west3436.zas.api.AnyThread;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Standard Overworld air: 101.325 kPa, 293.15 K, 79 % N₂ and 21 % O₂ by moles
 * (ENV-011). It is what the API returns when ZAS³ is absent (D-17).
 */
public final class StandardAir {
    /** The id of nitrogen, N₂. */
    public static final ResourceLocation NITROGEN = ResourceLocation.fromNamespaceAndPath("zas", "nitrogen");

    /** The id of oxygen, O₂. */
    public static final ResourceLocation OXYGEN = ResourceLocation.fromNamespaceAndPath("zas", "oxygen");

    /** The total pressure, in kPa. */
    public static final double PRESSURE = 101.325;

    /** The temperature, in kelvin. */
    public static final double TEMPERATURE = 293.15;

    /** The mole fraction of nitrogen. */
    public static final double NITROGEN_FRACTION = 0.79;

    /** The mole fraction of oxygen. */
    public static final double OXYGEN_FRACTION = 0.21;

    /** The gas volume of one full air cell, in litres (GEO-002). */
    public static final double CELL_VOLUME = 1000.0;

    private StandardAir() {}

    /**
     * Standard air filling a volume.
     *
     * <p>Thread: any.
     *
     * @param volume the volume in litres, positive
     * @return a mixture at 101.325 kPa and 293.15 K
     * @throws IllegalArgumentException if the volume is not positive
     */
    @AnyThread
    public static GasMixture mixture(double volume) {
        if (!(volume > 0) || !Double.isFinite(volume)) {
            throw new IllegalArgumentException("volume must be finite and > 0 L: " + volume);
        }
        double total = PRESSURE * volume / (GasMixture.GAS_CONSTANT * TEMPERATURE);
        return new GasMixture(
                Map.of(NITROGEN, total * NITROGEN_FRACTION, OXYGEN, total * OXYGEN_FRACTION), TEMPERATURE, volume);
    }
}
