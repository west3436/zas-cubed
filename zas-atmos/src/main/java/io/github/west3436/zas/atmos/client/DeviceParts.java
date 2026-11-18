package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.label.Labels;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

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

    /** The redstone row's dot, lit while the device gets a signal, as redstone dust is. */
    private static final int SIGNAL_ON = 0xFFFF2020;

    private static final int SIGNAL_OFF = 0xFF4A1010;

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

    /**
     * The redstone setting's row (CONT-170): its label, a button for each mode, and, unless it is
     * ignored, a dot lit while the device gets a signal.
     *
     * @param data the menu's {@link RedstoneControl#data()}
     * @param press presses a button by its id
     */
    static void redstone(Ui ui, int x, int y, int w, int data, IntConsumer press) {
        int labelX = x + Ui.PAD;
        label(ui, "redstone", labelX, y, Ui.BUTTON_HEIGHT);
        List<Component> labels = new ArrayList<>();
        for (RedstoneControl.Mode mode : RedstoneControl.Mode.values()) {
            labels.add(mode.label());
        }
        RedstoneControl.Mode mode = RedstoneControl.mode(data);
        int end = ui.choice(
                labelX + LABEL_WIDTH,
                y,
                labels,
                mode.ordinal(),
                -1,
                i -> press.accept(RedstoneControl.FIRST_BUTTON + i));
        if (mode != RedstoneControl.Mode.IGNORED) {
            int size = 4;
            ui.fill(
                    end + Ui.PAD + 1,
                    y + (Ui.BUTTON_HEIGHT - size) / 2,
                    size,
                    size,
                    RedstoneControl.signal(data) ? SIGNAL_ON : SIGNAL_OFF);
        }
    }

    /** What a device's state row says while redstone holds it. */
    static Component held() {
        return text("redstone.held");
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
