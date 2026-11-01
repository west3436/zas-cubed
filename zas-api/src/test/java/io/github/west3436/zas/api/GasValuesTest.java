package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** The values a query returns (API-010) and the fallback without ZAS³ (D-17). */
class GasValuesTest {
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;

    @Test
    void api_010_sample_derives_pressures_and_composition() {
        // 2 mol at 300 K in 100 L: P = 2 * 8.314462618 * 300 / 100 = 49.887 kPa.
        GasMixture mixture = new GasMixture(Map.of(N2, 1.5, O2, 0.5), 300, 100);
        AtmosphereSample sample = AtmosphereSample.ambient(mixture);
        assertEquals(2.0, mixture.totalMoles(), 1e-12);
        assertEquals(49.88677571, sample.pressure(), 1e-6);
        assertEquals(37.41508178, sample.partialPressures().get(N2), 1e-6);
        assertEquals(12.47169393, sample.partialPressures().get(O2), 1e-6);
        assertEquals(0.75, sample.composition().get(N2), 1e-12);
        assertEquals(0.25, sample.composition().get(O2), 1e-12);
        assertEquals(300, sample.temperature());
        assertEquals(AtmosphereState.AMBIENT, sample.state());
        assertTrue(sample.zone().isEmpty());
    }

    @Test
    void api_010_maps_are_sorted_and_drop_zero_entries() {
        GasParcel parcel = new GasParcel(Map.of(O2, 1.0, N2, 2.0, ResourceLocation.parse("a:zero"), 0.0), 300);
        assertEquals(List.of(N2, O2), List.copyOf(parcel.moles().keySet()));
        assertEquals(3.0, parcel.totalMoles());
        assertTrue(new GasMixture(Map.of(), 2.7, 1000).composition().isEmpty());
    }

    @Test
    void env_011_standard_air_is_101_325_kpa_at_293_15_k() {
        GasMixture air = StandardAir.mixture(StandardAir.CELL_VOLUME);
        assertEquals(101.325, air.pressure(), 1e-9);
        assertEquals(293.15, air.temperature());
        assertEquals(0.79, air.composition().get(N2), 1e-12);
        assertEquals(0.21, air.composition().get(O2), 1e-12);
    }

    @Test
    void api_009_fallback_returns_standard_air_and_absent_mutations() {
        // Levels are never touched by the fallback, so none is needed here.
        ZasApiProvider fallback = FallbackProvider.INSTANCE;
        assertFalse(fallback.isReal());
        AtmosphereSample at = fallback.sample(null, Vec3.ZERO);
        assertEquals(AtmosphereState.AMBIENT, at.state());
        assertEquals(101.325, at.pressure(), 1e-9);
        assertEquals(at, fallback.sample(null, BlockPos.ZERO));
        assertTrue(fallback.zone(null, new io.github.west3436.zas.api.atmosphere.ZoneId(1))
                .isEmpty());

        SourceId source = SourceId.of("test", "fallback");
        CompletableFuture<MutationResult> add =
                fallback.addGas(null, Vec3.ZERO, new GasParcel(Map.of(O2, 1.0), 300), source);
        CompletableFuture<MutationResult> remove = fallback.removeGas(null, Vec3.ZERO, Map.of(O2, 1.0), source);
        assertTrue(add.isDone() && remove.isDone());
        assertSame(MutationResult.ABSENT, add.join());
        assertSame(MutationResult.ABSENT, remove.join());
        assertTrue(add.join().moved().isEmpty());
    }

    /** Without ZAS³, oxygen is the only gas in a shipped tag (CORE-012, API-009). */
    @Test
    void core_012_fallback_tags_hold_standard_air() {
        assertEquals(Set.of(O2), GasTags.gases(GasTags.OXIDIZER));
        assertTrue(GasTags.contains(GasTags.OXIDIZER, O2));
        assertFalse(GasTags.contains(GasTags.OXIDIZER, N2));
        assertEquals(Set.of(), GasTags.gases(GasTags.FLAMMABLE));
        assertEquals(Set.of(), GasTags.gases(GasTags.TOXIC));
        assertEquals(Set.of(), GasTags.gases(GasTag.of("other", "tag")));
    }

    /**
     * The provider method has a default, so a provider built against an API from before it still
     * answers when a newer API is loaded. A provider that doesn't override it answers as the
     * fallback does.
     */
    @Test
    void core_012_provider_gases_in_tag_has_a_default() throws NoSuchMethodException {
        assertTrue(
                ZasApiProvider.class.getMethod("gasesInTag", GasTag.class).isDefault(),
                "ZasApiProvider.gasesInTag has no default");
        ZasApiProvider older = (ZasApiProvider) Proxy.newProxyInstance(
                ZasApiProvider.class.getClassLoader(), new Class<?>[] {ZasApiProvider.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        assertEquals(Set.of(O2), older.gasesInTag(GasTags.OXIDIZER));
        assertEquals(Set.of(), older.gasesInTag(GasTags.TOXIC));
        assertEquals(Set.of(), older.gasesInTag(GasTag.of("other", "tag")));
    }

    @Test
    void core_012_gas_tag_ids() {
        assertEquals("#zas:toxic", GasTags.TOXIC.toString());
        assertEquals(ResourceLocation.fromNamespaceAndPath("zas", "flammable"), GasTags.FLAMMABLE.id());
        assertEquals(GasTags.OXIDIZER, new GasTag(ResourceLocation.parse("zas:oxidizer")));
    }

    @Test
    void api_040_fallback_is_active_until_a_provider_is_installed() {
        // Unit tests never install a provider, so the API reports absent.
        assertFalse(ZasApi.isPresent());
        assertSame(FallbackProvider.INSTANCE, ZasApi.provider());
    }
}
