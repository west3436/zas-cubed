package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.vent.VentMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A vent's GUI (CONT-014): mode, bounds and power. While an area air controller controls the vent,
 * it shows the controller and its order, and its buttons are off.
 */
final class VentScreen extends DeviceScreen<VentMenu> {
    private static final String[] STEPS = {"-100", "-10", "+10", "+100"};

    private final List<Button> buttons = new ArrayList<>();

    VentScreen(VentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 143;
    }

    @Override
    protected void init() {
        super.init();
        buttons.clear();
        buttons.add(button(8, 75, 184, Component.translatable("gui.zas_atmos.vent.cycle"), VentMenu.CYCLE_MODE));
        for (int i = 0; i < 4; i++) {
            buttons.add(button(8 + 46 * i, 97, 44, Component.literal("E " + STEPS[i]), VentMenu.EXTERNAL_DOWN_100 + i));
            buttons.add(
                    button(8 + 46 * i, 119, 44, Component.literal("I " + STEPS[i]), VentMenu.INTERNAL_DOWN_100 + i));
        }
        enableButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        enableButtons();
    }

    /** The server refuses hand settings on a controlled vent; the buttons say so. */
    private void enableButtons() {
        boolean free = !menu.controlled();
        for (Button b : buttons) {
            b.active = free;
        }
    }

    @Override
    List<Component> lines() {
        Component mode =
                Component.translatable("gui.zas_atmos.vent.mode." + menu.mode().getSerializedName());
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
        lines.add(Component.translatable(
                "gui.zas_atmos.vent.bounds", menu.value(VentMenu.DATA_EXTERNAL), menu.value(VentMenu.DATA_INTERNAL)));
        lines.add(Component.translatable(
                menu.value(VentMenu.DATA_POWERED) != 0
                        ? "gui.zas_atmos.vent.powered"
                        : "gui.zas_atmos.vent.unpowered"));
        return lines;
    }
}
