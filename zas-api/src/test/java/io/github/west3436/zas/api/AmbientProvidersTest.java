package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProvider;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Ambient definitions and the provider registry (ENV-003, ENV-004, ENV-019). */
class AmbientProvidersTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final AmbientProvider PROVIDER = level -> AmbientDefinition.vacuum();

    /** A dimension of its own for each test, as the registry is static. */
    private static ResourceKey<Level> dimension(String test) {
        return ResourceKey.create(Registries.DIMENSION, id("test", test));
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static List<ResourceLocation> order(ResourceKey<Level> dimension) {
        return AmbientProviders.registered(dimension).stream()
                .map(AmbientProviders.Registration::id)
                .toList();
    }

    @Test
    void env_004_definition_copies_sorts_and_drops_zero_entries() {
        Map<ResourceLocation, Double> given = new TreeMap<>(Map.of(O2, 21.0, N2, 79.0, id("test", "none"), 0.0));
        AmbientDefinition definition = new AmbientDefinition(given, 250);
        given.put(O2, 1.0);
        assertEquals(List.of(N2, O2), List.copyOf(definition.partialPressures().keySet()));
        assertEquals(21.0, definition.partialPressures().get(O2));
        assertEquals(100.0, definition.pressure(), 1e-12);
        assertFalse(definition.isVacuum());
        assertThrows(
                UnsupportedOperationException.class,
                () -> definition.partialPressures().put(O2, 1.0));
    }

    @Test
    void env_004_definition_rejects_what_an_ambient_cant_be() {
        assertThrows(IllegalArgumentException.class, () -> new AmbientDefinition(Map.of(O2, -1.0), 250));
        assertThrows(IllegalArgumentException.class, () -> new AmbientDefinition(Map.of(O2, Double.NaN), 250));
        assertThrows(IllegalArgumentException.class, () -> new AmbientDefinition(Map.of(O2, 1.0), -1));
        assertThrows(IllegalArgumentException.class, () -> new AmbientDefinition(Map.of(O2, 1.0), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new AmbientDefinition(Map.of(O2, 1.0), 0));
        assertThrows(NullPointerException.class, () -> new AmbientDefinition(null, 250));
        assertEquals(0.0, AmbientDefinition.vacuum(0).temperature());
    }

    @Test
    void env_003_vacuum_defaults_to_the_cosmic_background() {
        assertTrue(AmbientDefinition.vacuum().isVacuum());
        assertEquals(2.7, AmbientDefinition.vacuum().temperature());
        assertEquals(737.0, AmbientDefinition.vacuum(737).temperature());
        assertEquals(0.0, AmbientDefinition.vacuum(737).pressure());
    }

    @Test
    void env_004_standard_air_at_a_temperature() {
        AmbientDefinition air = AmbientDefinition.standardAir(113.15);
        assertEquals(StandardAir.PRESSURE, air.pressure(), 1e-9);
        assertEquals(
                StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION,
                air.partialPressures().get(O2),
                1e-9);
        assertEquals(
                StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
                air.partialPressures().get(N2),
                1e-9);
        assertEquals(113.15, air.temperature());
    }

    @Test
    void env_019_the_highest_priority_comes_first() {
        ResourceKey<Level> d = dimension("priority");
        AmbientProviders.register(id("aaa", "low"), d, -1, PROVIDER);
        AmbientProviders.register(id("zzz", "high"), d, 5, PROVIDER);
        AmbientProviders.register(id("mmm", "default"), d, PROVIDER);
        assertEquals(List.of(id("zzz", "high"), id("mmm", "default"), id("aaa", "low")), order(d));
        assertEquals(
                AmbientProviders.DEFAULT_PRIORITY,
                AmbientProviders.registered(d).get(1).priority());
    }

    @Test
    void env_019_a_tie_goes_to_the_smallest_mod_id_whatever_the_path() {
        ResourceKey<Level> d = dimension("tie");
        AmbientProviders.register(id("bbb", "a"), d, PROVIDER);
        AmbientProviders.register(id("aaa", "z"), d, PROVIDER);
        AmbientProviders.register(id("aab", "a"), d, PROVIDER);
        assertEquals(List.of(id("aaa", "z"), id("aab", "a"), id("bbb", "a")), order(d));
    }

    @Test
    void env_019_two_providers_of_one_mod_order_by_path() {
        ResourceKey<Level> d = dimension("same_mod");
        AmbientProviders.register(id("aaa", "second"), d, PROVIDER);
        AmbientProviders.register(id("aaa", "first"), d, PROVIDER);
        assertEquals(List.of(id("aaa", "first"), id("aaa", "second")), order(d));
    }

    @Test
    void env_004_an_id_registers_once_per_dimension() {
        ResourceKey<Level> d = dimension("duplicate");
        ResourceKey<Level> other = dimension("duplicate_other");
        AmbientProviders.register(id("aaa", "one"), d, PROVIDER);
        assertThrows(IllegalArgumentException.class, () -> AmbientProviders.register(id("aaa", "one"), d, 3, PROVIDER));
        AmbientProviders.register(id("aaa", "one"), other, PROVIDER);
        assertEquals(1, AmbientProviders.registered(d).size());
        assertEquals(1, AmbientProviders.registered(other).size());
    }

    @Test
    void env_004_unregister_removes_only_that_provider() {
        ResourceKey<Level> d = dimension("unregister");
        AmbientProviders.register(id("aaa", "one"), d, PROVIDER);
        AmbientProviders.register(id("bbb", "two"), d, PROVIDER);
        assertFalse(AmbientProviders.unregister(id("ccc", "three"), d));
        assertTrue(AmbientProviders.unregister(id("aaa", "one"), d));
        assertEquals(List.of(id("bbb", "two")), order(d));
        assertTrue(AmbientProviders.unregister(id("bbb", "two"), d));
        assertTrue(AmbientProviders.registered(d).isEmpty());
        assertFalse(AmbientProviders.unregister(id("bbb", "two"), d));
    }

    @Test
    void env_004_arguments_are_checked() {
        ResourceKey<Level> d = dimension("arguments");
        assertThrows(NullPointerException.class, () -> AmbientProviders.register(null, d, PROVIDER));
        assertThrows(NullPointerException.class, () -> AmbientProviders.register(id("aaa", "x"), null, PROVIDER));
        assertThrows(NullPointerException.class, () -> AmbientProviders.register(id("aaa", "x"), d, null));
        assertThrows(NullPointerException.class, () -> AmbientProviders.registered(null));
        assertThrows(NullPointerException.class, () -> AmbientProviders.unregister(null, d));
        assertTrue(AmbientProviders.registered(d).isEmpty());
    }
}
