package io.github.west3436.zas.atmos.battery;

import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A battery (CONT-111): an item holding up to {@link #capacity()} FE in its {@code zas_atmos:energy}
 * component, which other mods reach through the FE item capability ({@link
 * io.github.west3436.zas.atmos.registry.AtmosCapabilities}). One to a stack, so each holds its own
 * charge. Its durability bar and tooltip show the charge.
 */
public class BatteryItem extends Item {
    /** The bar's colour: vanilla's durability green. */
    private static final int BAR = 0x55FF55;

    private final int capacity;

    public BatteryItem(int capacity, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
    }

    /** The most FE it holds. */
    public int capacity() {
        return capacity;
    }

    /** The FE a battery stack holds, 0 to its capacity. */
    public static int energy(ItemStack stack) {
        int stored = stack.getOrDefault(AtmosComponents.ENERGY.get(), 0);
        return stack.getItem() instanceof BatteryItem b ? Math.max(0, Math.min(b.capacity, stored)) : stored;
    }

    /** A new stack of this battery, charged full. */
    public ItemStack full() {
        ItemStack stack = new ItemStack(this);
        stack.set(AtmosComponents.ENERGY.get(), capacity);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * energy(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.zas_atmos.battery.charge", fe(energy(stack)), fe(capacity)));
    }

    /** FE with thousands separators, as the GUIs show it. */
    public static String fe(long fe) {
        return String.format(Locale.ROOT, "%,d", fe);
    }

    /** The charge as a fraction, 0 to 1, for the item model's {@code zas_atmos:charge} property. */
    public static float charge(ItemStack stack) {
        return stack.getItem() instanceof BatteryItem b ? Mth.clamp((float) energy(stack) / b.capacity, 0, 1) : 0;
    }
}
