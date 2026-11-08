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
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

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
 * <p><b>What a world saves.</b> Each member saves its share, with its chunk, so a change to the gas
 * tells every chunk the network spans that it has something new to save, once a chunk per change and
 * without loading any. A chunk saves only then, and writes its members' shares as they are at that
 * moment. A chunk that leaves full status gets one more save and then isn't saved again until it
 * unloads, so a network {@linkplain #usable() ends} before it changes gas any of that chunk's share
 * is in. A server that stops writes every chunk at one moment, so the shares add up. One that crashes
 * between two chunks' saves can bring back a share from before the other's, as it can with anything
 * else that moves between chunks.
 *
 * <p>Networks are built lazily, when a node needs its mixture. Server thread only.
 */
public final class PipeNetwork {
    private final List<PipeNode> members;
    private final List<PipeNode> radiators;
    private final GasStore gas;
    private final Set<PipeNode> steppedThisTick = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<LevelChunk> chunks = Collections.newSetFromMap(new IdentityHashMap<>());
    private final boolean builtAway;
    private boolean valid = true;

    private PipeNetwork(List<PipeNode> members, GasStore gas) {
        this.members = members;
        this.gas = gas;
        List<PipeNode> radiating = new ArrayList<>();
        for (PipeNode m : members) {
            if (m.radiates()) {
                radiating.add(m);
            }
            LevelChunk chunk = m.savedIn();
            if (chunk != null) {
                chunks.add(chunk);
            }
        }
        this.radiators = radiating.isEmpty() ? List.of() : List.copyOf(radiating);
        builtAway = chunkAway();
        gas.watch(this::tellChunks);
        if (!gas.isEmpty()) {
            tellChunks(); // gathering the shares may have changed what each member would save
        }
    }

    /** Tells the chunks the network spans that the gas has changed: only those it holds, so none loads. */
    private void tellChunks() {
        for (LevelChunk chunk : chunks) {
            chunk.setUnsaved(true);
        }
    }

    private boolean chunkAway() {
        for (LevelChunk chunk : chunks) {
            if (!chunk.getFullStatus().isOrAfter(FullChunkStatus.FULL)) {
                return true;
            }
        }
        return false;
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

    /**
     * The members that are radiator pipe (HEAT-020), in the order they were reached: none for most
     * networks, so a network with no radiator pipe pays nothing for them.
     */
    public List<PipeNode> radiators() {
        return radiators;
    }

    /** Whether the network is still in use: false once it has dissolved. */
    public boolean valid() {
        return valid;
    }

    /**
     * Whether a member can use the network now, ending it first if a chunk it spans has left full
     * status: that chunk's members keep their shares until it returns, as members of an unloaded chunk
     * do. A network built by a node in such a chunk, of that node alone, is for this once, so the node
     * looks for its neighbours again next time.
     *
     * @return true if the network is still in use
     */
    public boolean usable() {
        if (valid && (builtAway || chunkAway())) {
            dissolve();
        }
        return valid;
    }

    /**
     * Records that a member is stepping, and says whether that starts the network's atmos tick. Each
     * member steps once per atmos tick, at its own place in the period, and the network has no step
     * of its own, so the first member to step runs it for them all, and the network's next atmos
     * tick starts when a member steps again (CONT-006). A member that isn't stepping, because its
     * chunk has left full status, holds nothing up.
     *
     * @param node a member, stepping now
     * @return true if the caller runs the network's atmos tick now
     */
    public boolean startsAtmosTick(PipeNode node) {
        if (steppedThisTick.add(node) && steppedThisTick.size() > 1) {
            return false;
        }
        steppedThisTick.clear();
        steppedThisTick.add(node);
        return true;
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
        gas.watch(null); // each member's share stays as it was, so no chunk has anything new to save
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
