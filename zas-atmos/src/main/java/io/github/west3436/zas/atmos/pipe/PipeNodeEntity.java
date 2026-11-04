package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
 *       own.
 * </ul>
 *
 * <p>The network is built lazily, on the server, the first time {@link #network()} is called.
 */
public abstract class PipeNodeEntity extends AtmosDevice implements PipeNode {
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
        if (network == null || !network.valid()) {
            network = null;
            PipeNetwork.build(this, n -> ((PipeNodeEntity) n).connectedNodes());
        }
        return network;
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

    /** The node's share of its network's gas, or its own gas if it is in no network. */
    public GasParcel share() {
        return network != null && network.valid()
                ? network.shareOf(this)
                : new GasParcel(stored.moles(), stored.temperature());
    }

    /**
     * The loaded nodes this one shares gas with, in direction order. A neighbour in an unloaded chunk
     * isn't reached, and that chunk keeps its share saved.
     */
    public List<PipeNodeEntity> connectedNodes() {
        List<PipeNodeEntity> out = new ArrayList<>(6);
        if (level == null) {
            return out;
        }
        BlockState self = level.getBlockState(worldPosition);
        for (Direction d : Direction.values()) {
            BlockPos next = worldPosition.relative(d);
            if (!level.isLoaded(next)) {
                continue;
            }
            if (PipeConnector.joined(self, d, level.getBlockState(next))
                    && level.getBlockEntity(next) instanceof PipeNodeEntity n
                    && !n.isRemoved()) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * Puts gas that arrived from a zone into the network, or, if the node has been removed since,
     * releases it at the node's position.
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

    /** Dissolves the networks of connected neighbours, so they are rebuilt with or without this node. */
    private void dissolveNeighbours() {
        for (PipeNodeEntity n : connectedNodes()) {
            if (n.network != null) {
                n.network.dissolve();
            }
        }
    }

    /**
     * Dissolves this node's network and its neighbours', so the next ones built follow its current
     * connections: call it after they change. Members keep their shares by volume (CONT-003).
     */
    protected void reconnect() {
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
