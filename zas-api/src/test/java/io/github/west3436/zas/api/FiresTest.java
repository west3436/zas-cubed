package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.fire.Ignition;
import io.github.west3436.zas.api.fire.IgnitionOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** The fire API (API-055 to API-058): the result type, the fallback and the provider defaults. */
class FiresTest {
    private static final SourceId SOURCE = SourceId.of("test", "igniter");
    private static final ZoneId ZONE = new ZoneId(7);

    /** Without ZAS³ nothing burns: no hotspots, an ignition is ABSENT at once, nothing to put out (D-17). */
    @Test
    void api_055_fallback_has_no_fire() {
        ZasApiProvider fallback = FallbackProvider.INSTANCE;
        assertTrue(fallback.hotspots(null, ZONE).isEmpty());
        assertFalse(fallback.isBurning(null, ZONE));
        assertFalse(fallback.isHotspot(null, BlockPos.ZERO));
        CompletableFuture<Ignition> lit = fallback.ignite(null, BlockPos.ZERO, SOURCE);
        assertTrue(lit.isDone());
        assertSame(Ignition.ABSENT, lit.join());
        assertFalse(lit.join().burning());
        assertEquals(0, fallback.extinguish(null, BlockPos.ZERO, BlockPos.ZERO));
    }

    /** Every fire method of the provider has a default, so an older provider still answers (§6.2). */
    @Test
    void api_056_provider_fire_methods_have_defaults() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("hotspots", Level.class, ZoneId.class)
                .isDefault());
        assertTrue(ZasApiProvider.class
                .getMethod("isBurning", Level.class, ZoneId.class)
                .isDefault());
        assertTrue(ZasApiProvider.class
                .getMethod("isHotspot", Level.class, BlockPos.class)
                .isDefault());
        assertTrue(ZasApiProvider.class
                .getMethod("ignite", ServerLevel.class, BlockPos.class, SourceId.class)
                .isDefault());
        assertTrue(ZasApiProvider.class
                .getMethod("extinguish", ServerLevel.class, BlockPos.class, BlockPos.class)
                .isDefault());
    }

    /** An ignition names a hotspot exactly when a fire burns: IGNITED or BURNING (API-056, API-058). */
    @Test
    void api_058_ignition_names_a_hotspot_only_when_burning() {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(1, 2, 3);
        Ignition lit = new Ignition(IgnitionOutcome.IGNITED, Optional.of(at), Optional.of(ZONE));
        at.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), lit.hotspot().orElseThrow(), "kept immutable");
        assertTrue(lit.burning());
        assertTrue(new Ignition(IgnitionOutcome.BURNING, Optional.of(BlockPos.ZERO), Optional.of(ZONE)).burning());
        Ignition noSpace = new Ignition(IgnitionOutcome.NO_SPACE, Optional.empty(), Optional.of(ZONE));
        assertFalse(noSpace.burning());
        assertEquals(Optional.of(ZONE), noSpace.zone());
        assertThrows(
                IllegalArgumentException.class,
                () -> new Ignition(IgnitionOutcome.NO_SPACE, Optional.of(BlockPos.ZERO), Optional.of(ZONE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Ignition(IgnitionOutcome.IGNITED, Optional.empty(), Optional.of(ZONE)));
    }
}
