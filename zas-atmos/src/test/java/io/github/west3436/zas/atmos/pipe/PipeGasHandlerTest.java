package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** How much of a parcel a pipe's gas handler takes: up to a pump's highest pressure (API-021). */
class PipeGasHandlerTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation UNKNOWN = ResourceLocation.fromNamespaceAndPath("zas", "no_such_gas");

    private static GasStore after(GasStore store, GasParcel gas, double part) {
        GasStore mixed = new GasStore(store.volume());
        mixed.add(new GasParcel(store.moles(), store.temperature()));
        TreeMap<ResourceLocation, Double> moles = new TreeMap<>();
        gas.moles().forEach((id, n) -> moles.put(id, n * part));
        mixed.add(new GasParcel(moles, gas.temperature()));
        return mixed;
    }

    @Test
    void api_021_a_parcel_that_stays_under_the_limit_fits_whole() {
        GasStore store = new GasStore(140);
        store.add(new GasParcel(Map.of(N2, 5.0), 293.15));
        assertEquals(1, PipeGasHandler.fits(store, new GasParcel(Map.of(O2, 3.0), 400)));
        assertEquals(1, PipeGasHandler.fits(new GasStore(140), new GasParcel(Map.of(O2, 3.0), 400)));
    }

    @Test
    void api_021_the_part_that_fits_leaves_the_store_at_the_limit() {
        Random random = new Random(21);
        int partial = 0;
        for (int i = 0; i < 20_000; i++) {
            GasStore store = new GasStore(70 + random.nextDouble() * 5_000);
            if (random.nextInt(4) > 0) {
                double moles = random.nextDouble() * 1.5 * store.volume();
                store.add(new GasParcel(
                        Map.of(N2, moles, O2, moles * random.nextDouble()), 80 + random.nextDouble() * 900));
            }
            GasParcel gas = new GasParcel(
                    Map.of(O2, random.nextDouble() * 20_000, N2, random.nextDouble() * 20_000),
                    80 + random.nextDouble() * 900);
            double part = PipeGasHandler.fits(store, gas);
            assertTrue(part >= 0 && part <= 1, "the part is from 0 to 1: " + part);
            double before = store.pressure();
            double pressure = after(store, gas, part).pressure();
            if (before >= PipeGasHandler.FILL_LIMIT_KPA) {
                assertEquals(0, part, "nothing fits a store at or over the limit");
            } else if (part < 1) {
                partial++;
                assertEquals(PipeGasHandler.FILL_LIMIT_KPA, pressure, 1e-6, "a part leaves it at the limit");
            } else {
                assertTrue(pressure <= PipeGasHandler.FILL_LIMIT_KPA * (1 + 1e-12), "a whole parcel stays under");
            }
        }
        assertTrue(partial > 1_000, "the cases with a part: " + partial);
    }

    @Test
    void api_021_a_gas_no_pack_defines_fits_and_a_parcel_at_absolute_zero_doesnt() {
        GasStore store = new GasStore(140);
        // A gas no pack defines mixes with the default heat capacity, as it does in a store.
        assertEquals(1, PipeGasHandler.fits(store, new GasParcel(Map.of(UNKNOWN, 3.0), 300)));
        assertEquals(0, PipeGasHandler.fits(store, new GasParcel(Map.of(O2, 3.0), 0)));
    }
}
