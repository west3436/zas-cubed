package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.SourceId;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * Gas entered or left the simulation through the open air (CORE-058): a mutation added gas at a
 * point open to the dimension's ambient, which took it (CORE-053), or removed gas there, which the
 * ambient supplied (CORE-057).
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the server thread, when the atmos tick that applied
 * the mutation is applied (API-032), before the mutation's future completes. It is not fired for
 * work that is discarded and redone, and not when nothing moved. Not cancellable: the gas has
 * already moved.
 */
@ServerThread
public final class OpenAirGasEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final boolean added;
    private final GasParcel gas;
    private final SourceId source;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param level the level
     * @param pos the block position of the mutation
     * @param added true for gas added to the open air, false for gas removed from it
     * @param gas the gas that went to, or came from, the ambient
     * @param source who made the mutation
     */
    public OpenAirGasEvent(ServerLevel level, BlockPos pos, boolean added, GasParcel gas, SourceId source) {
        this.level = Objects.requireNonNull(level, "level");
        this.pos = pos.immutable();
        this.added = added;
        this.gas = Objects.requireNonNull(gas, "gas");
        this.source = Objects.requireNonNull(source, "source");
    }

    /**
     * The level.
     *
     * <p>Thread: server thread.
     *
     * @return the level the gas moved in
     */
    public ServerLevel level() {
        return level;
    }

    /**
     * The block position of the mutation.
     *
     * <p>Thread: server thread.
     *
     * @return an immutable position
     */
    public BlockPos pos() {
        return pos;
    }

    /**
     * Whether gas was added to the open air.
     *
     * <p>Thread: server thread.
     *
     * @return true for an addition, false for a removal
     */
    public boolean added() {
        return added;
    }

    /**
     * The gas that went to the ambient, for an addition, or came from it, for a removal.
     *
     * <p>Thread: server thread.
     *
     * @return the amounts and temperature
     */
    public GasParcel gas() {
        return gas;
    }

    /**
     * Who made the mutation.
     *
     * <p>Thread: server thread.
     *
     * @return the source given to the mutation
     */
    public SourceId source() {
        return source;
    }
}
