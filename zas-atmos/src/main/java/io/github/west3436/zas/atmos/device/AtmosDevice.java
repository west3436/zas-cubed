package io.github.west3436.zas.atmos.device;

import io.github.west3436.ss13.device.DeviceBlockEntity;
import io.github.west3436.ss13.device.DeviceTicker;
import io.github.west3436.ss13.device.DeviceTicks;
import io.github.west3436.ss13.device.LoadedBlocks;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The base of every ZAS³ block entity that steps once per atmos tick (D-31, CONT-006): devices, and
 * pipe segments that leak through an open end. It is SS13 Framework's {@link DeviceBlockEntity},
 * which keeps its place in the schedule, its save format, its label and how hard it reads as
 * working, with the gas parts on top.
 *
 * <p>While it is loaded on a server, the block entity is a member of its level's {@link
 * io.github.west3436.ss13.device.DeviceTicker} schedule, which calls {@link #atmosTick} once per
 * atmos tick on the server thread, spread across game ticks. It joins when it loads or is placed and
 * leaves when it is removed or its chunk unloads, so a device in an unloaded chunk never steps; a
 * powered part joins and leaves its level's {@link ZoneIndex} with it. The schedule steps it only
 * while its chunk ticks block entities, as vanilla's block entities tick ({@link #ticking()});
 * otherwise it waits as it is.
 *
 * <p>Everything a device does to zones goes through the public API (D-50, CONT-071): queries with
 * {@link io.github.west3436.zas.api.atmosphere.Atmospheres} and changes with {@link
 * io.github.west3436.zas.api.mutation.GasMutations}, through a {@link ZonePort} per port so that each
 * port has at most one mutation outstanding (CONT-072). A device that draws power implements {@link
 * PoweredDevice}.
 *
 * <p>A device a player can label names {@link io.github.west3436.ss13.api.label.Labelled}, and the
 * base keeps its label.
 */
public abstract class AtmosDevice extends DeviceBlockEntity {
    protected AtmosDevice(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * The ZAS³ devices loaded in a level, in the order they joined its schedule: {@link
     * DeviceTicker#loaded}'s, without other mods' devices. A firelock and a central controller look
     * here for the area air controllers; an area air controller finds its parts through the {@link
     * ZoneIndex} (architecture §11.3).
     *
     * @param level the level
     * @return a new list; empty if the level has none
     */
    public static List<AtmosDevice> loaded(ServerLevel level) {
        List<AtmosDevice> devices = new ArrayList<>();
        for (DeviceBlockEntity device : DeviceTicker.loaded(level)) {
            if (device instanceof AtmosDevice atmos) {
                devices.add(atmos);
            }
        }
        return devices;
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
        reportActivity(moles);
    }

    /**
     * One atmos tick of work, {@link DeviceTicks#SECONDS} seconds. Called on the server thread,
     * once per atmos tick, only while the block entity is loaded and {@link #readyToStep()}.
     *
     * @param level the level, a server level
     */
    protected abstract void atmosTick(ServerLevel level);

    /**
     * Runs before each {@link #atmosTick}, for what every device of a kind does whatever its own
     * step is: a network node's share of its network's atmos tick (CONT-100). The default does
     * nothing. It may remove the block entity, and {@link #atmosTick} is then skipped.
     *
     * @param level the level, a server level
     */
    protected void beforeAtmosTick(ServerLevel level) {}

    /** Has the {@link ZoneIndex} ask a part's zone again, since it steps, then runs {@link #beforeAtmosTick}. */
    @Override
    protected final void beforeDeviceTick(ServerLevel level) {
        ZoneIndex.mark(level, this);
        beforeAtmosTick(level);
    }

    @Override
    protected final void deviceTick(ServerLevel level) {
        atmosTick(level);
    }

    /**
     * A part waits with the device that last powered it, if that is loaded and waiting (architecture
     * §11.2), as the provider powers nothing while it waits. Asking loads nothing.
     */
    @Override
    protected final boolean waitsForAnother() {
        return this instanceof PoweredDevice part
                && part.power().source() instanceof AtmosDevice provider
                && provider != this
                && !provider.isRemoved()
                && DeviceTicker.isScheduled(provider)
                && provider.level instanceof ServerLevel there
                && !LoadedBlocks.ticking(there, provider.worldPosition);
    }

    /** A part that waits keeps its power ({@link DevicePower#hold}), so the wait never reads as a power cut. */
    @Override
    protected final void whilePassedOver() {
        if (this instanceof PoweredDevice part) {
            part.power().hold();
        }
    }

    /** A block changed in place, as when it is turned, may have moved the device's air point. */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (level instanceof ServerLevel server) {
            ZoneIndex.mark(server, this);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) {
            ZoneIndex.join(server, this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel server) {
            ZoneIndex.leave(server, this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level instanceof ServerLevel server) {
            ZoneIndex.leave(server, this);
        }
    }
}
