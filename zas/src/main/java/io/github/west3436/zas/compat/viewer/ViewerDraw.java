package io.github.west3436.zas.compat.viewer;

import io.github.west3436.zas.ZasMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws what the viewers show, so both draw it alike. Client only: a plugin calls this from the
 * methods its viewer calls to draw, never while it registers.
 */
public final class ViewerDraw {
    private static final ResourceLocation GAS =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "textures/gui/viewer/gas.png");

    /** The sheet of chip icons ({@link Icon}), which also holds the reactions' own 16 by 16 icon. */
    public static final ResourceLocation ICONS =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "textures/gui/viewer/icons.png");

    /** Where the reactions' icon is on the sheet. */
    public static final int REACTIONS_ICON_U = 48;

    public static final int REACTIONS_ICON_V = 16;

    private ViewerDraw() {}

    /** A line as a component. */
    public static Component text(Line line) {
        return switch (line) {
            case Line.Text plain -> Component.literal(plain.text());
            case Line.Key keyed -> {
                Object[] args = keyed.args().toArray();
                yield keyed.fallback().isEmpty()
                        ? Component.translatable(keyed.key(), args)
                        : Component.translatableWithFallback(keyed.key(), keyed.fallback(), args);
            }
        };
    }

    /** A line's width in the game's font. */
    public static int width(Line line) {
        return font().width(text(line));
    }

    /** A reaction's page, measured with the game's font. */
    public static ViewerLayout.Page page(ReactionEntry entry) {
        return ViewerLayout.reaction(entry, ViewerDraw::width);
    }

    /** A gas's page, measured with the game's font. */
    public static ViewerLayout.Page page(GasEntry gas) {
        return ViewerLayout.gas(gas, ViewerDraw::width);
    }

    /** A gas's name. */
    public static Component name(GasEntry gas) {
        return Component.translatable(gas.nameKey());
    }

    /**
     * Draws a gas in a 16 by 16 square: its icon in its colour, and its amount in the corner, as an
     * item's count is, unless there is one of it.
     */
    public static void gas(GuiGraphics graphics, GasStack stack, int x, int y) {
        int colour = stack.gas().colour();
        graphics.setColor((colour >> 16 & 0xFF) / 255f, (colour >> 8 & 0xFF) / 255f, (colour & 0xFF) / 255f, 1f);
        graphics.blit(GAS, x, y, 0, 0, 16, 16, 16, 16);
        graphics.setColor(1f, 1f, 1f, 1f);
        if (stack.amount() != 1) {
            Font font = font();
            String amount = ViewerNumbers.amount(stack.amount());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.drawString(font, amount, x + 17 - font.width(amount), y + 9, 0xFFFFFF, true);
            graphics.pose().popPose();
        }
    }

    /** Draws the reactions' icon, a flask, in a 16 by 16 square. */
    public static void reactionsIcon(GuiGraphics graphics, int x, int y) {
        graphics.blit(ICONS, x, y, REACTIONS_ICON_U, REACTIONS_ICON_V, 16, 16, Icon.SHEET_WIDTH, Icon.SHEET_HEIGHT);
    }

    /** Draws a page's text and chips. The viewer draws the slots and the arrow itself. */
    public static void page(GuiGraphics graphics, ViewerLayout.Page page) {
        Font font = font();
        for (ViewerLayout.Text text : page.texts()) {
            line(graphics, text(text.line()), text.x(), text.y(), page.width() - text.x(), text.colour());
        }
        for (ViewerLayout.PlacedChip placed : page.chips()) {
            Icon icon = placed.chip().icon();
            graphics.blit(
                    ICONS,
                    placed.x(),
                    placed.y(),
                    icon.u(),
                    icon.v(),
                    Icon.SIZE,
                    Icon.SIZE,
                    Icon.SHEET_WIDTH,
                    Icon.SHEET_HEIGHT);
            if (!placed.chip().text().isEmpty()) {
                int x = placed.x() + Icon.SIZE + ViewerLayout.ICON_GAP;
                line(
                        graphics,
                        text(placed.chip().text()),
                        x,
                        placed.y() + 1,
                        page.width() - x,
                        ViewerLayout.TEXT_COLOUR);
            }
        }
    }

    /** Draws a line, cut short with an ellipsis if it is wider than the room it has. */
    private static void line(GuiGraphics graphics, Component text, int x, int y, int room, int colour) {
        Font font = font();
        if (font.width(text) <= room) {
            graphics.drawString(font, text, x, y, colour, false);
            return;
        }
        FormattedText cut = font.substrByWidth(text, room - font.width(CommonComponents.ELLIPSIS));
        graphics.drawString(
                font,
                Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS)),
                x,
                y,
                colour,
                false);
    }

    /**
     * What shows when the pointer is over a gas: its name and formula, and its id when the player
     * has advanced tooltips on.
     */
    public static List<Component> tooltip(GasStack stack, boolean advanced) {
        GasEntry gas = stack.gas();
        List<Component> out = new ArrayList<>();
        out.add(name(gas));
        out.add(Component.literal(gas.formula()).withStyle(ChatFormatting.GRAY));
        if (advanced) {
            out.add(Component.literal(gas.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }
}
