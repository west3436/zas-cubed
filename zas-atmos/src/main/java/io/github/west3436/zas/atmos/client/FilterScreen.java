package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.filter.FilterMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A filter's GUI (CONT-013): the side port's, with a button per gas or gas tag it can select. An
 * entry that is on is green with a plus. Pages turn on the client only when they don't all fit.
 */
final class FilterScreen extends SidePortScreen<FilterMenu> {
    private static final List<String> PORTS =
            List.of("gui.zas_atmos.inline.input", "gui.zas_atmos.inline.output", "gui.zas_atmos.filter.side");

    /** Below the most lines: state, a status of two, the target, three ports and power. */
    private static final int SWITCH_Y = 110;

    private static final int ROWS = 4;
    private static final int PER_PAGE = 2 * ROWS;
    private static final int ROW_HEIGHT = 19;

    private final List<Button> entries = new ArrayList<>();
    private int page;

    FilterScreen(FilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PORTS, SWITCH_Y, SWITCH_Y + 40 + (ROWS + 1) * ROW_HEIGHT + 4);
    }

    @Override
    protected void init() {
        super.init();
        entries.clear();
        int count = menu.options().size();
        int pages = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        int y = ownButtonsY();
        for (int i = page * PER_PAGE; i < Math.min(count, (page + 1) * PER_PAGE); i++) {
            int place = i - page * PER_PAGE;
            entries.add(
                    button(8 + 93 * (place % 2), y + ROW_HEIGHT * (place / 2), 91, label(i), FilterMenu.OPTION + i));
        }
        if (pages > 1) {
            pageButton(8, y + ROW_HEIGHT * ROWS, "<", Math.floorMod(page - 1, pages));
            pageButton(101, y + ROW_HEIGHT * ROWS, ">", (page + 1) % pages);
        }
    }

    private void pageButton(int x, int y, String label, int target) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> {
                    page = target;
                    rebuildWidgets();
                })
                .bounds(leftPos + x, topPos + y, 91, 18)
                .build());
    }

    private Component label(int index) {
        Component name = menu.options().get(index).name();
        return menu.selected(index) ? Component.literal("+ ").append(name).withStyle(ChatFormatting.GREEN) : name;
    }

    /** Selections reach the client as data slots, so the labels follow them. */
    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < entries.size(); i++) {
            entries.get(i).setMessage(label(page * PER_PAGE + i));
        }
    }
}
