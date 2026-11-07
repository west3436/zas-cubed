package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.client.DisplayUnits;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.controller.ControllerSync;
import io.github.west3436.zas.atmos.controller.Thresholds;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The area air controller's window (CONT-040, CONT-041, CONT-047, CONT-049, CONT-110): its status,
 * power, room and mode above four tabs. Air shows each reading and its rating, Mode the modes and
 * presets, Thresholds the preset's editor, and Parts what it covers and where each came from.
 *
 * <p>A danger shows an icon and a word as well as a colour (CLIENT-014). Readings follow the player's
 * display units (CLIENT-006), while a threshold's steps stay in kPa and K, which is what the server
 * applies. The window is at most 240 tall, the least a screen has at any Auto GUI scale.
 */
public class ControllerScreen extends TguiScreen<ControllerMenu> {
    private static final int WIDTH = 280;
    private static final int HEIGHT = 236;
    private static final int LABEL_WIDTH = 30;
    private static final int STEPPER_HEIGHT = 16;
    private static final int MODE_ROW = 11;
    private static final int AIR_ROW = 11;

    private static final String KEY = "gui.zas_atmos.controller.";
    private static final String SAFE_ICON = "✔";
    private static final String WARNING_ICON = "⚠";
    private static final String DANGER_ICON = "☠";
    private static final String POWER_ICON = "⚡";

    private static final String[] BOUNDS = {"min2", "min1", "max1", "max2"};
    private static final int[] BOUND_COLOURS = {Ui.BAD, Ui.AVERAGE, Ui.AVERAGE, Ui.BAD};
    private static final String[] STEP_LABELS = {"−10", "−1", "−.1", "+.1", "+1", "+10"};

    private enum Tab {
        AIR,
        MODE,
        THRESHOLDS,
        PARTS
    }

    private Tab tab = Tab.AIR;
    private int partsScroll;
    private int partsMaxScroll;

    public ControllerScreen(ControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected int eyeColour() {
        ControllerSync s = menu.state();
        return s != null && status(s) == ControllerStatus.UNPOWERED ? Ui.AVERAGE : Ui.GOOD;
    }

    private static ControllerStatus status(ControllerSync s) {
        return ControllerStatus.values()[s.status()];
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    private static Component icon(String icon, Component word) {
        return Component.literal(icon + " ").append(word);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ControllerSync s = menu.state();
        if (s == null) {
            return;
        }
        int bottom = y + h;
        y = notices(ui, s, x, y, w);
        y = header(ui, s, x, y, w) + Ui.PAD;
        List<Component> tabs = List.of(
                text("tab.air"),
                text("tab.mode"),
                text("tab.thresholds"),
                text("tab.parts", s.parts().size()));
        y += ui.tabs(x, y, w, tabs, tab.ordinal(), i -> tab = Tab.values()[i]) + Ui.PAD;
        int left = bottom - y;
        switch (tab) {
            case AIR -> air(ui, s, x, y, w, left);
            case MODE -> modes(ui, s, x, y, w);
            case THRESHOLDS -> thresholds(ui, s, x, y, w, left);
            case PARTS -> parts(ui, s, x, y, w, left);
        }
    }

    // ---- Above the tabs ---------------------------------------------------------------------

    private int notices(Ui ui, ControllerSync s, int x, int y, int w) {
        return switch (status(s)) {
            case UNPOWERED -> y + ui.notice(x, y, w, Ui.NOTICE, Ui.BLACK, text("notice.unpowered")) + Ui.PAD;
            case DANGER2 -> {
                String cause = s.fire() ? "notice.fire" : s.breach() ? "notice.breach" : "notice.danger";
                yield y + ui.notice(x, y, w, Ui.NOTICE_DANGER, Ui.WHITE, text(cause)) + Ui.PAD;
            }
            default -> y;
        };
    }

    private int header(Ui ui, ControllerSync s, int x, int y, int w) {
        int rows = 4;
        ui.section(x, y, w, rows * Ui.ROW + 4, null);
        int labelX = x + Ui.PAD;
        int valueX = labelX + LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - LABEL_WIDTH;
        y += 2;

        ui.line(text("label.status"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
        status(ui, status(s), valueX, y);
        y += Ui.ROW;

        ui.line(text("label.power"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
        double charge = s.capacity() > 0 ? (double) s.energy() / s.capacity() : 0;
        boolean low = charge < 0.15;
        ui.bar(
                valueX,
                y + 1,
                valueW,
                Ui.ROW - 2,
                charge,
                low ? Ui.BAD_FILL : Ui.GOOD,
                low ? Ui.BAD_EDGE : Ui.GOOD_EDGE,
                text("power", formatted(s.energy()), formatted(s.capacity())));
        y += Ui.ROW;

        ui.line(text("label.room"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
        room(ui, s.pin(), valueX, y);
        y += Ui.ROW;

        ui.line(text("label.mode"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
        Component preset = text("preset." + s.preset());
        Component mode = text("mode." + ControllerMode.values()[s.mode()].key());
        ui.line(text(s.edited() ? "summary_edited" : "summary", mode, preset), valueX, y, Ui.ROW, Ui.WHITE, Face.BODY);
        return y + Ui.ROW + 2;
    }

    private static String formatted(int energy) {
        return String.format(Locale.ROOT, "%,d", energy);
    }

    private void status(Ui ui, ControllerStatus status, int x, int y) {
        Component word = text("status." + status.getSerializedName());
        switch (status) {
            case UNPOWERED -> ui.line(word, x, y, Ui.ROW, Ui.DIM, Face.BODY);
            case SAFE -> ui.line(icon(SAFE_ICON, word), x, y, Ui.ROW, Ui.GOOD, Face.BODY);
            case DANGER1 -> ui.line(ui.alert(WARNING_ICON, word), x, y, Ui.ROW, Ui.AVERAGE, Face.BOLD);
            case DANGER2 -> {
                Component pill = ui.alert(DANGER_ICON, word);
                ui.rounded(x, y + 1, (int) Math.ceil(ui.width(pill, Face.BOLD)) + 5, Ui.ROW - 2, Ui.NOTICE_DANGER);
                ui.line(pill, x + 2.5f, y, Ui.ROW, Ui.WHITE, Face.BOLD);
            }
        }
    }

    private void room(Ui ui, String pin, int x, int y) {
        switch (pin) {
            case "ZONE" -> ui.line(icon(SAFE_ICON, text("room.zone")), x, y, Ui.ROW, Ui.GOOD, Face.BODY);
            case "OPEN" -> ui.line(ui.alert(WARNING_ICON, text("room.open")), x, y, Ui.ROW, Ui.AVERAGE, Face.BODY);
            case "UNRESOLVED" -> ui.line(text("room.unresolved"), x, y, Ui.ROW, Ui.DIM, Face.BODY);
            default -> ui.line(text("room.none"), x, y, Ui.ROW, Ui.DIM, Face.BODY);
        }
    }

    // ---- Air --------------------------------------------------------------------------------

    private void air(Ui ui, ControllerSync s, int x, int y, int w, int h) {
        ui.section(x, y, w, h, null);
        double[] values = s.values();
        boolean live = status(s) != ControllerStatus.UNPOWERED;
        int inner = x + Ui.PAD;
        int innerW = w - 2 * Ui.PAD;
        composition(ui, s, inner, y + Ui.PAD, innerW);
        int rowY = y + Ui.PAD + 7;
        int iconWidth = 12;
        for (int i = 0; i < s.labels().size(); i++) {
            if (i % 2 == 1) {
                ui.stripe(x + 1, rowY, w - 2, AIR_ROW);
            }
            float nameX = inner;
            if (i >= 2) {
                ui.dot(inner, rowY + 4, colour(s.labels().get(i)));
                nameX += 7;
            }
            ui.line(label(s.labels().get(i)), nameX, rowY, AIR_ROW, Ui.WHITE, Face.BODY);
            int rating = live && i < s.ratings().length ? s.ratings()[i] : 0;
            int colour = !live ? Ui.DIM : rating == 2 ? Ui.BAD : rating == 1 ? Ui.AVERAGE : Ui.WHITE;
            Component value = Component.literal(i < values.length ? reading(i, values[i]) : "—");
            ui.lineRight(value, inner + innerW - iconWidth, rowY, AIR_ROW, colour, rating > 0 ? Face.BOLD : Face.BODY);
            if (live && i < s.ratings().length) {
                String glyph = rating == 2 ? DANGER_ICON : rating == 1 ? WARNING_ICON : SAFE_ICON;
                int glyphColour = rating == 2 ? Ui.BAD : rating == 1 ? Ui.AVERAGE : Ui.GOOD;
                ui.lineCentred(
                        Component.literal(glyph),
                        inner + innerW - iconWidth + 2,
                        iconWidth - 2,
                        rowY,
                        AIR_ROW,
                        glyphColour,
                        rating > 0 ? Face.ICON : Face.BODY);
                ui.tooltip(
                        inner + innerW - iconWidth,
                        rowY,
                        iconWidth,
                        AIR_ROW,
                        text("status." + (rating == 2 ? "danger2" : rating == 1 ? "danger1" : "safe")));
            }
            rowY += AIR_ROW;
        }
    }

    /** One bar of each gas's share. Readings 0 and 1 aren't gases. */
    private void composition(Ui ui, ControllerSync s, int x, int y, int w) {
        int h = 5;
        ui.track(x, y, w, h);
        double[] values = s.values();
        double total = 0;
        for (int i = 2; i < values.length; i++) {
            total += values[i];
        }
        if (total <= 0) {
            return;
        }
        float at = x;
        for (int i = 2; i < values.length && i < s.labels().size(); i++) {
            float part = (float) (w * values[i] / total);
            if (part > 0) {
                ui.box(at, y, part, h, colour(s.labels().get(i)));
                at += part;
            }
        }
    }

    private static int colour(String reading) {
        return GasParts.colour(reading);
    }

    private static String reading(int i, double value) {
        return i == 1 ? Readout.temperature(value) : Readout.pressure(value);
    }

    private static Component label(String reading) {
        return switch (reading) {
            case "pressure", "temperature", "others" -> text("reading." + reading);
            default -> {
                ResourceLocation id = ResourceLocation.tryParse(reading);
                yield id == null
                        ? Component.literal(reading)
                        : Component.translatable("gas." + id.getNamespace() + "." + id.getPath());
            }
        };
    }

    // ---- Mode -------------------------------------------------------------------------------

    private void modes(Ui ui, ControllerSync s, int x, int y, int w) {
        ControllerMode[] modes = ControllerMode.values();
        int modesHeight = 14 + modes.length * MODE_ROW + 3;
        int rowY = ui.section(x, y, w, modesHeight, text("heading.mode")) + 1;
        int buttonWidth = 36;
        for (ControllerMode mode : modes) {
            boolean on = mode.ordinal() == s.mode();
            Tone tone = on ? Tone.SELECTED : mode == ControllerMode.PANIC ? Tone.CAUTION : Tone.DEFAULT;
            ui.button(
                    x + Ui.PAD,
                    rowY,
                    buttonWidth,
                    MODE_ROW - 1,
                    text("mode." + mode.key()),
                    tone,
                    () -> press(mode.ordinal()));
            ui.line(
                    mode == ControllerMode.CYCLE
                            ? text("mode_help.cycle", Readout.pressure(ControllerMode.CYCLE_DONE_KPA))
                            : text("mode_help." + mode.key()),
                    x + Ui.PAD + buttonWidth + 4,
                    rowY,
                    MODE_ROW - 1,
                    on ? Ui.WHITE : Ui.LABEL,
                    Face.BODY);
            rowY += MODE_ROW;
        }

        y += modesHeight + Ui.PAD;
        rowY = ui.section(x, y, w, 14 + MODE_ROW + 3, text("heading.preset")) + 2;
        int presetX = x + Ui.PAD;
        for (int i = 0; i < ControllerPreset.SHIPPED.size(); i++) {
            String name = ControllerPreset.SHIPPED.get(i).name();
            int id = ControllerMenu.LOAD_PRESET + i;
            Component label = text("preset." + name);
            int width = ui.buttonWidth(label);
            ui.button(
                    presetX,
                    rowY,
                    width,
                    MODE_ROW - 1,
                    label,
                    name.equals(s.preset()) ? Tone.SELECTED : Tone.DEFAULT,
                    () -> press(id));
            presetX += width + 1;
        }
    }

    // ---- Thresholds -------------------------------------------------------------------------

    private void thresholds(Ui ui, ControllerSync s, int x, int y, int w, int h) {
        int tableHeight = h - STEPPER_HEIGHT - Ui.PAD;
        ui.section(x, y, w, tableHeight, null);
        int inner = x + Ui.PAD;
        int nameWidth = 84;
        int cellWidth = (w - 2 * Ui.PAD - nameWidth) / 4;
        int rowY = y + 1;
        for (int b = 0; b < 4; b++) {
            ui.lineCentred(
                    text("bound." + BOUNDS[b]),
                    inner + nameWidth + b * cellWidth,
                    cellWidth,
                    rowY,
                    Ui.ROW,
                    BOUND_COLOURS[b],
                    Face.BOLD);
        }
        rowY += Ui.ROW;
        for (int i = 0; i < s.labels().size(); i++) {
            if (i % 2 == 1) {
                ui.stripe(x + 1, rowY, w - 2, Ui.ROW);
            }
            Component name = label(s.labels().get(i));
            ui.line(name, inner, rowY, Ui.ROW, Ui.WHITE, Face.BODY);
            ui.line(
                    Component.literal(i == 1 ? DisplayUnits.temperatureSymbol() : DisplayUnits.pressureSymbol()),
                    inner + ui.width(name, Face.BODY) + 3,
                    rowY,
                    Ui.ROW,
                    Ui.LABEL,
                    Face.BODY);
            for (int b = 0; b < 4; b++) {
                double bound = s.bounds()[i * 4 + b];
                boolean off = bound == Thresholds.OFF;
                Component value = off ? text("bound_off") : Component.literal(boundNumber(i, bound));
                int id = ControllerMenu.SELECT_BOUND + i * 4 + b;
                ui.cell(
                        inner + nameWidth + b * cellWidth + 1,
                        rowY + 1,
                        cellWidth - 2,
                        Ui.ROW - 1,
                        value,
                        off,
                        i == s.reading() && b == s.bound(),
                        () -> press(id));
            }
            rowY += Ui.ROW;
        }
        stepper(ui, s, x, y + tableHeight + Ui.PAD, w);
    }

    private static String boundNumber(int reading, double bound) {
        return reading == 1 ? Readout.temperatureNumber(bound) : Readout.pressureNumber(bound);
    }

    private void stepper(Ui ui, ControllerSync s, int x, int y, int w) {
        ui.section(x, y, w, STEPPER_HEIGHT, null);
        int reading = s.reading();
        int b = s.bound();
        double bound = s.bounds()[reading * 4 + b];
        boolean off = bound == Thresholds.OFF;

        Component name = label(s.labels().get(reading));
        int textX = x + Ui.PAD;
        ui.line(name, textX, y, STEPPER_HEIGHT, Ui.WHITE, Face.BOLD);
        Component value = off
                ? text("bound_off")
                : Component.literal(reading == 1 ? Readout.temperature(bound) : Readout.pressure(bound));
        ui.line(
                text("selected_bound", text("bound." + BOUNDS[b]), value),
                textX + ui.width(name, Face.BOLD) + 3,
                y,
                STEPPER_HEIGHT,
                Ui.WHITE,
                Face.BODY);

        int buttonHeight = STEPPER_HEIGHT - 4;
        int buttonY = y + 2;
        Component toggle = text(off ? "bound_turn_on" : "bound_turn_off");
        int toggleWidth = ui.buttonWidth(toggle);
        int buttonX = x + w - Ui.PAD - toggleWidth;
        ui.button(
                buttonX,
                buttonY,
                toggleWidth,
                buttonHeight,
                toggle,
                Tone.DEFAULT,
                () -> press(ControllerMenu.TOGGLE_BOUND));
        int stepWidth = 15;
        String serverUnit = reading == 1 ? "K" : "kPa";
        for (int i = STEP_LABELS.length - 1; i >= 0; i--) {
            buttonX -= stepWidth + 1;
            int id = ControllerMenu.ADJUST + i;
            ui.button(
                    buttonX,
                    buttonY,
                    stepWidth,
                    buttonHeight,
                    ui.stepLabel(STEP_LABELS[i]),
                    Tone.DEFAULT,
                    off ? null : () -> press(id));
            ui.tooltip(buttonX, buttonY, stepWidth, buttonHeight, Component.literal(STEP_LABELS[i] + " " + serverUnit));
        }
    }

    // ---- Parts ------------------------------------------------------------------------------

    private void parts(Ui ui, ControllerSync s, int x, int y, int w, int h) {
        ui.section(x, y, w, h, null);
        int inner = x + Ui.PAD;
        List<AreaAirControllerBlockEntity.Part> parts = s.parts();
        partsMaxScroll = 0;
        if (parts.isEmpty()) {
            ui.wrapped(text("parts.empty"), inner, y + Ui.PAD, w - 2 * Ui.PAD, Ui.ROW, Ui.LABEL, Face.BODY);
            return;
        }
        int scrollbar = 3;
        int positionX = inner + 72;
        int fromX = inner + 134;
        int powerRight = x + w - Ui.PAD - scrollbar;
        int rowY = y + 1;
        ui.line(text("parts.part"), inner, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        ui.line(text("parts.position"), positionX, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        ui.line(text("parts.from"), fromX, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        ui.lineRight(text("parts.power"), powerRight, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        rowY += Ui.ROW;

        int viewHeight = h - Ui.ROW - 2;
        int fullHeight = parts.size() * Ui.ROW;
        partsMaxScroll = Math.max(0, fullHeight - viewHeight);
        partsScroll = Math.min(partsScroll, partsMaxScroll);
        ui.clip(x, rowY, w, viewHeight);
        for (int i = 0; i < parts.size(); i++) {
            int top = rowY + i * Ui.ROW - partsScroll;
            if (top + Ui.ROW <= rowY || top >= rowY + viewHeight) {
                continue;
            }
            if (i % 2 == 1) {
                ui.stripe(x + 1, top, w - 2, Ui.ROW);
            }
            part(ui, parts.get(i), inner, positionX, fromX, powerRight, top);
        }
        ui.endClip();
        ui.scrollbar(x + w - 3, rowY, viewHeight, partsScroll, partsMaxScroll, fullHeight);
    }

    private void part(
            Ui ui, AreaAirControllerBlockEntity.Part p, int nameX, int positionX, int fromX, int powerRight, int y) {
        ui.line(
                Component.translatable(
                        "block." + p.block().getNamespace() + "." + p.block().getPath()),
                nameX,
                y,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
        BlockPos pos = p.pos();
        ui.line(
                Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()),
                positionX,
                y,
                Ui.ROW,
                Ui.LABEL,
                Face.BODY);
        String from = p.inZone() && p.linked() ? "zone_and_link" : p.inZone() ? "zone" : "link";
        ui.line(text("from." + from), fromX, y, Ui.ROW, Ui.WHITE, Face.BODY);
        if (p.powered()) {
            ui.lineRight(icon(POWER_ICON, text("part_on")), powerRight, y, Ui.ROW, Ui.GOOD, Face.BODY);
        } else {
            ui.lineRight(text("part_off"), powerRight, y, Ui.ROW, Ui.DIM, Face.BODY);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.PARTS && scrollY != 0) {
            partsScroll = Math.max(0, Math.min(partsMaxScroll, partsScroll - (int) Math.signum(scrollY) * Ui.ROW));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
