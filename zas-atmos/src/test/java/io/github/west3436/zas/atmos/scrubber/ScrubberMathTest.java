package io.github.west3436.zas.atmos.scrubber;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** What a scrubber takes from a mixed room: its modes (CONT-079), rate (CONT-080) and stall (CONT-081). */
class ScrubberMathTest {
    private static final double R = GasMixture.GAS_CONSTANT;
    private static final double T = StandardAir.TEMPERATURE;
    private static final double ROOM = 27_000;
    private static final double BUFFER = 200;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final GasMixture MIXED = new GasMixture(Map.of(N2, 800.0, O2, 200.0, CO2, 30.0, CO, 6.0), T, ROOM);
    private static final GasMixture EMPTY_BUFFER = new GasMixture(Map.of(), 0, BUFFER);
    private static final GasSelection CO2_AND_CO = GasSelection.of(List.of(CO2, CO), List.of());

    @Test
    void cont_080_a_scrubber_samples_500_litres_a_second_and_1000_in_panic() {
        assertEquals(500, ScrubberMath.SAMPLE_LITRES);
        assertEquals(1_000, ScrubberMath.PANIC_LITRES);
        assertEquals(500 / ROOM, ScrubberMath.sampledFraction(ScrubberMath.SAMPLE_LITRES, ROOM), 1e-12);
        assertEquals(1_000 / ROOM, ScrubberMath.sampledFraction(ScrubberMath.PANIC_LITRES, ROOM), 1e-12);

        Map<ResourceLocation, Double> normal =
                ScrubberMath.take(MIXED, ScrubberMath.SAMPLE_LITRES, ScrubberMath.EVERY_GAS, EMPTY_BUFFER);
        assertEquals(800 * 500 / ROOM, normal.get(N2), 1e-9);
        Map<ResourceLocation, Double> panic =
                ScrubberMath.take(MIXED, ScrubberMath.PANIC_LITRES, ScrubberMath.EVERY_GAS, EMPTY_BUFFER);
        assertEquals(800 * 1_000 / ROOM, panic.get(N2), 1e-9);
    }

    @Test
    void cont_080_a_sample_is_never_more_than_all_the_air() {
        assertEquals(1, ScrubberMath.sampledFraction(1_000, 1_000));
        assertEquals(1, ScrubberMath.sampledFraction(1_000, 200));
        assertEquals(0, ScrubberMath.sampledFraction(0, 200));
        GasMixture small = new GasMixture(Map.of(N2, 10.0), T, 300);
        assertEquals(
                10.0,
                ScrubberMath.take(small, 500, ScrubberMath.EVERY_GAS, EMPTY_BUFFER)
                        .get(N2),
                1e-12);
    }

    @Test
    void cont_079_scrubbing_keeps_only_the_selected_gases() {
        Map<ResourceLocation, Double> take =
                ScrubberMath.take(MIXED, ScrubberMath.SAMPLE_LITRES, CO2_AND_CO, EMPTY_BUFFER);
        assertEquals(List.of(CO2, CO), List.copyOf(take.keySet()), "only the selected gases, sorted by id");
        assertEquals(30 * 500 / ROOM, take.get(CO2), 1e-12);
        assertEquals(6 * 500 / ROOM, take.get(CO), 1e-12);
    }

    @Test
    void cont_079_siphon_keeps_its_whole_sample() {
        Map<ResourceLocation, Double> take =
                ScrubberMath.take(MIXED, ScrubberMath.SAMPLE_LITRES, ScrubberMath.EVERY_GAS, EMPTY_BUFFER);
        assertEquals(4, take.size());
        MIXED.moles().forEach((gas, n) -> assertEquals(n * 500 / ROOM, take.get(gas), 1e-12, gas.toString()));
    }

    @Test
    void cont_079_panic_is_siphon_at_the_panic_rate() {
        Map<ResourceLocation, Double> siphon =
                ScrubberMath.take(MIXED, ScrubberMath.SAMPLE_LITRES, ScrubberMath.EVERY_GAS, EMPTY_BUFFER);
        Map<ResourceLocation, Double> panic =
                ScrubberMath.take(MIXED, ScrubberMath.PANIC_LITRES, ScrubberMath.EVERY_GAS, EMPTY_BUFFER);
        siphon.forEach((gas, n) -> assertEquals(2 * n, panic.get(gas), 1e-12, gas.toString()));
    }

    @Test
    void cont_079_nothing_selected_or_no_air_takes_nothing() {
        GasSelection none = GasSelection.of(List.of(), List.of());
        assertTrue(ScrubberMath.take(MIXED, 500, none, EMPTY_BUFFER).isEmpty());
        GasSelection absent =
                GasSelection.of(List.of(ResourceLocation.fromNamespaceAndPath("zas", "hydrogen")), List.of());
        assertTrue(ScrubberMath.take(MIXED, 500, absent, EMPTY_BUFFER).isEmpty());
        GasMixture vacuum = new GasMixture(Map.of(), 0, ROOM);
        assertTrue(ScrubberMath.take(vacuum, 500, ScrubberMath.EVERY_GAS, EMPTY_BUFFER)
                .isEmpty());
    }

    @Test
    void cont_081_a_scrubber_stops_at_50_atm_in_its_buffer() {
        assertEquals(5_066.25, ScrubberMath.STALL_KPA, 1e-9);
        GasMixture at = buffer(ScrubberMath.STALL_KPA);
        GasMixture over = buffer(ScrubberMath.STALL_KPA + 100);
        GasMixture under = buffer(ScrubberMath.STALL_KPA - 1_000);
        assertTrue(ScrubberMath.stalled(at));
        assertTrue(ScrubberMath.stalled(over));
        assertFalse(ScrubberMath.stalled(under));
        assertTrue(ScrubberMath.take(MIXED, 500, ScrubberMath.EVERY_GAS, at).isEmpty());
        assertTrue(ScrubberMath.take(MIXED, 500, ScrubberMath.EVERY_GAS, over).isEmpty());
        assertFalse(ScrubberMath.take(MIXED, 500, ScrubberMath.EVERY_GAS, under).isEmpty());
    }

    @Test
    void cont_081_the_last_sample_stops_at_50_atm() {
        // 1 kPa short of the stall in 200 L is much less than the 19 mol a sample holds.
        GasMixture nearly = buffer(ScrubberMath.STALL_KPA - 1);
        Map<ResourceLocation, Double> take = ScrubberMath.take(MIXED, 500, ScrubberMath.EVERY_GAS, nearly);
        double total = take.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(1 * BUFFER / (R * T), total, 1e-9, "just what reaches 50 atm");
        // It cuts every gas alike, so the sample's mixture stays the same.
        assertEquals(800.0 / 200.0, take.get(N2) / take.get(O2), 1e-9);
    }

    /** A buffer of oxygen at a pressure. */
    private static GasMixture buffer(double kPa) {
        return new GasMixture(Map.of(O2, kPa * BUFFER / (R * T)), T, BUFFER);
    }
}
