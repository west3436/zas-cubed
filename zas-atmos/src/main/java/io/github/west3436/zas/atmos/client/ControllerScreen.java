package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerMenu;
import io.github.west3436.zas.atmos.controller.ControllerMode;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.controller.ControllerStatus;
import io.github.west3436.zas.atmos.controller.ControllerSync;
import io.github.west3436.zas.atmos.controller.Thresholds;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The area air controller's screen: status, power and pin, the mode and preset buttons, each
 * reading with its value and bounds, the preset editor, and the parts the controller covers with
 * where each came from (CONT-049). It has no texture yet and is drawn with flat panels.
 */
public class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final int BACKGROUND = 0xF0202428;
    private static final int PANEL = 0xFF30353B;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int DIM = 0xFF9AA0A6;
    private static final int[] DANGER_COLOURS = {0xFF55D36B, 0xFFE8C547, 0xFFE8574A};
    private static final String[] BOUNDS = {"min2", "min1", "max1", "max2"};
    private static final int PARTS_SHOWN = 6;

    public ControllerScreen(ControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 300;
        imageHeight = 250;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 6;
        int y = topPos + 40;
        int modeX = x;
        for (ControllerMode mode : ControllerMode.values()) {
            Component label = Component.translatable("gui.zas_atmos.controller.mode." + mode.key());
            int width = font.width(label) + 10;
            button(modeX, y, width, label, mode.ordinal());
            modeX += width + 2;
        }
        y += 22;
        for (int i = 0; i < ControllerPreset.SHIPPED.size(); i++) {
            String name = ControllerPreset.SHIPPED.get(i).name();
            button(
                    x + 150 + i * 56,
                    y - 2,
                    54,
                    Component.translatable("gui.zas_atmos.controller.preset." + name),
                    ControllerMenu.LOAD_PRESET + i);
        }
        int ey = topPos + 172;
        button(x, ey, 14, Component.literal("<"), ControllerMenu.PREV_READING);
        button(x + 96, ey, 14, Component.literal(">"), ControllerMenu.NEXT_READING);
        button(x + 114, ey, 14, Component.literal("<"), ControllerMenu.PREV_BOUND);
        button(x + 160, ey, 14, Component.literal(">"), ControllerMenu.NEXT_BOUND);
        String[] steps = {"-10", "-1", "-.1", "+.1", "+1", "+10"};
        for (int i = 0; i < steps.length; i++) {
            button(x + 178 + i * 16, ey, 16, Component.literal(steps[i]), ControllerMenu.ADJUST + i);
        }
        button(x + 274, ey, 14, Component.literal("Ø"), ControllerMenu.TOGGLE_BOUND);
    }

    private void button(int x, int y, int w, Component label, int id) {
        addRenderableWidget(
                Button.builder(label, b -> click(id)).bounds(x, y, w, 14).build());
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BACKGROUND);
        g.fill(leftPos + 4, topPos + 78, leftPos + imageWidth - 4, topPos + 168, PANEL);
        g.fill(leftPos + 4, topPos + 190, leftPos + imageWidth - 4, topPos + imageHeight - 4, PANEL);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 6, 6, TEXT, false);
        ControllerSync s = menu.state();
        if (s == null) {
            return;
        }
        ControllerStatus status = ControllerStatus.values()[s.status()];
        int colour = status == ControllerStatus.UNPOWERED ? DIM : DANGER_COLOURS[Math.min(2, status.ordinal() - 1)];
        g.drawString(
                font,
                Component.translatable("gui.zas_atmos.controller.status." + status.getSerializedName()),
                6,
                18,
                colour,
                false);
        g.drawString(
                font,
                Component.translatable("gui.zas_atmos.controller.power", s.energy(), s.capacity()),
                110,
                18,
                TEXT,
                false);
        g.drawString(
                font,
                Component.translatable(
                        "gui.zas_atmos.controller.pin",
                        s.pin().isEmpty() ? "-" : s.pin().toLowerCase(Locale.ROOT)),
                6,
                29,
                DIM,
                false);
        g.drawString(
                font,
                Component.translatable(
                        "gui.zas_atmos.controller.mode",
                        Component.translatable(
                                "gui.zas_atmos.controller.mode." + ControllerMode.values()[s.mode()].key())),
                110,
                29,
                DIM,
                false);
        Component preset = Component.translatable("gui.zas_atmos.controller.preset." + s.preset());
        g.drawString(
                font,
                s.edited()
                        ? Component.translatable("gui.zas_atmos.controller.preset_edited", preset)
                        : Component.translatable("gui.zas_atmos.controller.preset_named", preset),
                6,
                64,
                TEXT,
                false);

        int y = 81;
        for (int i = 0; i < s.labels().size(); i++) {
            boolean selected = i == s.reading();
            int c = i < s.ratings().length ? DANGER_COLOURS[s.ratings()[i]] : DIM;
            g.drawString(
                    font, (selected ? "> " : "  ") + label(s.labels().get(i)).getString(), 6, y, c, false);
            if (i < s.values().length) {
                g.drawString(font, format(s.values()[i]) + unit(i), 120, y, TEXT, false);
            }
            StringBuilder b = new StringBuilder();
            for (int j = 0; j < 4; j++) {
                double v = s.bounds()[i * 4 + j];
                String text = v == Thresholds.OFF ? "-" : format(v);
                b.append(j > 0 ? " / " : "").append(selected && j == s.bound() ? "[" + text + "]" : text);
            }
            g.drawString(font, b.toString(), 190, y, DIM, false);
            y += 9;
        }

        int ey = 175;
        g.drawString(font, label(s.labels().get(s.reading())).getString(), 22, ey, TEXT, false);
        g.drawString(font, BOUNDS[s.bound()], 132, ey, TEXT, false);

        y = 193;
        g.drawString(
                font,
                Component.translatable(
                        "gui.zas_atmos.controller.parts", s.parts().size()),
                6,
                y,
                TEXT,
                false);
        y += 10;
        for (int i = 0; i < Math.min(PARTS_SHOWN, s.parts().size()); i++) {
            AreaAirControllerBlockEntity.Part p = s.parts().get(i);
            String where = p.inZone() && p.linked() ? "zone_and_link" : p.inZone() ? "zone" : "link";
            Component line = Component.translatable(
                    "gui.zas_atmos.controller.part",
                    Component.translatable("block." + p.block().getNamespace() + "."
                            + p.block().getPath()),
                    p.pos().getX() + " " + p.pos().getY() + " " + p.pos().getZ(),
                    Component.translatable("gui.zas_atmos.controller.from." + where),
                    Component.translatable(
                            p.powered() ? "gui.zas_atmos.controller.on" : "gui.zas_atmos.controller.off"));
            g.drawString(font, line, 6, y, p.powered() ? TEXT : DIM, false);
            y += 9;
        }
        if (s.parts().size() > PARTS_SHOWN) {
            g.drawString(
                    font,
                    Component.translatable(
                            "gui.zas_atmos.controller.more", s.parts().size() - PARTS_SHOWN),
                    6,
                    y,
                    DIM,
                    false);
        }
    }

    private static Component label(String reading) {
        return switch (reading) {
            case "pressure", "temperature", "others" ->
                Component.translatable("gui.zas_atmos.controller.reading." + reading);
            default -> {
                ResourceLocation id = ResourceLocation.tryParse(reading);
                yield id == null
                        ? Component.literal(reading)
                        : Component.translatable("gas." + id.getNamespace() + "." + id.getPath());
            }
        };
    }

    private static String unit(int reading) {
        return reading == 1 ? " K" : " kPa";
    }

    private static String format(double v) {
        if (v != 0 && Math.abs(v) < 0.1) {
            return String.format(Locale.ROOT, "%.3f", v);
        }
        return String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
