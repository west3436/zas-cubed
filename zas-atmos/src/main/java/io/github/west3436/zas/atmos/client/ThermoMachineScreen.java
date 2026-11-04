package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.heater.ThermoMachineMenu;
import io.github.west3436.zas.atmos.heater.ThermoStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A heater's or air conditioner's GUI (CONT-019): on or off, setpoint beside the room's temperature,
 * what it did last step and its battery's charge, with a switch, four setpoint buttons, its battery
 * slot and the player's inventory.
 */
final class ThermoMachineScreen<M extends ThermoMachineMenu> extends DeviceScreen<M> {
    private static final String[] STEPS = {"-10", "-1", "+1", "+10"};

    ThermoMachineScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = ThermoMachineMenu.INVENTORY_Y + 82;
        inventoryLabelY = ThermoMachineMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        int top = ThermoMachineMenu.BATTERY_Y - 1; // below up to five lines; the battery slot beside the switch
        button(8, top, 160, Component.translatable("gui.zas_atmos.thermo.toggle"), ThermoMachineMenu.TOGGLE);
        for (int i = 0; i < 4; i++) {
            button(
                    8 + 46 * i,
                    top + 20,
                    44,
                    Component.literal(STEPS[i] + " K"),
                    ThermoMachineMenu.SETPOINT_DOWN_10 + i);
        }
    }

    private static String kelvin(int tenths) {
        return String.format(Locale.ROOT, "%.1f", tenths / 10.0);
    }

    @Override
    List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                menu.value(ThermoMachineMenu.DATA_ON) != 0 ? "gui.zas_atmos.thermo.on" : "gui.zas_atmos.thermo.off"));
        String setpoint = kelvin(menu.value(ThermoMachineMenu.DATA_SETPOINT));
        int room = menu.value(ThermoMachineMenu.DATA_ROOM);
        lines.add(
                room < 0
                        ? Component.translatable("gui.zas_atmos.thermo.setpoint_no_room", setpoint)
                        : Component.translatable("gui.zas_atmos.thermo.setpoint", setpoint, kelvin(room)));
        ThermoStatus status = menu.status();
        if (status.working()) {
            lines.add(Component.translatable(status.key(), menu.value(ThermoMachineMenu.DATA_WATTS)));
        } else if (status != ThermoStatus.OFF) {
            lines.add(Component.translatable(status.key()));
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
