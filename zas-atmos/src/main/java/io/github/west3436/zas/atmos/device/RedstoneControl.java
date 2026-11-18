package io.github.west3436.zas.atmos.device;

import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A device's redstone setting (CONT-170 to CONT-173): whether a redstone signal at its block lets it
 * run. {@linkplain Mode#IGNORED Ignored} by default, so a device placed before the setting existed,
 * or never set, runs as it always has. {@linkplain Mode#HIGH High} lets it run only while it gets a
 * signal, {@linkplain Mode#LOW low} only while it gets none.
 *
 * <p><b>It only ever stops a device.</b> The setting is one more condition on a device's running,
 * after its own switch, its mode, a controller's orders and its power: it never switches a device on
 * and never changes a setting, so a controller's orders and redstone meet as an AND (CONT-172). A
 * device held by redstone draws no power, as one switched off draws none.
 *
 * <p><b>When it reads.</b> A device reads its signal at each of its steps, through {@link #read()},
 * and only when its mode heeds one, so a device left at the default reads nothing. A step runs only
 * while the device's chunk ticks, and then the neighbours a signal comes from are loaded. A mode set
 * from a window or a computer reads at once, so the window shows the signal straight away.
 *
 * <p><b>How a device takes it</b> (for a new device): hold one, built with the device, and implement
 * {@link RedstoneControlled}; call {@link #read()} at the start of each step and run only while it
 * returns true, drawing nothing meanwhile; {@link #save} and {@link #load} it with the device; give
 * its menu a data slot of {@link #data()} and pass {@link #button} the ids {@link #isButton} claims;
 * its screen draws the row with {@code DeviceParts.redstone}, and its peripheral gets {@code
 * getRedstoneMode} and {@code setRedstoneMode} from {@code Lua.redstoneMode} and {@code
 * Lua.setRedstoneMode} in the CC: Tweaked adapter (COMPAT-023).
 */
public final class RedstoneControl {
    /** The setting. */
    public enum Mode {
        /** Redstone does nothing: the default. */
        IGNORED,
        /** It runs only while it gets a signal. */
        HIGH,
        /** It runs only while it gets none. */
        LOW;

        private static final Mode[] ALL = values();

        /** Its word in a save and to a computer: its name in lower case. */
        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Its label in a window. */
        public Component label() {
            return Component.translatable("gui.zas_atmos.device.redstone." + word());
        }

        /** The mode of a menu's data or a save's ordinal; anything else is {@link #IGNORED}. */
        public static Mode of(int ordinal) {
            return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : IGNORED;
        }
    }

    /** The key it is saved under; nothing is saved while it is {@link Mode#IGNORED}. */
    public static final String KEY = "Redstone";

    /**
     * A window's button that sets mode {@code FIRST_BUTTON + ordinal}: above every device window's own
     * buttons, and below 128.
     */
    public static final int FIRST_BUTTON = 100;

    /** The last of those buttons. */
    public static final int LAST_BUTTON = FIRST_BUTTON + Mode.ALL.length - 1;

    /** The bit of {@link #data()} that says it got a signal at its last read. */
    private static final int SIGNAL_BIT = 4;

    private final BlockEntity owner;
    private Mode mode = Mode.IGNORED;
    private boolean signal;

    /** @param owner the device: where the signal is read, and what is saved when the mode changes */
    public RedstoneControl(BlockEntity owner) {
        this.owner = owner;
    }

    /** Its mode. */
    public Mode mode() {
        return mode;
    }

    /**
     * Sets its mode, and reads the signal now if the device is in a server level. Server side.
     *
     * @return whether it changed
     */
    public boolean set(Mode next) {
        if (next == mode) {
            return false;
        }
        mode = next;
        owner.setChanged();
        read();
        return true;
    }

    /**
     * Reads the signal at the device's block, if its mode heeds one, and says whether the device may
     * run. Server side; in no server level, or with the device removed, it reads nothing.
     *
     * @return {@link #allows()} after the read
     */
    public boolean read() {
        if (mode == Mode.IGNORED) {
            signal = false;
        } else if (!owner.isRemoved() && owner.getLevel() instanceof ServerLevel level) {
            signal = level.hasNeighborSignal(owner.getBlockPos());
        }
        return allows();
    }

    /** Whether it got a signal at its last read; false while it is ignored. */
    public boolean signal() {
        return signal;
    }

    /** Whether it lets the device run, by its mode and the signal it last read. */
    public boolean allows() {
        return allows(mode, signal);
    }

    private static boolean allows(Mode mode, boolean signal) {
        return switch (mode) {
            case IGNORED -> true;
            case HIGH -> signal;
            case LOW -> !signal;
        };
    }

    /** What a menu syncs: the mode's ordinal, and whether it got a signal. */
    public int data() {
        return mode.ordinal() | (signal ? SIGNAL_BIT : 0);
    }

    /** The mode in a menu's {@link #data()}. */
    public static Mode mode(int data) {
        return Mode.of(data & (SIGNAL_BIT - 1));
    }

    /** Whether a menu's {@link #data()} says it got a signal. */
    public static boolean signal(int data) {
        return (data & SIGNAL_BIT) != 0;
    }

    /** Whether a menu's {@link #data()} says redstone stops the device now. */
    public static boolean holds(int data) {
        return !allows(mode(data), signal(data));
    }

    /** Whether a window's button id is one of these. */
    public static boolean isButton(int id) {
        return id >= FIRST_BUTTON && id <= LAST_BUTTON;
    }

    /**
     * A window's button, on the server: sets the mode it names.
     *
     * @return false for an id that isn't one of these
     */
    public boolean button(int id) {
        if (!isButton(id)) {
            return false;
        }
        set(Mode.of(id - FIRST_BUTTON));
        return true;
    }

    /** Saves its mode, if it isn't the default. */
    public void save(CompoundTag tag) {
        if (mode != Mode.IGNORED) {
            tag.putString(KEY, mode.word());
        }
    }

    /** Loads what {@link #save} wrote: a missing or unknown word is the default. */
    public void load(CompoundTag tag) {
        String word = tag.getString(KEY);
        mode = Mode.IGNORED;
        for (Mode m : Mode.ALL) {
            if (m.word().equals(word)) {
                mode = m;
            }
        }
    }
}
