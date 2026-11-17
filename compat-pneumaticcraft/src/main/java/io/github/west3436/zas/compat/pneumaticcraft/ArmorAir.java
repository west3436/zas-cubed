package io.github.west3436.zas.compat.pneumaticcraft;

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
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The air in a piece of Pneumatic Armor, as a tank ZAS³'s internals breathe from (COMPAT-011). Each
 * of the four pieces holds its own air, read and changed through PneumaticCraft's item air handler
 * ({@code PNCCapabilities.AIR_HANDLER_ITEM}), so drawing a breath lowers that piece's pressure as
 * PneumaticCraft shows it.
 *
 * <ul>
 *   <li><b>What it holds.</b> Its air is standard air at 293.15 K, so many moles a mL ({@link
 *       PncSettings#molesPerMillilitre}), in the piece's volume, upgrades included. At the default
 *       factor the gas's pressure in kPa is the piece's pressure in bar times 100.
 *   <li><b>How it delivers.</b> At 101.3 kPa, an air tank's distribution pressure (LIFE-044): a
 *       piece delivers while it holds at least about 1 bar, which is most of a full piece's 10 bar.
 *       A piece too low to deliver isn't offered as a tank at all, so that it doesn't keep internals
 *       from a tank found after it.
 *   <li><b>Where it is found.</b> Wherever internals look for a tank (LIFE-028): the armor slots
 *       first, head to feet, so a worn piece is breathed before any tank carried; then the slots
 *       other mods add, the offhand and the hotbar, as for any tank. The wearer's internals switch
 *       decides, as Pneumatic Armor has no breathing switch of its own.
 *   <li><b>In whole mL.</b> A breath is a few mL, and PneumaticCraft keeps whole mL. So air leaves the
 *       piece a whole mL at a time, ahead of use, and what hasn't been breathed yet is remembered
 *       for the stack. It is forgotten when the stack is: less than 1 mL, never in the wearer's
 *       favour.
 * </ul>
 *
 * <p>Its protection is data, in the adapter's data pack (COMPAT-010): this capability gives the air
 * alone, so the data still rates the piece.
 */
final class ArmorAir implements IInternalSupply {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The four pieces, by path in PneumaticCraft's namespace, head to feet. */
    static final List<String> PIECES =
            List.of("pneumatic_helmet", "pneumatic_chestplate", "pneumatic_leggings", "pneumatic_boots");

    /** The pressure it delivers at, in kPa: an air tank's (LIFE-044). */
    static final double DISTRIBUTION_PRESSURE = 101.3;

    /**
     * The air taken out of each stack and not yet breathed, in moles. Stacks are keyed by identity,
     * and an entry goes when its stack does.
     */
    private static final Map<ItemStack, Double> TAKEN = Collections.synchronizedMap(new WeakHashMap<>());

    private final ItemStack stack;

    private ArmorAir(ItemStack stack) {
        this.stack = stack;
    }

    /** Makes the four pieces internal supplies. On the mod bus. */
    static void register(RegisterCapabilitiesEvent event) {
        for (String path : PIECES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(PneumaticCraftCompat.MOD_ID, path);
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent()) {
                event.registerItem(IInternalSupply.ITEM, (stack, context) -> of(stack), item.get());
            } else {
                LOGGER.warn("PneumaticCraft has no item {}, so its air can't be used as an internals tank", id);
            }
        }
    }

    /**
     * A piece's air as a tank, or null while it can't deliver its pressure.
     *
     * @param stack the piece
     * @return the supply, or null
     */
    static @Nullable IInternalSupply of(ItemStack stack) {
        ArmorAir supply = new ArmorAir(stack);
        return supply.contents().pressure() >= DISTRIBUTION_PRESSURE ? supply : null;
    }

    @Override
    public GasMixture contents() {
        double moles = TAKEN.getOrDefault(stack, 0.0);
        double litres = StandardAir.CELL_VOLUME;
        IAirHandlerItem air = handler(stack);
        if (air != null) {
            moles += Math.max(0, air.getAir()) * PncSettings.molesPerMillilitre();
            int volume = air.getVolume();
            if (volume > 0) {
                litres = volume * AirUnits.LITRES_PER_ML;
            }
        }
        GasParcel held = AirUnits.standardAir(moles);
        return new GasMixture(held.moles(), AirUnits.TEMPERATURE, litres);
    }

    @Override
    public double distributionPressure() {
        return DISTRIBUTION_PRESSURE;
    }

    @Override
    public GasParcel draw(double moles) {
        if (!(moles > 0)) {
            return GasParcel.EMPTY;
        }
        double taken = TAKEN.getOrDefault(stack, 0.0);
        if (taken < moles) {
            taken += takeOut(moles - taken);
        }
        double given = Math.min(moles, taken);
        if (taken > given) {
            TAKEN.put(stack, taken - given);
        } else {
            TAKEN.remove(stack);
        }
        return AirUnits.standardAir(given);
    }

    /**
     * Takes whole mL out of the piece, enough to cover these moles if it holds that much.
     *
     * @return what was taken out, in moles
     */
    private double takeOut(double moles) {
        double perMl = PncSettings.molesPerMillilitre();
        IAirHandlerItem air = handler(stack);
        if (air == null || !(perMl > 0)) {
            return 0;
        }
        int held = air.getAir();
        int wanted = (int) Math.min(held, Math.ceil(moles / perMl));
        if (wanted <= 0) {
            return 0;
        }
        int before = held;
        air.addAir(-wanted);
        // PneumaticCraft refuses to change a stack of more than one item.
        int out = before - air.getAir();
        return Math.max(0, out) * perMl;
    }

    private static @Nullable IAirHandlerItem handler(ItemStack stack) {
        return stack.isEmpty() ? null : stack.getCapability(PNCCapabilities.AIR_HANDLER_ITEM);
    }
}
