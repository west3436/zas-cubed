package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.HazardHandler;
import io.github.west3436.zas.api.life.HazardHandlers;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The hazard handler registry, which works without ZAS³ (LIFE-032, D-17). */
class HazardHandlersTest {
    private static final HazardHandler HANDLER = exposure -> {};

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    @Test
    void life_032_handlers_run_in_order_of_id_namespace_first() {
        ResourceLocation b = id("b", "a");
        ResourceLocation a2 = id("a", "z");
        ResourceLocation a1 = id("a", "m");
        HazardHandlers.register(b, StandardAir.OXYGEN, HANDLER);
        HazardHandlers.register(a2, GasTags.TOXIC, HANDLER);
        HazardHandlers.register(a1, StandardAir.NITROGEN, HANDLER);
        try {
            List<ResourceLocation> order = HazardHandlers.registered().stream()
                    .map(HazardHandlers.Registration::id)
                    .toList();
            assertEquals(List.of(a1, a2, b), order);
        } finally {
            HazardHandlers.unregister(b);
            HazardHandlers.unregister(a2);
            HazardHandlers.unregister(a1);
        }
    }

    @Test
    void life_032_an_id_can_only_be_registered_once() {
        ResourceLocation handler = id("test", "once");
        HazardHandlers.register(handler, StandardAir.OXYGEN, HANDLER);
        try {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> HazardHandlers.register(handler, StandardAir.NITROGEN, HANDLER));
            assertThrows(
                    IllegalArgumentException.class, () -> HazardHandlers.register(handler, GasTags.TOXIC, HANDLER));
        } finally {
            assertTrue(HazardHandlers.unregister(handler));
        }
        assertFalse(HazardHandlers.unregister(handler), "it was registered twice");
    }

    @Test
    void life_032_registering_needs_a_gas_and_a_handler() {
        ResourceLocation handler = id("test", "nulls");
        assertThrows(NullPointerException.class, () -> HazardHandlers.register(handler, StandardAir.OXYGEN, null));
        assertThrows(
                NullPointerException.class, () -> HazardHandlers.register(handler, (ResourceLocation) null, HANDLER));
        assertThrows(NullPointerException.class, () -> HazardHandlers.register(null, StandardAir.OXYGEN, HANDLER));
    }

    /** Without ZAS³, {@code #zas:oxidizer} holds oxygen and not nitrogen. */
    @Test
    void life_032_a_handler_applies_to_its_gas_or_its_tags_gases() {
        ResourceLocation byGas = id("test", "by_gas");
        ResourceLocation byTag = id("test", "by_tag");
        HazardHandlers.register(byGas, StandardAir.NITROGEN, HANDLER);
        HazardHandlers.register(byTag, GasTags.OXIDIZER, HANDLER);
        try {
            List<HazardHandlers.Registration> registered = HazardHandlers.registered();
            HazardHandlers.Registration gas = registered.get(0);
            HazardHandlers.Registration tag = registered.get(1);
            assertEquals(byGas, gas.id());
            assertTrue(gas.appliesTo(StandardAir.NITROGEN));
            assertFalse(gas.appliesTo(StandardAir.OXYGEN));
            assertEquals(byTag, tag.id());
            assertTrue(tag.appliesTo(StandardAir.OXYGEN));
            assertFalse(tag.appliesTo(StandardAir.NITROGEN));
        } finally {
            HazardHandlers.unregister(byGas);
            HazardHandlers.unregister(byTag);
        }
    }
}
