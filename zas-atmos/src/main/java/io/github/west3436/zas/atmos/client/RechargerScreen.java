package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.battery.RechargerMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The recharger's GUI (CONT-113): what it is doing and its battery's charge, its battery slot and the player's inventory. */
final class RechargerScreen extends DeviceScreen<RechargerMenu> {
    RechargerScreen(RechargerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = RechargerMenu.INVENTORY_Y + 82;
        inventoryLabelY = RechargerMenu.INVENTORY_Y - 11;
    }

    @Override
    int textWidth() {
        return RechargerMenu.BATTERY_X - 12; // clear of the battery slot
    }

    @Override
    List<Component> lines() {
        return List.of(
                Component.translatable(menu.status().key()),
                menu.capacity() > 0
                        ? Component.translatable(
                                "gui.zas_atmos.battery.charge",
                                BatteryItem.fe(menu.energy()),
                                BatteryItem.fe(menu.capacity()))
                        : Component.translatable("gui.zas_atmos.battery.none"));
    }
}
