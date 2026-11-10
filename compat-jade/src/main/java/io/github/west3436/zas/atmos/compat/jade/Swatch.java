package io.github.west3436.zas.atmos.compat.jade;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;

/** A square of a gas's colour, as the device windows put before a gas's name. */
final class Swatch extends Element {
    /** Its box, the height of a line of text; the square is drawn inside it. */
    private static final int BOX = 8;

    private static final int SQUARE = 6;

    private final int colour;

    /** @param colour ARGB */
    Swatch(int colour) {
        this.colour = colour;
    }

    @Override
    public Vec2 getSize() {
        return new Vec2(BOX, BOX);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        IDisplayHelper.get().drawGradientRect(graphics, x + 1, y + 1, SQUARE, SQUARE, colour, colour);
    }
}
