package io.github.west3436.zas.atmos.device;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The base of every block entity that steps once per atmos tick (D-31, CONT-006): devices, and
 * pipe segments that leak through an open end.
 *
 * <p>While it is loaded on a server, the block entity is a member of its level's {@link
 * DeviceTicker} schedule, which calls {@link #atmosTick} once per atmos tick on the server thread,
 * spread across game ticks. It joins when it loads or is placed and leaves when it is removed or its
 * chunk unloads, so a device in an unloaded chunk never steps.
 *
 * <p>Everything a device does to zones goes through the public API (D-50, CONT-071): queries with
 * {@link io.github.west3436.zas.api.atmosphere.Atmospheres} and changes with {@link
 * io.github.west3436.zas.api.mutation.GasMutations}, through a {@link ZonePort} per port so that each
 * port has at most one mutation outstanding (CONT-072). A device that draws power implements {@link
 * io.github.west3436.zas.atmos.power.PoweredDevice}.
 */
public abstract class AtmosDevice extends BlockEntity implements DeviceSchedule.Member {
    protected AtmosDevice(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * One atmos tick of work, {@link AtmosTicks#SECONDS} seconds. Called on the server thread,
     * once per atmos tick, only while the block entity is loaded and {@link #readyToStep()}.
     *
     * @param level the level, a server level
     */
    protected abstract void atmosTick(ServerLevel level);

    /**
     * Whether the device can step now. The default is true. A device returns false while a port
     * has a mutation outstanding, and is then asked again next game tick (CONT-072).
     */
    @Override
    public boolean readyToStep() {
        return true;
    }

    /**
     * Called by the schedule; runs {@link #atmosTick} if the block entity is still in a server level
     * and its chunk is loaded in full. A chunk next to a loaded one that drops out of full status
     * keeps its block entities in memory without unloading them; a device there waits, since reading
     * its own block would load the chunk again.
     */
    @Override
    public final void step() {
        if (!isRemoved() && level instanceof ServerLevel server && server.isLoaded(worldPosition)) {
            beforeAtmosTick(server);
            if (!isRemoved()) {
                atmosTick(server);
            }
        }
    }

    /**
     * Runs before each {@link #atmosTick}, for what every device of a kind does whatever its own
     * step is: a network node's share of its network's atmos tick (CONT-100). The default does
     * nothing. It may remove the block entity, and {@link #atmosTick} is then skipped.
     *
     * @param level the level, a server level
     */
    protected void beforeAtmosTick(ServerLevel level) {}

    /** The current game tick, as {@link AtmosTicks#now}; only on the server. */
    protected long now() {
        return level instanceof ServerLevel server ? AtmosTicks.now(server) : 0L;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            DeviceTicker.add(server, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            DeviceTicker.remove(server, this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            DeviceTicker.remove(server, this);
        }
    }
}
