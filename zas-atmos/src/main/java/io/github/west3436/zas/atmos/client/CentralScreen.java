package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.client.tgui.Ui.Tone;
import io.github.west3436.zas.atmos.controller.CentralMenu;
import io.github.west3436.zas.atmos.controller.CentralSync;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.controller.ZonePriority;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The central atmospherics controller's window (CONT-117, CONT-121, CONT-123, CONT-124): its status,
 * power and range above the area air controllers it has claimed. Each row shows a controller's
 * position, its zone's pressure and temperature, its danger, whether it has power and its zone
 * priority, with a button that opens the controller's own window. A controller out of contact keeps
 * its last readings, dimmed, and has no button.
 *
 * <p>Like the area air controller's window, a danger shows an icon as well as a colour, with its word
 * in a tooltip (CLIENT-014), and readings follow the player's display units (CLIENT-006).
 */
public class CentralScreen extends TguiScreen<CentralMenu> {
    private static final int WIDTH = 300;
    private static final int HEIGHT = 220;
    private static final int LABEL_WIDTH = 30;
    private static final int RANGE_WIDTH = 46;
    private static final int SCROLLBAR = 3;

    private static final String KEY = "gui.zas_atmos.central.";
    private static final String CONTROLLER_KEY = "gui.zas_atmos.controller.";
    private static final String SAFE_ICON = "✔";
    private static final String WARNING_ICON = "⚠";
    private static final String DANGER_ICON = "☠";
    private static final String POWER_ICON = "⚡";

    private int scroll;
    private int maxScroll;

    public CentralScreen(CentralMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected int eyeColour() {
        CentralSync s = menu.state();
        return s != null && status(s) == ControllerStatus.UNPOWERED ? Ui.AVERAGE : Ui.GOOD;
    }

    private static ControllerStatus status(CentralSync s) {
        return ControllerStatus.values()[s.status()];
    }

    private static Component text(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    private static Component controllerText(String key, Object... args) {
        return Component.translatable(CONTROLLER_KEY + key, args);
    }

    private static Component icon(String icon, Component word) {
        return Component.literal(icon + " ").append(word);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        CentralSync s = menu.state();
        if (s == null) {
            return;
        }
        int bottom = y + h;
        if (status(s) == ControllerStatus.UNPOWERED) {
            y += ui.notice(x, y, w, Ui.NOTICE, Ui.BLACK, controllerText("notice.unpowered")) + Ui.PAD;
        }
        y = header(ui, s, x, y, w) + Ui.PAD;
        list(ui, s, x, y, w, bottom - y);
    }

    // ---- Above the list ---------------------------------------------------------------------

    private int header(Ui ui, CentralSync s, int x, int y, int w) {
        int rows = 3;
        ui.section(x, y, w, rows * Ui.ROW + 4, null);
        int labelX = x + Ui.PAD;
        int valueX = labelX + LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - LABEL_WIDTH;
        y += 2;

        ui.line(controllerText("label.status"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
        status(ui, status(s), valueX, y);
        y += Ui.ROW;

        ui.line(controllerText("label.power"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
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
                controllerText("power", formatted(s.energy()), formatted(s.capacity())));
        y += Ui.ROW;

        ui.line(text("label.range"), labelX, y, Ui.ROW, Ui.LABEL, Face.BODY);
        range(ui, s, valueX, y);
        return y + Ui.ROW + 2;
    }

    private static String formatted(int energy) {
        return String.format(Locale.ROOT, "%,d", energy);
    }

    private void status(Ui ui, ControllerStatus status, int x, int y) {
        Component word = controllerText("status." + status.getSerializedName());
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

    /** The range between buttons that lower and raise it, each greyed out at its end (CONT-117). */
    private void range(Ui ui, CentralSync s, int x, int y) {
        Component down = ui.stepLabel("−");
        Component up = ui.stepLabel("+");
        int buttonWidth = Ui.BUTTON_HEIGHT + 1;
        ui.button(
                x,
                y,
                buttonWidth,
                Ui.BUTTON_HEIGHT,
                down,
                Tone.DEFAULT,
                s.range() > 1 ? () -> press(CentralMenu.RANGE_DOWN) : null);
        ui.lineCentred(
                text(s.range() == 1 ? "range.one" : "range", s.range()),
                x + buttonWidth,
                RANGE_WIDTH,
                y,
                Ui.ROW,
                Ui.WHITE,
                Face.BOLD);
        ui.button(
                x + buttonWidth + RANGE_WIDTH,
                y,
                buttonWidth,
                Ui.BUTTON_HEIGHT,
                up,
                Tone.DEFAULT,
                s.range() < s.maxRange() ? () -> press(CentralMenu.RANGE_UP) : null);
    }

    // ---- The list ---------------------------------------------------------------------------

    private void list(Ui ui, CentralSync s, int x, int y, int w, int h) {
        ui.section(x, y, w, h, null);
        int inner = x + Ui.PAD;
        List<CentralSync.Row> rows = s.rows();
        maxScroll = 0;
        if (rows.isEmpty()) {
            ui.line(text("empty"), inner, y + Ui.PAD, Ui.ROW, Ui.LABEL, Face.BODY);
            return;
        }
        Columns c = new Columns(inner, x + w - Ui.PAD - SCROLLBAR);
        int rowY = y + 1;
        ui.line(text("heading.position"), c.position, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        ui.lineRight(text("heading.pressure"), c.pressureRight, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        ui.lineRight(text("heading.temperature"), c.temperatureRight, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        ui.line(text("heading.priority"), c.priority, rowY, Ui.ROW, Ui.WHITE, Face.BOLD);
        rowY += Ui.ROW;

        int viewHeight = h - Ui.ROW - 2;
        int fullHeight = rows.size() * Ui.BUTTON_ROW;
        maxScroll = Math.max(0, fullHeight - viewHeight);
        scroll = Math.min(scroll, maxScroll);
        ui.clip(x, rowY, w, viewHeight);
        for (int i = 0; i < rows.size(); i++) {
            int top = rowY + i * Ui.BUTTON_ROW - scroll;
            if (top + Ui.BUTTON_ROW <= rowY || top >= rowY + viewHeight) {
                continue;
            }
            if (i % 2 == 1) {
                ui.stripe(x + 1, top, w - 2, Ui.BUTTON_ROW);
            }
            row(ui, rows.get(i), i, c, top);
        }
        ui.endClip();
        ui.scrollbar(x + w - SCROLLBAR, rowY, viewHeight, scroll, maxScroll, fullHeight);
    }

    /** Where each column of the list starts or, for numbers, ends. */
    private static final class Columns {
        final int position;
        final int pressureRight;
        final int temperatureRight;
        final int danger;
        final int power;
        final int priority;
        final int right;

        Columns(int left, int right) {
            this.position = left;
            this.pressureRight = left + 104;
            this.temperatureRight = pressureRight + 34;
            this.danger = temperatureRight + 4;
            this.power = danger + 12;
            this.priority = power + 14;
            this.right = right;
        }
    }

    private void row(Ui ui, CentralSync.Row r, int index, Columns c, int y) {
        int textY = y + (Ui.BUTTON_ROW - Ui.ROW) / 2;
        boolean contact = r.contact();
        int colour = contact ? Ui.WHITE : Ui.DIM;
        BlockPos p = r.pos();
        Component position = Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ());
        if (r.label().isEmpty()) {
            ui.line(position, c.position, textY, Ui.ROW, contact ? Ui.LABEL : Ui.DIM, Face.BODY);
        } else {
            // Its label in the position's place, which pointing at it shows.
            Component pressure =
                    Component.literal(Double.isFinite(r.pressure()) ? Readout.pressure(r.pressure()) : "—");
            int room = c.pressureRight - c.position - (int) Math.ceil(ui.width(pressure, Face.BODY)) - Ui.PAD;
            Component label = Component.literal(r.label());
            ui.line(ui.fit(label, Face.BODY, room), c.position, textY, Ui.ROW, colour, Face.BODY);
            ui.tooltip(
                    c.position,
                    textY,
                    room,
                    Ui.ROW,
                    Component.empty().append(label).append(" · ").append(position));
        }
        ui.lineRight(
                Component.literal(Double.isFinite(r.pressure()) ? Readout.pressure(r.pressure()) : "—"),
                c.pressureRight,
                textY,
                Ui.ROW,
                colour,
                Face.BODY);
        ui.lineRight(
                Component.literal(Double.isFinite(r.temperature()) ? Readout.temperature(r.temperature()) : "—"),
                c.temperatureRight,
                textY,
                Ui.ROW,
                colour,
                Face.BODY);
        danger(ui, r, c.danger, textY);
        if (r.powered()) {
            ui.lineCentred(
                    Component.literal(POWER_ICON), c.power, 10, textY, Ui.ROW, contact ? Ui.GOOD : Ui.DIM, Face.ICON);
        } else {
            ui.lineCentred(Component.literal("✕"), c.power, 10, textY, Ui.ROW, Ui.DIM, Face.BODY);
            ui.tooltip(c.power, textY, 10, Ui.ROW, controllerText("status.unpowered"));
        }

        List<Component> labels = new ArrayList<>();
        for (ZonePriority priority : ZonePriority.values()) {
            labels.add(text("priority." + priority.key()));
        }
        int n = ZonePriority.values().length;
        ui.choice(c.priority, y, labels, r.priority(), -1, choice -> press(CentralMenu.PRIORITY + index * n + choice));

        if (contact) {
            Component open = text("open");
            int openWidth = ui.buttonWidth(open);
            ui.button(
                    c.right - openWidth,
                    y,
                    openWidth,
                    Ui.BUTTON_HEIGHT,
                    open,
                    Tone.DEFAULT,
                    () -> press(CentralMenu.OPEN + index));
        } else {
            ui.lineRight(text("no_contact"), c.right, textY, Ui.ROW, Ui.DIM, Face.BODY);
        }
    }

    /** The controller's danger as an icon, with its word in a tooltip. Nothing while it has no power. */
    private void danger(Ui ui, CentralSync.Row r, int x, int y) {
        if (!r.powered()) {
            ui.lineCentred(Component.literal("—"), x, 10, y, Ui.ROW, Ui.DIM, Face.BODY);
            return;
        }
        String glyph = r.danger() == 2 ? DANGER_ICON : r.danger() == 1 ? WARNING_ICON : SAFE_ICON;
        int colour = !r.contact() ? Ui.DIM : r.danger() == 2 ? Ui.BAD : r.danger() == 1 ? Ui.AVERAGE : Ui.GOOD;
        ui.lineCentred(Component.literal(glyph), x, 10, y, Ui.ROW, colour, r.danger() > 0 ? Face.ICON : Face.BODY);
        ui.tooltip(
                x,
                y,
                10,
                Ui.ROW,
                controllerText("status." + (r.danger() == 2 ? "danger2" : r.danger() == 1 ? "danger1" : "safe")));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY) * Ui.BUTTON_ROW));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
