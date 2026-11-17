package io.github.west3436.zas.compat.pneumaticcraft;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;

/**
 * PneumaticCraft's air as ZAS³'s gas (Q-24). Pure: no PneumaticCraft and no game, so the unit tests
 * reach every case.
 *
 * <p><b>What its air is.</b> PneumaticCraft keeps the air in a machine or an item as one whole
 * number, the pressure in bar times the volume in mL, and calls that amount mL. Its pressure is
 * gauge pressure: 0 bar is the air outside, and a machine at 0 bar holds no air of its own. So one
 * unit is 1 mL of air at 1 bar above the air round it, which is 1 mL compressed to 1 bar: 100 kPa ×
 * 0.001 L / (R × T) moles. At standard air's 293.15 K that is about 4.103 × 10⁻⁵ mol, so a full
 * Pneumatic Chestplate, 28,000 mL at 10 bar, holds about 11.5 mol, and a machine leaking at 5 bar
 * gives about 0.18 mol a second.
 *
 * <p><b>What gas it is.</b> Standard air (79 % N₂, 21 % O₂) at 293.15 K: PneumaticCraft's compressors
 * take in the air round them and its air has no temperature of its own.
 *
 * <p>The factor is a server setting ({@link PncSettings#molesPerMillilitre}), so a pack can scale
 * it; this is its default.
 */
public final class AirUnits {
    /** One bar, in kPa: the pressure one unit of PneumaticCraft's air is counted at. */
    public static final double KPA_PER_BAR = 100.0;

    /** One mL, in litres. */
    public static final double LITRES_PER_ML = 0.001;

    /** The temperature PneumaticCraft's air is taken to be at, in kelvin: standard air's. */
    public static final double TEMPERATURE = StandardAir.TEMPERATURE;

    /** The moles one unit of PneumaticCraft's air is unless set: 1 mL at 1 bar and 293.15 K. */
    public static final double DEFAULT_MOLES_PER_ML =
            KPA_PER_BAR * LITRES_PER_ML / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);

    /** The least the factor can be set to: 0 makes the air worth nothing, so no leak adds gas. */
    public static final double MIN_MOLES_PER_ML = 0.0;

    /** The most the factor can be set to: a thousand times the default. */
    public static final double MAX_MOLES_PER_ML = 1000 * DEFAULT_MOLES_PER_ML;

    private AirUnits() {}

    /**
     * Standard air, an amount of it.
     *
     * @param moles the moles in all, at least 0
     * @return 79 % N₂ and 21 % O₂ at 293.15 K; empty for 0
     */
    public static GasParcel standardAir(double moles) {
        if (!(moles > 0)) {
            return GasParcel.EMPTY;
        }
        return new GasParcel(
                Map.of(
                        StandardAir.NITROGEN,
                        moles * StandardAir.NITROGEN_FRACTION,
                        StandardAir.OXYGEN,
                        moles * StandardAir.OXYGEN_FRACTION),
                TEMPERATURE);
    }

    /**
     * How much air a leak of PneumaticCraft's gives in a tick: {@code pressure × 40 + 20} mL, cut to
     * a whole number as PneumaticCraft cuts it, and never more than the air there is. This is the
     * amount its API documents for {@code IAirHandlerMachine.setSideLeaking}, worked out in the
     * same float arithmetic.
     *
     * @param pressure the handler's pressure in bar, as it reads it
     * @param air the air it holds, in mL
     * @return the air that leaks, in mL; 0 if it holds none
     */
    public static int leakPerTick(float pressure, int air) {
        if (air <= 0) {
            return 0;
        }
        int leaked = (int) (pressure * 40) + 20;
        return Math.max(0, Math.min(leaked, air));
    }
}
