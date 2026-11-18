package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A proximity sensor (CONT-187): fitted to a tank transfer valve in its timer's place (CONT-184), it
 * opens the valve of the primed TTV when something living comes within its range. On its own it does
 * nothing. Its range is set in the TTV's window, 1 block to {@code [ttv_triggers]
 * proximity_max_range}, and the sensor keeps it.
 */
public class ProximitySensorItem extends Item {
    /** The narrowest range, blocks. */
    public static final int MIN_RANGE = 1;

    /** A new sensor's range, blocks. */
    public static final int DEFAULT_RANGE = 2;

    public ProximitySensorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The widest range, blocks: {@code [ttv_triggers] proximity_max_range}. */
    public static int maxRange() {
        return AtmosServerConfig.get(AtmosServerConfig.PROXIMITY_MAX_RANGE);
    }

    /** A range clamped to {@value #MIN_RANGE} to {@link #maxRange()} blocks. */
    public static int clampRange(int blocks) {
        return Math.max(MIN_RANGE, Math.min(maxRange(), blocks));
    }

    /** A sensor's range, blocks, clamped as the config now has it. */
    public static int range(ItemStack sensor) {
        return clampRange(sensor.getOrDefault(AtmosComponents.PROXIMITY_RANGE.get(), DEFAULT_RANGE));
    }

    /** Sets a sensor's range, clamped. */
    public static void setRange(ItemStack sensor, int blocks) {
        sensor.set(AtmosComponents.PROXIMITY_RANGE.get(), clampRange(blocks));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.zas_atmos.proximity_sensor.range", range(stack))
                .withStyle(ChatFormatting.GRAY));
    }
}
