package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.atmos.client.GasParts;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IDisplayHelper;

/**
 * A bar of a body's gases, each as wide as its share, in the device windows' colours and order
 * (COMPAT-031). The gases the reading doesn't list take the rest of the bar, in one grey. Alone on
 * its line, it is as wide as the tooltip.
 */
final class ShareBar extends Element {
    /** As tall as the bar in the device windows. */
    private static final int HEIGHT = 5;

    /** The least it is wide; Jade stretches it to the tooltip's width. */
    private static final int LEAST_WIDTH = 48;

    private final List<Reading.Share> shares;

    ShareBar(List<Reading.Share> shares) {
        this.shares = shares;
    }

    @Override
    public Vec2 getSize() {
        return new Vec2(LEAST_WIDTH, HEIGHT);
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        IDisplayHelper display = IDisplayHelper.get();
        float width = maxX - x;
        float at = x;
        for (Reading.Share share : shares) {
            float part = Math.min(width * share.fraction(), maxX - at);
            if (part <= 0) {
                continue;
            }
            int colour = GasParts.colour(share.gas());
            display.drawGradientRect(graphics, at, y, part, HEIGHT, colour, colour);
            at += part;
        }
        if (maxX - at > 0.01F) {
            display.drawGradientRect(graphics, at, y, maxX - at, HEIGHT, GasParts.OTHER, GasParts.OTHER);
        }
    }
}
