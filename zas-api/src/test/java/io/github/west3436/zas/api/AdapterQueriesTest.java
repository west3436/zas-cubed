package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.atmosphere.AtmospherePreset;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientSeed;
import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.LocalAtmosphereProvider;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * What a space-mod adapter needs, without ZAS³ and from an older provider: the preset (SPACE-004),
 * whether a zone touches a block (SPACE-014), whether fire burns in some air (SPACE-015), the
 * registries of local atmospheres (SPACE-017) and of ambient seeds (SPACE-020), and the capability
 * of an item that only holds breathing gas (SPACE-023).
 */
class AdapterQueriesTest {
    private static final BlockPos HERE = new BlockPos(3, 70, -5);

    /**
     * A provider from before these queries: its defaults answer, and a sample is by {@code states},
     * ambient wherever the map doesn't say.
     */
    private static ZasApiProvider older(Map<BlockPos, AtmosphereState> states) {
        return (ZasApiProvider) Proxy.newProxyInstance(
                ZasApiProvider.class.getClassLoader(), new Class<?>[] {ZasApiProvider.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    if (method.getName().equals("sample") && args[1] instanceof BlockPos pos) {
                        AtmosphereState state = states.getOrDefault(pos, AtmosphereState.AMBIENT);
                        GasMixture air = StandardAir.mixture(StandardAir.CELL_VOLUME);
                        boolean zone = state == AtmosphereState.ZONED || state == AtmosphereState.PAUSED;
                        return new AtmosphereSample(state, zone ? Optional.of(new ZoneId(1)) : Optional.empty(), air);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static GasMixture air(double nitrogenKpa, double oxygenKpa) {
        double perKpa = StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        return new GasMixture(
                Map.of(StandardAir.NITROGEN, nitrogenKpa * perKpa, StandardAir.OXYGEN, oxygenKpa * perKpa),
                StandardAir.TEMPERATURE,
                StandardAir.CELL_VOLUME);
    }

    /** Without ZAS³ the preset is Normal, and an older provider says the same. */
    @Test
    void space_004_the_preset_is_normal_without_zas() throws NoSuchMethodException {
        assertEquals(AtmospherePreset.NORMAL, FallbackProvider.INSTANCE.preset());
        assertEquals(AtmospherePreset.NORMAL, Atmospheres.preset());
        assertTrue(ZasApiProvider.class.getMethod("preset").isDefault());
        assertEquals(AtmospherePreset.NORMAL, older(Map.of()).preset());
    }

    /** Without ZAS³ no zone exists, so none touches a block. */
    @Test
    void space_014_no_zone_touches_a_block_without_zas() {
        assertFalse(FallbackProvider.INSTANCE.zoneTouches(null, HERE));
        assertThrows(NullPointerException.class, () -> Atmospheres.zoneTouches(null, HERE));
    }

    /**
     * An older provider answers from its samples: a zone at the position or on any of its six
     * sides touches it, paused or not, an unresolved position counts, and one two blocks off or on
     * a diagonal doesn't.
     */
    @Test
    void space_014_an_older_provider_answers_from_its_samples() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("zoneTouches", Level.class, BlockPos.class)
                .isDefault());
        assertFalse(older(Map.of()).zoneTouches(null, HERE));
        assertTrue(older(Map.of(HERE, AtmosphereState.ZONED)).zoneTouches(null, HERE));
        assertTrue(older(Map.of(HERE, AtmosphereState.PAUSED)).zoneTouches(null, HERE));
        assertTrue(older(Map.of(HERE, AtmosphereState.UNRESOLVED)).zoneTouches(null, HERE));
        for (Direction side : Direction.values()) {
            assertTrue(older(Map.of(HERE.relative(side), AtmosphereState.ZONED)).zoneTouches(null, HERE), "" + side);
            assertTrue(
                    older(Map.of(HERE.relative(side), AtmosphereState.PAUSED)).zoneTouches(null, HERE), "" + side);
            assertFalse(
                    older(Map.of(HERE.relative(side, 2), AtmosphereState.ZONED)).zoneTouches(null, HERE), "" + side);
        }
        assertFalse(older(Map.of(HERE.offset(1, 1, 0), AtmosphereState.ZONED)).zoneTouches(null, HERE));
    }

    /**
     * Without ZAS³ fire burns by FIRE-030's default minimums, both of which must hold: an O₂ fraction
     * of 0.14 and an O₂ partial pressure of 10 kPa. No gas burns nothing.
     */
    @Test
    void space_015_fire_burns_by_the_default_minimums_without_zas() throws NoSuchMethodException {
        assertEquals(0.14, Fires.DEFAULT_OXYGEN_FRACTION);
        assertEquals(10.0, Fires.DEFAULT_OXYGEN_PRESSURE);
        assertTrue(
                ZasApiProvider.class.getMethod("fireBurnsIn", GasMixture.class).isDefault());
        for (ZasApiProvider provider : List.of(FallbackProvider.INSTANCE, older(Map.of()))) {
            assertTrue(provider.fireBurnsIn(StandardAir.mixture(StandardAir.CELL_VOLUME)), "standard air");
            assertTrue(provider.fireBurnsIn(air(61.5, 10.1)), "just over both minimums");
            assertFalse(provider.fireBurnsIn(air(90, 14)), "13.5 % O2 at 14 kPa");
            assertFalse(provider.fireBurnsIn(air(0, 9.9)), "pure O2 at 9.9 kPa");
            assertFalse(provider.fireBurnsIn(air(101.325, 0)), "nitrogen");
            assertFalse(provider.fireBurnsIn(air(0, 0)), "a vacuum");
        }
        assertTrue(Fires.canBurnIn(StandardAir.mixture(StandardAir.CELL_VOLUME)));
        assertFalse(Fires.canBurnIn(air(0, 0)));
        assertThrows(NullPointerException.class, () -> Fires.canBurnIn(null));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** The registry works without ZAS³: providers are kept in order of id, namespace first, each id once. */
    @Test
    void space_017_local_atmosphere_providers_are_kept_in_order_of_id() {
        LocalAtmosphereProvider provider = (level, point) -> null;
        ResourceLocation b = id("b", "a");
        ResourceLocation a2 = id("a", "z");
        ResourceLocation a1 = id("a", "m");
        assertEquals(List.of(), LocalAtmospheres.registered());
        LocalAtmospheres.register(b, provider);
        LocalAtmospheres.register(a2, provider);
        LocalAtmospheres.register(a1, provider);
        try {
            assertEquals(
                    List.of(a1, a2, b),
                    LocalAtmospheres.registered().stream()
                            .map(LocalAtmospheres.Registration::id)
                            .toList());
            assertSame(provider, LocalAtmospheres.registered().get(0).provider());
            assertThrows(IllegalArgumentException.class, () -> LocalAtmospheres.register(a1, provider));
            assertThrows(NullPointerException.class, () -> LocalAtmospheres.register(id("c", "c"), null));
            assertThrows(NullPointerException.class, () -> LocalAtmospheres.register(null, provider));
        } finally {
            assertTrue(LocalAtmospheres.unregister(b));
            assertTrue(LocalAtmospheres.unregister(a2));
            assertTrue(LocalAtmospheres.unregister(a1));
        }
        assertFalse(LocalAtmospheres.unregister(a1), "it was still registered");
        assertEquals(List.of(), LocalAtmospheres.registered());
    }

    /**
     * Without ZAS³ no provider is asked, so no point has local air, and an older provider says the
     * same. The lookup refuses a missing level or point before it asks.
     */
    @Test
    void space_017_no_point_has_local_air_without_zas() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("localAir", ServerLevel.class, Vec3.class)
                .isDefault());
        LocalAtmosphereProvider provider = (level, point) -> StandardAir.mixture(StandardAir.CELL_VOLUME);
        ResourceLocation id = id("a", "air");
        LocalAtmospheres.register(id, provider);
        try {
            assertNull(FallbackProvider.INSTANCE.localAir(null, Vec3.ZERO));
            assertNull(older(Map.of()).localAir(null, Vec3.ZERO));
            assertThrows(NullPointerException.class, () -> LocalAtmospheres.at(null, Vec3.ZERO));
            assertThrows(NullPointerException.class, () -> LocalAtmospheres.at(null, null));
        } finally {
            assertTrue(LocalAtmospheres.unregister(id));
        }
    }

    /**
     * The seed registry works without ZAS³: seeds are kept in the order they are asked, by id with the
     * namespace first, each id once, and one registration covers every dimension.
     */
    @Test
    void space_020_ambient_seeds_are_kept_in_order_of_id() {
        AmbientSeed seed = level -> AmbientDefinition.vacuum(100.15);
        ResourceLocation b = id("b", "a");
        ResourceLocation a2 = id("a", "z");
        ResourceLocation a1 = id("a", "m");
        assertEquals(List.of(), AmbientSeeds.registered());
        AmbientSeeds.register(b, seed);
        AmbientSeeds.register(a2, seed);
        AmbientSeeds.register(a1, seed);
        try {
            assertEquals(
                    List.of(a1, a2, b),
                    AmbientSeeds.registered().stream()
                            .map(AmbientSeeds.Registration::id)
                            .toList());
            assertSame(seed, AmbientSeeds.registered().get(0).seed());
            assertThrows(IllegalArgumentException.class, () -> AmbientSeeds.register(a1, seed));
            assertThrows(NullPointerException.class, () -> AmbientSeeds.register(id("c", "c"), null));
            assertThrows(NullPointerException.class, () -> AmbientSeeds.register(null, seed));
            assertThrows(NullPointerException.class, () -> AmbientSeeds.unregister(null));
        } finally {
            assertTrue(AmbientSeeds.unregister(b));
            assertTrue(AmbientSeeds.unregister(a2));
            assertTrue(AmbientSeeds.unregister(a1));
        }
        assertFalse(AmbientSeeds.unregister(a1), "it was still registered");
        assertEquals(List.of(), AmbientSeeds.registered());
    }

    /**
     * An item that only holds breathing gas has a capability of its own, {@code zas:internal_supply},
     * apart from life support's, so its protection can come from data.
     */
    @Test
    void space_023_an_item_that_only_holds_gas_has_its_own_capability() {
        assertEquals(id("zas", "internal_supply"), IInternalSupply.ITEM.name());
        assertEquals(IInternalSupply.class, IInternalSupply.ITEM.typeClass());
        assertEquals(void.class, IInternalSupply.ITEM.contextClass());
        assertFalse(IInternalSupply.ITEM.name().equals(ILifeSupport.ITEM.name()), "it is life support's capability");
    }
}
