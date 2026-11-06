package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** How a window shows gases: each one's colour, a bar of their shares, and a list of what's there. */
final class GasParts {
    /** Each gas's colour, after SS13's canisters. */
    private static final Map<String, Integer> COLOURS = Map.of(
            "zas:oxygen", 0xFF2A79C8,
            "zas:nitrogen", 0xFFD92626,
            "zas:carbon_dioxide", 0xFF7A7A7A,
            "zas:nitrous_oxide", 0xFFE7A0A0,
            "zas:carbon_monoxide", 0xFF8A4FD0,
            "zas:hydrogen", 0xFFF26C0D,
            "zas:methane", 0xFFA96A3C,
            "zas:water_vapor", 0xFFE8E8E8);

    private static final int OTHER = 0xFFB0B0B0;
    private static final int BAR_HEIGHT = 5;
    private static final int COLUMNS = 2;

    private GasParts() {}

    /** A gas's colour, by its id. */
    static int colour(String gas) {
        return COLOURS.getOrDefault(gas, OTHER);
    }

    /** A gas's name, after a square of its colour. */
    static Component label(ResourceLocation gas) {
        return Component.literal("■ ")
                .withColor(colour(gas.toString()) & 0xFFFFFF)
                .append(GasCodecs.name(gas).copy().withColor(0xFFFFFF));
    }

    /** How tall {@link #contents} draws. */
    static int contentsHeight(int gases) {
        return gases == 0 ? 0 : BAR_HEIGHT + 2 + (gases + COLUMNS - 1) / COLUMNS * Ui.ROW;
    }

    /** One bar of each gas's share, and each gas's moles below it. Gases keep their place, by id. */
    static void contents(Ui ui, int x, int y, int w, Map<ResourceLocation, Double> moles) {
        if (moles.isEmpty()) {
            return;
        }
        Map<ResourceLocation, Double> sorted = new TreeMap<>(moles);
        double total = sorted.values().stream().mapToDouble(Double::doubleValue).sum();
        ui.track(x, y, w, BAR_HEIGHT);
        float at = x;
        for (Map.Entry<ResourceLocation, Double> gas : sorted.entrySet()) {
            float part = total > 0 ? (float) (w * gas.getValue() / total) : 0;
            ui.box(at, y, part, BAR_HEIGHT, colour(gas.getKey().toString()));
            at += part;
        }
        int gap = 4;
        int columnWidth = (w - gap) / COLUMNS;
        int i = 0;
        for (Map.Entry<ResourceLocation, Double> gas : sorted.entrySet()) {
            int columnX = x + i % COLUMNS * (columnWidth + gap);
            int rowY = y + BAR_HEIGHT + 2 + i / COLUMNS * Ui.ROW;
            ui.line(label(gas.getKey()), columnX, rowY, Ui.ROW, Ui.WHITE, Face.BODY);
            ui.lineRight(moles(gas.getValue()), columnX + columnWidth, rowY, Ui.ROW, Ui.LABEL, Face.BODY);
            i++;
        }
    }

    private static Component moles(double moles) {
        return DeviceParts.text("moles", String.format(Locale.ROOT, "%,.1f", moles));
    }
}
