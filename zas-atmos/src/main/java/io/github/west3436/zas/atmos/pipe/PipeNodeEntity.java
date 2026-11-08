package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.device.ZonePort;
import io.github.west3436.zas.atmos.gas.ContainerReactions;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.particle.JetPlan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The block entity of a pipe network node: a pipe, a cap, or a device with a port buffer
 * (architecture §11.4). It keeps the node's place in its {@link PipeNetwork} and its share of the
 * gas.
 *
 * <ul>
 *   <li><b>Joining.</b> When it loads or is placed, the networks of its connected neighbours
 *       dissolve, so the next network built includes it with its saved share.
 *   <li><b>Leaving.</b> When it is removed, its share of the gas is released into its cell through
 *       the API (CONT-009); the rest stays with the remaining nodes, shared by volume (CONT-003).
 *       When its chunk unloads, the network dissolves and its share stays saved with it.
 *   <li><b>Saving.</b> It saves its share by volume: of its network's gas if it is in one, or its
 *       own. The network tells its chunk when that changes ({@link PipeNetwork}).
 * </ul>
 *
 * <p>The network is built lazily, on the server, the first time {@link #network()} is called.
 */
public abstract class PipeNodeEntity extends AtmosDevice implements PipeNode, PipeHost {
    /** Who removed pipes' gas is released by, for the conservation ledger. */
    public static final SourceId RELEASE = SourceId.of(ZasAtmosMod.MOD_ID, "pipe_release");

    private static final String GAS = "Gas";

    private final GasStore stored;
    private final CellWatch cellWatch = new CellWatch();
    private PipeNetwork network;
    private boolean unloading;

    protected PipeNodeEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, double volume) {
        super(type, pos, state);
        this.stored = new GasStore(volume);
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

    /**
     * The node's network, built now if it has none. Server side only.
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
     * The network's atmos tick, run by the first of its members to step. It bursts if the last atmos
     * tick left it overpressured (CONT-108); otherwise its mixture reacts through the API, with no
     * ignition source (CONT-100). A device's own step comes after this, so it sees the pressure the
     * reaction left, and a canister on the network, rated lower than a pipe, fails before the pipe
     * can burst. Its gas handlers' turn comes next (API-025, API-026), for the same reason. A node on
     * a chunk's edge first rejoins a neighbour past it, if the two came back {@linkplain #apart()
     * apart}.
     */
    @Override
    protected void beforeAtmosTick(ServerLevel level) {
        network();
        if (onChunkEdge() && apart()) {
            reconnect();
        }
        atmosTurn(level, network(), this);
        if (!isRemoved()) {
            exchangeWithHandlers(level);
        }
    }

    /** Whether a neighbour can be in another chunk, which comes and goes apart from this node's. */
    private boolean onChunkEdge() {
        int x = SectionPos.sectionRelative(worldPosition.getX());
        int z = SectionPos.sectionRelative(worldPosition.getZ());
        return x == 0 || x == SectionPos.SECTION_MAX_INDEX || z == 0 || z == SectionPos.SECTION_MAX_INDEX;
    }

    /** A transfer with each gas handler beside it; any of them may remove it. */
    private void exchangeWithHandlers(ServerLevel level) {
        int faces = GasHandlers.faces(level, worldPosition, getBlockState());
        followHandlers(level, faces);
        if (faces == 0) {
            return;
        }
        GasHandlers.Buffer buffer = new GasHandlers.Buffer(() -> network().gas(), this::deliver, this::isRemoved);
        for (Direction d : Direction.values()) {
            if (GasHandlers.has(faces, d) && !isRemoved()) {
                GasHandlers.exchange(level, worldPosition, getBlockState(), d, buffer);
            }
        }
    }

    /**
     * Told each step which faces connect to a gas handler, for a node whose block shows it: a pipe's
     * arms. The default does nothing.
     *
     * @param level the level
     * @param faces a mask for {@link GasHandlers#has}
     */
    protected void followHandlers(ServerLevel level, int faces) {}

    /** A member's turn in its network's atmos tick: the first to step runs it for them all. */
    static void atmosTurn(ServerLevel level, PipeNetwork net, PipeNode node) {
        if (net.startsAtmosTick(node) && !PipeBurst.check(level, net)) {
            ContainerReactions.react(net.gas());
        }
    }

    /** Whether the node is a pipe segment, which can burst (CONT-108). The default is false. */
    protected boolean isSegment() {
        return false;
    }

    /** The watch on the cell the node acts on, reset when it loads. */
    protected CellWatch cellWatch() {
        return cellWatch;
    }

    /**
     * Whether the node keeps its share when it is removed now, instead of releasing it: a canister
     * mined into an item that carries its gas (CONT-017). The default is false.
     */
    protected boolean keepsShareWhenRemoved() {
        return false;
    }

    /**
     * Whether the node destroys its share when it is removed now, instead of releasing it: a
     * canister broken in creative. The default is false.
     */
    protected boolean voidsShareWhenRemoved() {
        return false;
    }

    @Override
    public double highestPressure() {
        return network().gas().pressure();
    }

    /** The node's share of its network's gas, or its own gas if it is in no network. */
    public GasParcel share() {
        return network != null && network.valid()
                ? network.shareOf(this)
                : new GasParcel(stored.moles(), stored.temperature());
    }

    /**
     * The loaded nodes this one shares gas with, in direction order. A neighbour in an unloaded chunk
     * isn't reached, and that chunk keeps its share saved. A node whose own chunk has left full status
     * reaches none, since reading its block would load the chunk again. Nothing is read through the
     * level, so nothing is kept loaded ({@link LoadedBlocks}).
     */
    @Override
    public List<PipeNode> connectedNodes() {
        List<PipeNode> out = new ArrayList<>(6);
        BlockState self = level instanceof ServerLevel server ? LoadedBlocks.state(server, worldPosition) : null;
        if (self == null) {
            return out;
        }
        for (Direction d : Direction.values()) {
            PipeNode n = PipeHost.across(level, worldPosition, self, d);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    /** Every face leads to the one node: this one. */
    @Override
    public PipeNode nodeOn(Direction face) {
        return this;
    }

    /**
     * Puts gas that arrived from a zone or a gas handler into the network, or, if the node has been
     * removed since, releases it at the node's position.
     *
     * @param gas the gas
     */
    protected void deliver(GasParcel gas) {
        if (gas.isEmpty()) {
            return;
        }
        if (!isRemoved()) {
            network().gas().add(gas);
        } else if (level instanceof ServerLevel server) {
            GasMutations.add(server, Vec3.atCenterOf(worldPosition), gas, RELEASE);
        }
    }

    /**
     * Makes an {@linkplain PipeBlockEntity#openEndFlow open end's flow} through a port, as one change
     * if it goes both ways. What the zone doesn't take comes back, and what leaves it arrives with the
     * result. A flow of nothing does nothing. It reports the net flow as what the node moved (DATA-017):
     * gases only passing each other at one pressure push nothing through, so they make no sound.
     *
     * @param level the level
     * @param port the port, which isn't busy
     * @param point where the gas goes
     * @param flow what moves
     */
    protected void exchange(ServerLevel level, ZonePort port, Vec3 point, PipeBlockEntity.Flow flow) {
        // Its loop goes with its jet, so a flow that has tailed off is silent as well as unseen.
        double net = flow.net();
        if (JetPlan.showsFlow(net / AtmosTicks.SECONDS)) {
            reportMoved(Math.abs(net));
        } else {
            reportStopped();
        }
        if (flow.out().isEmpty() && flow.in().isEmpty()) {
            return;
        }
        GasParcel out = network().gas().take(flow.out());
        if (flow.in().isEmpty()) {
            port.add(level, point, out, r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())));
        } else if (out.isEmpty()) {
            port.remove(level, point, flow.in(), r -> deliver(r.moved()));
        } else {
            port.exchange(
                    level,
                    point,
                    out,
                    flow.in(),
                    r -> deliver(new GasParcel(ZonePort.untaken(out, r), out.temperature())),
                    r -> deliver(r.moved()));
        }
    }

    /** Dissolves the networks of connected neighbours, so they are rebuilt with or without this node. */
    private void dissolveNeighbours() {
        for (PipeNode n : connectedNodes()) {
            PipeNetwork linked = n.currentNetwork();
            if (linked != null) {
                linked.dissolve();
            }
        }
    }

    /**
     * Dissolves this node's network and its neighbours', so the next ones built follow its current
     * connections: call it after they change. Members keep their shares by volume (CONT-003).
     */
    @Override
    public void reconnect() {
        if (network != null) {
            network.dissolve();
        }
        dissolveNeighbours();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) {
            unloading = false;
            cellWatch.reset();
            reconnect();
        }
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        if (network != null) {
            network.dissolve();
        }
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        if (!unloading && level instanceof ServerLevel server) {
            GasParcel share = network != null && network.valid() ? network.detach(this) : stored.drain();
            network = null;
            if (keepsShareWhenRemoved()) {
                stored.set(share); // carried away, as by a mined canister's item
            } else if (!share.isEmpty() && !voidsShareWhenRemoved()) { // a voided share is destroyed
                GasMutations.add(server, Vec3.atCenterOf(worldPosition), share, RELEASE);
            }
        }
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(GAS, GasStore.save(share()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(GAS)) {
            stored.set(GasStore.parcel(tag.getCompound(GAS)));
        }
    }
}
