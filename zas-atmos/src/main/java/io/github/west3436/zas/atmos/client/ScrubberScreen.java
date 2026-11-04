package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.scrubber.ScrubberMenu;
import io.github.west3436.zas.atmos.scrubber.ScrubberStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A scrubber's GUI (CONT-015, CONT-079): mode, what it did last atmos tick (moving gas, or idle and
 * why), its network's pressure, power, and a toggle for each gas and gas tag it can select. While an
 * area air controller controls the scrubber, it shows the controller and its order, and its buttons
 * are off.
 */
final class ScrubberScreen extends DeviceScreen<ScrubberMenu> {
    private static final int COLUMNS = 2;

    private final List<Button> buttons = new ArrayList<>();
    private final Button[] toggles = new Button[ScrubberMenu.OPTIONS];

    ScrubberScreen(ScrubberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 240;
        imageHeight = 236;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        // Below the most lines there can be: three of control, a status of two, network, power.
        int top = 100;
        buttons.add(
                button(8, top, 224, Component.translatable("gui.zas_atmos.scrubber.cycle"), ScrubberMenu.CYCLE_MODE));
        for (int i = 0; i < ScrubberMenu.OPTIONS; i++) {
            int x = 8 + 113 * (i % COLUMNS);
            int y = top + 22 + 19 * (i / COLUMNS);
            toggles[i] = button(x, y, 111, label(i), ScrubberMenu.FIRST_OPTION + i);
            buttons.add(toggles[i]);
        }
        refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    /** The server refuses hand settings on a controlled scrubber; the buttons say so. */
    private void refresh() {
        boolean free = !menu.controlled();
        for (Button b : buttons) {
            b.active = free;
        }
        for (int i = 0; i < toggles.length; i++) {
            toggles[i].setMessage(label(i));
        }
    }

    /** A toggle's label: whether its option is selected, and its gas or tag's name. */
    private Component label(int option) {
        Component name;
        if (option < ScrubberMenu.GASES.size()) {
            ResourceLocation id = ScrubberMenu.GASES.get(option);
            name = Component.translatable("gas." + id.getNamespace() + "." + id.getPath());
        } else {
            ResourceLocation id =
                    ScrubberMenu.TAGS.get(option - ScrubberMenu.GASES.size()).id();
            name = Component.translatable("gui.zas_atmos.scrubber.tag." + id.getPath());
        }
        return Component.literal(menu.selected(option) ? "[x] " : "[ ] ").append(name);
    }

    @Override
    List<Component> lines() {
        Component mode = Component.translatable(
                "gui.zas_atmos.scrubber.mode." + menu.mode().getSerializedName());
        List<Component> lines = new ArrayList<>();
        if (menu.controlled()) {
            lines.add(Component.translatable("gui.zas_atmos.vent.controlled"));
            menu.controller()
                    .ifPresent(p -> lines.add(
                            Component.translatable("gui.zas_atmos.vent.controller_at", p.getX(), p.getY(), p.getZ())));
            lines.add(Component.translatable("gui.zas_atmos.vent.order", mode));
        } else {
            lines.add(Component.translatable("gui.zas_atmos.vent.mode", mode));
        }
        if (menu.status() != ScrubberStatus.OFF) {
            lines.add(Component.translatable(menu.status().key()));
        }
        lines.add(Component.translatable("gui.zas_atmos.vent.network", menu.value(ScrubberMenu.DATA_NETWORK)));
        lines.add(Component.translatable(
                menu.value(ScrubberMenu.DATA_POWERED) != 0
                        ? "gui.zas_atmos.vent.powered"
                        : "gui.zas_atmos.vent.unpowered"));
        return lines;
    }
}
