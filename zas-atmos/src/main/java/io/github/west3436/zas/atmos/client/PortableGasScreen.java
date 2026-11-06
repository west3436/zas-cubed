package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.TguiScreen;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.portable.PortableGasMenu;
import io.github.west3436.zas.atmos.portable.PortableStatus;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.scrubber.ScrubberMath;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A portable pump's or scrubber's window (CONT-018): its state and switch, its store against the most
 * a scrubber fills it to, and its two slots. The battery slot sits beside its charge, and the tank slot
 * beside the air it works on, which is the tank's with one in and the room's without.
 */
abstract class PortableGasScreen<M extends PortableGasMenu> extends TguiScreen<M> {
    private static final int WIDTH = 176;
    private static final int TOP_ROWS = 2 + Ui.BUTTON_ROW + Ui.ROW;
    private static final int SLOT_ROWS = 2 * 18 + 3;
    private static final int BATTERY_SLOT = 0;
    private static final int TANK_SLOT = 1;

    private final int shift;

    /** @param shift how much taller its own rows make it */
    PortableGasScreen(M menu, Inventory inventory, Component title, int shift) {
        super(menu, inventory, title, WIDTH, heightWithInventory(PortableGasMenu.INVENTORY_Y + shift));
        this.shift = shift;
    }

    /** Draws its own rows, in the space the shift gives them. */
    protected void ownRows(Ui ui, int labelX, int valueX, int valueW, int y) {}

    @Override
    protected void draw(Ui ui, int x, int y, int w, int h) {
        ui.section(x, y, w, TOP_ROWS + shift + SLOT_ROWS, null);
        int labelX = x + Ui.PAD;
        int valueX = labelX + DeviceParts.LABEL_WIDTH;
        int valueW = w - 2 * Ui.PAD - DeviceParts.LABEL_WIDTH;
        int row = y + 2;

        DeviceParts.label(ui, "state", labelX, row, Ui.BUTTON_HEIGHT);
        PortableStatus status = menu.status();
        DeviceParts.status(ui, Component.translatable(status.key()), colour(status), valueX, row, Ui.BUTTON_HEIGHT);
        DeviceParts.onOff(
                ui, x + w - Ui.PAD, row, menu.value(PortableGasMenu.DATA_ON) != 0, () -> press(PortableGasMenu.TOGGLE));
        row += Ui.BUTTON_ROW;

        ownRows(ui, labelX, valueX, valueW, row);
        row += shift;

        DeviceParts.label(ui, "store", labelX, row, Ui.ROW - 1);
        int store = menu.value(PortableGasMenu.DATA_STORE);
        boolean full = store >= ScrubberMath.STALL_KPA;
        ui.bar(
                valueX,
                row,
                valueW,
                Ui.ROW - 1,
                store / ScrubberMath.STALL_KPA,
                full ? Ui.AVERAGE : Ui.TEAL,
                full ? Ui.AVERAGE : Ui.TEAL_EDGE,
                Component.literal(Readout.pressure(store)));

        slots(ui, PortableGasMenu.INVENTORY_Y + shift);
        int slotX = leftPos + PortableGasMenu.SLOT_X;
        int batteryY = topPos + PortableGasMenu.BATTERY_Y + shift;
        int tankY = topPos + PortableGasMenu.TANK_Y + shift;
        if (!menu.getSlot(BATTERY_SLOT).hasItem()) {
            ui.ghost(slotX, batteryY, new ItemStack(AtmosItems.BATTERY.get()));
        }
        if (!menu.getSlot(TANK_SLOT).hasItem()) {
            ui.ghost(slotX, tankY, new ItemStack(AtmosItems.EMPTY_TANK.get()));
        }
        int besideX = slotX + 19;
        DeviceParts.charge(ui, besideX, batteryY + 3, x + w - Ui.PAD - besideX, Ui.ROW, menu.energy(), menu.capacity());
        boolean onTank = menu.value(PortableGasMenu.DATA_ON_TANK) != 0;
        int air = menu.value(PortableGasMenu.DATA_AIR);
        DeviceParts.label(ui, onTank ? "tank" : "room", besideX, tankY + 3, Ui.ROW);
        ui.line(
                Component.literal(air == PortableGasMenu.NO_AIR ? "—" : Readout.pressure(air)),
                besideX + 22,
                tankY + 3,
                Ui.ROW,
                Ui.WHITE,
                Face.BODY);
    }

    private static int colour(PortableStatus status) {
        return switch (status) {
            case OFF -> Ui.DIM;
            case WORKING -> Ui.GOOD;
            case WAITING, AT_TARGET, NOTHING_TO_MOVE -> Ui.LABEL;
            case NO_BATTERY, BATTERY_EMPTY, NO_ROOM, STORE_FULL -> Ui.AVERAGE;
        };
    }
}
