package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Pipe networks: volumes, mixing and gas-conserving rebuilds (CONT-001, CONT-003, CONT-007, CONT-008). */
class PipeNetworkTest {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final double T = 293.15;
    private static final double REL = 1e-12;

    /** A node in a test graph, linked by hand. */
    private static final class Node implements PipeNode {
        final GasStore stored;
        final List<Node> links = new ArrayList<>();
        PipeNetwork network;

        Node(double volume) {
            stored = new GasStore(volume);
        }

        @Override
        public double nodeVolume() {
            return stored.volume();
        }

        @Override
        public GasStore stored() {
            return stored;
        }

        @Override
        public PipeNetwork currentNetwork() {
            return network;
        }

        @Override
        public void joinNetwork(PipeNetwork network) {
            this.network = network;
        }

        PipeNetwork net() {
            return network != null && network.valid() ? network : PipeNetwork.build(this, n -> ((Node) n).links);
        }
    }

    private static void link(Node a, Node b) {
        a.links.add(b);
        b.links.add(a);
    }

    private static void unlink(Node a, Node b) {
        a.links.remove(b);
        b.links.remove(a);
    }

    private static List<Node> run(int pipes) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < pipes; i++) {
            Node n = new Node(PipeVolumes.PIPE);
            if (i > 0) {
                link(nodes.get(i - 1), n);
            }
            nodes.add(n);
        }
        return nodes;
    }

    private static void close(double expected, double actual, String what) {
        assertEquals(expected, actual, REL * Math.max(1, Math.abs(expected)), what);
    }

    private static GasParcel oxygen(double n) {
        return new GasParcel(Map.of(O2, n), T);
    }

    @Test
    void cont_007_part_volumes() {
        assertEquals(70, PipeVolumes.PIPE);
        assertEquals(200, PipeVolumes.PORT);
        assertEquals(400, PipeVolumes.VENT);
        assertEquals(1000, PipeVolumes.CANISTER);
    }

    @Test
    void cont_001_a_run_holds_one_mixture_whose_volume_is_the_sum_of_its_segments() {
        for (int length : new int[] {1, 2, 7, 50}) {
            List<Node> nodes = run(length);
            PipeNetwork net = nodes.get(0).net();
            close(PipeVolumes.PIPE * length, net.volume(), "volume of " + length + " pipes");
            for (Node n : nodes) {
                assertSame(net, n.network, "every segment is in the one network");
            }
        }
        List<Node> nodes = run(3);
        Node vent = new Node(PipeVolumes.VENT);
        link(nodes.get(2), vent);
        close(3 * PipeVolumes.PIPE + PipeVolumes.VENT, vent.net().volume(), "pipes and a vent buffer");
    }

    @Test
    void cont_008_gas_added_at_one_end_is_uniform_across_the_run() {
        List<Node> nodes = run(10);
        PipeNetwork net = nodes.get(0).net();
        net.gas().add(oxygen(5));
        double pressure = net.gas().pressure();
        for (Node n : nodes) {
            GasParcel share = net.shareOf(n);
            GasMixture m = new GasMixture(share.moles(), share.temperature(), n.nodeVolume());
            close(pressure, m.pressure(), "each segment's pressure");
            close(0.5, share.moles(O2), "each segment's moles");
        }
    }

    @Test
    void cont_003_a_split_shares_gas_by_volume() {
        List<Node> nodes = run(4);
        Node vent = new Node(PipeVolumes.VENT);
        link(nodes.get(3), vent);
        PipeNetwork net = nodes.get(0).net();
        net.gas().add(new GasParcel(Map.of(O2, 6.0, N2, 12.0), T));
        double total = 4 * PipeVolumes.PIPE + PipeVolumes.VENT;

        unlink(nodes.get(1), nodes.get(2));
        net.dissolve();
        assertFalse(net.valid());
        assertNull(nodes.get(0).network);

        PipeNetwork left = nodes.get(0).net();
        PipeNetwork right = vent.net();
        close(2 * PipeVolumes.PIPE, left.volume(), "left volume");
        close(2 * PipeVolumes.PIPE + PipeVolumes.VENT, right.volume(), "right volume");
        close(6.0 * left.volume() / total, left.gas().moles(O2), "left O2");
        close(12.0 * right.volume() / total, right.gas().moles(N2), "right N2");
        close(18.0, left.gas().totalMoles() + right.gas().totalMoles(), "total");
        close(left.gas().pressure(), right.gas().pressure(), "both halves keep the pressure");
    }

    @Test
    void cont_003_a_merge_adds_the_parts_together() {
        List<Node> a = run(3);
        List<Node> b = run(5);
        PipeNetwork na = a.get(0).net();
        PipeNetwork nb = b.get(0).net();
        na.gas().add(oxygen(3));
        nb.gas().add(new GasParcel(Map.of(N2, 10.0), T));

        link(a.get(2), b.get(0));
        // Building from a node still in an old network dissolves the old one first.
        PipeNetwork merged = PipeNetwork.build(a.get(0), n -> ((Node) n).links);
        assertFalse(na.valid());
        assertFalse(nb.valid());
        close(8 * PipeVolumes.PIPE, merged.volume(), "merged volume");
        close(3, merged.gas().moles(O2), "O2");
        close(10, merged.gas().moles(N2), "N2");
        for (Node n : a) {
            assertTrue(n.stored().isEmpty(), "a member keeps gas of its own");
        }
    }

    @Test
    void cont_009_a_detached_node_takes_its_share() {
        List<Node> nodes = run(5);
        PipeNetwork net = nodes.get(0).net();
        net.gas().add(oxygen(10));
        Node middle = nodes.get(2);
        unlink(nodes.get(1), middle);
        unlink(middle, nodes.get(3));
        GasParcel share = net.detach(middle);
        close(2, share.moles(O2), "the removed segment's share");
        double rest =
                nodes.get(0).net().gas().moles(O2) + nodes.get(4).net().gas().moles(O2);
        close(8, rest, "the rest stays in the pipes");
    }

    @Test
    void gas_store_mixes_moles_and_saves() {
        GasStore s = new GasStore(100);
        s.add(new GasParcel(Map.of(O2, 1.0), 250));
        s.add(new GasParcel(Map.of(N2, 3.0), 250));
        close(250, s.temperature(), "equal temperatures stay");
        close(4 * GasMixture.GAS_CONSTANT * 250 / 100, s.pressure(), "pressure");
        GasParcel taken = s.take(Map.of(O2, 5.0, N2, 1.0));
        close(1, taken.moles(O2), "takes no more than there is");
        close(2, s.moles(N2), "N2 left");
        GasParcel back = GasStore.parcel(GasStore.save(s.fraction(0.5)));
        close(1, back.moles(N2), "saved and loaded");
        close(250, back.temperature(), "saved temperature");
    }

    /**
     * Gas at different temperatures mixes conserving thermal energy (CONT-008, CORE-003): the
     * store's C·T is the sum of the parts', with each gas's heat capacity from the API. Without
     * ZAS³ (as here) the API answers with the shipped N₂ and O₂ values.
     */
    @Test
    void cont_008_gas_at_different_temperatures_mixes_conserving_thermal_energy() {
        double cO2 = GasProperties.molarHeatCapacity(O2);
        double cN2 = GasProperties.molarHeatCapacity(N2);
        assertEquals(21.06, cO2);
        assertEquals(20.81, cN2);
        GasStore s = new GasStore(100);
        s.add(new GasParcel(Map.of(O2, 1.0), 400));
        s.add(new GasParcel(Map.of(N2, 3.0), 200));
        double energy = cO2 * 400 + 3 * cN2 * 200;
        close(energy / (cO2 + 3 * cN2), s.temperature(), "heat-capacity-weighted temperature");
        close(energy, s.thermalEnergy(), "thermal energy conserved");

        // A whole network: a hot and a cold part mixed by a rebuild keep their energy.
        List<Node> nodes = run(2);
        nodes.get(0).stored.set(new GasParcel(Map.of(O2, 2.0), 500));
        nodes.get(1).stored.set(new GasParcel(Map.of(N2, 6.0), 250));
        PipeNetwork net = nodes.get(0).net();
        close(2 * cO2 * 500 + 6 * cN2 * 250, net.gas().thermalEnergy(), "network energy conserved");

        // A gas the server doesn't define uses the default heat capacity.
        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath("test", "unknown");
        assertEquals(GasProperties.DEFAULT_MOLAR_HEAT_CAPACITY, GasProperties.molarHeatCapacity(unknown));
        GasStore u = new GasStore(100);
        u.add(new GasParcel(Map.of(O2, 1.0), 300));
        u.add(new GasParcel(Map.of(unknown, 1.0), 100));
        double cu = GasProperties.DEFAULT_MOLAR_HEAT_CAPACITY;
        close((cO2 * 300 + cu * 100) / (cO2 + cu), u.temperature(), "unknown gas mixes at the default");
    }
}
