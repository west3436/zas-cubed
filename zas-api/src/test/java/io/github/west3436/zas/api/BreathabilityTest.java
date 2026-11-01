package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/** The breathability verdict as a value (API-014, API-046). */
class BreathabilityTest {
    private static final GasMixture AIR = StandardAir.mixture(StandardAir.CELL_VOLUME);

    @Test
    void api_014_reason_decides_whether_it_can_breathe() {
        Set<BreathReason> breathes = EnumSet.of(
                BreathReason.BREATHABLE,
                BreathReason.FLUID_BREATHING,
                BreathReason.EXEMPT,
                BreathReason.DOES_NOT_BREATHE);
        for (BreathReason reason : BreathReason.values()) {
            assertEquals(breathes.contains(reason), reason.canBreathe(), reason.name());
            assertEquals(
                    reason.canBreathe(),
                    Breathability.of(reason, BreathSource.ATMOSPHERE).canBreathe(),
                    reason.name());
        }
        assertFalse(BreathReason.DROWNING.canBreathe());
        assertFalse(BreathReason.LOW_OXYGEN.canBreathe());
    }

    @Test
    void api_014_only_gas_sources_carry_a_breath() {
        assertEquals(
                List.of(BreathSource.ATMOSPHERE, BreathSource.INTERNALS),
                EnumSet.allOf(BreathSource.class).stream()
                        .filter(BreathSource::isGas)
                        .toList());
        Breathability fromTank = Breathability.builder(BreathReason.BREATHABLE, BreathSource.INTERNALS)
                .breath(AIR)
                .build();
        assertEquals(Optional.of(AIR), fromTank.breath());
        for (BreathSource source : List.of(BreathSource.FLUID, BreathSource.NONE)) {
            Breathability.Builder b =
                    Breathability.builder(BreathReason.BREATHABLE, source).breath(AIR);
            assertThrows(IllegalArgumentException.class, b::build, source.name());
        }
    }

    @Test
    void api_014_defaults_carry_no_breath_hazards_or_protection() {
        Breathability v = Breathability.of(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE);
        assertTrue(v.canBreathe());
        assertEquals(BreathReason.BREATHABLE, v.reason());
        assertEquals(BreathSource.ATMOSPHERE, v.source());
        assertEquals(Optional.empty(), v.breath());
        assertEquals(Set.of(), v.hazards());
        assertEquals(Set.of(), v.protection());
        assertTrue(v.isProtected(), "no hazard means protected");
    }

    @Test
    void api_014_protected_means_no_hazard() {
        Breathability suited = Breathability.builder(BreathReason.BREATHABLE, BreathSource.INTERNALS)
                .breath(AIR)
                .protection(Protection.PRESSURE)
                .protection(Protection.THERMAL)
                .protection(Protection.SEALED_BREATHING)
                .build();
        assertTrue(suited.isProtected());
        assertEquals(
                EnumSet.of(Protection.PRESSURE, Protection.THERMAL, Protection.SEALED_BREATHING), suited.protection());

        Breathability exposed = Breathability.builder(BreathReason.LOW_OXYGEN, BreathSource.ATMOSPHERE)
                .hazard(Hazard.COLD)
                .hazard(Hazard.LOW_PRESSURE)
                .hazard(Hazard.COLD)
                .build();
        assertFalse(exposed.isProtected());
        assertFalse(exposed.canBreathe());
        // Declaration order, whatever order they were added in, and no duplicates.
        assertEquals(List.of(Hazard.LOW_PRESSURE, Hazard.COLD), List.copyOf(exposed.hazards()));
    }

    @Test
    void api_014_verdict_cannot_be_changed() {
        Breathability.Builder b = Breathability.builder(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE)
                .hazard(Hazard.HEAT)
                .protection(Protection.IMMUNE);
        Breathability built = b.build();
        assertThrows(UnsupportedOperationException.class, () -> built.hazards().add(Hazard.COLD));
        assertThrows(
                UnsupportedOperationException.class, () -> built.protection().clear());

        // Later changes to the builder don't reach a verdict already built.
        b.hazard(Hazard.COLD).protection(Protection.EXEMPT).breath(AIR);
        assertEquals(Set.of(Hazard.HEAT), built.hazards());
        assertEquals(Set.of(Protection.IMMUNE), built.protection());
        assertEquals(Optional.empty(), built.breath());
        assertNotEquals(built, b.build());
    }

    @Test
    void api_014_verdict_is_a_value() {
        Breathability a = Breathability.builder(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE)
                .breath(AIR)
                .hazard(Hazard.TOXIC_GAS)
                .protection(Protection.GAS_FILTERING)
                .build();
        Breathability b = Breathability.builder(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE)
                .protection(Protection.GAS_FILTERING)
                .hazard(Hazard.TOXIC_GAS)
                .breath(StandardAir.mixture(StandardAir.CELL_VOLUME))
                .build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(a.toString(), b.toString());
        assertTrue(a.toString().contains("BREATHABLE"), a.toString());

        assertNotEquals(
                a,
                Breathability.builder(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE)
                        .breath(new GasMixture(Map.of(StandardAir.OXYGEN, 1.0), 293.15, 1000))
                        .hazard(Hazard.TOXIC_GAS)
                        .protection(Protection.GAS_FILTERING)
                        .build());
        assertNotEquals(
                Breathability.of(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE),
                Breathability.of(BreathReason.BREATHABLE, BreathSource.INTERNALS));
        assertNotEquals(
                Breathability.of(BreathReason.DROWNING, BreathSource.FLUID),
                Breathability.of(BreathReason.FLUID_BREATHING, BreathSource.FLUID));
    }

    @Test
    void api_014_verdict_rejects_nulls() {
        assertThrows(NullPointerException.class, () -> Breathability.of(null, BreathSource.ATMOSPHERE));
        assertThrows(NullPointerException.class, () -> Breathability.of(BreathReason.BREATHABLE, null));
        Breathability.Builder b = Breathability.builder(BreathReason.BREATHABLE, BreathSource.ATMOSPHERE);
        assertThrows(NullPointerException.class, () -> b.breath(null));
        assertThrows(NullPointerException.class, () -> b.hazard(null));
        assertThrows(NullPointerException.class, () -> b.protection(null));
    }

    /**
     * The provider method has a default, so a provider built against an API from before it still
     * answers when a newer API is loaded; the fallback answers itself.
     */
    @Test
    void api_014_provider_breathability_has_a_default() throws NoSuchMethodException {
        Method method = ZasApiProvider.class.getMethod("breathability", LivingEntity.class);
        assertTrue(method.isDefault(), "ZasApiProvider.breathability has no default");
        assertEquals(Breathability.class, method.getReturnType());
        assertEquals(
                FallbackProvider.class,
                FallbackProvider.class
                        .getMethod("breathability", LivingEntity.class)
                        .getDeclaringClass());
    }
}
