package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.content.NorthstarDataComponents;
import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The oxygen a Northstar space suit holds, as the tank ZAS³'s internals breathe from, as Ad Astra's
 * suits are. The store is the chest piece's {@code northstar:oxygen}, in mB, which Northstar's
 * oxygen machines fill.
 *
 * <p>The store behaves as one ZAS³ O₂ tank, on the same gameplay scale as Ad Astra's suits: a chest
 * piece holding Northstar's default base capacity, 1800 mB, counts as 17.5 mol of O₂ at 293.15 K in 70 L, and one that
 * holds more, with Create's Capacity enchantment, counts as more. It delivers at an O₂ tank's
 * 21.3 kPa, and a store too low for that isn't offered as a tank.
 *
 * <p>It is {@linkplain #alwaysOn() always on}: a suit has no switch, and under Northstar alone its
 * oxygen is breathed whenever the whole suit is worn. Oxygen leaves the store a whole mB at a time,
 * ahead of use, and what hasn't been breathed yet is remembered for the worn stack.
 */
final class SuitOxygen implements IInternalSupply {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The chest pieces that hold a suit's oxygen, by path in Northstar's namespace. */
    static final List<String> CHEST_PIECES =
            List.of("iron_space_suit_chestpiece", "martian_steel_space_suit_chestpiece");

    /** What a store of Northstar's base capacity counts as, in moles of O₂: one ZAS³ O₂ tank's. */
    static final double FULL_MOLES = 17.5;

    /**
     * Northstar's default base capacity, in mB. A server that changes it keeps this scale per mB, so
     * a bigger store counts as more oxygen.
     */
    static final int BASE_CAPACITY = 1800;

    /** The volume of the tank the store behaves as, in litres. */
    static final double VOLUME = 70.0;

    /** The temperature its oxygen is at, in kelvin. */
    static final double TEMPERATURE = StandardAir.TEMPERATURE;

    /** The pressure it delivers at, in kPa: an O₂ tank's (LIFE-044). */
    static final double DISTRIBUTION_PRESSURE = 21.3;

    /** The oxygen taken out of each stack's store and not yet breathed, in moles, by identity. */
    private static final Map<ItemStack, Double> TAKEN = Collections.synchronizedMap(new WeakHashMap<>());

    private final ItemStack stack;

    private SuitOxygen(ItemStack stack) {
        this.stack = stack;
    }

    /** Makes the chest pieces internal supplies. On the mod bus. */
    static void register(RegisterCapabilitiesEvent event) {
        for (String path : CHEST_PIECES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NorthstarVersions.MOD_ID, path);
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent()) {
                event.registerItem(IInternalSupply.ITEM, (stack, context) -> of(stack), item.get());
            } else {
                LOGGER.warn("Northstar has no item {}, so its oxygen can't be used as an internals tank", id);
            }
        }
    }

    /** A chest piece's store as a tank, or null while it can't deliver its pressure. */
    static @Nullable IInternalSupply of(ItemStack stack) {
        SuitOxygen supply = new SuitOxygen(stack);
        return supply.contents().pressure() >= DISTRIBUTION_PRESSURE ? supply : null;
    }

    @Override
    public GasMixture contents() {
        double moles = TAKEN.getOrDefault(stack, 0.0) + stored(stack) * molesPerMb();
        return new GasMixture(moles > 0 ? Map.of(StandardAir.OXYGEN, moles) : Map.of(), TEMPERATURE, VOLUME);
    }

    @Override
    public double distributionPressure() {
        return DISTRIBUTION_PRESSURE;
    }

    /** True: a suit has no switch, so its oxygen needs none of ours. */
    @Override
    public boolean alwaysOn() {
        return true;
    }

    @Override
    public GasParcel draw(double moles) {
        if (!(moles > 0)) {
            return GasParcel.EMPTY;
        }
        double taken = TAKEN.getOrDefault(stack, 0.0);
        if (taken < moles) {
            int stored = stored(stack);
            int wanted = (int) Math.min(stored, Math.ceil((moles - taken) / molesPerMb()));
            if (wanted > 0) {
                stack.set(NorthstarDataComponents.OXYGEN, stored - wanted);
                taken += wanted * molesPerMb();
            }
        }
        double given = Math.min(moles, taken);
        if (taken > given) {
            TAKEN.put(stack, taken - given);
        } else {
            TAKEN.remove(stack);
        }
        return given > 0 ? new GasParcel(Map.of(StandardAir.OXYGEN, given), TEMPERATURE) : GasParcel.EMPTY;
    }

    /** The mB of oxygen a chest piece holds. */
    static int stored(ItemStack stack) {
        return Math.max(0, stack.getOrDefault(NorthstarDataComponents.OXYGEN, 0));
    }

    /** Moles of O₂ per mB of Northstar's oxygen. */
    static double molesPerMb() {
        return FULL_MOLES / BASE_CAPACITY;
    }
}
