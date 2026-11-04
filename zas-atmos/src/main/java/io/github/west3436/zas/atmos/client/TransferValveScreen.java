package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.ttv.TransferValveSettings;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A held TTV's settings (CONT-103, CONT-104): its tanks and valve, and buttons for the valve, the
 * timer and the tanks. A button sends only the hand and its id; the server applies and checks it. It
 * closes when the hand no longer holds a TTV, as it doesn't once its timer starts.
 */
final class TransferValveScreen extends Screen {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;
    private static final int PANEL = 0xF0202020;
    private static final int BORDER = 0xFF808080;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int HINT = 0xFFA0A0A0;

    private final InteractionHand hand;
    private int left;
    private int top;
    private Button valveButton;
    private Button startButton;
    private Button takeFirst;
    private Button takeSecond;

    TransferValveScreen(InteractionHand hand, Component title) {
        super(title);
        this.hand = hand;
    }

    private ItemStack stack() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        button(8, 66, 40, Component.literal("-10"), TransferValveSettings.DOWN_10);
        button(52, 66, 36, Component.literal("-1"), TransferValveSettings.DOWN_1);
        button(92, 66, 36, Component.literal("+1"), TransferValveSettings.UP_1);
        button(132, 66, 40, Component.literal("+10"), TransferValveSettings.UP_10);
        button(176, 66, 56, Component.translatable("gui.zas_atmos.ttv.default"), TransferValveSettings.DEFAULT);
        valveButton = button(8, 90, 110, Component.translatable("gui.zas_atmos.ttv.open"), TransferValveSettings.VALVE);
        startButton =
                button(122, 90, 110, Component.translatable("gui.zas_atmos.ttv.start"), TransferValveSettings.START);
        takeFirst = button(
                8, 112, 110, Component.translatable("gui.zas_atmos.ttv.take_first"), TransferValveSettings.TAKE_FIRST);
        takeSecond = button(
                122,
                112,
                110,
                Component.translatable("gui.zas_atmos.ttv.take_second"),
                TransferValveSettings.TAKE_SECOND);
        refresh();
    }

    private Button button(int x, int y, int w, Component label, int id) {
        return addRenderableWidget(Button.builder(label, b -> send(id))
                .bounds(left + x, top + y, w, 18)
                .build());
    }

    private void send(int id) {
        PacketDistributor.sendToServer(new TransferValveSettings(hand.ordinal(), id));
    }

    @Override
    public void tick() {
        if (!(stack().getItem() instanceof TransferValveItem)) {
            onClose();
            return;
        }
        refresh();
    }

    /** Buttons that would do nothing are greyed out, and the valve's says what it would do. */
    private void refresh() {
        TransferValve valve = TransferValveItem.valve(stack());
        valveButton.active = valve.joined();
        valveButton.setMessage(
                Component.translatable(valve.isOpen() ? "gui.zas_atmos.ttv.close" : "gui.zas_atmos.ttv.open"));
        startButton.active = !valve.isOpen();
        takeFirst.active = !valve.first().isEmpty();
        takeSecond.active = !valve.second().isEmpty();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, BORDER);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, PANEL);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ItemStack stack = stack();
        if (!(stack.getItem() instanceof TransferValveItem)) {
            return;
        }
        TransferValve valve = TransferValveItem.valve(stack);
        graphics.drawString(font, title, left + 8, top + 6, TEXT, false);
        graphics.drawString(font, tankLine("gui.zas_atmos.ttv.first", valve.first()), left + 8, top + 18, TEXT, false);
        graphics.drawString(
                font, tankLine("gui.zas_atmos.ttv.second", valve.second()), left + 8, top + 29, TEXT, false);
        graphics.drawString(
                font,
                Component.translatable(
                        valve.isOpen() ? "gui.zas_atmos.ttv.valve_open" : "gui.zas_atmos.ttv.valve_closed"),
                left + 8,
                top + 40,
                TEXT,
                false);
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.zas_atmos.ttv.timer",
                        TransferValveItem.timer(stack),
                        TransferValve.MIN_TIMER,
                        TransferValve.MAX_TIMER),
                left + 8,
                top + 53,
                TEXT,
                false);
        graphics.drawWordWrap(
                font, Component.translatable("gui.zas_atmos.ttv.hint"), left + 8, top + 134, WIDTH - 16, HINT);
    }

    private static Component tankLine(String key, ItemStack tank) {
        if (tank.isEmpty()) {
            return Component.translatable(key, Component.translatable("gui.zas_atmos.ttv.no_tank"));
        }
        return Component.translatable(
                key,
                Component.translatable(
                        "gui.zas_atmos.ttv.tank",
                        tank.getHoverName(),
                        String.format(
                                Locale.ROOT, "%.0f", GasTankItem.contents(tank).pressure()),
                        String.format(Locale.ROOT, "%.0f", GasTankItem.gas(tank).temperature())));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
