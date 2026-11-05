package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.pump.PumpMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A pump's GUI (CONT-011): the inline device's, with its setting and four buttons to step it. */
final class PumpScreen extends InlineDeviceScreen<PumpMenu> {
    private static final List<String> PORTS = List.of("gui.zas_atmos.inline.input", "gui.zas_atmos.inline.output");

    private final String settingKey;
    private final String[] steps;

    private PumpScreen(PumpMenu menu, Inventory inventory, Component title, String settingKey, String... steps) {
        super(menu, inventory, title, PORTS);
        this.settingKey = settingKey;
        this.steps = steps;
    }

    /** A pressure pump's: its target, stepped by 10 and 100 kPa. */
    static PumpScreen pressure(PumpMenu menu, Inventory inventory, Component title) {
        return new PumpScreen(menu, inventory, title, "gui.zas_atmos.pump.target", "-100", "-10", "+10", "+100");
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < steps.length; i++) {
            button(8 + 46 * i, BUTTONS_Y, 44, Component.literal(steps[i]), PumpMenu.STEP_DOWN_BIG + i);
        }
    }

    @Override
    List<Component> settingLines() {
        return List.of(Component.translatable(settingKey, menu.value(PumpMenu.DATA_SETTING)));
    }
}
