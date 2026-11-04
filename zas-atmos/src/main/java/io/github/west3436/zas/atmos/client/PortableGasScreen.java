package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.portable.PortableGasMenu;
import io.github.west3436.zas.atmos.portable.PortableStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A portable pump's or scrubber's GUI (CONT-018): on or off, what it did last step, the pressures of
 * the air it works on and of its store, and its battery's charge, with a switch, its battery and tank
 * slots and the player's inventory.
 */
class PortableGasScreen<M extends PortableGasMenu> extends DeviceScreen<M> {
    /** The slots' indices in the menu. */
    private static final int BATTERY_SLOT = 0;

    private static final int TANK_SLOT = 1;

    PortableGasScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = PortableGasMenu.INVENTORY_Y + 82;
        inventoryLabelY = PortableGasMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        button(8, PortableGasMenu.BATTERY_Y - 1, 160, Component.translatable("gui.zas_atmos.portable.toggle"), 0);
    }

    @Override
    List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                menu.value(PortableGasMenu.DATA_ON) != 0 ? "gui.zas_atmos.portable.on" : "gui.zas_atmos.portable.off"));
        PortableStatus status = menu.status();
        if (status != PortableStatus.OFF) {
            lines.add(Component.translatable(status.key()));
        }
        int air = menu.value(PortableGasMenu.DATA_AIR);
        if (air != PortableGasMenu.NO_AIR) {
            lines.add(Component.translatable(
                    menu.value(PortableGasMenu.DATA_ON_TANK) != 0
                            ? "gui.zas_atmos.portable.tank_pressure"
                            : "gui.zas_atmos.portable.room_pressure",
                    air));
        }
        lines.add(Component.translatable("gui.zas_atmos.portable.store", menu.value(PortableGasMenu.DATA_STORE)));
        lines.add(
                menu.capacity() > 0
                        ? Component.translatable(
                                "gui.zas_atmos.battery.charge",
                                BatteryItem.fe(menu.energy()),
                                BatteryItem.fe(menu.capacity()))
                        : Component.translatable("gui.zas_atmos.battery.none"));
        return lines;
    }

    /** An empty slot says what it takes, since the plain panels have no icons for it. */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            if (hoveredSlot.index == BATTERY_SLOT) {
                graphics.renderTooltip(
                        font, Component.translatable("gui.zas_atmos.portable.battery_slot"), mouseX, mouseY);
            } else if (hoveredSlot.index == TANK_SLOT) {
                graphics.renderTooltip(
                        font, Component.translatable("gui.zas_atmos.portable.tank_slot"), mouseX, mouseY);
            }
        }
    }
}
