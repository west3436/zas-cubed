package io.github.west3436.zas.atmos.firelock;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * A firelock's work, once per atmos tick (CONT-043, CONT-092, CONT-093, architecture §11.8), for
 * the standalone firelock and for one fitted to an airlock (CONT-137). It reads the cells on the
 * door's two sides, in front of its panel and behind it.
 *
 * <ul>
 *   <li><b>The alarm.</b> It is alarmed while any area air controller covering it is at danger 2.
 *       A controller covers it if the cell on either side is in its zone, or if the firelock itself
 *       is linked to it with the multitool ({@link AreaAirControllerBlockEntity#covers}).
 *   <li><b>Closing</b> (CONT-043). When the alarm starts, an open firelock closes. The controller
 *       steps it at once ({@link #alarmRaised}), so it closes in the atmos tick the controller
 *       alarms. While the alarm lasts, a firelock opened by hand or forced open closes again after
 *       {@link #RECLOSE_STEPS} s.
 *   <li><b>Reopening</b> (CONT-092). Once the alarm ends, a firelock the alarm closed opens again,
 *       unless it is locked down; it then opens when the lockdown ends. One closed by hand stays
 *       closed.
 *   <li><b>Lockdown</b> (CONT-093). A closed firelock is locked down while {@link Lockdown#of} says
 *       so, and its block state shows it.
 * </ul>
 *
 * A side that reads as unresolved changes nothing that step.
 */
public final class FirelockLogic {
    /** Atmos ticks an opening lasts while the alarm holds, 5 s. */
    public static final int RECLOSE_STEPS = 5;

    /** What the logic works: a firelock's panel, wherever it stands. Server thread. */
    public interface Door {
        /** Where a controller links it, and where its device is. */
        BlockPos linkPos();

        /** The cell in front of the panel, on the side it seals when closed. */
        BlockPos front();

        /** The cell behind the panel. */
        BlockPos back();

        /** Whether the panel is open. */
        boolean isOpen();

        /** Opens or closes the panel, with no checks; the door calls {@link #changed} when it does. */
        void setOpen(ServerLevel level, boolean open);

        /** Shows the lockdown, or stops showing it. */
        void setLocked(ServerLevel level, boolean locked);
    }

    /** A block entity whose block has a firelock, or may have one: the standalone firelock or an airlock. */
    public interface Holder {
        /** Whether it has a firelock now. */
        boolean hasFirelock();

        /** The firelock's state. */
        FirelockLogic firelock();

        /** What the firelock works. */
        Door firelockDoor();
    }

    private boolean alarm;
    private boolean closedByAlarm;
    private int reclose = -1;
    private Optional<Lockdown> lockdown = Optional.empty();

    /** One atmos tick of the firelock on {@code door}. */
    public void step(ServerLevel level, Door door) {
        AtmosphereSample a = Atmospheres.at(level, door.front());
        AtmosphereSample b = Atmospheres.at(level, door.back());
        if (a.state() == AtmosphereState.UNRESOLVED || b.state() == AtmosphereState.UNRESOLVED) {
            return;
        }
        boolean was = alarm;
        alarm = alarmed(level, door.linkPos(), a, b);
        boolean open = door.isOpen();
        if (alarm && open) {
            if (!was || reclose == 0) {
                door.setOpen(level, false);
                closedByAlarm = true;
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
                door.setOpen(level, true);
                open = true;
            }
        }
        door.setLocked(level, lockdown.isPresent());
    }

    /** Whether any loaded controller at danger 2 covers the firelock linked at {@code at}. */
    private static boolean alarmed(ServerLevel level, BlockPos at, AtmosphereSample front, AtmosphereSample back) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof AreaAirControllerBlockEntity c && c.alarming() && c.covers(at, front, back)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called by a controller whose alarm has just started: each loaded firelock it covers that
     * isn't alarmed yet steps now, so it closes in the same atmos tick rather than at its own step,
     * up to an atmos tick later (CONT-043).
     *
     * @param level the level
     * @param controller the controller now at danger 2
     */
    public static void alarmRaised(ServerLevel level, AreaAirControllerBlockEntity controller) {
        for (AtmosDevice d : DeviceTicker.loaded(level)) {
            if (d instanceof Holder h
                    && h.hasFirelock()
                    && !h.firelock().alarm
                    && !d.isRemoved()
                    && level.isLoaded(d.getBlockPos())) {
                Door door = h.firelockDoor();
                if (controller.covers(
                        door.linkPos(), Atmospheres.at(level, door.front()), Atmospheres.at(level, door.back()))) {
                    d.step();
                }
            }
        }
    }

    /**
     * Called whenever the firelock opens or closes, by anyone. An opening while the alarm holds
     * lasts {@link #RECLOSE_STEPS} s. The firelock counts as closed by the alarm only when the alarm
     * closed it, which {@link #step} records after this call.
     */
    public void changed(boolean open) {
        closedByAlarm = false;
        reclose = open && alarm ? RECLOSE_STEPS - 1 : -1;
        if (open) {
            lockdown = Optional.empty();
        }
    }

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

    /** Forgets everything, as a firelock just fitted or removed. */
    public void reset() {
        alarm = false;
        closedByAlarm = false;
        reclose = -1;
        lockdown = Optional.empty();
    }

    /** Saves the alarm, whether it closed the firelock, the re-close countdown and the lockdown. */
    public void save(CompoundTag tag) {
        tag.putBoolean("Alarm", alarm);
        tag.putBoolean("ClosedByAlarm", closedByAlarm);
        tag.putInt("Reclose", reclose);
        lockdown.ifPresent(l -> {
            tag.putString("Lockdown", l.reason().name());
            tag.putDouble("LockdownValue", l.value());
        });
    }

    /** Loads what {@link #save} wrote. */
    public void load(CompoundTag tag) {
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
