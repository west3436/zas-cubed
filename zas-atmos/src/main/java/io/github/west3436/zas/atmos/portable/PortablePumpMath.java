package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.pump.PumpFlow;

/**
 * What a portable pump moves in one second (CONT-018), through {@link PumpFlow}. It has two
 * directions. Releasing, it fills the air around it from its store to a target pressure, up to {@link
 * #RELEASE_LITRES} of the store's gas a second. Siphoning, it fills its store from the air to a target
 * pressure, with no such limit, as vg's does.
 */
public final class PortablePumpMath {
    /** The most it releases per second, L at the store's conditions: vg's 300 L per 2 s tick (D-20). */
    public static final double RELEASE_LITRES = 150;

    /** A new pump's target, kPa: one atmosphere. */
    public static final double DEFAULT_TARGET = StandardAir.PRESSURE;

    private PortablePumpMath() {}

    /** A target within the pump's range: 0 to {@link PumpFlow#MAX_TARGET} kPa (CONT-011). */
    public static double clampTarget(double kPa) {
        return Math.max(0, Math.min(PumpFlow.MAX_TARGET, kPa));
    }

    /**
     * The moles to release from the store to the air in one second.
     *
     * @param target the air's target pressure, kPa
     * @param store its store
     * @param air the air: a zone or a held tank
     * @return the moles, never more than the store holds; 0 once the air is at the target
     */
    public static double release(double target, GasMixture store, GasMixture air) {
        PumpFlow.Side in = PumpFlow.Side.of(store);
        return Math.min(
                PumpFlow.molesToTarget(target, in, PumpFlow.Side.of(air)), PumpFlow.molesInVolume(RELEASE_LITRES, in));
    }

    /**
     * The moles to siphon from the air to the store in one second.
     *
     * @param target the store's target pressure, kPa
     * @param air the air: a zone or a held tank
     * @param store its store
     * @return the moles, never more than the air holds; 0 once the store is at the target
     */
    public static double siphon(double target, GasMixture air, GasMixture store) {
        return Math.min(
                PumpFlow.molesToTarget(target, PumpFlow.Side.of(air), PumpFlow.Side.of(store)), air.totalMoles());
    }
}
