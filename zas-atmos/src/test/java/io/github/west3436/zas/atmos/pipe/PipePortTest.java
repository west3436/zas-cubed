package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** A device's pipe ports: a block with several sits in several networks (CONT-001, CONT-003, CONT-007). */
class PipePortTest {
    private static final double T = 293.15;

    /** A pipe segment in a test graph, linked by hand. */
    private static final class Segment implements PipeNode {
        final GasStore stored = new GasStore(PipeVolumes.PIPE);
        final List<PipeNode> links = new ArrayList<>();
        PipeNetwork network;

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
            return links;
        }
    }

    private static PipePort port(List<PipeNode> links) {
        return new PipePort(PipeVolumes.PORT, () -> links);
    }

    @Test
    void cont_007_a_port_is_a_200_litre_node_in_its_network() {
        PipePort lone = port(List.of());
        assertEquals(200, lone.network().volume());

        List<PipeNode> links = new ArrayList<>();
        PipePort port = port(links);
        Segment pipe = new Segment();
        links.add(pipe);
        pipe.links.add(port);
        assertEquals(270, port.network().volume(), "the port's buffer and a pipe, each once");
        assertSame(port.network(), pipe.currentNetwork());
    }

    @Test
    void cont_001_two_ports_with_no_path_between_them_keep_two_networks() {
        PipePort in = port(List.of());
        PipePort out = port(List.of());
        assertNotSame(in.network(), out.network());
        in.network().gas().add(new GasParcel(Map.of(StandardAir.NITROGEN, 10.0), T));
        assertEquals(0, out.network().gas().totalMoles());
    }

    @Test
    void cont_001_pipes_between_two_ports_join_them_into_one_network() {
        List<PipeNode> inLinks = new ArrayList<>();
        List<PipeNode> outLinks = new ArrayList<>();
        PipePort in = port(inLinks);
        PipePort out = port(outLinks);
        Segment pipe = new Segment();
        inLinks.add(pipe);
        outLinks.add(pipe);
        pipe.links.add(in);
        pipe.links.add(out);
        assertSame(in.network(), out.network());
        assertEquals(470, in.network().volume());
    }

    @Test
    void cont_003_a_dissolved_port_keeps_its_share_by_volume_and_rebuilds() {
        List<PipeNode> links = new ArrayList<>();
        PipePort port = port(links);
        Segment pipe = new Segment();
        links.add(pipe);
        pipe.links.add(port);
        PipeNetwork first = port.network();
        first.gas().add(new GasParcel(Map.of(StandardAir.NITROGEN, 27.0), T));

        port.dissolve();
        assertFalse(first.valid());
        assertNull(port.currentNetwork());
        assertEquals(20.0, port.share().moles(StandardAir.NITROGEN), 1e-9, "200 L of 270 L");
        assertEquals(20.0, port.stored().moles(StandardAir.NITROGEN), 1e-9);
        assertEquals(7.0, pipe.stored.moles(StandardAir.NITROGEN), 1e-9, "70 L of 270 L");

        PipeNetwork second = port.network();
        assertTrue(second.valid());
        assertEquals(27.0, second.gas().moles(StandardAir.NITROGEN), 1e-9, "gathered again");
    }

    @Test
    void cont_003_dissolving_a_ports_links_ends_their_network() {
        List<PipeNode> links = new ArrayList<>();
        PipePort port = port(links);
        Segment pipe = new Segment();
        links.add(pipe);
        pipe.links.add(port);
        PipeNetwork net = port.network();
        port.dissolveLinks();
        assertFalse(net.valid());
    }

    @Test
    void cont_009_a_detached_port_takes_its_share_and_leaves_the_rest() {
        List<PipeNode> links = new ArrayList<>();
        PipePort port = port(links);
        Segment pipe = new Segment();
        links.add(pipe);
        pipe.links.add(port);
        port.network().gas().add(new GasParcel(Map.of(StandardAir.NITROGEN, 27.0), T));

        GasParcel share = port.detach();
        assertEquals(20.0, share.moles(StandardAir.NITROGEN), 1e-9);
        assertTrue(port.stored().isEmpty());
        assertEquals(7.0, pipe.stored.moles(StandardAir.NITROGEN), 1e-9);
    }

    @Test
    void cont_003_a_port_saves_its_share() {
        List<PipeNode> links = new ArrayList<>();
        PipePort port = port(links);
        Segment pipe = new Segment();
        links.add(pipe);
        pipe.links.add(port);
        port.network().gas().add(new GasParcel(Map.of(StandardAir.NITROGEN, 27.0), T));

        PipePort loaded = port(List.of());
        loaded.load(port.save());
        assertEquals(20.0, loaded.stored().moles(StandardAir.NITROGEN), 1e-9);
        assertEquals(T, loaded.stored().temperature());
    }
}
