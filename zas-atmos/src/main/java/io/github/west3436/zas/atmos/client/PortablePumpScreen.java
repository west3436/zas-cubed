package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.portable.PortableGasMenu;
import io.github.west3436.zas.atmos.portable.PortablePumpMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * A portable pump's GUI (CONT-018): a portable's ({@link PortableGasScreen}), with which way it pumps
 * and its target, a button to change the direction and four to change the target.
 */
final class PortablePumpScreen extends PortableGasScreen<PortablePumpMenu> {
    private static final String[] STEPS = {"-100", "-10", "+10", "+100"};

    PortablePumpScreen(PortablePumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int top = PortableGasMenu.BATTERY_Y - 1 + 20;
        button(8, top, 160, Component.translatable("gui.zas_atmos.portable_pump.direction"), PortablePumpMenu.TOGGLE_DIRECTION);
        for (int i = 0; i < STEPS.length; i++) {
            button(8 + 41 * i, top + 20, 37, Component.literal(STEPS[i]), PortablePumpMenu.TARGET_DOWN_100 + i);
        }
    }

    @Override
    List<Component> lines() {
        List<Component> lines = super.lines();
        lines.add(
                1,
                Component.translatable(
                        menu.value(PortablePumpMenu.DATA_SIPHON) != 0
                                ? "gui.zas_atmos.portable_pump.siphoning"
                                : "gui.zas_atmos.portable_pump.releasing",
                        menu.value(PortablePumpMenu.DATA_TARGET)));
        return lines;
    }
}
