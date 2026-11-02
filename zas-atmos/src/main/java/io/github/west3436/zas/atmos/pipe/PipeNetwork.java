package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * A pipe network: connected nodes sharing one perfectly mixed mixture whose volume is the sum of
 * theirs (CONT-001, CONT-007, CONT-008). Its gas is a single {@link GasStore}, so every change mixes
 * the whole network at once.
 *
 * <p><b>Rebuilds share gas by volume</b> (CONT-003). A network never changes its members. When one
 * is added, removed or unloaded, the network {@linkplain #dissolve() dissolves}: each member takes
 * its volume's share of the gas into {@link PipeNode#stored()}. The next {@link #build} from any
 * node gathers the shares of every node it reaches, so a split gives each part gas by volume and a
 * merge adds the parts together. Building over a node that is still in another network dissolves
 * that network first, so no gas is counted twice.
 *
 * <p>Networks are built lazily, when a node needs its mixture. Server thread only.
 */
public final class PipeNetwork {
    private final List<PipeNode> members;
    private final GasStore gas;
    private boolean valid = true;

    private PipeNetwork(List<PipeNode> members, GasStore gas) {
        this.members = members;
        this.gas = gas;
    }

    /**
     * Builds the network of every node connected to a start node, breadth-first in the order the
     * neighbour function gives, and gathers their gas.
     *
     * @param start the node to start from
     * @param neighbours the nodes connected to a node
     * @return the new network, which every reached node is now in
     */
    public static PipeNetwork build(
            PipeNode start, Function<PipeNode, ? extends Iterable<? extends PipeNode>> neighbours) {
        Set<PipeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<PipeNode> reached = new ArrayList<>();
        ArrayDeque<PipeNode> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            PipeNode n = queue.poll();
            reached.add(n);
            for (PipeNode next : neighbours.apply(n)) {
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        for (PipeNode n : reached) {
            PipeNetwork old = n.currentNetwork();
            if (old != null) {
                old.dissolve();
            }
        }
        double volume = 0;
        for (PipeNode n : reached) {
            volume += n.nodeVolume();
        }
        GasStore gas = new GasStore(volume);
        for (PipeNode n : reached) {
            gas.merge(n.stored());
        }
        PipeNetwork network = new PipeNetwork(List.copyOf(reached), gas);
        for (PipeNode n : reached) {
            n.joinNetwork(network);
        }
        return network;
    }

    /** The network's mixture: change it directly, and the whole network is mixed (CONT-008). */
    public GasStore gas() {
        return gas;
    }

    /** The total volume in L, the sum of the members' (CONT-001). */
    public double volume() {
        return gas.volume();
    }

    /** The members, in the order they were reached. */
    public List<PipeNode> members() {
        return members;
    }

    /** Whether the network is still in use: false once it has dissolved. */
    public boolean valid() {
        return valid;
    }

    /**
     * A member's share of the gas, by volume, without taking it: what it would hold if the
     * network dissolved now. Nodes save this.
     *
     * @param node a member
     * @return its share
     */
    public GasParcel shareOf(PipeNode node) {
        return gas.fraction(node.nodeVolume() / gas.volume());
    }

    /**
     * Ends the network: each member takes its share of the gas by volume and leaves (CONT-003).
     * Dissolving an ended network does nothing.
     */
    public void dissolve() {
        if (!valid) {
            return;
        }
        valid = false;
        GasParcel all = gas.drain();
        GasStore whole = new GasStore(gas.volume());
        whole.set(all);
        for (PipeNode m : members) {
            m.stored().set(whole.fraction(m.nodeVolume() / whole.volume()));
            m.joinNetwork(null);
        }
    }

    /**
     * Removes a member, which takes its share with it: the network dissolves, and the member's
     * share is returned rather than kept (CONT-009).
     *
     * @param node a member
     * @return its share of the gas
     */
    public GasParcel detach(PipeNode node) {
        dissolve();
        return node.stored().drain();
    }
}
