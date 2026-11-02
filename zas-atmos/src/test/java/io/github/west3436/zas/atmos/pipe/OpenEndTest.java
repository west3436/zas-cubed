package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** An open pipe end's exchange: the one-connection edge of CORE-031 (CONT-004). */
class OpenEndTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final double R = PipeBlockEntity.OPEN_END_RATIO;

    @Test
    void cont_004_open_end_exchanges_with_a_zone_at_the_one_connection_ratio() {
        double vp = 70;
        double vz = 27_000;
        GasMixture zone = new GasMixture(Map.of(N2, 900.0), 293.15, vz);
        PipeBlockEntity.Flow f = PipeBlockEntity.openEndFlow(Map.of(O2, 10.0), vp, zone, false, R);
        assertEquals(R * 10.0 * vz / (vp + vz), f.out().get(O2), 1e-12);
        assertEquals(R * 900.0 * vp / (vp + vz), f.in().get(N2), 1e-12);
    }

    @Test
    void cont_004_two_ticks_close_the_gap_as_the_ratio_says() {
        // After one tick a gas's distance from equilibrium shrinks by the factor 1 - r.
        double vp = 70;
        double vz = 1_000;
        double np = 10.0;
        double eq = np * vp / (vp + vz);
        GasMixture zone = new GasMixture(Map.of(), 293.15, vz);
        PipeBlockEntity.Flow f = PipeBlockEntity.openEndFlow(Map.of(O2, np), vp, zone, false, R);
        double after = np - f.out().get(O2);
        assertEquals((1 - R) * (np - eq), after - eq, 1e-12);
    }

    @Test
    void cont_004_open_end_vents_to_an_infinite_ambient() {
        GasMixture vacuum = new GasMixture(Map.of(), 2.7, 1000);
        PipeBlockEntity.Flow f = PipeBlockEntity.openEndFlow(Map.of(O2, 10.0), 70, vacuum, true, R);
        assertEquals(R * 10.0, f.out().get(O2), 1e-12);
        assertTrue(f.in().isEmpty());
        GasMixture air = StandardAir.mixture(1000);
        PipeBlockEntity.Flow g = PipeBlockEntity.openEndFlow(Map.of(), 70, air, true, R);
        assertEquals(R * air.moles(N2) / 1000 * 70, g.in().get(N2), 1e-12);
    }

    @Test
    void cont_004_a_settled_open_end_moves_nothing() {
        GasMixture zone = new GasMixture(Map.of(O2, 100.0), 293.15, 1000);
        PipeBlockEntity.Flow f = PipeBlockEntity.openEndFlow(Map.of(O2, 7.0), 70, zone, false, R);
        assertTrue(f.out().isEmpty() && f.in().isEmpty());
    }
}
