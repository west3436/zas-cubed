package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.List;

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
     * The nodes connected to this one in the world, for {@link PipeNetwork#build}. A node that isn't
     * in a world, as in a unit test, has none and is linked by the function given to {@code build}.
     */
    default List<PipeNode> connectedNodes() {
        return List.of();
    }
}
