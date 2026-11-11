package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.client.tgui.Readout;
import io.github.west3436.zas.atmos.client.tgui.Ui;
import io.github.west3436.zas.atmos.client.tgui.Ui.Face;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * How a window shows gases: each one's colour, a bar of their shares, and a list of what's there.
 * The colours are public for whatever else shows gases beside the windows, such as the Jade adapter.
 */
public final class GasParts {
    /**
     * Each gas's colour. Ours are after SS13's canisters. The gases the Mekanism adapter adds
     * (MEK-010) have the tint Mekanism gives the chemical each one is, read from Mekanism 10.7.19,
     * so a gas is the same colour in a Mekanism tank and in our windows.
     */
    private static final Map<String, Integer> COLOURS = Map.ofEntries(
            Map.entry("zas:oxygen", 0xFF2A79C8),
            Map.entry("zas:nitrogen", 0xFFD92626),
            Map.entry("zas:carbon_dioxide", 0xFF7A7A7A),
            Map.entry("zas:nitrous_oxide", 0xFFE7A0A0),
            Map.entry("zas:carbon_monoxide", 0xFF8A4FD0),
            Map.entry("zas:hydrogen", 0xFFF26C0D),
            Map.entry("zas:methane", 0xFFA96A3C),
            Map.entry("zas:water_vapor", 0xFFE8E8E8),
            Map.entry("zas:chlorine", 0xFFCFE800),
            Map.entry("zas:hydrogen_chloride", 0xFFA8F1E9),
            Map.entry("zas:sulfur_dioxide", 0xFFA99D90),
            Map.entry("zas:ethene", 0xFFEACCF9),
            Map.entry("zas:deuterium", 0xFFFF3232),
            Map.entry("zas:tritium", 0xFF64FF70));

    /** The colour of a gas that has none of its own, and of the gases a bar doesn't tell apart. */
    public static final int OTHER = 0xFFB0B0B0;

    private static final int BAR_HEIGHT = 5;
    private static final int COLUMNS = 2;

    /** The most rows {@link #shares} lists; past it, the last row adds the smallest gases together. */
    static final int MOST_SHARE_ROWS = 12;

    /** Where a row's share ends, from the right edge: the partial pressure's column is the rest. */
    private static final int PRESSURE_COLUMN = 56;

    private GasParts() {}

    /** A gas's colour, ARGB, by its id. */
    public static int colour(String gas) {
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
        bar(ui, x, y, w, sorted);
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

    /** The bar of each gas's share, in the order given. */
    private static void bar(Ui ui, int x, int y, int w, Map<ResourceLocation, Double> moles) {
        double total = moles.values().stream().mapToDouble(Double::doubleValue).sum();
        ui.track(x, y, w, BAR_HEIGHT);
        float at = x;
        for (Map.Entry<ResourceLocation, Double> gas : moles.entrySet()) {
            float part = total > 0 ? (float) (w * gas.getValue() / total) : 0;
            ui.box(at, y, part, BAR_HEIGHT, colour(gas.getKey().toString()));
            at += part;
        }
    }

    /** How tall {@link #shares} draws for a mixture of this many gases. */
    static int sharesHeight(int gases) {
        return gases == 0 ? 0 : BAR_HEIGHT + 2 + Math.min(gases, MOST_SHARE_ROWS) * Ui.ROW;
    }

    /**
     * One bar of each gas's share, then a row for each gas: its share, and its partial pressure in the
     * player's unit. Gases keep their place, by id. With more than {@link #MOST_SHARE_ROWS} gases, the
     * smallest are added together in the last row.
     *
     * @param rowsFrom where the striped rows start on the left, which can be left of {@code x}
     * @param rowsWidth how wide the stripes are
     * @param colour the shares' colour
     */
    static void shares(Ui ui, int x, int y, int w, int rowsFrom, int rowsWidth, GasMixture gas, int colour) {
        Map<ResourceLocation, Double> moles = new TreeMap<>(gas.moles());
        if (moles.isEmpty()) {
            return;
        }
        bar(ui, x, y, w, moles);
        double total = gas.totalMoles();
        List<ResourceLocation> listed = new ArrayList<>(moles.keySet());
        double others = 0;
        if (listed.size() > MOST_SHARE_ROWS) {
            listed.sort(Comparator.comparingDouble((ResourceLocation id) -> moles.get(id))
                    .reversed());
            for (ResourceLocation id : listed.subList(MOST_SHARE_ROWS - 1, listed.size())) {
                others += moles.get(id);
            }
            listed = new ArrayList<>(listed.subList(0, MOST_SHARE_ROWS - 1));
            listed.sort(Comparator.naturalOrder());
        }
        int rowY = y + BAR_HEIGHT + 2;
        int i = 0;
        for (ResourceLocation id : listed) {
            row(ui, x, rowY, w, rowsFrom, rowsWidth, i++, label(id), moles.get(id), total, gas, colour);
            rowY += Ui.ROW;
        }
        if (others > 0) {
            Component label = Component.literal("■ ")
                    .withColor(OTHER & 0xFFFFFF)
                    .append(Component.translatable("gui.zas_atmos.analyzer.others")
                            .withColor(0xFFFFFF));
            row(ui, x, rowY, w, rowsFrom, rowsWidth, i, label, others, total, gas, colour);
        }
    }

    private static void row(
            Ui ui,
            int x,
            int y,
            int w,
            int rowsFrom,
            int rowsWidth,
            int index,
            Component label,
            double moles,
            double total,
            GasMixture gas,
            int colour) {
        if (index % 2 == 1) {
            ui.stripe(rowsFrom, y, rowsWidth, Ui.ROW);
        }
        ui.line(label, x, y, Ui.ROW, Ui.WHITE, Face.BODY);
        double share = total > 0 ? moles / total : 0;
        ui.lineRight(
                DeviceParts.text("percent", Readout.percentNumber(share)),
                x + w - PRESSURE_COLUMN,
                y,
                Ui.ROW,
                colour,
                Face.BODY);
        ui.lineRight(
                Component.literal(Readout.pressure(gas.pressure() * share)), x + w, y, Ui.ROW, Ui.LABEL, Face.BODY);
    }

    private static Component moles(double moles) {
        return DeviceParts.text("moles", String.format(Locale.ROOT, "%,.1f", moles));
    }
}
