package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.EdgeSummary;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Read queries return immutable snapshots (API-046). */
class SnapshotTest {
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation O2 = StandardAir.OXYGEN;

    /**
     * A stand-in for a provider's published snapshot: each atmos tick replaces
     * it, and queries return whatever is published.
     */
    private static final class Published {
        private volatile AtmosphereSample current;

        Published(AtmosphereSample first) {
            current = first;
        }

        AtmosphereSample query() {
            return current;
        }

        void tick(AtmosphereSample next) {
            current = next;
        }
    }

    @Test
    void api_046_mixture_copies_its_input() {
        Map<ResourceLocation, Double> moles = new HashMap<>(Map.of(N2, 10.0, O2, 2.0));
        GasMixture mixture = new GasMixture(moles, 300, 1000);
        moles.put(O2, 99.0);
        moles.remove(N2);
        assertEquals(10.0, mixture.moles(N2));
        assertEquals(2.0, mixture.moles(O2));
    }

    @Test
    void api_046_snapshot_maps_cannot_be_changed() {
        GasMixture mixture = StandardAir.mixture(1000);
        assertThrows(UnsupportedOperationException.class, () -> mixture.moles().put(O2, 1.0));
        assertThrows(
                UnsupportedOperationException.class, () -> mixture.composition().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> mixture.partialPressures().remove(N2));
        GasParcel parcel = mixture.parcel();
        assertThrows(UnsupportedOperationException.class, () -> parcel.moles().put(O2, 1.0));
        AtmosphereSample sample = AtmosphereSample.ambient(mixture);
        assertThrows(
                UnsupportedOperationException.class,
                () -> sample.partialPressures().clear());
        MutationResult result = new MutationResult(MutationOutcome.ZONE, parcel);
        assertThrows(
                UnsupportedOperationException.class,
                () -> result.moved().moles().clear());
    }

    @Test
    void api_046_snapshot_does_not_change_after_the_next_tick() {
        ZoneId id = new ZoneId(7);
        AtmosphereSample before = new AtmosphereSample(
                AtmosphereState.ZONED, Optional.of(id), new GasMixture(Map.of(N2, 30.0, O2, 8.0), 293.15, 2000));
        Published published = new Published(before);

        AtmosphereSample seen = published.query();
        double pressure = seen.pressure();
        Map<ResourceLocation, Double> composition = seen.composition();

        // The next tick drains the zone.
        published.tick(new AtmosphereSample(
                AtmosphereState.ZONED, Optional.of(id), new GasMixture(Map.of(N2, 1.0), 250, 2000)));

        assertNotEquals(seen, published.query());
        assertEquals(before, seen);
        assertEquals(pressure, seen.pressure());
        assertEquals(composition, seen.composition());
        assertEquals(293.15, seen.temperature());
        assertEquals(8.0, seen.mixture().moles(O2));
    }

    @Test
    void api_046_zone_info_is_a_value() {
        GasMixture mixture = StandardAir.mixture(3000);
        ZoneInfo a = new ZoneInfo(new ZoneId(1), 3000, 3, mixture, new EdgeSummary(1, 0, 2), false, false);
        ZoneInfo b =
                new ZoneInfo(new ZoneId(1), 3000, 3, StandardAir.mixture(3000), new EdgeSummary(1, 0, 2), false, false);
        assertEquals(a, b);
        assertEquals(true, a.enclosed());
    }

    @Test
    void api_046_values_reject_bad_input() {
        assertThrows(IllegalArgumentException.class, () -> new GasParcel(Map.of(O2, -1.0), 300));
        assertThrows(IllegalArgumentException.class, () -> new GasParcel(Map.of(O2, Double.NaN), 300));
        assertThrows(IllegalArgumentException.class, () -> new GasMixture(Map.of(), 300, 0));
        assertThrows(IllegalArgumentException.class, () -> new GasMixture(Map.of(), -1, 1000));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AtmosphereSample(AtmosphereState.ZONED, Optional.empty(), StandardAir.mixture(1000)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AtmosphereSample(
                        AtmosphereState.AMBIENT, Optional.of(new ZoneId(1)), StandardAir.mixture(1000)));
    }
}
