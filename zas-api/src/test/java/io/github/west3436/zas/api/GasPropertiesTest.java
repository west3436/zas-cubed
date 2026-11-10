package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * Gases' heat capacities (API-051), the atmos period (API-052), whether container explosions break
 * blocks (API-059) and the near-vacuum density (API-064), with their fallbacks.
 */
class GasPropertiesTest {
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("test", "unknown");

    /** A provider built against an API from before these methods: it overrides none of them. */
    private static ZasApiProvider older() {
        return (ZasApiProvider) Proxy.newProxyInstance(
                ZasApiProvider.class.getClassLoader(), new Class<?>[] {ZasApiProvider.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    /** Without ZAS³, standard air's gases have the shipped heat capacities, and no other gas has one. */
    @Test
    void api_051_fallback_heat_capacities_are_standard_airs() {
        assertEquals(OptionalDouble.of(20.81), FallbackProvider.INSTANCE.molarHeatCapacity(N2));
        assertEquals(OptionalDouble.of(21.06), FallbackProvider.INSTANCE.molarHeatCapacity(O2));
        assertEquals(OptionalDouble.empty(), FallbackProvider.INSTANCE.molarHeatCapacity(UNKNOWN));
        assertEquals(20.81, GasProperties.molarHeatCapacity(N2));
        assertEquals(21.06, GasProperties.molarHeatCapacity(O2));
        assertEquals(GasProperties.DEFAULT_MOLAR_HEAT_CAPACITY, GasProperties.molarHeatCapacity(UNKNOWN));
    }

    /** Without ZAS³, standard air's gases are the only ones defined. */
    @Test
    void api_051_fallback_defines_only_standard_airs_gases() {
        assertTrue(GasProperties.isDefined(N2));
        assertTrue(GasProperties.isDefined(O2));
        assertFalse(GasProperties.isDefined(UNKNOWN));
        assertThrows(NullPointerException.class, () -> GasProperties.isDefined(null));
    }

    /** Without ZAS³, the list of gases is standard air's, and a gas's name key has the usual form. */
    @Test
    void api_051_fallback_lists_standard_airs_gases_and_names_them_by_the_usual_key() {
        assertEquals(List.of(N2, O2), List.copyOf(GasProperties.defined()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> GasProperties.defined().add(UNKNOWN));
        assertEquals("gas.zas.oxygen", GasProperties.translationKey(O2));
        assertEquals("gas." + UNKNOWN.getNamespace() + "." + UNKNOWN.getPath(), GasProperties.translationKey(UNKNOWN));
        assertEquals(Component.translatableWithFallback("gas.zas.nitrogen", "zas:nitrogen"), GasProperties.name(N2));
        assertThrows(NullPointerException.class, () -> GasProperties.translationKey(null));
    }

    /** The provider methods have defaults, which answer as the fallback does (architecture §6.2). */
    @Test
    void api_051_provider_methods_have_defaults() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("molarHeatCapacity", ResourceLocation.class)
                .isDefault());
        assertTrue(ZasApiProvider.class.getMethod("atmosPeriodTicks").isDefault());
        ZasApiProvider older = older();
        assertEquals(OptionalDouble.of(20.81), older.molarHeatCapacity(N2));
        assertEquals(OptionalDouble.of(21.06), older.molarHeatCapacity(O2));
        assertEquals(OptionalDouble.empty(), older.molarHeatCapacity(UNKNOWN));
        assertEquals(20, older.atmosPeriodTicks());
        assertTrue(ZasApiProvider.class.getMethod("definedGases").isDefault());
        assertTrue(ZasApiProvider.class
                .getMethod("gasTranslationKey", ResourceLocation.class)
                .isDefault());
        assertEquals(Set.of(N2, O2), older.definedGases());
        assertEquals(Optional.empty(), older.gasTranslationKey(O2));
    }

    @Test
    void api_051_heat_capacity_sums_moles_times_molar_heat_capacity() {
        GasParcel gas = new GasParcel(Map.of(N2, 2.0, O2, 1.0, UNKNOWN, 0.5), 300);
        assertEquals(2 * 20.81 + 21.06 + 0.5 * 20.8, GasProperties.heatCapacity(gas), 1e-12);
        assertEquals(0, GasProperties.heatCapacity(GasParcel.EMPTY));
    }

    /** Mixing conserves thermal energy: the mix's C·T is the sum of the parts'. */
    @Test
    void api_051_mix_conserves_thermal_energy() {
        GasParcel hot = new GasParcel(Map.of(O2, 1.0), 600);
        GasParcel cold = new GasParcel(Map.of(N2, 4.0, O2, 0.5), 200);
        GasParcel mix = GasProperties.mix(hot, cold);
        double energy = 21.06 * 600 + (4 * 20.81 + 0.5 * 21.06) * 200;
        assertEquals(energy, GasProperties.heatCapacity(mix) * mix.temperature(), 1e-9);
        assertEquals(1.5, mix.moles(O2), 1e-12);
        assertEquals(4.0, mix.moles(N2), 1e-12);
        // Order doesn't matter, and an empty parcel adds nothing.
        assertEquals(mix.temperature(), GasProperties.mix(cold, hot).temperature(), 1e-9);
        assertSame(hot, GasProperties.mix(hot, GasParcel.EMPTY));
        assertSame(hot, GasProperties.mix(GasParcel.EMPTY, hot));
    }

    @Test
    void api_051_mixed_temperature_edge_cases() {
        // Equal temperatures stay exact, whatever the capacities.
        assertEquals(293.15, GasProperties.mixedTemperature(0.1, 293.15, 7.3, 293.15));
        // A body with no heat capacity changes nothing.
        assertEquals(300, GasProperties.mixedTemperature(5, 300, 0, 900));
        assertEquals(900, GasProperties.mixedTemperature(0, 300, 5, 900));
        assertEquals(300, GasProperties.mixedTemperature(0, 300, 0, 900));
        assertEquals(400, GasProperties.mixedTemperature(1, 300, 1, 500), 1e-12);
    }

    /** Without ZAS³ the period is CORE-041's default, 20 game ticks. */
    @Test
    void api_052_fallback_period_is_20_ticks() {
        assertEquals(20, Atmospheres.DEFAULT_PERIOD_TICKS);
        assertEquals(20, FallbackProvider.INSTANCE.atmosPeriodTicks());
        assertEquals(20, Atmospheres.periodTicks());
    }

    /** Without ZAS³ a container's explosion breaks no blocks, and an older provider says the same. */
    @Test
    void api_059_container_explosions_break_no_blocks_without_zas() throws NoSuchMethodException {
        assertEquals(false, FallbackProvider.INSTANCE.containerExplosionsBreakBlocks());
        assertEquals(false, Atmospheres.containerExplosionsBreakBlocks());
        assertTrue(
                ZasApiProvider.class.getMethod("containerExplosionsBreakBlocks").isDefault());
        assertEquals(false, older().containerExplosionsBreakBlocks());
    }

    /**
     * Without ZAS³ the near-vacuum density is HEAT-008's default, 1 % of standard air's, and an older
     * provider says the same.
     */
    @Test
    void api_064_fallback_near_vacuum_is_1_percent_of_standard_air() throws NoSuchMethodException {
        double standard = 101.325 / (8.314462618 * 293.15);
        assertEquals(0.01 * standard, Atmospheres.DEFAULT_NEAR_VACUUM_DENSITY, 1e-15);
        assertEquals(Atmospheres.DEFAULT_NEAR_VACUUM_DENSITY, FallbackProvider.INSTANCE.nearVacuumDensity());
        assertEquals(Atmospheres.DEFAULT_NEAR_VACUUM_DENSITY, Atmospheres.nearVacuumDensity());
        assertTrue(ZasApiProvider.class.getMethod("nearVacuumDensity").isDefault());
        assertEquals(Atmospheres.DEFAULT_NEAR_VACUUM_DENSITY, older().nearVacuumDensity());
    }
}
