package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.mutation.PinOutcome;
import io.github.west3436.zas.api.mutation.PinResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** Zone pins in the API (API-017, API-050): the result type, the fallback and the provider defaults. */
class ZonePinsTest {
    private static final SourceId OWNER = SourceId.of("test", "controller/1_64_-3");

    /** Without ZAS³ a pin pins nothing and says so, and an unpin is done at once (API-009, D-17). */
    @Test
    void api_050_fallback_pins_nothing() {
        ZasApiProvider fallback = FallbackProvider.INSTANCE;
        CompletableFuture<PinResult> pin = fallback.pin(null, Vec3.ZERO, OWNER);
        assertTrue(pin.isDone());
        assertSame(PinResult.ABSENT, pin.join());
        assertEquals(PinOutcome.ABSENT, pin.join().outcome());
        assertTrue(pin.join().zone().isEmpty());
        CompletableFuture<Void> unpin = fallback.unpin(null, OWNER);
        assertTrue(unpin.isDone());
        assertNull(unpin.join());
    }

    /**
     * Both provider methods have defaults, so a provider built against an API from before them still
     * answers when a newer API is loaded, as the fallback does (architecture §6.2).
     */
    @Test
    void api_050_provider_pin_methods_have_defaults() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("pin", ServerLevel.class, Vec3.class, SourceId.class)
                .isDefault());
        assertTrue(ZasApiProvider.class
                .getMethod("unpin", ServerLevel.class, SourceId.class)
                .isDefault());
        ZasApiProvider older = (ZasApiProvider) Proxy.newProxyInstance(
                ZasApiProvider.class.getClassLoader(), new Class<?>[] {ZasApiProvider.class}, (proxy, method, args) -> {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        assertSame(PinResult.ABSENT, older.pin(null, Vec3.ZERO, OWNER).join());
        assertNull(older.unpin(null, OWNER).join());
    }

    /** A result names a zone exactly when the outcome is ZONE (API-050). */
    @Test
    void api_050_result_names_a_zone_only_when_pinned() {
        PinResult pinned = PinResult.pinned(new ZoneId(7));
        assertEquals(PinOutcome.ZONE, pinned.outcome());
        assertEquals(Optional.of(new ZoneId(7)), pinned.zone());
        assertEquals(PinOutcome.OPEN, PinResult.none(PinOutcome.OPEN).outcome());
        assertTrue(PinResult.none(PinOutcome.UNRESOLVED).zone().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> PinResult.none(PinOutcome.ZONE));
        assertThrows(IllegalArgumentException.class, () -> new PinResult(PinOutcome.OPEN, Optional.of(new ZoneId(1))));
        assertThrows(NullPointerException.class, () -> new PinResult(null, Optional.empty()));
    }
}
