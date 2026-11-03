package io.github.west3436.zas.engine.reaction;

import static io.github.west3436.zas.engine.reaction.Chemistry.CH4;
import static io.github.west3436.zas.engine.reaction.Chemistry.CO;
import static io.github.west3436.zas.engine.reaction.Chemistry.CO2;
import static io.github.west3436.zas.engine.reaction.Chemistry.COMBUSTION;
import static io.github.west3436.zas.engine.reaction.Chemistry.FIRE;
import static io.github.west3436.zas.engine.reaction.Chemistry.H2;
import static io.github.west3436.zas.engine.reaction.Chemistry.H2O;
import static io.github.west3436.zas.engine.reaction.Chemistry.N2;
import static io.github.west3436.zas.engine.reaction.Chemistry.N2O;
import static io.github.west3436.zas.engine.reaction.Chemistry.O2;
import static io.github.west3436.zas.engine.reaction.Chemistry.REGISTRY;
import static io.github.west3436.zas.engine.reaction.Chemistry.fuelInAir;
import static io.github.west3436.zas.engine.reaction.Chemistry.mixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.gas.GasMixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReactionSetTest {
    private static final double EPS = 1e-9;

    // ---- FIRE-003: built-in combustion ---------------------------------------------------------

    @Test
    void fire_003_hydrogen_burns_to_water_releasing_242_kj_per_mol() {
        GasMixture m = fuelInAir(1000, 293.15, H2, 0.10);
        double h2 = m.moles(H2);
        double o2 = m.moles(O2);
        double energy = m.heatCapacity() * m.temperature();

        ReactionResult r = COMBUSTION.burn(m, 1000, true, FIRE);

        assertEquals(1, r.reactions());
        assertEquals(0, m.moles(H2));
        assertEquals(h2, m.moles(H2O), EPS);
        assertEquals(o2 - h2 / 2, m.moles(O2), EPS);
        assertEquals(242_000 * h2, r.heat(), 1e-6);
        assertEquals(energy + r.heat(), m.heatCapacity() * m.temperature(), 1e-6);
        assertEquals(-h2, r.moles()[H2], EPS);
        assertEquals(h2, r.moles()[H2O], EPS);
    }

    @Test
    void fire_003_methane_burns_to_carbon_dioxide_and_water_releasing_802_kj_per_mol() {
        GasMixture m = fuelInAir(1000, 293.15, CH4, 0.08);
        double ch4 = m.moles(CH4);
        double o2 = m.moles(O2);
        double energy = m.heatCapacity() * m.temperature();

        ReactionResult r = COMBUSTION.burn(m, 1000, true, FIRE);

        assertEquals(0, m.moles(CH4));
        assertEquals(ch4, m.moles(CO2), EPS);
        assertEquals(2 * ch4, m.moles(H2O), EPS);
        assertEquals(o2 - 2 * ch4, m.moles(O2), EPS);
        assertEquals(802_000 * ch4, r.heat(), 1e-6);
        assertEquals(energy + r.heat(), m.heatCapacity() * m.temperature(), 1e-6);
    }

    @Test
    void fire_003_carbon_monoxide_burns_to_carbon_dioxide_releasing_283_kj_per_mol() {
        GasMixture m = fuelInAir(1000, 293.15, CO, 0.20);
        double co = m.moles(CO);
        double o2 = m.moles(O2);
        double energy = m.heatCapacity() * m.temperature();

        ReactionResult r = COMBUSTION.burn(m, 1000, true, FIRE);

        assertEquals(0, m.moles(CO));
        assertEquals(co, m.moles(CO2), EPS);
        assertEquals(o2 - co / 2, m.moles(O2), EPS);
        assertEquals(283_000 * co, r.heat(), 1e-6);
        assertEquals(energy + r.heat(), m.heatCapacity() * m.temperature(), 1e-6);
    }

    @Test
    void fire_003_heat_raises_the_temperature_through_the_products_heat_capacities() {
        // 1 mol H2 and 0.5 mol O2 burn to 1 mol H2O: T' = (C·T + 242 kJ) / C(H2O).
        GasMixture m = mixture(100, 300, H2, 1, O2, 0.5);
        double before = (20.52 + 0.5 * 21.06) * 300;
        // Outside the flammability limits as a fraction? H2 is 2/3 of it: within 4-75 %.
        COMBUSTION.burn(m, 100, true, FIRE);
        assertEquals((before + 242_000) / 25.28, m.temperature(), 1e-6);
    }

    // ---- FIRE-006: reactions requiring ignition ------------------------------------------------

    @Test
    void fire_006_a_reaction_requiring_ignition_waits_for_a_source() {
        GasMixture m = fuelInAir(1000, 293.15, H2, 0.10);
        double[] before = Chemistry.moles(m);

        assertFalse(COMBUSTION.burn(m, 1000, false, FIRE).reacted());
        assertArrayEquals(before, Chemistry.moles(m));
        // Zone-wide reactions never run it, whatever the time.
        assertFalse(COMBUSTION.react(m, 3600, FIRE).reacted());
        assertArrayEquals(before, Chemistry.moles(m));

        assertTrue(COMBUSTION.burn(m, 1000, true, FIRE).reacted());
        assertEquals(0, m.moles(H2));
    }

    @Test
    void fire_006_autoignition_stands_in_for_a_source() {
        GasMixture hot = fuelInAir(1000, 900, H2, 0.10);
        assertTrue(COMBUSTION.burn(hot, 1000, false, FIRE).reacted());

        GasMixture warm = fuelInAir(1000, 800, H2, 0.10);
        assertFalse(COMBUSTION.burn(warm, 1000, false, FIRE).reacted());
    }

    @Test
    void fire_006_flammability_reports_the_lowest_autoignition_of_what_can_burn() {
        GasMixture m = fuelInAir(1000, 293.15, H2, 0.10);
        assertEquals(new Flammability(true, 858), COMBUSTION.flammability(m, FIRE));
        assertFalse(COMBUSTION.flammability(m, FIRE).ignitesAt(800));
        assertTrue(COMBUSTION.flammability(m, FIRE).ignitesAt(900));

        // H2 and CH4 both within their limits: methane's 810 K is the lower.
        GasMixture both = fuelInAir(1000, 293.15, H2, 0.10);
        both.setMoles(CH4, both.totalMoles() * 0.07);
        assertEquals(new Flammability(true, 810), COMBUSTION.flammability(both, FIRE));

        assertEquals(Flammability.NONE, COMBUSTION.flammability(fuelInAir(1000, 293.15, N2, 0.10), FIRE));
    }

    // ---- FIRE-001: activation conditions -------------------------------------------------------

    @Test
    void fire_001_a_fuel_outside_its_flammability_limits_does_not_burn() {
        assertFalse(COMBUSTION
                .burn(fuelInAir(1000, 293.15, H2, 0.03), 1000, true, FIRE)
                .reacted());
        // Above the upper limit, in pure O2 so the oxygen threshold is met.
        assertFalse(COMBUSTION
                .burn(mixture(1000, 293.15, H2, 0.80 * 41.6, O2, 0.20 * 41.6), 1000, true, FIRE)
                .reacted());
        assertFalse(
                COMBUSTION.flammability(fuelInAir(1000, 293.15, H2, 0.03), FIRE).flammable());
        assertTrue(COMBUSTION
                .burn(fuelInAir(1000, 293.15, H2, 0.04), 1000, true, FIRE)
                .reacted());
        assertTrue(COMBUSTION
                .burn(mixture(1000, 293.15, H2, 0.75 * 41.6, O2, 0.25 * 41.6), 1000, true, FIRE)
                .reacted());
    }

    @Test
    void fire_001_a_fire_needs_the_oxygen_threshold() {
        // 10 % H2 with only 12 % O2: below the 0.14 fraction.
        GasMixture lean = mixture(1000, 293.15, H2, 4.1, O2, 4.9, N2, 32.6);
        assertFalse(COMBUSTION.burn(lean, 1000, true, FIRE).reacted());
        assertFalse(COMBUSTION.flammability(lean, FIRE).flammable());

        // Air at 30 kPa: 21 % O2 but only 6.3 kPa of it.
        GasMixture thin = fuelInAir(1000, 293.15, H2, 0.10);
        GasMixture low =
                mixture(1000, 293.15, H2, thin.moles(H2) * 0.3, O2, thin.moles(O2) * 0.3, N2, thin.moles(N2) * 0.3);
        assertFalse(COMBUSTION.burn(low, 1000, true, FIRE).reacted());
    }

    @Test
    void fire_001_a_parcel_burns_its_share_of_the_mixture() {
        GasMixture m = fuelInAir(1000, 293.15, H2, 0.10);
        double h2 = m.moles(H2);
        COMBUSTION.burn(m, 375, true, FIRE);
        assertEquals(h2 * (1 - 0.375), m.moles(H2), EPS);
    }

    @Test
    void fire_001_temperature_limits() {
        ReactionSet set = Chemistry.compile(zoneWide(
                "test:between",
                Map.of("zas:nitrous_oxide", 1.0),
                new ReactionConditions(500, 1000, List.of(), List.of(), false, Optional.empty()),
                Optional.empty()));
        assertFalse(set.react(mixture(1000, 400, N2O, 1), 1, FIRE).reacted());
        assertTrue(set.react(mixture(1000, 600, N2O, 1), 1, FIRE).reacted());
        assertFalse(set.react(mixture(1000, 1100, N2O, 1), 1, FIRE).reacted());
    }

    @Test
    void fire_001_concentration_limits() {
        ReactionSet set = Chemistry.compile(zoneWide(
                "test:concentrated",
                Map.of("zas:nitrous_oxide", 1.0),
                new ReactionConditions(
                        0,
                        Double.POSITIVE_INFINITY,
                        List.of(new ReactionConditions.Concentration("zas:nitrous_oxide", 0.2, 0.6)),
                        List.of(),
                        false,
                        Optional.empty()),
                Optional.empty()));
        assertFalse(set.react(mixture(1000, 300, N2O, 1, N2, 9), 1, FIRE).reacted());
        assertTrue(set.react(mixture(1000, 300, N2O, 5, N2, 5), 1, FIRE).reacted());
        assertFalse(set.react(mixture(1000, 300, N2O, 9, N2, 1), 1, FIRE).reacted());
    }

    @Test
    void fire_001_catalysts_must_be_present_and_are_not_consumed() {
        ReactionSet set = Chemistry.compile(zoneWide(
                "test:catalysed",
                Map.of("zas:nitrous_oxide", 1.0),
                new ReactionConditions(
                        0,
                        Double.POSITIVE_INFINITY,
                        List.of(),
                        List.of(new ReactionConditions.Concentration("zas:carbon_monoxide", 0.01, 1)),
                        false,
                        Optional.empty()),
                Optional.empty()));
        assertFalse(set.react(mixture(1000, 300, N2O, 1), 1, FIRE).reacted());
        assertFalse(set.react(mixture(1000, 300, N2O, 1, CO, 0.001), 1, FIRE).reacted());
        GasMixture m = mixture(1000, 300, N2O, 1, CO, 0.1);
        assertTrue(set.react(m, 1, FIRE).reacted());
        assertEquals(0.1, m.moles(CO));
    }

    @Test
    void fire_001_a_rate_is_per_second_and_first_order() {
        ReactionSet set = Chemistry.compile(zoneWide(
                "test:slow",
                Map.of("zas:nitrous_oxide", 1.0),
                ReactionConditions.NONE,
                Optional.of(ReactionRate.constant(0.1))));
        GasMixture once = mixture(1000, 300, N2O, 1);
        set.react(once, 2, FIRE);
        assertEquals(Math.exp(-0.2), once.moles(N2O), 1e-12);

        // Two 1 s steps at the same temperature give the same as one 2 s step.
        GasMixture twice = mixture(1000, 300, N2O, 1);
        set.react(twice, 1, FIRE);
        twice.setTemperature(300);
        set.react(twice, 1, FIRE);
        assertEquals(once.moles(N2O), twice.moles(N2O), 1e-12);
    }

    @Test
    void fire_001_an_activation_energy_follows_arrhenius() {
        ReactionRate rate = new ReactionRate(0.01, 250_000, 1000);
        assertEquals(0.01, rate.at(1000), 1e-15);
        assertEquals(0.01 * Math.exp(-250_000 / 8.314462618 * (1 / 1100.0 - 1 / 1000.0)), rate.at(1100), 1e-12);
        assertTrue(rate.at(900) < 0.01);
        assertEquals(0, rate.at(0));
    }

    @Test
    void fire_001_products_and_heat_follow_the_extent() {
        // N2O -> N2 + 1/2 O2, 82 kJ/mol released, at 0.5/s for 1 s.
        ReactionSet set = Chemistry.compile(ReactionDefinition.standard(
                "test:decomposition",
                Map.of("zas:nitrous_oxide", 1.0),
                Map.of("zas:nitrogen", 1.0, "zas:oxygen", 0.5),
                -82_000,
                ReactionConditions.NONE,
                Optional.of(ReactionRate.constant(0.5)),
                Optional.empty()));
        GasMixture m = mixture(1000, 1000, N2O, 2);
        double energy = m.heatCapacity() * m.temperature();
        ReactionResult r = set.react(m, 1, FIRE);
        double extent = 2 * -Math.expm1(-0.5);
        assertEquals(2 - extent, m.moles(N2O), 1e-12);
        assertEquals(extent, m.moles(N2), 1e-12);
        assertEquals(extent / 2, m.moles(O2), 1e-12);
        assertEquals(82_000 * extent, r.heat(), 1e-6);
        assertEquals(energy + r.heat(), m.heatCapacity() * m.temperature(), 1e-6);
    }

    @Test
    void fire_001_definitions_naming_unknown_gases_or_repeating_ids_are_skipped() {
        ReactionDefinition unknown = ReactionDefinition.standard(
                "test:unknown",
                Map.of("test:unobtainium", 1.0),
                Map.of(),
                0,
                ReactionConditions.NONE,
                Optional.empty(),
                Optional.empty());
        List<String> skipped = new ArrayList<>();
        ReactionSet set = ReactionSet.compile(
                REGISTRY,
                List.of(unknown, Chemistry.HYDROGEN, Chemistry.HYDROGEN),
                (d, why) -> skipped.add(d.id() + ": " + why));
        assertEquals(
                List.of("zas:hydrogen_combustion"),
                set.definitions().stream().map(ReactionDefinition::id).toList());
        assertEquals(
                List.of(
                        "test:unknown: no such gas: test:unobtainium",
                        "zas:hydrogen_combustion: duplicate reaction id zas:hydrogen_combustion"),
                skipped);
    }

    @Test
    void fire_001_invalid_definitions_are_rejected_naming_the_field() {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> ReactionDefinition.standard(
                        "test:negative",
                        Map.of("zas:oxygen", -1.0),
                        Map.of(),
                        0,
                        ReactionConditions.NONE,
                        Optional.empty(),
                        Optional.empty()));
        assertTrue(e.getMessage().startsWith("reactants:"), e.getMessage());
        e = assertThrows(
                IllegalArgumentException.class,
                () -> ReactionDefinition.standard(
                        "test:ignited_rate",
                        Map.of("zas:hydrogen", 1.0),
                        Map.of(),
                        0,
                        ReactionConditions.combustion(new ReactionConditions.FlammabilityLimits(
                                "zas:hydrogen", 0.1, 0.5, Double.POSITIVE_INFINITY)),
                        Optional.of(ReactionRate.constant(1)),
                        Optional.empty()));
        assertTrue(e.getMessage().startsWith("rate:"), e.getMessage());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReactionConditions.FlammabilityLimits("zas:hydrogen", 0.5, 0.4, 800));
    }

    @Test
    void fire_001_a_mixture_of_other_gases_is_refused() {
        GasMixture other = new GasMixture(io.github.west3436.zas.engine.gas.GasRegistry.EMPTY, 1000, 300);
        assertThrows(IllegalArgumentException.class, () -> COMBUSTION.burn(other, 1000, true, FIRE));
    }

    // ---- FIRE-002: fair allocation -------------------------------------------------------------

    @Test
    void fire_002_only_competing_reactions_are_scaled_back() {
        // Two reactions both want all the N2O; a third, on CH4, competes with nothing.
        ReactionSet set = Chemistry.compile(
                instant("test:a", Map.of("zas:nitrous_oxide", 1.0), Map.of("zas:nitrogen", 1.0)),
                instant(
                        "test:b",
                        Map.of("zas:nitrous_oxide", 1.0, "zas:carbon_monoxide", 1.0),
                        Map.of("zas:carbon_dioxide", 1.0)),
                instant("test:c", Map.of("zas:methane", 1.0), Map.of("zas:hydrogen", 2.0)));
        GasMixture m = mixture(1000, 300, N2O, 1, CO, 1, CH4, 1);

        ReactionResult r = set.react(m, 1, FIRE);

        assertEquals(3, r.reactions());
        // a and b each wanted 1 mol of N2O, with 1 mol there: each gets half.
        assertEquals(0, m.moles(N2O), EPS);
        assertEquals(0.5, m.moles(N2), EPS);
        assertEquals(0.5, m.moles(CO2), EPS);
        assertEquals(0.5, m.moles(CO), EPS);
        // c competes with nothing, so it isn't scaled: ZAS's global scaling would have halved it.
        assertEquals(0, m.moles(CH4), EPS);
        assertEquals(2, m.moles(H2), EPS);
    }

    @Test
    void fire_002_a_reaction_is_scaled_by_its_scarcest_reactant() {
        // a and b share N2O, which is short; b also uses CO, which is plentiful.
        ReactionSet set = Chemistry.compile(
                instant("test:a", Map.of("zas:nitrous_oxide", 2.0), Map.of("zas:nitrogen", 2.0)),
                instant(
                        "test:b",
                        Map.of("zas:nitrous_oxide", 1.0, "zas:carbon_monoxide", 1.0),
                        Map.of("zas:carbon_dioxide", 1.0)));
        GasMixture m = mixture(1000, 300, N2O, 1, CO, 10);

        set.react(m, 1, FIRE);

        // a wants extent 0.5 (1 mol), b wants extent 1 (1 mol): 2 mol asked for 1, so each gets half.
        assertEquals(0, m.moles(N2O), EPS);
        assertEquals(0.5, m.moles(N2), EPS);
        assertEquals(0.5, m.moles(CO2), EPS);
        assertEquals(9.5, m.moles(CO), EPS);
    }

    @Test
    void fire_002_competing_fuels_share_the_oxygen_of_a_parcel() {
        // H2 and CH4 burn together in too little O2: both are scaled by the same oxygen ratio.
        GasMixture m = mixture(1000, 293.15, H2, 4, CH4, 3, O2, 7, N2, 27);
        COMBUSTION.burn(m, 1000, true, FIRE);
        // H2 wants 2 mol O2, CH4 wants 6: 8 asked for 7.
        double ratio = 7.0 / 8;
        assertEquals(0, m.moles(O2), EPS);
        assertEquals(4 - 4 * ratio, m.moles(H2), EPS);
        assertEquals(3 - 3 * ratio, m.moles(CH4), EPS);
    }

    // ---- FIRE-031: combustion in containers ----------------------------------------------------

    /** A tank of 2 H2 to 1 O2 in N2, at 1,000 kPa. */
    private static GasMixture tank(double litres, double temperature) {
        double total = 1000 * litres / (8.314462618 * temperature);
        return mixture(litres, temperature, H2, total * 0.2, O2, total * 0.3, N2, total * 0.5);
    }

    @Test
    void fire_031_a_70_l_tank_burns_completely_in_one_tick() {
        GasMixture m = tank(70, 293.15);
        double h2 = m.moles(H2);
        ReactionResult r = COMBUSTION.applyStandalone(m, 1, true, FIRE);
        assertEquals(0, m.moles(H2));
        assertEquals(h2, m.moles(H2O), EPS);
        assertEquals(242_000 * h2, r.heat(), 1e-6);
        assertTrue(m.temperature() > 1500, "T " + m.temperature());
    }

    @Test
    void fire_031_a_1000_l_canister_burns_375_l() {
        GasMixture m = tank(1000, 293.15);
        double h2 = m.moles(H2);
        COMBUSTION.applyStandalone(m, 1, true, FIRE);
        assertEquals(h2 * (1 - 0.375), m.moles(H2), EPS);
        assertEquals(h2 * 0.375, m.moles(H2O), EPS);

        // Two seconds burn 750 L in one parcel.
        GasMixture two = tank(1000, 293.15);
        COMBUSTION.applyStandalone(two, 2, true, FIRE);
        assertEquals(h2 * (1 - 0.75), two.moles(H2), EPS);
    }

    @Test
    void fire_031_a_container_ignites_by_a_source_or_by_autoignition() {
        assertFalse(COMBUSTION.applyStandalone(tank(70, 293.15), 1, false, FIRE).reacted());
        assertTrue(COMBUSTION.applyStandalone(tank(70, 900), 1, false, FIRE).reacted());
        assertFalse(COMBUSTION.applyStandalone(tank(70, 800), 1, false, FIRE).reacted());
    }

    @Test
    void fire_031_a_container_needs_the_fire_oxygen_threshold() {
        // 10 % H2, 12 % O2: the fraction is below 0.14 however high the pressure.
        GasMixture lean = mixture(70, 293.15, H2, 2.9, O2, 3.5, N2, 22.6);
        assertFalse(COMBUSTION.applyStandalone(lean, 1, true, FIRE).reacted());
    }

    @Test
    void fire_031_a_standalone_step_also_runs_zone_reactions() {
        ReactionSet set = Chemistry.compile(
                Chemistry.HYDROGEN,
                zoneWide(
                        "test:slow",
                        Map.of("zas:nitrous_oxide", 1.0),
                        ReactionConditions.NONE,
                        Optional.of(ReactionRate.constant(0.1))));
        GasMixture m = tank(70, 293.15);
        m.setMoles(N2O, 1);
        ReactionResult r = set.applyStandalone(m, 1, true, FIRE);
        assertEquals(2, r.reactions());
        assertEquals(0, m.moles(H2));
        assertEquals(Math.exp(-0.1), m.moles(N2O), 1e-12);
    }

    // ---- API-020: code-defined reactions (the engine's side) -----------------------------------

    private static ReactionDefinition code(String id, ReactionConditions conditions, CodeReaction code) {
        return new ReactionDefinition(
                id,
                "test:code",
                Map.of(),
                Map.of(),
                0,
                conditions,
                Optional.empty(),
                Optional.empty(),
                Optional.of(code));
    }

    @Test
    void api_020_a_code_reaction_changes_the_mixture_as_it_asks() {
        ReactionSet set = Chemistry.compile(code("test:half", ReactionConditions.NONE, in -> {
            double[] consumed = new double[in.moles().length];
            double[] produced = new double[in.moles().length];
            consumed[N2O] = in.available()[N2O] / 2;
            produced[N2] = consumed[N2O];
            return new CodeReaction.Change(consumed, produced, 500);
        }));
        GasMixture m = mixture(1000, 300, N2O, 2, O2, 1);
        double energy = m.heatCapacity() * m.temperature();
        ReactionResult r = set.react(m, 1, FIRE);
        assertEquals(1, m.moles(N2O), EPS);
        assertEquals(1, m.moles(N2), EPS);
        assertEquals(500, r.heat());
        assertEquals(energy + 500, m.heatCapacity() * m.temperature(), 1e-6);
    }

    @Test
    void api_020_a_code_reaction_asking_for_more_than_there_is_is_scaled_to_fit() {
        ReactionSet set = Chemistry.compile(code("test:greedy", ReactionConditions.NONE, in -> {
            double[] consumed = new double[in.moles().length];
            double[] produced = new double[in.moles().length];
            consumed[N2O] = 4;
            produced[N2] = 4;
            return new CodeReaction.Change(consumed, produced, 0);
        }));
        GasMixture m = mixture(1000, 300, N2O, 2);
        set.react(m, 1, FIRE);
        assertEquals(0, m.moles(N2O));
        assertEquals(2, m.moles(N2), EPS);
    }

    @Test
    void api_020_a_failing_code_reaction_is_skipped_and_logged_once() {
        List<String> log = new ArrayList<>();
        ReactionSet set = Chemistry.compile(
                code("test:broken", ReactionConditions.NONE, in -> {
                    throw new IllegalStateException("boom");
                }),
                code(
                        "test:negative",
                        ReactionConditions.NONE,
                        in -> new CodeReaction.Change(new double[in.moles().length], new double[] {-1}, 0)),
                instant("test:fine", Map.of("zas:nitrous_oxide", 1.0), Map.of("zas:nitrogen", 1.0)));
        io.github.west3436.zas.engine.gas.GasConfig config =
                io.github.west3436.zas.engine.gas.GasConfig.DEFAULT.withLog(log::add);
        for (int i = 0; i < 3; i++) {
            GasMixture m = new GasMixture(REGISTRY, config, 1000, 300);
            m.setMoles(N2O, 1);
            ReactionResult r = set.react(m, 1, FIRE);
            assertEquals(1, r.reactions());
            assertEquals(1, m.moles(N2), EPS);
        }
        assertEquals(2, log.size(), log.toString());
        assertTrue(log.get(0).contains("test:broken") && log.get(0).contains("boom"), log.get(0));
        assertTrue(log.get(1).contains("test:negative"), log.get(1));
    }

    @Test
    void api_020_a_code_reaction_runs_only_where_its_conditions_hold() {
        int[] calls = {0};
        ReactionSet set = Chemistry.compile(code(
                "test:hot",
                new ReactionConditions(1000, Double.POSITIVE_INFINITY, List.of(), List.of(), false, Optional.empty()),
                in -> {
                    calls[0]++;
                    return null;
                }));
        assertFalse(set.react(mixture(1000, 300, N2O, 1), 1, FIRE).reacted());
        assertEquals(0, calls[0]);
        assertFalse(set.react(mixture(1000, 1100, N2O, 1), 1, FIRE).reacted());
        assertEquals(1, calls[0]);
    }

    private static ReactionDefinition instant(String id, Map<String, Double> reactants, Map<String, Double> products) {
        return ReactionDefinition.standard(
                id, reactants, products, 0, ReactionConditions.NONE, Optional.empty(), Optional.empty());
    }

    private static ReactionDefinition zoneWide(
            String id, Map<String, Double> reactants, ReactionConditions conditions, Optional<ReactionRate> rate) {
        return ReactionDefinition.standard(
                id, reactants, Map.of("zas:nitrogen", 1.0), 0, conditions, rate, Optional.empty());
    }
}
