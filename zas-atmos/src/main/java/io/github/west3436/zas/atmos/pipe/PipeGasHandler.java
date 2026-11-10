package io.github.west3436.zas.atmos.pipe;

import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.atmos.device.LoadedBlocks;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pump.PumpFlow;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A pipe part's network as a gas handler (API-021), which is what lets another mod's pipe or machine
 * join ZAS³'s: the store it fills and drains is the network's own, so gas put in on one face is in
 * the whole network at once.
 *
 * <p><b>Where.</b> A part offers it on a face where it would join another mod's gas block ({@link
 * GasHandlers#connects}): a pipe on every face, a cap on the face it points at, a connector port on
 * its bottom, a device on each pipe face. A canister and radiator pipe join only ZAS³'s own parts, so
 * they offer none. A shut valve offers it, as its pipes keep their arms, and holds and moves nothing
 * until it opens. Only on a server, and only for a face: none for no face in particular.
 *
 * <p><b>Between ZAS³'s own parts</b> it is never used: they join as pipe does and share one network,
 * so {@link GasHandlers} skips a neighbour that is a pipe part, and a part offers none toward one.
 *
 * <p><b>Limit.</b> A fill stops where it would bring the network to {@link #FILL_LIMIT_KPA}, the
 * highest pressure a pump of ZAS³'s own can reach, so another mod's pump gets a pipe no nearer to
 * bursting than ours do (CONT-011, CONT-108). A drain is limited only by what the network holds.
 *
 * <p>The handler reads its part anew on every call, so it stays right when a valve shuts or the
 * network is rebuilt, and holds nothing once its part has gone. Server thread only.
 */
public final class PipeGasHandler implements IGasHandler {
    /** The pressure a fill stops at, in kPa: a pump's highest target. */
    public static final double FILL_LIMIT_KPA = PumpFlow.MAX_TARGET;

    private final BlockEntity entity;
    private final PipeHost host;
    private final Direction face;

    private PipeGasHandler(BlockEntity entity, PipeHost host, Direction face) {
        this.entity = entity;
        this.host = host;
        this.face = face;
    }

    /**
     * The capability's provider for every pipe part.
     *
     * @param level the level
     * @param pos the part's position
     * @param state its state
     * @param entity its block entity
     * @param face the face asked about, or null for none in particular
     * @return its handler on that face, or null if it offers none there
     */
    @Nullable
    public static IGasHandler of(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction face) {
        return face != null
                        && level instanceof ServerLevel server
                        && entity instanceof PipeHost host
                        && !entity.isRemoved()
                        && offers(server, pos, state, face)
                ? new PipeGasHandler(entity, host, face)
                : null;
    }

    /**
     * Whether a part offers the capability on a face: where it offers a pipe connection to a block
     * that isn't a pipe part, which is where it would join one that provides the capability itself.
     * The neighbour is read without loading its chunk ({@link LoadedBlocks}).
     */
    private static boolean offers(ServerLevel level, BlockPos pos, BlockState self, Direction face) {
        if (!(self.getBlock() instanceof PipeConnector block)) {
            return false;
        }
        BlockState neighbour = LoadedBlocks.state(level, pos.relative(face));
        return neighbour != null
                && !(neighbour.getBlock() instanceof PipeConnector)
                && block.pipeConnects(self, face, neighbour);
    }

    /** The network's store behind the face now, or null if no gas passes there: a shut valve, a part gone. */
    @Nullable
    private GasStore store() {
        if (entity.isRemoved() || !(entity.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        ServerThreads.require(level, "A pipe's gas handler");
        BlockState self = entity.getBlockState();
        if (!offers(level, entity.getBlockPos(), self, face)
                || !((PipeConnector) self.getBlock()).carriesGas(self, face)) {
            return null;
        }
        PipeNode node = host.nodeOn(face);
        return node == null ? null : node.network().gas();
    }

    /** The network's gas and its whole volume; nothing, in a pipe's volume, where no gas passes. */
    @Override
    public GasMixture contents() {
        GasStore store = store();
        return store == null ? new GasMixture(Map.of(), 0, PipeVolumes.PIPE) : store.mixture();
    }

    @Override
    public GasParcel fill(GasParcel gas, Action action) {
        GasStore store = store();
        if (store == null || gas.isEmpty()) {
            return GasParcel.EMPTY;
        }
        double part = fits(store, gas);
        if (!(part > 0)) {
            return GasParcel.EMPTY;
        }
        GasParcel taken = part >= 1 ? gas : scaled(gas, part);
        if (action == Action.EXECUTE) {
            store.add(taken);
        }
        return taken;
    }

    /**
     * The part of a parcel, from 0 to 1, that brings a store to the limit once it has mixed in,
     * conserving thermal energy as the store does. None of a parcel at 0 K, which no gas in play is.
     */
    static double fits(GasStore store, GasParcel gas) {
        double moles = gas.totalMoles();
        PumpFlow.Side in = new PumpFlow.Side(moles, GasProperties.heatCapacity(gas.moles()), gas.temperature(), 1);
        return Math.min(1, PumpFlow.molesToTarget(FILL_LIMIT_KPA, in, PumpFlow.Side.of(store)) / moles);
    }

    private static GasParcel scaled(GasParcel gas, double part) {
        TreeMap<ResourceLocation, Double> moles = new TreeMap<>();
        gas.moles().forEach((id, n) -> moles.put(id, n * part));
        return new GasParcel(moles, gas.temperature());
    }

    @Override
    public GasParcel drain(Map<ResourceLocation, Double> moles, Action action) {
        GasStore store = store();
        if (store == null) {
            return GasParcel.EMPTY;
        }
        if (action == Action.EXECUTE) {
            return store.take(moles);
        }
        TreeMap<ResourceLocation, Double> would = new TreeMap<>();
        moles.forEach((id, n) -> {
            double gives = Math.min(store.moles(id), Math.max(0, n));
            if (gives > 0) {
                would.put(id, gives);
            }
        });
        return new GasParcel(would, store.temperature());
    }
}
