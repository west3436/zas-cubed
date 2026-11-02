package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.TankSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A held gas tank's settings (LIFE-044): its contents, and buttons for its distribution pressure.
 * A button sends only the hand and its id; the server applies and checks it. It closes when the
 * hand no longer holds a tank.
 */
final class TankScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int HEIGHT = 120;
    private static final int PANEL = 0xF0202020;
    private static final int BORDER = 0xFF808080;
    private static final int TEXT = 0xFFE0E0E0;

    private final InteractionHand hand;
    private int left;
    private int top;

    TankScreen(InteractionHand hand, Component title) {
        super(title);
        this.hand = hand;
    }

    private ItemStack tank() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getItemInHand(hand);
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        button(8, 74, 44, "-10", TankSettings.DOWN_10);
        button(56, 74, 40, "-1", TankSettings.DOWN_1);
        button(100, 74, 40, "+1", TankSettings.UP_1);
        button(144, 74, 48, "+10", TankSettings.UP_10);
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.zas_atmos.gas_tank.default"), b -> send(TankSettings.DEFAULT))
                .bounds(left + 8, top + 96, 90, 18)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.zas_atmos.gas_tank.close"), b -> send(TankSettings.CLOSE))
                .bounds(left + 102, top + 96, 90, 18)
                .build());
    }

    private void button(int x, int y, int w, String label, int id) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> send(id))
                .bounds(left + x, top + y, w, 18)
                .build());
    }

    private void send(int id) {
        PacketDistributor.sendToServer(new TankSettings(hand.ordinal(), id));
    }

    @Override
    public void tick() {
        if (!(tank().getItem() instanceof GasTankItem)) {
            onClose();
        }
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
        ItemStack tank = tank();
        graphics.drawString(font, tank.getHoverName(), left + 8, top + 6, TEXT, false);
        if (!(tank.getItem() instanceof GasTankItem)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        GasTankItem.lines(tank, lines);
        int y = top + 20;
        for (Component line : lines.subList(Math.max(0, lines.size() - 4), lines.size())) {
            graphics.drawString(font, line.getString(), left + 8, y, TEXT, false);
            y += 11;
        }
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.zas_atmos.gas_tank.range",
                        String.format(Locale.ROOT, "%.0f", GasTankItem.MAX_DISTRIBUTION_KPA)),
                left + 8,
                top + 63,
                0xFFA0A0A0,
                false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
