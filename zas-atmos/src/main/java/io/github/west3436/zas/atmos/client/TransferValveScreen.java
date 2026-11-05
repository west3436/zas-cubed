package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveMenu;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A held TTV's GUI (CONT-103, CONT-104): a slot for each tank beside what it holds, its valve and
 * timer with their buttons, and the player's inventory. The server applies and checks everything, and
 * closes it when the hand no longer holds the TTV, as it doesn't once its timer starts.
 */
final class TransferValveScreen extends DeviceScreen<TransferValveMenu> {
    private static final int HINT = 0xFFA0A0A0;

    /** The TTV's own slot is lighter than the others, as the one thing here that can't be moved. */
    private static final int VALVE_SLOT = 0xFF3C4248;

    private static final int LINE_HEIGHT = 11;
    private static final int TEXT_X = TransferValveMenu.TANK_X + 22;
    private static final int STATE_Y = TransferValveMenu.SECOND_Y + 22;
    private static final int TIMER_BUTTONS_Y = STATE_Y + 12;
    private static final int VALVE_BUTTONS_Y = TIMER_BUTTONS_Y + 22;
    private static final int TOOLTIP_WIDTH = 180;

    private Button valveButton;
    private Button startButton;

    TransferValveScreen(TransferValveMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = TransferValveMenu.WIDTH;
        imageHeight = TransferValveMenu.INVENTORY_Y + 82;
        inventoryLabelX = TransferValveMenu.INVENTORY_X;
        inventoryLabelY = TransferValveMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        button(8, TIMER_BUTTONS_Y, 40, Component.literal("-10"), TransferValveMenu.DOWN_10);
        button(52, TIMER_BUTTONS_Y, 36, Component.literal("-1"), TransferValveMenu.DOWN_1);
        button(92, TIMER_BUTTONS_Y, 36, Component.literal("+1"), TransferValveMenu.UP_1);
        button(132, TIMER_BUTTONS_Y, 40, Component.literal("+10"), TransferValveMenu.UP_10);
        button(
                176,
                TIMER_BUTTONS_Y,
                56,
                Component.translatable("gui.zas_atmos.ttv.default"),
                TransferValveMenu.DEFAULT);
        valveButton = button(
                8, VALVE_BUTTONS_Y, 110, Component.translatable("gui.zas_atmos.ttv.open"), TransferValveMenu.VALVE);
        startButton = button(
                122, VALVE_BUTTONS_Y, 110, Component.translatable("gui.zas_atmos.ttv.start"), TransferValveMenu.START);
        refresh();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    /** Buttons that would do nothing are greyed out, and the valve's says what it would do. */
    private void refresh() {
        TransferValve valve = menu.valve();
        valveButton.active = valve.joined();
        valveButton.setMessage(
                Component.translatable(valve.isOpen() ? "gui.zas_atmos.ttv.close" : "gui.zas_atmos.ttv.open"));
        startButton.active = !valve.isOpen() && menu.getCarried().isEmpty();
    }

    @Override
    List<Component> lines() {
        return List.of();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        Slot slot = menu.getSlot(menu.valveSlot());
        graphics.fill(
                leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, VALVE_SLOT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        TransferValve valve = menu.valve();
        tank(graphics, "gui.zas_atmos.ttv.first", valve.first(), TransferValveMenu.FIRST_Y);
        tank(graphics, "gui.zas_atmos.ttv.second", valve.second(), TransferValveMenu.SECOND_Y);
        graphics.drawString(
                font,
                Component.translatable(
                        valve.isOpen() ? "gui.zas_atmos.ttv.valve_open" : "gui.zas_atmos.ttv.valve_closed"),
                8,
                STATE_Y,
                TEXT,
                false);
        Component timer = Component.translatable(
                "gui.zas_atmos.ttv.timer", menu.timer(), TransferValve.MIN_TIMER, TransferValve.MAX_TIMER);
        graphics.drawString(font, timer, imageWidth - 8 - font.width(timer), STATE_Y, TEXT, false);
    }

    /** What a tank's slot holds, beside it: its name, cut short if it was renamed, and its gas. */
    private void tank(GuiGraphics graphics, String key, ItemStack tank, int y) {
        Component name = tank.isEmpty() ? Component.translatable("gui.zas_atmos.ttv.no_tank") : tank.getHoverName();
        graphics.drawString(
                font,
                font.split(Component.translatable(key, name), imageWidth - TEXT_X - 8)
                        .get(0),
                TEXT_X,
                y - 1,
                TEXT,
                false);
        if (!tank.isEmpty()) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.zas_atmos.ttv.tank",
                            String.format(
                                    Locale.ROOT,
                                    "%.0f",
                                    GasTankItem.contents(tank).pressure()),
                            String.format(
                                    Locale.ROOT, "%.0f", GasTankItem.gas(tank).temperature())),
                    TEXT_X,
                    y - 1 + LINE_HEIGHT,
                    HINT,
                    false);
        }
    }

    /**
     * An empty tank's slot says what it is for, since the plain panels have no icons for it, and a
     * tank carried over an open valve's slots says why it won't go on.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hoveredSlot == null || hoveredSlot.index > TransferValve.SECOND) {
            return;
        }
        String hint = null;
        if (!menu.getCarried().isEmpty()) {
            if (menu.valve().isOpen()) {
                hint = "gui.zas_atmos.ttv.close_first";
            }
        } else if (!hoveredSlot.hasItem()) {
            hint = hoveredSlot.index == TransferValve.FIRST
                    ? "gui.zas_atmos.ttv.first_slot"
                    : "gui.zas_atmos.ttv.second_slot";
        }
        if (hint != null) {
            graphics.renderTooltip(font, font.split(Component.translatable(hint), TOOLTIP_WIDTH), mouseX, mouseY);
        }
    }
}
