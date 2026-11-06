package io.github.west3436.zas.atmos.device;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

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
    /** How hard it reads as working (DATA-017): made by its first report, so most pay nothing. */
    private @Nullable DeviceActivity activity;

    private @Nullable LevelChunk chunk;

    protected AtmosDevice(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * The chunk that saves this block entity, on a server. It is kept from the first time it is asked
     * for, because the level stops handing out a chunk that has left full status, though it still
     * saves the block entity when it unloads. Asking loads nothing.
     *
     * @return the chunk, or null if it isn't in a server level or hasn't been in a chunk in full status
     */
    public @Nullable LevelChunk savedIn() {
        if (chunk == null && level instanceof ServerLevel server) {
            chunk = server.getChunkSource()
                    .getChunkNow(
                            SectionPos.blockToSectionCoord(worldPosition.getX()),
                            SectionPos.blockToSectionCoord(worldPosition.getZ()));
        }
        return chunk;
    }

    /**
     * Says how many moles of gas this step moves, so nearby players can hear and see how hard the
     * device is working (DATA-017, DATA-018). A device that moves gas calls this from {@link
     * #atmosTick}, with what it has decided to move, and that is all it has to do. A step with no
     * call counts as moving none, and the level then falls after a short hold.
     *
     * @param moles the moles it moves in this step; more than one call adds up
     */
    protected final void reportMoved(double moles) {
        if (moles > 0) {
            if (activity == null) {
                activity = new DeviceActivity();
            }
            activity.moved(moles);
        }
    }

    /**
     * Says a device that moves no gas, such as a heater, is running in this step, from {@link
     * #atmosTick}. It reads as {@linkplain DeviceActivity#WORKING working} for as long as it says so.
     */
    protected final void reportRunning() {
        reportMoved(DeviceActivity.RUNNING_MOLES);
    }

    /**
     * Says the device is off, has no power, or moves too little to show, from {@link #atmosTick}: it
     * reads as idle after this step, with no hold, so its sound stops when it does. A device switched
     * between steps follows this with {@link #finishActivity}, so it reads as idle at once.
     */
    protected final void reportStopped() {
        if (activity != null) {
            activity.stopNow();
        }
    }

    /**
     * Ends what has been reported so far and passes the level on, if this device has ever reported any.
     * The base does this at the end of each step. A device switched between steps does it too, so that
     * its level changes when its switch does.
     */
    protected final void finishActivity(ServerLevel server) {
        if (activity != null) {
            DeviceActivitySync.report(server, worldPosition, activity.finishStep(), AtmosTicks.now(server));
        }
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
            if (!isRemoved()) {
                finishActivity(server);
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
            forgetActivity(server);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            DeviceTicker.remove(server, this);
            forgetActivity(server);
        }
    }

    private void forgetActivity(ServerLevel server) {
        if (activity != null) {
            DeviceActivitySync.remove(server, worldPosition);
        }
    }
}
