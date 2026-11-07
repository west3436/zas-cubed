package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/**
 * What the device windows share. They hold to labels, values and graphics: a status is a dot and a
 * few words, a bar stands in for a sentence, and a state that's fine says nothing.
 */
final class DeviceParts {
    /** The column of labels in a window's rows. */
    static final int LABEL_WIDTH = 34;

    /** What a stepper's buttons add, whatever unit its value shows in (CLIENT-006). */
    static final String KPA = "kPa";

    /** What a stepper's buttons add, whatever unit its value shows in (CLIENT-006). */
    static final String KELVIN = "K";

    static final String[] BIG_STEPS = {"−100", "−10", "+10", "+100"};
    static final String[] SMALL_STEPS = {"−10", "−1", "+1", "+10"};

    private static final int LOW_CHARGE_PERCENT = 15;
    private static final int LOCK_ROOM = 8;

    private DeviceParts() {}

    /** A device window's text. */
    static Component text(String key, Object... args) {
        return Component.translatable("gui.zas_atmos.device." + key, args);
    }

    /** A row's label. */
    static void label(Ui ui, String key, int x, int y, int height) {
        ui.line(text(key), x, y, height, Ui.LABEL, Face.BODY);
    }

    /** A status: a dot in its colour, then its word. */
    static void status(Ui ui, Component word, int colour, int x, int y, int height) {
        ui.line(Component.literal("● ").append(word), x, y, height, colour, Face.BODY);
    }

    /** The switch, ending at {@code right}. */
    static void onOff(Ui ui, int right, int y, boolean on, Runnable toggle) {
        Component label = text(on ? "on" : "off");
        int w = ui.buttonWidth(text("off"));
        ui.button(right - w, y, w, Ui.BUTTON_HEIGHT, label, on ? Tone.SELECTED : Tone.DEFAULT, toggle);
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
            Component who = controller
                    .map(p -> text("controller_at", p.getX(), p.getY(), p.getZ()))
                    .orElseGet(() -> text("controller"));
            int h = ui.notice(x, y, w, Ui.NOTICE_INFO, Ui.WHITE, who, LOCK_ROOM);
            ui.lock(x + Ui.PAD, y + 2, Ui.WHITE);
            y += h + Ui.PAD;
        }
        return y;
    }
}
