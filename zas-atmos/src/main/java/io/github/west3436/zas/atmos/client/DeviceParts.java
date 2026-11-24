package io.github.west3436.zas.atmos.client;

import io.github.west3436.ss13.api.label.Labels;
import io.github.west3436.ss13.client.tgui.Ui;
import io.github.west3436.ss13.client.tgui.Ui.Face;
import io.github.west3436.ss13.device.RedstoneControl;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import java.util.Optional;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * What the device windows share. They hold to labels, values and graphics: a status is a dot and a
 * few words, a bar stands in for a sentence, and a state that's fine says nothing.
 *
 * <p>The parts every device window has, such as the switch and the redstone row, are SS13
 * Framework's ({@link io.github.west3436.ss13.client.tgui.DeviceParts}); this calls them with ZAS³'s
 * text and menu data, and adds the gas parts.
 */
final class DeviceParts {
    /** The column of labels in a window's rows. */
    static final int LABEL_WIDTH = io.github.west3436.ss13.client.tgui.DeviceParts.LABEL_WIDTH;

    /** What a stepper's buttons add, whatever unit its value shows in (CLIENT-006). */
    static final String KPA = "kPa";

    /** What a stepper's buttons add, whatever unit its value shows in (CLIENT-006). */
    static final String KELVIN = "K";

    /** A rate in moles a second, as a stepper's unit. */
    static final String MOL_PER_SECOND = "mol/s";

    static final String[] BIG_STEPS = io.github.west3436.ss13.client.tgui.DeviceParts.BIG_STEPS;
    static final String[] SMALL_STEPS = io.github.west3436.ss13.client.tgui.DeviceParts.SMALL_STEPS;

    private static final int LOW_CHARGE_PERCENT = 15;
    private static final int LOCK_ROOM = 8;

    private DeviceParts() {}

    /** A device window's text. */
    static Component text(String key, Object... args) {
        return Component.translatable("gui.zas_atmos.device." + key, args);
    }

    /**
     * Where a block is, as a player reads it: where the world shows it, which for a block on a Sable
     * sub-level is on the ship, not in its plot (API-068).
     */
    static BlockPos shown(BlockPos p) {
        Level level = Minecraft.getInstance().level;
        return level == null ? p : Relocations.shownAt(level, p);
    }

    /** A block's coordinates, as {@link #shown} places it. */
    static Component position(BlockPos p) {
        BlockPos at = shown(p);
        return Component.literal(at.getX() + ", " + at.getY() + ", " + at.getZ());
    }

    /** A row's label, by its key under {@code gui.zas_atmos.device}. */
    static void label(Ui ui, String key, int x, int y, int height) {
        io.github.west3436.ss13.client.tgui.DeviceParts.label(ui, text(key), x, y, height);
    }

    /** A status: a dot in its colour, then its word. */
    static void status(Ui ui, Component word, int colour, int x, int y, int height) {
        io.github.west3436.ss13.client.tgui.DeviceParts.status(ui, word, colour, x, y, height);
    }

    /**
     * The heat a vent's heater or a scrubber's air conditioner moved at its last step, ending at {@code
     * right}: an arrow up for heating or down for cooling, and the power (CONT-166, CONT-167). Nothing
     * when it moved none.
     */
    static void heat(Ui ui, int right, int y, int height, int watts, boolean heating) {
        if (watts <= 0) {
            return;
        }
        Component shown = text(heating ? "heating" : "cooling", String.format(java.util.Locale.ROOT, "%,d", watts));
        ui.lineRight(shown, right, y, height, heating ? Ui.ORANGE : Ui.BLUE, Face.BODY);
    }

    /** The switch, ending at {@code right}. */
    static void onOff(Ui ui, int right, int y, boolean on, Runnable toggle) {
        io.github.west3436.ss13.client.tgui.DeviceParts.onOff(ui, right, y, on, toggle);
    }

    /** How wide {@link #onOff} draws, as wide as its "Off": the framework's text. */
    static int onOffWidth(Ui ui) {
        return ui.buttonWidth(Component.translatable("gui.ss13_framework.device.off"));
    }

    /**
     * The redstone setting's row (CONT-170): its label, a button for each mode, and, unless it is
     * ignored, a dot lit while the device gets a signal.
     *
     * @param data the menu's {@link RedstoneControl#data()}
     * @param press presses a button by its id
     */
    static void redstone(Ui ui, int x, int y, int w, int data, IntConsumer press) {
        io.github.west3436.ss13.client.tgui.DeviceParts.redstone(
                ui,
                x,
                y,
                w,
                RedstoneControl.mode(data).ordinal(),
                RedstoneControl.signal(data),
                i -> press.accept(RedstoneControl.FIRST_BUTTON + i));
    }

    /** What a device's state row says while redstone holds it. */
    static Component held() {
        return io.github.west3436.ss13.client.tgui.DeviceParts.held();
    }

    /** A battery's or a buffer's charge. With no capacity, an empty bar and a dash. */
    static void charge(Ui ui, int x, int y, int w, int h, int energy, int capacity) {
        if (capacity <= 0) {
            ui.bar(x, y, w, h, 0, Ui.GOOD, Ui.DIM, Component.literal("—"));
            return;
        }
        boolean low = energy * 100L < capacity * (long) LOW_CHARGE_PERCENT;
        ui.bar(
                x,
                y,
                w,
                h,
                (double) energy / capacity,
                low ? Ui.BAD_FILL : Ui.GOOD,
                low ? Ui.BAD_EDGE : Ui.GOOD_EDGE,
                text("fe", BatteryItem.fe(energy), BatteryItem.fe(capacity)));
    }

    /**
     * Which controller a device follows: by its label, if a player gave it one and it is loaded here,
     * cut to fit the notice's one line, or by where it is.
     */
    private static Component controllerAt(Ui ui, BlockPos p, int room) {
        Level level = Minecraft.getInstance().level;
        String label = level == null ? "" : Labels.of(level, p);
        if (label.isEmpty()) {
            BlockPos at = shown(p);
            return text("controller_at", at.getX(), at.getY(), at.getZ());
        }
        float before = ui.width(text("controller_named", ""), Face.BOLD);
        return text("controller_named", ui.fit(Component.literal(label), Face.BOLD, room - before));
    }

    /** How tall {@link #notices} draws. */
    static int noticesHeight(boolean powered, boolean controlled) {
        return ((powered ? 0 : 1) + (controlled ? 1 : 0)) * (Ui.NOTICE_HEIGHT + Ui.PAD);
    }

    /**
     * The notices of a device a controller can run: no power, and whose orders it is following.
     *
     * @return where what's below them starts
     */
    static int notices(Ui ui, int x, int y, int w, boolean powered, boolean controlled, Optional<BlockPos> controller) {
        if (!powered) {
            y += ui.notice(x, y, w, Ui.NOTICE, Ui.BLACK, text("no_power")) + Ui.PAD;
        }
        if (controlled) {
            int room = w - 2 * Ui.PAD - LOCK_ROOM;
            Component who = controller.map(p -> controllerAt(ui, p, room)).orElseGet(() -> text("controller"));
            int h = ui.notice(x, y, w, Ui.NOTICE_INFO, Ui.WHITE, who, LOCK_ROOM);
            ui.lock(x + Ui.PAD, y + 2, Ui.WHITE);
            y += h + Ui.PAD;
        }
        return y;
    }
}
