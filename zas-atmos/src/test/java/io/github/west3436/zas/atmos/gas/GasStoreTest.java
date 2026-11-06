package io.github.west3436.zas.atmos.gas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** A store's watcher, which is how a pipe network's chunks learn they have something new to save. */
class GasStoreTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final double T = 293.15;

    /** Runs a change on a store holding 10 mol and returns how many times its watcher was told. */
    private static int told(java.util.function.Consumer<GasStore> change) {
        GasStore store = new GasStore(100);
        store.add(Map.of(O2, 10.0), T);
        int[] told = new int[1];
        store.watch(() -> told[0]++);
        change.accept(store);
        return told[0];
    }

    /** NFR-012: every change to the moles or the temperature tells the watcher, once a call. */
    @Test
    void nfr_012_a_store_tells_its_watcher_of_every_change() {
        assertEquals(1, told(s -> s.add(Map.of(O2, 1.0), T)), "add");
        assertEquals(1, told(s -> s.add(new GasParcel(Map.of(O2, 1.0), T))), "add a parcel");
        assertEquals(1, told(s -> s.take(Map.of(O2, 1.0))), "take");
        assertEquals(1, told(s -> s.takeFraction(0.5)), "take a fraction");
        assertEquals(1, told(GasStore::drain), "drain");
        assertEquals(1, told(s -> s.set(new GasParcel(Map.of(O2, 1.0), T))), "set");
        assertEquals(1, told(s -> s.setTemperature(300)), "set the temperature");
        GasStore other = new GasStore(100);
        other.add(Map.of(O2, 1.0), T);
        assertEquals(1, told(s -> s.merge(other)), "merge");
    }

    /** NFR-012: reading, and calls that change nothing, don't tell it, so an idle network costs no saves. */
    @Test
    void nfr_012_a_store_tells_its_watcher_of_nothing_else() {
        assertEquals(0, told(GasStore::mixture), "read");
        assertEquals(0, told(s -> s.fraction(0.5)), "a fraction, not taken");
        assertEquals(0, told(s -> s.add(Map.of(), T)), "add nothing");
        assertEquals(0, told(s -> s.take(Map.of(StandardAir.NITROGEN, 1.0))), "take a gas it doesn't hold");
        assertEquals(0, told(s -> s.takeFraction(0)), "take no fraction");
        assertEquals(0, told(s -> s.setTemperature(T)), "the same temperature");
        assertEquals(0, told(s -> s.merge(new GasStore(100))), "merge an empty store");
        GasStore empty = new GasStore(100);
        empty.watch(() -> {
            throw new AssertionError("told of draining an empty store");
        });
        empty.drain();
    }
}
