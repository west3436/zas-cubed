package io.github.west3436.zas.atmos.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The controller's pure logic: thresholds, presets, danger and modes (CONT-040, CONT-041, CONT-046, CONT-047). */
class ControllerLogicTest {
    private static final double V = 1000;

    /** A mixture with these partial pressures, in kPa, at a temperature. */
    private static GasMixture air(double temperature, Map<ResourceLocation, Double> kpa) {
        Map<ResourceLocation, Double> moles = new HashMap<>();
        kpa.forEach((g, p) -> moles.put(g, p * V / (GasMixture.GAS_CONSTANT * temperature)));
        return new GasMixture(moles, temperature, V);
    }

    private static GasMixture standard() {
        return StandardAir.mixture(V);
    }

    @Test
    void cont_040_thresholds_rate_each_band() {
        Thresholds t = new Thresholds(81, 91, 111, 122);
        assertEquals(2, t.rate(50));
        assertEquals(2, t.rate(81), "at min2 is danger 2");
        assertEquals(1, t.rate(85));
        assertEquals(1, t.rate(91), "at min1 is danger 1");
        assertEquals(0, t.rate(101));
        assertEquals(1, t.rate(111), "at max1 is danger 1");
        assertEquals(1, t.rate(115));
        assertEquals(2, t.rate(122), "at max2 is danger 2");
        assertEquals(0, Thresholds.NONE.rate(0));
        assertEquals(0, Thresholds.max(5, 10).rate(0), "a disabled minimum never trips");
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(10, 5, -1, -1));
        assertThrows(IllegalArgumentException.class, () -> new Thresholds(-1, -1, 1, 6000));
    }

    @Test
    void cont_040_standard_air_is_safe_for_humans_and_each_reading_trips() {
        ControllerPreset h = ControllerPreset.HUMAN;
        assertEquals(0, h.danger(standard()), "standard air under Human");
        assertEquals(2, ControllerPreset.VACUUM.danger(standard()), "standard air under Vacuum");

        double o2 = 21.0;
        double n2 = 80.0;
        // pressure
        assertEquals(1, h.danger(air(293.15, Map.of(StandardAir.OXYGEN, 19.0, StandardAir.NITROGEN, 68.0))));
        // temperature
        assertEquals(1, h.danger(air(320, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2))));
        assertEquals(2, h.danger(air(200, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2))));
        // each listed gas
        assertEquals(
                1,
                h.danger(air(
                        293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, ControllerPreset.CO2, 6.0))));
        assertEquals(
                2,
                h.danger(air(
                        293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, ControllerPreset.CO2, 10.0))));
        assertEquals(
                2,
                h.danger(air(
                        293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, ControllerPreset.CO, 0.2))));
        assertEquals(
                1,
                h.danger(air(
                        293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, ControllerPreset.H2, 1.5))));
        assertEquals(2, h.danger(air(293.15, Map.of(StandardAir.OXYGEN, 15.0, StandardAir.NITROGEN, 86.0))), "low O2");
        // the other gases, summed
        ResourceLocation a = ResourceLocation.fromNamespaceAndPath("test", "a");
        ResourceLocation b = ResourceLocation.fromNamespaceAndPath("test", "b");
        assertEquals(0, h.danger(air(293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, a, 0.4))));
        assertEquals(
                1, h.danger(air(293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, a, 0.4, b, 0.3))));
        double[] v = h.values(air(293.15, Map.of(StandardAir.OXYGEN, o2, StandardAir.NITROGEN, n2, a, 0.4, b, 0.3)));
        assertEquals(0.7, v[h.readings() - 1], 1e-9, "the other gases' reading");
    }

    @Test
    void cont_047_presets_hold_the_table() {
        ControllerPreset h = ControllerPreset.HUMAN;
        assertEquals(new Thresholds(81, 91, 111, 122), h.pressure());
        assertEquals(new Thresholds(243, 273, 313, 343), h.temperature());
        assertEquals(new Thresholds(16, 18, 135, 140), h.gases().get(StandardAir.OXYGEN));
        assertEquals(Thresholds.max(5, 10), h.gases().get(ControllerPreset.CO2));
        assertEquals(Thresholds.max(0.5, 1), h.gases().get(ControllerPreset.N2O));
        assertEquals(Thresholds.max(0.005, 0.12), h.gases().get(ControllerPreset.CO));
        assertEquals(Thresholds.max(1, 2), h.gases().get(ControllerPreset.H2));
        assertEquals(Thresholds.max(1, 2.5), h.gases().get(ControllerPreset.CH4));
        assertEquals(Thresholds.max(0.5, 1), h.others());
        assertEquals(101, h.targetPressure(), 1e-9, "the middle of Human's safe band");
        assertEquals(1, ControllerPreset.VACUUM.pressure().max1());
        assertEquals(5, ControllerPreset.VACUUM.pressure().max2());
    }

    @Test
    void cont_047_edits_clamp_keep_order_and_survive_saving() {
        Thresholds t = new Thresholds(81, 91, 111, 122);
        assertEquals(111, t.with(1, 500).min1(), "min1 can't pass max1");
        assertEquals(81, t.with(1, 0).min1(), "min1 can't pass min2");
        assertEquals(Thresholds.MAX, t.with(3, 1e9).max2());
        assertEquals(Thresholds.OFF, t.with(0, Thresholds.OFF).min2());
        assertEquals(0, t.with(0, Thresholds.OFF).with(1, -5).min1(), "never below 0");

        ControllerPreset edited = ControllerPreset.HUMAN.with(4, Thresholds.max(2, 3)); // CO2
        assertTrue(edited.edited());
        assertEquals(ControllerPreset.CO2, edited.gas(4));
        GasMixture co2 =
                air(293.15, Map.of(StandardAir.OXYGEN, 21.0, StandardAir.NITROGEN, 80.0, ControllerPreset.CO2, 2.5));
        assertEquals(0, ControllerPreset.HUMAN.danger(co2));
        assertEquals(1, edited.danger(co2), "the edit changes the rating");
        ControllerPreset loaded = ControllerPreset.load(edited.save());
        assertEquals(edited, loaded);
    }

    @Test
    void cont_046_danger_rises_at_once_and_falls_after_10_s() {
        DangerLevel d = new DangerLevel();
        assertEquals(2, d.update(2), "rises at once");
        for (int i = 1; i < DangerLevel.FALL_READINGS; i++) {
            assertEquals(2, d.update(0), "fell after " + i + " s");
        }
        assertEquals(0, d.update(0), "falls after 10 s");

        d.update(2);
        for (int i = 0; i < 5; i++) {
            d.update(0);
        }
        d.update(2); // a high reading starts the count again
        for (int i = 1; i < DangerLevel.FALL_READINGS; i++) {
            d.update(i == 3 ? 1 : 0);
        }
        assertEquals(2, d.level());
        assertEquals(1, d.update(0), "falls to the highest of the lower readings");
        assertEquals(2, d.update(2));
    }

    @Test
    void cont_041_modes_give_each_order() {
        ControllerPreset h = ControllerPreset.HUMAN;
        assertEquals(VentOrder.blow(101), ControllerMode.SCRUBBING.vent(h));
        assertEquals(
                ScrubberOrder.Mode.SCRUB, ControllerMode.SCRUBBING.scrubber(h).mode());
        assertTrue(ControllerMode.SCRUBBING.scrubber(h).gases().contains(ControllerPreset.CO2));
        assertEquals(VentOrder.blow(101), ControllerMode.REPLACEMENT.vent(h));
        assertEquals(ScrubberOrder.PANIC, ControllerMode.REPLACEMENT.scrubber(h));
        assertEquals(VentOrder.OFF, ControllerMode.PANIC.vent(h));
        assertEquals(ScrubberOrder.PANIC, ControllerMode.PANIC.scrubber(h));
        assertEquals(VentOrder.OFF, ControllerMode.CYCLE.vent(h));
        assertEquals(ScrubberOrder.PANIC, ControllerMode.CYCLE.scrubber(h));
        assertEquals(VentOrder.blow(101), ControllerMode.FILL.vent(h));
        assertEquals(ScrubberOrder.OFF, ControllerMode.FILL.scrubber(h));
        assertEquals(VentOrder.OFF, ControllerMode.OFF.vent(h));
        assertEquals(ScrubberOrder.OFF, ControllerMode.OFF.scrubber(h));

        // A controller names itself on its orders; the mode's own orders have no sender.
        assertEquals(null, ControllerMode.SCRUBBING.vent(h).sender());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(3, -58, 7);
        VentOrder sent = ControllerMode.SCRUBBING.vent(h).from(at);
        at.set(0, 0, 0);
        assertEquals(new BlockPos(3, -58, 7), sent.sender(), "the sender is copied, not shared");
        assertTrue(sent.on());
        assertEquals(101, sent.targetKpa());
        assertEquals(
                new BlockPos(3, -58, 7),
                VentOrder.OFF.from(new BlockPos(3, -58, 7)).sender());
        assertFalse(VentOrder.OFF.from(new BlockPos(3, -58, 7)).on());

        assertEquals(ControllerMode.CYCLE, ControllerMode.CYCLE.after(5.1, h));
        assertEquals(ControllerMode.FILL, ControllerMode.CYCLE.after(5.0, h), "below 5 % of 1 atm");
        assertEquals(ControllerMode.FILL, ControllerMode.FILL.after(95, h));
        assertEquals(ControllerMode.SCRUBBING, ControllerMode.FILL.after(95.95, h), "at 95 % of 101 kPa");
        assertEquals(ControllerMode.PANIC, ControllerMode.PANIC.after(0, h));
        assertFalse(ControllerMode.OFF.vent(h).on());
    }

    @Test
    void cont_048_signal_strengths() {
        assertArrayEquals(
                new int[] {0, 0, 7, 15},
                java.util.Arrays.stream(ControllerStatus.values())
                        .mapToInt(ControllerStatus::signal)
                        .toArray());
        assertEquals(ControllerStatus.DANGER2, ControllerStatus.of(2));
    }
}
