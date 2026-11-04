package io.github.west3436.zas.atmos.portable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class PortablePumpMathTest {
    private static final ResourceLocation O2 = ResourceLocation.fromNamespaceAndPath("zas", "oxygen");
    private static final double T = 293.15;
    private static final double RT = GasMixture.GAS_CONSTANT * T;
    private static final double ROOM = 67_500;

    /** A mixture of O₂ at a pressure, in kPa. */
    private static GasMixture air(double kPa, double volume) {
        return new GasMixture(Map.of(O2, kPa * volume / RT), T, volume);
    }

    private static GasMixture store(double kPa) {
        return air(kPa, PortableGasDevice.STORE_LITRES);
    }

    /** Releasing far from the target, it moves the moles of 150 L of its store each second. */
    @Test
    void cont_018_a_release_is_limited_to_150_litres_of_the_store() {
        GasMixture store = store(1_000);
        assertEquals(
                store.totalMoles() * 150 / PortableGasDevice.STORE_LITRES,
                PortablePumpMath.release(500, store, air(100, ROOM)),
                1e-9);
    }

    /** Releasing close to the target, it moves what brings the air to the target. */
    @Test
    void cont_018_a_release_stops_at_the_target() {
        assertEquals(1 * ROOM / RT, PortablePumpMath.release(101, store(1_000), air(100, ROOM)), 1e-6);
        assertEquals(0, PortablePumpMath.release(100, store(1_000), air(100, ROOM)), 1e-9);
        assertEquals(0, PortablePumpMath.release(90, store(1_000), air(100, ROOM)), 1e-9);
    }

    /** A small tank takes all of what brings it to the target, if the store's limit allows. */
    @Test
    void cont_018_a_release_never_takes_more_than_the_store_holds() {
        GasMixture nearlyEmpty = store(0.1);
        assertEquals(
                nearlyEmpty.totalMoles() * 150 / PortableGasDevice.STORE_LITRES,
                PortablePumpMath.release(4_500, nearlyEmpty, air(0, 70)),
                1e-9);
    }

    /** Siphoning, it fills its store to the target in one go, with no limit but what the air holds. */
    @Test
    void cont_018_a_siphon_fills_the_store_to_the_target_at_once() {
        assertEquals(
                400 * PortableGasDevice.STORE_LITRES / RT,
                PortablePumpMath.siphon(500, air(100, ROOM), store(100)),
                1e-6);
    }

    /** A siphon stops at the target, and takes no more than the air holds. */
    @Test
    void cont_018_a_siphon_stops_at_the_target_and_when_the_air_runs_out() {
        assertEquals(0, PortablePumpMath.siphon(100, air(100, ROOM), store(100)), 1e-9);
        assertEquals(0, PortablePumpMath.siphon(90, air(100, ROOM), store(100)), 1e-9);
        GasMixture tank = air(100, 70);
        assertEquals(tank.totalMoles(), PortablePumpMath.siphon(4_500, tank, store(0)), 1e-9);
    }

    /** The target stays in the pump's range (CONT-011). */
    @Test
    void cont_018_the_target_is_clamped_to_the_pump_range() {
        assertEquals(0, PortablePumpMath.clampTarget(-5), 0);
        assertEquals(4_500, PumpFlow.MAX_TARGET, 0);
        assertEquals(PumpFlow.MAX_TARGET, PortablePumpMath.clampTarget(9_000), 0);
        assertEquals(101.325, PortablePumpMath.DEFAULT_TARGET, 0);
    }
}
