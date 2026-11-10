package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.List;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * A member of a pipe network: a pipe, a cap, a device's port buffer, an attached canister
 * (CONT-001, CONT-007). Plain Java, so networks are unit-tested without Minecraft.
 *
 * <p>While a node is in a network its gas is in the network's mixture and its {@link #stored()}
 * store is empty. When the network dissolves, each member takes its volume's share of the gas into
 * {@code stored()}, and a network built later gathers those shares again (CONT-003).
 */
public interface PipeNode {
    /** The node's gas volume in L (CONT-007). */
    double nodeVolume();

    /** The node's own gas while it is in no network; empty while it is in one. */
    GasStore stored();

    /** The network the node is in, or null. */
    PipeNetwork currentNetwork();

    /** Sets the network the node is in; called only by {@link PipeNetwork}. */
    void joinNetwork(PipeNetwork network);

    /**
     * The node's network, built now if it has none or its own has ended. Server side.
     *
     * @return the network, valid
     */
    default PipeNetwork network() {
        PipeNetwork own = currentNetwork();
        return own != null && own.usable() ? own : PipeNetwork.build(this, PipeNode::connectedNodes);
    }

    /**
     * The nodes connected to this one in the world, for {@link PipeNetwork#build}. A node that isn't
     * in a world, as in a unit test, has none and is linked by the function given to {@code build}.
     */
    default List<PipeNode> connectedNodes() {
        return List.of();
    }

    /**
     * Whether a node connected to this one is in another network. Across a chunk border, two sides can
     * each hold a valid network: each built its own while the other's chunk was out of full status,
     * and a chunk's return tells nobody, since its nodes never left memory. The caller rebuilds.
     *
     * @return true if a connected node isn't in this node's network
     */
    default boolean apart() {
        PipeNetwork own = currentNetwork();
        for (PipeNode n : connectedNodes()) {
            if (n.currentNetwork() != own) {
                return true;
            }
        }
        return false;
    }

    /**
     * The chunk that saves the node's share, which its network tells when the gas changes. A node that
     * isn't in a world, as in a unit test, has none.
     */
    default @Nullable LevelChunk savedIn() {
        return null;
    }

    /**
     * Whether the node is radiator pipe, which exchanges heat with the gas around it each atmos tick
     * (HEAT-020, HEAT-021). The default is false.
     */
    default boolean radiates() {
        return false;
    }
}
