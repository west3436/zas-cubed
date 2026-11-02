package io.github.west3436.zas.atmos.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/** A port issues a new change only once its last one has completed (CONT-072). */
class ZonePortTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** Mutations whose futures the test completes by hand. */
    private static class Pending implements ZonePort.Mutations {
        final List<CompletableFuture<MutationResult>> futures = new ArrayList<>();

        @Override
        public CompletableFuture<MutationResult> add(ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
            CompletableFuture<MutationResult> f = new CompletableFuture<>();
            futures.add(f);
            return f;
        }

        @Override
        public CompletableFuture<MutationResult> remove(
                ServerLevel level, Vec3 point, Map<ResourceLocation, Double> moles, SourceId source) {
            return add(level, point, null, source);
        }
    }

    private static final SourceId SOURCE = SourceId.of("zas_atmos", "test");

    private static MutationResult zone(GasParcel moved) {
        return new MutationResult(MutationOutcome.ZONE, moved);
    }

    @Test
    void cont_072_a_port_waits_for_its_future() {
        Pending api = new Pending();
        ZonePort port = new ZonePort(SOURCE, api);
        GasParcel gas = new GasParcel(Map.of(O2, 1.0), 293.15);
        List<MutationResult> done = new ArrayList<>();

        assertTrue(port.add(null, Vec3.ZERO, gas, done::add));
        assertTrue(port.busy());
        assertFalse(port.add(null, Vec3.ZERO, gas, done::add), "a second change while the first is outstanding");
        assertFalse(port.remove(null, Vec3.ZERO, Map.of(O2, 1.0), done::add));
        assertEquals(1, api.futures.size(), "the refused changes reached the API");

        api.futures.get(0).complete(zone(gas));
        assertFalse(port.busy());
        assertEquals(1, done.size());
        assertTrue(port.remove(null, Vec3.ZERO, Map.of(O2, 1.0), done::add));
        assertEquals(2, api.futures.size());
    }

    @Test
    void cont_072_an_exchange_waits_for_both_its_futures() {
        Pending api = new Pending();
        ZonePort port = new ZonePort(SOURCE, api);
        GasParcel out = new GasParcel(Map.of(O2, 1.0), 293.15);
        assertTrue(port.exchange(null, Vec3.ZERO, out, Map.of(N2, 2.0), r -> {}, r -> {}));
        assertEquals(2, api.futures.size());
        api.futures.get(1).complete(zone(GasParcel.EMPTY));
        assertTrue(port.busy(), "free with the addition outstanding");
        api.futures.get(0).complete(zone(out));
        assertFalse(port.busy());
    }

    @Test
    void cont_072_a_change_that_completes_at_once_leaves_the_port_free() {
        ZonePort port = new ZonePort(SOURCE, new Pending() {
            @Override
            public CompletableFuture<MutationResult> add(
                    ServerLevel level, Vec3 point, GasParcel gas, SourceId source) {
                return CompletableFuture.completedFuture(MutationResult.ABSENT);
            }
        });
        GasParcel gas = new GasParcel(Map.of(O2, 1.0), 293.15);
        List<Map<ResourceLocation, Double>> back = new ArrayList<>();
        assertTrue(port.add(null, Vec3.ZERO, gas, r -> back.add(ZonePort.untaken(gas, r))));
        assertFalse(port.busy());
        assertEquals(List.of(Map.of(O2, 1.0)), back, "the gas ZAS³ didn't take is handed back");
    }

    @Test
    void cont_072_a_failed_mutation_frees_the_port() {
        Pending api = new Pending();
        ZonePort port = new ZonePort(SOURCE, api);
        port.remove(null, Vec3.ZERO, Map.of(O2, 1.0), r -> {});
        api.futures.get(0).completeExceptionally(new IllegalStateException("test"));
        assertFalse(port.busy());
    }
}
