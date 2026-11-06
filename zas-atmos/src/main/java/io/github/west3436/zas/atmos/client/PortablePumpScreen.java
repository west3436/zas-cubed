package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.portable.PortablePumpMenu;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A portable pump's window (CONT-018): a portable's, with which way it pumps and its target. */
final class PortablePumpScreen extends PortableGasScreen<PortablePumpMenu> {
    private static final List<Component> DIRECTIONS = List.of(DeviceParts.text("release"), DeviceParts.text("siphon"));

    PortablePumpScreen(PortablePumpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, PortablePumpMenu.SHIFT);
    }

    @Override
    protected void ownRows(Ui ui, int labelX, int valueX, int valueW, int y) {
        DeviceParts.label(ui, "mode", labelX, y, Ui.BUTTON_HEIGHT);
        ui.choice(
                valueX,
                y,
                DIRECTIONS,
                menu.value(PortablePumpMenu.DATA_SIPHON),
                -1,
                i -> press(PortablePumpMenu.TOGGLE_DIRECTION));
        y += Ui.BUTTON_ROW;
        DeviceParts.label(ui, "target", labelX, y, Ui.BUTTON_HEIGHT);
        ui.stepper(
                valueX,
                y,
                valueW,
                Component.literal(Readout.pressure(menu.value(PortablePumpMenu.DATA_TARGET))),
                Ui.WHITE,
                DeviceParts.BIG_STEPS,
                DeviceParts.KPA,
                i -> press(PortablePumpMenu.TARGET_DOWN_100 + i),
                null,
                null);
    }
}
