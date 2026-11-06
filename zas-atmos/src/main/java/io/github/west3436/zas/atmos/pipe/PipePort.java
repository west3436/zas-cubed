package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * One pipe port of a device with several: a network node of its own that isn't a block entity, so
 * one block can sit in several networks (CONT-007, architecture §11.5). It behaves as a {@link
 * PipeNodeEntity} does, and its device calls it where a node's block entity would act: when it
 * loads, unloads, is removed and is saved.
 *
 * <p>Server thread only.
 */
public final class PipePort implements PipeNode {
    private final GasStore stored;
    private final Supplier<List<PipeNode>> links;
    private final Supplier<@Nullable LevelChunk> chunk;
    private PipeNetwork network;

    /**
     * A port that isn't in a world, as in a unit test.
     *
     * @param volume the port's buffer in L
     * @param links the nodes connected to the port now
     */
    public PipePort(double volume, Supplier<List<PipeNode>> links) {
        this(volume, links, () -> null);
    }

    /**
     * @param volume the port's buffer in L
     * @param links the nodes connected to the port now
     * @param chunk the chunk that saves its device
     */
    public PipePort(double volume, Supplier<List<PipeNode>> links, Supplier<@Nullable LevelChunk> chunk) {
        this.stored = new GasStore(volume);
        this.links = links;
        this.chunk = chunk;
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
        return links.get();
    }

    @Override
    public @Nullable LevelChunk savedIn() {
        return chunk.get();
    }

    /**
     * The port's network, built now if it has none.
     *
     * @return the network, valid
     */
    public PipeNetwork network() {
        if (network == null || !network.usable()) {
            network = null;
            PipeNetwork.build(this, PipeNode::connectedNodes);
        }
        return network;
    }

    /**
     * The port's turn in its network's atmos tick, rebuilding the network first if it has dissolved,
     * as a {@link PipeNodeEntity} takes its own. A network may hold only ports, with no pipe to run
     * it, and among pipes a port may be the first member to step, so whichever does runs the burst
     * check and the reaction for them all (CONT-100, CONT-108). Its device calls this before each
     * atmos tick of its own. Two ports can meet across a chunk border with no pipe between them, so a
     * port first rejoins the node on its face, if the two came back {@linkplain #apart() apart}.
     *
     * @param level the level
     */
    public void beforeAtmosTick(ServerLevel level) {
        network();
        if (apart()) {
            dissolve();
            dissolveLinks();
        }
        PipeNodeEntity.atmosTurn(level, network(), this);
    }

    /** The port's share of its network's gas, or its own gas if it is in no network. */
    public GasParcel share() {
        return network != null && network.valid()
                ? network.shareOf(this)
                : new GasParcel(stored.moles(), stored.temperature());
    }

    /** Ends the port's network, if it has one: each member keeps its share by volume (CONT-003). */
    public void dissolve() {
        if (network != null) {
            network.dissolve();
        }
    }

    /** Dissolves the networks of the nodes connected to the port, so they are rebuilt with or without it. */
    public void dissolveLinks() {
        for (PipeNode n : connectedNodes()) {
            PipeNetwork linked = n.currentNetwork();
            if (linked != null) {
                linked.dissolve();
            }
        }
    }

    /**
     * Takes the port out of its network for good, as when its device is removed: the network
     * dissolves, and the port's share is returned rather than kept (CONT-009).
     *
     * @return its share of the gas
     */
    public GasParcel detach() {
        GasParcel share = network != null && network.valid() ? network.detach(this) : stored.drain();
        network = null;
        return share;
    }

    /** Writes the port's share, as a node's is saved. */
    public CompoundTag save() {
        return GasStore.save(share());
    }

    /** Reads what {@link #save} wrote. */
    public void load(CompoundTag tag) {
        stored.set(GasStore.parcel(tag));
    }
}
