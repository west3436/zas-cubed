package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.filter.FilterMenu;
import io.github.west3436.zas.atmos.filter.FilterOption;
import io.github.west3436.zas.atmos.inline.SidePortMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A filter's window (CONT-013): its strip with the side port, its state and target, and a box per gas
 * or gas tag it can send to the side port. The list scrolls a row at a time when it doesn't all fit.
 */
final class FilterScreen extends InlineScreen<FilterMenu> {
    private static final int SETTINGS = 2 + STATE_ROW + Ui.BUTTON_ROW + 1;

    /** The most rows it shows: what ZAS³ ships fits, and only a datapack's gases scroll. */
    private static final int MOST_ROWS = 6;

    private static final int COLUMNS = 2;

    private final int allRows;
    private final int shownRows;
    private int scrollRows;

    FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SIDE_STRIP + Ui.PAD + SETTINGS + Ui.PAD + listHeight(rows(menu)));
        allRows = rows(menu);
        shownRows = Math.min(allRows, MOST_ROWS);
    }

    private static int rows(FilterMenu menu) {
        return (menu.options().size() + COLUMNS - 1) / COLUMNS;
    }

    private static int listHeight(int rows) {
        return 14 + Math.min(rows, MOST_ROWS) * Ui.ROW + 2;
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        y = noPower(ui, x, y, w);
        sideStrip(ui, menu, x, y, w, DeviceParts.text("input"), DeviceParts.text("filtered"), false);
        y += SIDE_STRIP + Ui.PAD;
        ui.section(x, y, w, SETTINGS, null);
        stateRow(ui, x, y + 2, w);
        targetRow(ui, x, y + 2 + STATE_ROW, w, menu.value(SidePortMenu.DATA_TARGET));
        y += SETTINGS + Ui.PAD;

        int top = ui.section(x, y, w, listHeight(allRows), DeviceParts.text("filtered"));
        List<FilterOption> options = menu.options();
        int columnWidth = (w - 2 * Ui.PAD - 3) / COLUMNS;
        for (int i = scrollRows * COLUMNS; i < options.size() && i < (scrollRows + shownRows) * COLUMNS; i++) {
            FilterOption option = options.get(i);
            int index = i;
            ui.checkbox(
                    x + Ui.PAD + i % COLUMNS * columnWidth,
                    top + (i / COLUMNS - scrollRows) * Ui.ROW,
                    columnWidth - 2,
                    Ui.ROW,
                    option.tag() ? option.name() : GasParts.label(option.id()),
                    menu.selected(index),
                    () -> press(FilterMenu.OPTION + index));
        }
        ui.scrollbar(
                x + w - 3,
                top,
                shownRows * Ui.ROW,
                scrollRows * Ui.ROW,
                (allRows - shownRows) * Ui.ROW,
                allRows * Ui.ROW);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (allRows > shownRows && scrollY != 0) {
            scrollRows = Math.max(0, Math.min(allRows - shownRows, scrollRows - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
