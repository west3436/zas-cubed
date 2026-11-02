package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.heater.HeaterMenu;
import io.github.west3436.zas.atmos.heater.HeaterStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A heater's GUI (CONT-019): on or off, setpoint beside the room's temperature, what it did last
 * step, its network's temperature, and power, with a switch and four setpoint buttons.
 */
final class HeaterScreen extends DeviceScreen<HeaterMenu> {
    private static final String[] STEPS = {"-10", "-1", "+1", "+10"};

    HeaterScreen(HeaterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 140;
    }

    @Override
    protected void init() {
        super.init();
        int top = 96; // below five lines and a two-line status
        button(8, top, 184, Component.translatable("gui.zas_atmos.heater.toggle"), HeaterMenu.TOGGLE);
        for (int i = 0; i < 4; i++) {
            button(8 + 46 * i, top + 22, 44, Component.literal(STEPS[i] + " K"), HeaterMenu.SETPOINT_DOWN_10 + i);
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
        lines.add(Component.translatable("gui.zas_atmos.heater.network", kelvin(menu.value(HeaterMenu.DATA_NETWORK))));
        lines.add(Component.translatable(
                menu.value(HeaterMenu.DATA_POWERED) != 0
                        ? "gui.zas_atmos.vent.powered"
                        : "gui.zas_atmos.vent.unpowered"));
        return lines;
    }
}
