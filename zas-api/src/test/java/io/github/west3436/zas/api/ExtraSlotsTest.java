package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.life.ExtraSlotProvider;
import io.github.west3436.zas.api.life.ExtraSlotVisitor;
import io.github.west3436.zas.api.life.ExtraSlots;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/**
 * The registry of extra equipment slots (COMPAT-040 to COMPAT-042) without ZAS³: it keeps what is
 * registered, and nothing is asked. What ZAS³ does with the providers is in its GameTests.
 */
class ExtraSlotsTest {
    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    /** Providers are kept in the order they are asked: by id, namespace first, each id once. */
    @Test
    void compat_040_extra_slot_providers_are_kept_in_order_of_id() {
        ExtraSlotProvider provider = (entity, visitor) -> {};
        ResourceLocation b = id("b", "a");
        ResourceLocation a2 = id("a", "z");
        ResourceLocation a1 = id("a", "m");
        assertEquals(List.of(), ExtraSlots.registered());
        ExtraSlots.register(b, provider);
        ExtraSlots.register(a2, provider);
        ExtraSlots.register(a1, provider);
        try {
            assertEquals(
                    List.of(a1, a2, b),
                    ExtraSlots.registered().stream()
                            .map(ExtraSlots.Registration::id)
                            .toList());
            assertSame(provider, ExtraSlots.registered().get(0).provider());
            assertThrows(IllegalArgumentException.class, () -> ExtraSlots.register(a1, provider));
            assertThrows(NullPointerException.class, () -> ExtraSlots.register(id("c", "c"), null));
            assertThrows(NullPointerException.class, () -> ExtraSlots.register(null, provider));
            assertThrows(NullPointerException.class, () -> ExtraSlots.unregister(null));
        } finally {
            assertTrue(ExtraSlots.unregister(b));
            assertTrue(ExtraSlots.unregister(a2));
            assertTrue(ExtraSlots.unregister(a1));
        }
        assertFalse(ExtraSlots.unregister(a1), "it was still registered");
        assertEquals(List.of(), ExtraSlots.registered());
    }

    /**
     * Without ZAS³ no provider is asked, and a provider from before 1.1.0 has the same answer: the
     * method that asks them has a default that gives nothing. The lookup refuses a missing entity
     * or visitor before it asks.
     */
    @Test
    void compat_040_no_extra_slot_provider_is_asked_without_zas() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("extraSlotItems", LivingEntity.class, ExtraSlotVisitor.class)
                .isDefault());
        int[] asked = new int[1];
        ResourceLocation id = id("a", "slots");
        ExtraSlots.register(id, (entity, visitor) -> asked[0]++);
        try {
            FallbackProvider.INSTANCE.extraSlotItems(null, (stack, kind) -> asked[0]++);
            assertEquals(0, asked[0]);
            assertThrows(NullPointerException.class, () -> ExtraSlots.forEach(null, (stack, kind) -> {}));
        } finally {
            assertTrue(ExtraSlots.unregister(id));
        }
    }
}
