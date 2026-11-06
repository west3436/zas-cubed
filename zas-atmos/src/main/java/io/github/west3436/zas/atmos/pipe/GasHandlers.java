package io.github.west3436.zas.atmos.pipe;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Where pipes and devices meet blocks that provide the gas-handler capability (API-025, API-026).
 * Every capability lookup and every call into a handler is here, each guarded: a handler that throws
 * is skipped for the atmos tick and logged at most once a minute (NFR-013).
 *
 * <p>A handler is a neighbour, but not a network member: it adds no volume and has no share. A node
 * exchanges gas with it in its step, on the server thread, by moving gas between the handler and its
 * network's store. No zone is involved, so the conservation ledger has nothing to explain (CORE-073).
 *
 * <p>The client never looks: providers are other mods' code, which runs only on the server thread
 * here, and the server's block state brings a pipe's arms to the client.
 */
public final class GasHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The sharing ratio per atmos tick: all the way to an equal concentration, as a canister on a
     * connector port would be, since a handler is the same sort of store.
     */
    private static final double RATIO = 1;

    private GasHandlers() {}

    /**
     * A node's side of a transfer.
     *
     * @param gas the store it draws on, its network's, built if it has dissolved
     * @param deliver puts gas into that store, or releases it if the node has been removed
     * @param removed whether the node has been removed, which a handler's call may have done
     */
    public record Buffer(Supplier<GasStore> gas, Consumer<GasParcel> deliver, BooleanSupplier removed) {}

    /**
     * Whether a block connects to a gas handler on a face: it offers a connection there, and the
     * neighbour is no pipe part but provides the capability toward it. A shut valve stays connected,
     * and {@link #exchange} moves no gas through it. Asks only on a server level: false on the client
     * and in world generation.
     *
     * @param level the level
     * @param next the neighbour's position, loaded or not
     * @param self the block's state
     * @param face the face, toward the neighbour
     * @param neighbour the neighbour's state
     * @return true if it connects
     */
    public static boolean connects(
            LevelAccessor level, BlockPos next, BlockState self, Direction face, BlockState neighbour) {
        return level instanceof ServerLevel server
                && !(neighbour.getBlock() instanceof PipeConnector)
                && self.getBlock() instanceof PipeConnector block
                && block.pipeConnects(self, face, neighbour)
                && server.isLoaded(next)
                && provides(server, next, neighbour, face);
    }

    /**
     * Whether a neighbour provides the capability toward a face. A lookup that throws counts as yes,
     * since only a block with a provider can run one, and a pipe's arm mustn't go with the fault, which
     * would leave an open end for the gas to escape from (NFR-013).
     */
    private static boolean provides(Level level, BlockPos next, BlockState neighbour, Direction face) {
        try {
            return lookup(level, next, neighbour, face) != null;
        } catch (Exception | LinkageError e) {
            failed(neighbour, e);
            return true;
        }
    }

    /**
     * {@link #connects(LevelAccessor, BlockPos, BlockState, Direction, BlockState)} for a block whose
     * neighbour isn't known yet. It loads nothing to find out.
     *
     * @param level the level
     * @param pos the block's position
     * @param self the block's state
     * @param face the face, toward the neighbour
     * @return true if it connects
     */
    public static boolean connects(Level level, BlockPos pos, BlockState self, Direction face) {
        BlockPos next = pos.relative(face);
        return level.isLoaded(next) && connects(level, next, self, face, level.getBlockState(next));
    }

    /**
     * The faces on which a block connects to a gas handler now.
     *
     * @param level the level
     * @param pos the block's position
     * @param self the block's state
     * @return a mask for {@link #has}
     */
    static int faces(Level level, BlockPos pos, BlockState self) {
        int faces = 0;
        for (Direction d : Direction.values()) {
            if (connects(level, pos, self, d)) {
                faces |= 1 << d.ordinal();
            }
        }
        return faces;
    }

    /** Whether a mask from {@link #faces} holds a face. */
    static boolean has(int faces, Direction face) {
        return (faces & (1 << face.ordinal())) != 0;
    }

    /**
     * One atmos tick's transfer with the handler on a face, in both directions (API-026): each gas
     * moves toward an equal concentration on both sides, as far as the handler lets it. What the
     * handler offers is taken out of the store first, so nothing is lost whatever the handler does
     * from inside the call: refuses it, takes part of it, throws, or removes the node. Then the node
     * delivers what's left, which releases it if the node has gone (CONT-009).
     *
     * @param level the level
     * @param pos the block's position
     * @param self the block's state
     * @param face the face, toward the handler
     * @param buffer the node's side of it
     * @throws IllegalStateException if called off the server thread
     */
    public static void exchange(ServerLevel level, BlockPos pos, BlockState self, Direction face, Buffer buffer) {
        ServerThreads.require(level, "A gas handler transfer");
        BlockPos next = pos.relative(face);
        if (!(self.getBlock() instanceof PipeConnector block)
                || !block.carriesGas(self, face)
                || !level.isLoaded(next)) {
            return;
        }
        BlockState neighbour = level.getBlockState(next);
        IGasHandler handler = handler(level, next, neighbour, face);
        if (handler == null) {
            return;
        }
        GasStore gas = buffer.gas().get();
        PipeBlockEntity.Flow flow;
        try {
            flow = PipeBlockEntity.openEndFlow(gas.moles(), gas.volume(), handler.contents(), false, RATIO);
        } catch (Exception | LinkageError e) {
            failed(neighbour, e);
            return;
        }
        if (!flow.out().isEmpty()) {
            GasParcel offered = gas.take(flow.out());
            GasParcel took = null;
            try {
                took = handler.fill(offered, IGasHandler.Action.EXECUTE);
            } catch (Exception | LinkageError e) {
                failed(neighbour, e);
                return;
            } finally {
                buffer.deliver().accept(left(offered, took));
            }
        }
        if (!flow.in().isEmpty() && !buffer.removed().getAsBoolean()) {
            GasParcel drained;
            try {
                drained = Objects.requireNonNull(handler.drain(flow.in(), IGasHandler.Action.EXECUTE), "its drain");
            } catch (Exception | LinkageError e) {
                failed(neighbour, e);
                return;
            }
            buffer.deliver().accept(drained);
        }
    }

    /** The handler on a face, or null if it has none or the lookup throws (NFR-013). */
    @Nullable
    private static IGasHandler handler(Level level, BlockPos next, BlockState neighbour, Direction face) {
        try {
            return lookup(level, next, neighbour, face);
        } catch (Exception | LinkageError e) {
            failed(neighbour, e);
            return null;
        }
    }

    @Nullable
    private static IGasHandler lookup(Level level, BlockPos next, BlockState neighbour, Direction face) {
        return level.getCapability(IGasHandler.BLOCK, next, neighbour, null, face.getOpposite());
    }

    /** Logs a handler's fault, at most once a minute for each kind of block (NFR-013). */
    private static void failed(BlockState neighbour, Throwable error) {
        String source = "Gas handler of " + BuiltInRegistries.BLOCK.getKey(neighbour.getBlock());
        if (ThirdPartyFaults.shouldLog(source)) {
            LOGGER.error("{} threw; skipped for the atmos tick (logged at most once a minute)", source, error);
        }
    }

    /**
     * What's left of what was offered: all of it if the handler took nothing, or never answered. It
     * can't take more of a gas than it was offered, whatever it says.
     */
    private static GasParcel left(GasParcel offered, @Nullable GasParcel took) {
        if (took == null) {
            return offered;
        }
        TreeMap<ResourceLocation, Double> left = new TreeMap<>();
        offered.moles().forEach((gas, n) -> left.put(gas, Math.max(0, n - took.moles(gas))));
        return new GasParcel(left, offered.temperature());
    }
}
