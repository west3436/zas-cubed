package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.client.GasParts;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

/**
 * The readout's client part: draws the reading the server sent for the block looked at (COMPAT-030,
 * COMPAT-031), and nothing where it sent none (COMPAT-032).
 *
 * <p>A row is an icon and a pressure, then a thermometer and a temperature, in the player's units.
 * Several rows keep their numbers in columns. A single body of gas has a bar of its gases' shares
 * below, in the device windows' colours, and a line for each gas listed. There are no words but the
 * gases' names.
 */
enum ReadoutTooltip implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    /** The icons' side, the height of a line of text. */
    private static final int ICON = 8;

    /** Between an icon and its number. */
    private static final int GAP = 2;

    /** Between a row's pressure and its temperature, and the least between a gas's name and its share. */
    private static final int WIDE_GAP = 6;

    private static final ResourceLocation PRESSURE = ZasAtmosMod.id("jade/pressure");
    private static final ResourceLocation TEMPERATURE = ZasAtmosMod.id("jade/temperature");
    private static final ResourceLocation IN = ZasAtmosMod.id("jade/in");
    private static final ResourceLocation OUT = ZasAtmosMod.id("jade/out");
    private static final ResourceLocation SIDE = ZasAtmosMod.id("jade/side");

    @Override
    public ResourceLocation getUid() {
        return ReadoutData.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(ReadoutData.KEY, Tag.TAG_BYTE_ARRAY)) {
            return;
        }
        ReadingCodec.decode(data.getByteArray(ReadoutData.KEY)).ifPresent(reading -> draw(tooltip, reading));
    }

    private static void draw(ITooltip tooltip, Reading reading) {
        IElementHelper elements = IElementHelper.get();
        Font font = Minecraft.getInstance().font;
        // The widest of each number, so the rows' numbers end at the same place.
        int pressureWidth = 0;
        int temperatureWidth = 0;
        for (Reading.Row row : reading.rows()) {
            if (row.hasPressure()) {
                pressureWidth = Math.max(pressureWidth, font.width(ReadoutText.pressure(row.kpa())));
            }
            if (row.hasTemperature()) {
                temperatureWidth = Math.max(temperatureWidth, font.width(ReadoutText.temperature(row.kelvin())));
            }
        }
        for (Reading.Row row : reading.rows()) {
            List<IElement> line = row(elements, font, row, pressureWidth, temperatureWidth);
            if (!line.isEmpty()) {
                tooltip.add(line);
            }
        }
        if (reading.shares().isEmpty()) {
            return;
        }
        tooltip.add(new ShareBar(reading.shares()));
        for (Reading.Share share : reading.shares()) {
            ResourceLocation gas = ResourceLocation.tryParse(share.gas());
            if (gas == null) {
                continue;
            }
            tooltip.add(List.of(
                    new Swatch(GasParts.colour(share.gas())),
                    elements.spacer(GAP, ICON),
                    elements.text(GasCodecs.name(gas)),
                    elements.spacer(WIDE_GAP, ICON)));
            Component percent =
                    Component.translatable("gui.zas_atmos.device.percent", ReadoutText.percent(share.fraction()));
            tooltip.append(elements.text(percent).align(IElement.Align.RIGHT));
        }
    }

    /**
     * A row's icons and numbers, left to right. Each number is pushed right by what it lacks of its
     * column's width.
     */
    private static List<IElement> row(
            IElementHelper elements, Font font, Reading.Row row, int pressureWidth, int temperatureWidth) {
        List<IElement> line = new ArrayList<>(8);
        if (row.hasPressure()) {
            String pressure = ReadoutText.pressure(row.kpa());
            line.add(lead(elements, row));
            line.add(elements.spacer(GAP + pressureWidth - font.width(pressure), ICON));
            line.add(elements.text(Component.literal(pressure)));
        }
        if (row.hasTemperature()) {
            String temperature = ReadoutText.temperature(row.kelvin());
            if (!line.isEmpty()) {
                line.add(elements.spacer(WIDE_GAP, ICON));
            }
            line.add(elements.sprite(TEMPERATURE, ICON, ICON));
            line.add(elements.spacer(GAP + temperatureWidth - font.width(temperature), ICON));
            line.add(elements.text(Component.literal(temperature)));
        }
        return line;
    }

    /** What stands in front of a row's pressure. */
    private static IElement lead(IElementHelper elements, Reading.Row row) {
        return switch (row.lead()) {
            case BODY -> elements.sprite(PRESSURE, ICON, ICON);
            case IN -> elements.sprite(IN, ICON, ICON);
            case OUT -> elements.sprite(OUT, ICON, ICON);
            case SIDE -> elements.sprite(SIDE, ICON, ICON);
            case GAS -> new Swatch(GasParts.colour(row.id()));
            case ITEM -> {
                Item item = item(row.id());
                yield item == null ? elements.sprite(PRESSURE, ICON, ICON) : elements.smallItem(new ItemStack(item));
            }
        };
    }

    /** The item an id names, or null if it names none. */
    private static @Nullable Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }
}
