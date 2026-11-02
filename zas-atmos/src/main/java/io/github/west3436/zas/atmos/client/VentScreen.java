package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.vent.VentMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A vent's GUI (CONT-014): mode, bounds and power. */
final class VentScreen extends DeviceScreen<VentMenu> {
    private static final String[] STEPS = {"-100", "-10", "+10", "+100"};

    VentScreen(VentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 132;
    }

    @Override
    protected void init() {
        super.init();
        button(8, 64, 184, Component.translatable("gui.zas_atmos.vent.cycle"), VentMenu.CYCLE_MODE);
        for (int i = 0; i < 4; i++) {
            button(8 + 46 * i, 86, 44, Component.literal("E " + STEPS[i]), VentMenu.EXTERNAL_DOWN_100 + i);
            button(8 + 46 * i, 108, 44, Component.literal("I " + STEPS[i]), VentMenu.INTERNAL_DOWN_100 + i);
        }
    }

    @Override
    List<Component> lines() {
        return List.of(
                Component.translatable(
                        "gui.zas_atmos.vent.mode",
                        Component.translatable(
                                "gui.zas_atmos.vent.mode." + menu.mode().getSerializedName())),
                Component.translatable(
                        "gui.zas_atmos.vent.bounds",
                        menu.value(VentMenu.DATA_EXTERNAL),
                        menu.value(VentMenu.DATA_INTERNAL)),
                Component.translatable(
                        menu.value(VentMenu.DATA_POWERED) != 0
                                ? "gui.zas_atmos.vent.powered"
                                : "gui.zas_atmos.vent.unpowered"));
    }
}
