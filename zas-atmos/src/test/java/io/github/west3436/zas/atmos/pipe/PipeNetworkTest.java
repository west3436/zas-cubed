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

    /** CONT-100, CONT-006: whichever member steps first runs the network's atmos tick, once per round. */
    @Test
    void cont_100_a_network_runs_one_atmos_tick_per_round_of_its_members() {
        Node a = new Node(70);
        Node b = new Node(70);
        Node c = new Node(70);
        a.links.add(b);
        b.links.add(c);
        PipeNetwork net = a.net();
        assertTrue(net.startsAtmosTick(b));
        assertFalse(net.startsAtmosTick(a));
        assertFalse(net.startsAtmosTick(c));
        assertTrue(net.startsAtmosTick(b), "b steps again: the next atmos tick");
        assertFalse(net.startsAtmosTick(a));
        assertFalse(net.startsAtmosTick(c));
    }

    /** CONT-100: a member whose chunk has left full status doesn't step, and holds nothing up. */
    @Test
    void cont_100_a_member_that_is_not_stepping_holds_up_no_atmos_tick() {
        Node a = new Node(70);
        Node away = new Node(70);
        a.links.add(away);
        PipeNetwork net = a.net();
        assertEquals(2, net.members().size());
        assertTrue(net.startsAtmosTick(a));
        assertTrue(net.startsAtmosTick(a));
        assertTrue(net.startsAtmosTick(a));
    }

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

        @Override
        public List<PipeNode> connectedNodes() {
            return List.copyOf(links);
        }

        PipeNetwork net() {
            return network != null && network.valid() ? network : PipeNetwork.build(this, n -> ((Node) n).links);
        }
    }

    /**
     * CONT-003: nodes that each built a network while the other's chunk was away are apart once they
     * connect again, and rebuilding from either joins them with all their gas. A node with a neighbour
     * that has no network yet counts as apart too, and joins the same way.
     */
    @Test
    void cont_003_a_node_with_a_neighbour_in_another_network_is_apart() {
        Node a = new Node(70);
        Node b = new Node(70);
        a.net().gas().add(new GasParcel(Map.of(N2, 7.0), T));
        b.net().gas().add(new GasParcel(Map.of(O2, 3.0), T));
        assertFalse(a.apart(), "with nothing connected");

        link(a, b);
        assertTrue(a.apart(), "beside a node in another network");
        assertTrue(b.apart(), "beside a node in another network");

        a.net().dissolve();
        PipeNetwork one = PipeNetwork.build(a, n -> ((Node) n).links);
        assertFalse(a.apart(), "after the rebuild");
        assertFalse(b.apart(), "after the rebuild");
        assertSame(one, b.currentNetwork());
        assertEquals(7.0, one.gas().moles(N2), REL);
        assertEquals(3.0, one.gas().moles(O2), REL);

        Node c = new Node(70);
        link(b, c);
        assertTrue(b.apart(), "beside a node in no network");
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
        assertEquals(200, PipeVolumes.VALVE);
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

    /**
     * An open valve is a node linked to both sides, and a shut one is linked to neither (CONT-075):
     * opening adds the sides' gas and energy together, and shutting shares them out by volume, valve
     * included.
     */
    @Test
    void cont_075_a_valve_joins_two_networks_and_splits_them_by_volume() {
        List<Node> left = run(3);
        List<Node> right = run(5);
        Node valve = new Node(PipeVolumes.VALVE);
        left.get(0).stored.set(new GasParcel(Map.of(O2, 2.0), 300));
        right.get(0).stored.set(new GasParcel(Map.of(N2, 6.0), 500));
        double energy = 2 * GasProperties.molarHeatCapacity(O2) * 300 + 6 * GasProperties.molarHeatCapacity(N2) * 500;
        double volume = 8 * PipeVolumes.PIPE + PipeVolumes.VALVE;
        close(2.0, left.get(0).net().gas().moles(O2), "left O2 while the valve is shut");
        close(6.0, right.get(0).net().gas().moles(N2), "right N2 while the valve is shut");

        link(left.get(2), valve);
        link(valve, right.get(0));
        PipeNetwork joined = PipeNetwork.build(valve, n -> ((Node) n).links);
        assertSame(joined, left.get(0).network, "left joined");
        assertSame(joined, right.get(4).network, "right joined");
        close(volume, joined.volume(), "joined volume, the valve's included");
        close(2.0, joined.gas().moles(O2), "O2 joined");
        close(6.0, joined.gas().moles(N2), "N2 joined");
        close(energy, joined.gas().thermalEnergy(), "energy joined");
        double mixed = joined.gas().temperature();

        unlink(left.get(2), valve);
        unlink(valve, right.get(0));
        joined.dissolve();
        PipeNetwork l = left.get(0).net();
        PipeNetwork r = right.get(0).net();
        PipeNetwork v = valve.net();
        close(3 * PipeVolumes.PIPE, l.volume(), "left volume");
        close(5 * PipeVolumes.PIPE, r.volume(), "right volume");
        close(PipeVolumes.VALVE, v.volume(), "the shut valve is a network of its own");
        for (PipeNetwork part : List.of(l, r, v)) {
            close(2.0 * part.volume() / volume, part.gas().moles(O2), "O2 by volume");
            close(6.0 * part.volume() / volume, part.gas().moles(N2), "N2 by volume");
            close(mixed, part.gas().temperature(), "each part keeps the mixed temperature");
        }
        close(
                energy,
                l.gas().thermalEnergy() + r.gas().thermalEnergy() + v.gas().thermalEnergy(),
                "energy split");
    }

    /** Turning a valve again and again neither loses nor makes gas or energy (CONT-003, CONT-075). */
    @Test
    void cont_075_turning_a_valve_again_and_again_conserves_gas_and_energy() {
        List<Node> left = run(4);
        List<Node> right = run(9);
        Node valve = new Node(PipeVolumes.VALVE);
        left.get(0).stored.set(new GasParcel(Map.of(O2, 3.0, N2, 11.0), 320));
        right.get(0).stored.set(new GasParcel(Map.of(N2, 0.7), 650));
        double energy = (3 * GasProperties.molarHeatCapacity(O2) + 11 * GasProperties.molarHeatCapacity(N2)) * 320
                + 0.7 * GasProperties.molarHeatCapacity(N2) * 650;
        for (int turn = 0; turn < 1_000; turn++) {
            link(left.get(3), valve);
            link(valve, right.get(0));
            PipeNetwork joined = PipeNetwork.build(valve, n -> ((Node) n).links);
            assertEquals(14, joined.members().size());
            assertEquals(3.0, joined.gas().moles(O2), 1e-9, "O2 at turn " + turn);
            assertEquals(11.7, joined.gas().moles(N2), 1e-9, "N2 at turn " + turn);
            assertEquals(energy, joined.gas().thermalEnergy(), 1e-9 * energy, "energy at turn " + turn);
            unlink(left.get(3), valve);
            unlink(valve, right.get(0));
            joined.dissolve();
            double parts = left.get(0).net().gas().totalMoles()
                    + right.get(0).net().gas().totalMoles()
                    + valve.net().gas().totalMoles();
            assertEquals(14.7, parts, 1e-9, "the parts' moles at turn " + turn);
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
