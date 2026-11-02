package io.github.west3436.zas.atmos.firelock;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A firelock's work, once per atmos tick, on its lower half (CONT-043, CONT-092, CONT-093,
 * architecture §11.8). It reads the cells on its two sides, in front of the panel and behind it.
 *
 * <ul>
 *   <li><b>The alarm.</b> It is alarmed while any area air controller covering either side is at
 *       danger 2. A controller covers a side if the side's cell is in its zone, or in the zone (or,
 *       unzoned, the cell) of a part linked to it ({@link AreaAirControllerBlockEntity#covers}).
 *   <li><b>Closing</b> (CONT-043). When the alarm starts, an open firelock closes. While it lasts,
 *       a firelock opened by hand or forced open closes again after {@link #RECLOSE_STEPS} s.
 *   <li><b>Reopening</b> (CONT-092). Once the alarm ends, a firelock the alarm closed opens again,
 *       unless it is locked down; it then opens when the lockdown ends. One closed by hand stays
 *       closed.
 *   <li><b>Lockdown</b> (CONT-093). A closed firelock is locked down while {@link Lockdown#of}
 *       says so, and its block state shows it.
 * </ul>
 *
 * A side that reads as unresolved changes nothing that step.
 */
public class FirelockBlockEntity extends AtmosDevice {
    /** Atmos ticks an opening lasts while the alarm holds, 5 s. */
    public static final int RECLOSE_STEPS = 5;

    private boolean alarm;
    private boolean closedByAlarm;
    private int reclose = -1;
    private Optional<Lockdown> lockdown = Optional.empty();

    public FirelockBlockEntity(BlockPos pos, BlockState state) {
        super(AtmosBlockEntities.FIRELOCK.get(), pos, state);
    }

    /** The cell in front of the panel, on the side it seals when closed. */
    public BlockPos front() {
        return getBlockPos().relative(getBlockState().getValue(FirelockBlock.FACING));
    }

    /** The cell behind the panel, whose room the firelock's own cells join. */
    public BlockPos back() {
        return getBlockPos()
                .relative(getBlockState().getValue(FirelockBlock.FACING).getOpposite());
    }

    @Override
    protected void atmosTick(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof FirelockBlock)) {
            return;
        }
        BlockPos front = front();
        BlockPos back = back();
        AtmosphereSample a = Atmospheres.at(level, front);
        AtmosphereSample b = Atmospheres.at(level, back);
        if (a.state() == AtmosphereState.UNRESOLVED || b.state() == AtmosphereState.UNRESOLVED) {
            return;
        }
        boolean was = alarm;
        alarm = alarmed(level, a, front) || alarmed(level, b, back);
        boolean open = state.getValue(FirelockBlock.OPEN);
        if (alarm && open) {
            if (!was || reclose == 0) {
                close(level);
                open = false;
            } else if (reclose < 0) {
                reclose = RECLOSE_STEPS - 1; // opened while the alarm was off, or before a reload
            } else {
                reclose--;
            }
        }
        lockdown = open ? Optional.empty() : Lockdown.of(a.pressure(), a.temperature(), b.pressure(), b.temperature());
        if (!alarm) {
            reclose = -1;
            if (!open && closedByAlarm && lockdown.isEmpty()) {
                FirelockBlock.setOpen(level, getBlockPos(), true, null);
                open = true;
            }
        }
        FirelockBlock.setLocked(level, getBlockPos(), lockdown.isPresent());
        setChanged();
    }

    private void close(ServerLevel level) {
        FirelockBlock.setOpen(level, getBlockPos(), false, null);
        closedByAlarm = true;
    }

    /** Whether any loaded controller at danger 2 covers this side. */
    private static boolean alarmed(ServerLevel level, AtmosphereSample side, BlockPos cell) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AreaAirControllerBlockEntity c && c.alarming() && c.covers(side, cell)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called whenever the firelock opens or closes, by anyone. An opening while the alarm holds
     * lasts {@link #RECLOSE_STEPS} s. The firelock counts as closed by the alarm only when the
     * alarm closed it, which it records after this call.
     */
    void changed(boolean open) {
        closedByAlarm = false;
        reclose = open && alarm ? RECLOSE_STEPS - 1 : -1;
        if (open) {
            lockdown = Optional.empty();
        }
        setChanged();
    }

    // ---- For the block, the GUI-less checks and tests -----------------------------------

    /** Why it is locked down, if it is (CONT-093); empty while open. */
    public Optional<Lockdown> lockdown() {
        return lockdown;
    }

    /** Whether a controller covering either side was at danger 2 at its last step. */
    public boolean alarm() {
        return alarm;
    }

    /** Whether the alarm closed it, so it reopens when the alarm ends (CONT-092). */
    public boolean closedByAlarm() {
        return closedByAlarm;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Alarm", alarm);
        tag.putBoolean("ClosedByAlarm", closedByAlarm);
        tag.putInt("Reclose", reclose);
        lockdown.ifPresent(l -> {
            tag.putString("Lockdown", l.reason().name());
            tag.putDouble("LockdownValue", l.value());
        });
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        alarm = tag.getBoolean("Alarm");
        closedByAlarm = tag.getBoolean("ClosedByAlarm");
        reclose = tag.contains("Reclose") ? tag.getInt("Reclose") : -1;
        lockdown = Optional.empty();
        if (tag.contains("Lockdown")) {
            try {
                lockdown = Optional.of(new Lockdown(
                        Lockdown.Reason.valueOf(tag.getString("Lockdown")), tag.getDouble("LockdownValue")));
            } catch (IllegalArgumentException ignored) {
                // an unknown reason: the next step decides again
            }
        }
    }
}
