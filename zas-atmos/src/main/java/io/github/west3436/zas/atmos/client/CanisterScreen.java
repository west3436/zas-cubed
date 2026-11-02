package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.canister.CanisterMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A canister's GUI (CONT-084): pressure, contents, valve and release pressure. */
final class CanisterScreen extends DeviceScreen<CanisterMenu> {
    CanisterScreen(CanisterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        button(8, 64, 44, Component.literal("-100"), CanisterMenu.RELEASE_DOWN_100);
        button(56, 64, 40, Component.literal("-10"), CanisterMenu.RELEASE_DOWN_10);
        button(100, 64, 40, Component.literal("+10"), CanisterMenu.RELEASE_UP_10);
        button(144, 64, 48, Component.literal("+100"), CanisterMenu.RELEASE_UP_100);
        button(8, 86, 184, Component.translatable("gui.zas_atmos.canister.valve"), CanisterMenu.TOGGLE_VALVE);
    }

    @Override
    List<Component> lines() {
        return List.of(
                Component.translatable(
                        "gui.zas_atmos.canister.contents",
                        menu.value(CanisterMenu.DATA_PRESSURE),
                        menu.value(CanisterMenu.DATA_MOLES)),
                Component.translatable("gui.zas_atmos.canister.release", menu.value(CanisterMenu.DATA_RELEASE)),
                Component.translatable(
                        menu.value(CanisterMenu.DATA_OPEN) != 0
                                ? "gui.zas_atmos.canister.open"
                                : "gui.zas_atmos.canister.closed"));
    }
}
