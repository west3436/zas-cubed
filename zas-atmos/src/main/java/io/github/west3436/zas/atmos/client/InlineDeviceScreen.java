package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.inline.InlineDeviceMenu;
import io.github.west3436.zas.atmos.inline.InlineStatus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The base of an inline device's GUI: whether it is on and what it did last atmos tick, its own
 * settings, the pressure at each port, whether it has power, and a switch. The device's own buttons
 * go below the switch.
 */
abstract class InlineDeviceScreen<M extends InlineDeviceMenu> extends DeviceScreen<M> {
    /** Below the most lines there can be: state, a status of two, the setting, two ports and power. */
    static final int SWITCH_Y = 100;

    /** Where a device's own buttons start. */
    static final int BUTTONS_Y = SWITCH_Y + 22;

    private final List<String> portKeys;

    /**
     * @param portKeys each port's line's translation key, which takes the pressure in kPa
     */
    InlineDeviceScreen(M menu, Inventory inventory, Component title, List<String> portKeys) {
        super(menu, inventory, title);
        this.portKeys = portKeys;
        imageHeight = BUTTONS_Y + 26;
    }

    /** The lines of the device's own settings. */
    abstract List<Component> settingLines();

    /** Where the switch goes, below the most lines there can be. */
    int switchY() {
        return SWITCH_Y;
    }

    @Override
    protected void init() {
        super.init();
        button(8, switchY(), 184, Component.translatable("gui.zas_atmos.inline.toggle"), InlineDeviceMenu.TOGGLE);
    }

    @Override
    List<Component> lines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(menu.on() ? "gui.zas_atmos.inline.on" : "gui.zas_atmos.inline.off"));
        if (menu.status() != InlineStatus.OFF) {
            lines.add(Component.translatable(menu.status().key()));
        }
        lines.addAll(settingLines());
        for (int i = 0; i < portKeys.size(); i++) {
            lines.add(Component.translatable(portKeys.get(i), menu.pressure(i)));
        }
        lines.add(Component.translatable(
                menu.powered() ? "gui.zas_atmos.inline.powered" : "gui.zas_atmos.inline.unpowered"));
        return lines;
    }
}
