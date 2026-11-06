package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.power.GeneratorMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The generator's window (CONT-061): its fuel slot beside two bars, how long the burning fuel lasts
 * and what it has stored. Energy is teal, as TGUI keeps red for trouble.
 */
public class GeneratorScreen extends TguiScreen<GeneratorMenu> {
    private static final int WIDTH = 176;
    private static final int HEIGHT = heightWithInventory(GeneratorMenu.INVENTORY_Y);
    private static final int SECTION_HEIGHT = 24;
    private static final int LABEL_WIDTH = 24;
    private static final int TICKS_PER_SECOND = 20;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ui.section(x, y, w, SECTION_HEIGHT, null);
        slots(ui, GeneratorMenu.INVENTORY_Y);
        int slotX = leftPos + GeneratorMenu.FUEL_X;
        int slotY = topPos + GeneratorMenu.FUEL_Y;
        if (!menu.getSlot(0).hasItem()) {
            ui.ghost(slotX, slotY, new ItemStack(Items.COAL));
        }
        int labelX = slotX + 20;
        int barX = labelX + LABEL_WIDTH;
        int barW = x + w - Ui.PAD - barX;
        int barH = Ui.ROW - 1;
        int row = y + 2;

        DeviceParts.label(ui, "burn", labelX, row, barH);
        int left = menu.burnLeft();
        int total = menu.burnTotal();
        ui.bar(
                barX,
                row,
                barW,
                barH,
                total > 0 ? (double) left / total : 0,
                Ui.ORANGE,
                Ui.ORANGE_EDGE,
                left > 0
                        ? DeviceParts.text("seconds", (left + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND)
                        : Component.literal("—"));
        row += Ui.ROW;

        DeviceParts.label(ui, "stored", labelX, row, barH);
        int capacity = menu.capacity();
        ui.bar(
                barX,
                row,
                barW,
                barH,
                capacity > 0 ? (double) menu.energy() / capacity : 0,
                Ui.TEAL,
                Ui.TEAL_EDGE,
                DeviceParts.text("fe", BatteryItem.fe(menu.energy()), BatteryItem.fe(capacity)));
    }
}
