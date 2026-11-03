package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.heater.HeaterStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A heater's GUI (CONT-019): on or off, setpoint beside the room's temperature, what it did last
 * step and its battery's charge, with a switch, four setpoint buttons, its battery slot and the
 * player's inventory.
 */
final class HeaterScreen extends DeviceScreen<HeaterMenu> {
    private static final String[] STEPS = {"-10", "-1", "+1", "+10"};

    HeaterScreen(HeaterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = HeaterMenu.INVENTORY_Y + 82;
        inventoryLabelY = HeaterMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int top = HeaterMenu.BATTERY_Y - 1; // below up to five lines; the battery slot beside the switch
        button(8, top, 160, Component.translatable("gui.zas_atmos.heater.toggle"), HeaterMenu.TOGGLE);
        for (int i = 0; i < 4; i++) {
            button(8 + 46 * i, top + 20, 44, Component.literal(STEPS[i] + " K"), HeaterMenu.SETPOINT_DOWN_10 + i);
        }
    }

    private static String kelvin(int tenths) {
        return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    @Override
    List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                menu.value(HeaterMenu.DATA_ON) != 0 ? "gui.zas_atmos.heater.on" : "gui.zas_atmos.heater.off"));
        String setpoint = kelvin(menu.value(HeaterMenu.DATA_SETPOINT));
        int room = menu.value(HeaterMenu.DATA_ROOM);
        lines.add(
                room < 0
                        ? Component.translatable("gui.zas_atmos.heater.setpoint_no_room", setpoint)
                        : Component.translatable("gui.zas_atmos.heater.setpoint", setpoint, kelvin(room)));
        if (menu.status() == HeaterStatus.HEATING) {
            lines.add(Component.translatable("gui.zas_atmos.heater.heating", menu.value(HeaterMenu.DATA_WATTS)));
        } else if (menu.status() != HeaterStatus.OFF) {
            lines.add(Component.translatable(menu.status().key()));
        }
        lines.add(
                menu.capacity() > 0
                        ? Component.translatable(
                                "gui.zas_atmos.battery.charge",
                                BatteryItem.fe(menu.energy()),
                                BatteryItem.fe(menu.capacity()))
                        : Component.translatable("gui.zas_atmos.battery.none"));
        return lines;
    }
}
