package io.github.west3436.zas.atmos.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.failure.ContainerFailure.Kind;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Verdict;
import org.junit.jupiter.api.Test;

/** The failure rule at each threshold, with the formula's power and the cap (CONT-038, CONT-085, CONT-101, CONT-102, CONT-109). */
class ContainerFailureTest {
    private static final double ATM = 101.325;
    private static final double CAP = ContainerFailure.DEFAULT_POWER_CAP;
    private static final double ROOM_K = 293.15;
    private static final double EPS = 1e-9;

    private static Kind tank(double atm) {
        return ContainerFailure.tank(atm * ATM, CAP).kind();
    }

    private static Kind canister(double kPa) {
        return ContainerFailure.canister(kPa, ROOM_K, CAP).kind();
    }

    @Test
    void cont_038_a_tank_holds_up_to_30_atm() {
        assertEquals(Kind.NONE, tank(0));
        assertEquals(Kind.NONE, tank(6));
        assertEquals(Kind.NONE, tank(30));
    }

    @Test
    void cont_038_a_tank_leaks_above_30_atm_up_to_40() {
        assertEquals(Kind.LEAK, tank(30.001));
        assertEquals(Kind.LEAK, tank(40));
    }

    @Test
    void cont_038_a_tank_ruptures_above_40_atm_up_to_50() {
        assertEquals(Kind.RUPTURE, tank(40.001));
        assertEquals(Kind.RUPTURE, tank(50));
    }

    @Test
    void cont_038_a_tank_explodes_above_50_atm() {
        assertEquals(Kind.EXPLODE, tank(50.001));
        assertEquals(Kind.EXPLODE, tank(500));
    }

    @Test
    void cont_038_only_an_explosion_has_power() {
        assertEquals(0, ContainerFailure.tank(35 * ATM, CAP).power());
        assertEquals(0, ContainerFailure.tank(45 * ATM, CAP).power());
    }

    @Test
    void cont_038_a_tanks_power_is_its_pressure_above_50_atm_over_10_atm() {
        assertEquals(0.1, ContainerFailure.tank(51 * ATM, CAP).power(), EPS);
        assertEquals(1, ContainerFailure.tank(60 * ATM, CAP).power(), EPS);
        assertEquals(4, ContainerFailure.tank(90 * ATM, CAP).power(), EPS);
        assertEquals(15.5, ContainerFailure.tank(205 * ATM, CAP).power(), EPS);
    }

    @Test
    void cont_109_a_tanks_power_is_capped_at_16_by_default() {
        assertEquals(16, ContainerFailure.DEFAULT_POWER_CAP);
        assertEquals(16, ContainerFailure.tank(210 * ATM, CAP).power(), EPS);
        assertEquals(16, ContainerFailure.tank(1000 * ATM, CAP).power(), EPS);
    }

    @Test
    void cont_109_the_cap_is_a_parameter() {
        assertEquals(8, ContainerFailure.tank(210 * ATM, 8).power(), EPS);
        assertEquals(1, ContainerFailure.tank(60 * ATM, 8).power(), EPS);
        assertEquals(0, ContainerFailure.tank(210 * ATM, 0).power(), EPS);
        assertEquals(Kind.EXPLODE, ContainerFailure.tank(210 * ATM, 0).kind());
        assertEquals(8, ContainerFailure.canister(13680 + 100 * ATM, ROOM_K, 8).power(), EPS);
    }

    @Test
    void cont_109_a_pressure_that_overflows_is_still_capped() {
        assertEquals(16, ContainerFailure.tank(Double.POSITIVE_INFINITY, CAP).power());
    }

    @Test
    void cont_101_a_canister_ruptures_just_above_its_rating_and_not_below() {
        assertEquals(Kind.NONE, canister(0));
        assertEquals(Kind.NONE, canister(9119));
        assertEquals(Kind.NONE, canister(9120));
        assertEquals(Kind.RUPTURE, canister(9120.001));
        assertEquals(Kind.RUPTURE, canister(13680));
    }

    @Test
    void cont_085_a_canister_ruptures_above_1273_k_at_any_pressure() {
        assertEquals(Verdict.NONE, ContainerFailure.canister(100, 1273, CAP));
        assertEquals(Verdict.RUPTURE, ContainerFailure.canister(100, 1273.001, CAP));
        assertEquals(Verdict.RUPTURE, ContainerFailure.canister(0, 2000, CAP));
    }

    @Test
    void cont_102_a_canister_explodes_above_150_percent_of_its_rating() {
        assertEquals(13680.0, 1.5 * ContainerFailure.CANISTER_RATED_KPA);
        assertEquals(Kind.EXPLODE, canister(13680.001));
    }

    @Test
    void cont_102_a_hot_canister_above_the_threshold_explodes_rather_than_ruptures() {
        assertEquals(Kind.EXPLODE, ContainerFailure.canister(14000, 2000, CAP).kind());
    }

    @Test
    void cont_102_a_canisters_power_is_its_pressure_above_135_atm_over_10_atm() {
        assertEquals(1, ContainerFailure.canister(13680 + 10 * ATM, ROOM_K, CAP).power(), EPS);
        assertEquals(
                2.5, ContainerFailure.canister(13680 + 25 * ATM, ROOM_K, CAP).power(), EPS);
        assertEquals(
                16, ContainerFailure.canister(13680 + 500 * ATM, ROOM_K, CAP).power(), EPS);
    }

    @Test
    void cont_038_a_rupture_and_an_explosion_destroy_the_container() {
        assertFalse(Verdict.NONE.destroys());
        assertFalse(Verdict.LEAK.destroys());
        assertTrue(Verdict.RUPTURE.destroys());
        assertTrue(ContainerFailure.tank(60 * ATM, CAP).destroys());
    }

    @Test
    void cont_038_a_leak_lets_out_a_tenth_of_the_gas_each_tick() {
        assertEquals(10, ContainerFailure.leakMoles(100, 39 * ATM), EPS);
    }

    @Test
    void cont_038_a_leak_stops_at_30_atm() {
        double kPa = 31 * ATM;
        double moles = 100;
        double out = ContainerFailure.leakMoles(moles, kPa);
        assertEquals(100.0 / 31, out, EPS, "only down to 30 atm, less than a tenth");
        assertEquals(30 * ATM, (moles - out) / moles * kPa, EPS);
        assertEquals(0, ContainerFailure.leakMoles(100, 30 * ATM));
        assertEquals(0, ContainerFailure.leakMoles(100, 6 * ATM));
        assertEquals(0, ContainerFailure.leakMoles(0, 35 * ATM));
    }
}
