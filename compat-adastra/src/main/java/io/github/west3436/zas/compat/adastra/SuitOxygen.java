package io.github.west3436.zas.compat.adastra;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The oxygen an Ad Astra suit holds, as the tank ZAS³'s internals breathe from (SPACE-023). The
 * store is in the suit's chest piece and is reached through NeoForge's fluid item capability, so
 * nothing of Ad Astra's is named here but three item ids and a fluid tag.
 *
 * <p>The store behaves as one ZAS³ O₂ tank: a full one counts as 17.5 mol of O₂ at 293.15 K in 70 L,
 * whatever its size in mB, scaled by how full it is, and it delivers at an O₂ tank's 21.3 kPa. That
 * is a gameplay scale, kept here.
 *
 * <p>A store too low to deliver that pressure isn't offered as a tank at all. Internals stop at the
 * first tank they find, and unlike a tank in a suit's slot this one can't be taken out, so an empty
 * one would otherwise keep a tank in the hotbar from ever being used.
 *
 * <p>A breath is a small fraction of 1 mB, the least the store gives up. So oxygen leaves the store
 * a whole mB at a time, ahead of use, and what hasn't been breathed yet is remembered for the worn
 * stack. It is forgotten when the stack is: at most 1 mB, never in the wearer's favour.
 */
final class SuitOxygen implements IInternalSupply {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The chest pieces that hold a suit's oxygen, by path in Ad Astra's namespace. */
    static final List<String> CHEST_PIECES = List.of("space_suit", "netherite_space_suit", "jet_suit");

    /** What a full store counts as, in moles of O₂: one ZAS³ O₂ tank's (CONT-030). */
    static final double FULL_MOLES = 17.5;

    /** The volume of the tank the store behaves as, in litres. */
    static final double VOLUME = 70.0;

    /** The temperature its oxygen is at, in kelvin. */
    static final double TEMPERATURE = StandardAir.TEMPERATURE;

    /** The pressure it delivers at, in kPa: an O₂ tank's (LIFE-044). */
    static final double DISTRIBUTION_PRESSURE = 21.3;

    /** The fluids Ad Astra's suits hold. */
    private static final TagKey<Fluid> OXYGEN =
            TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath(AdAstraCompat.AD_ASTRA, "oxygen"));

    /**
     * The oxygen taken out of each stack's store and not yet breathed, in moles. Stacks are keyed by
     * identity, and an entry goes when its stack does.
     */
    private static final Map<ItemStack, Double> TAKEN = Collections.synchronizedMap(new WeakHashMap<>());

    private final ItemStack stack;

    private SuitOxygen(ItemStack stack) {
        this.stack = stack;
    }

    /** Makes the three chest pieces internal supplies. On the mod bus. */
    static void register(RegisterCapabilitiesEvent event) {
        for (String path : CHEST_PIECES) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AdAstraCompat.AD_ASTRA, path);
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isPresent()) {
                event.registerItem(IInternalSupply.ITEM, (stack, context) -> of(stack), item.get());
            } else {
                LOGGER.warn("Ad Astra has no item {}, so its oxygen can't be used as an internals tank", id);
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
        double moles = TAKEN.getOrDefault(stack, 0.0);
        IFluidHandlerItem store = store(stack);
        if (store != null) {
            moles += store.getFluidInTank(0).getAmount() * molesPerMb(store);
        }
        return new GasMixture(moles > 0 ? Map.of(StandardAir.OXYGEN, moles) : Map.of(), TEMPERATURE, VOLUME);
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
        return given > 0 ? new GasParcel(Map.of(StandardAir.OXYGEN, given), TEMPERATURE) : GasParcel.EMPTY;
    }

    /**
     * Takes whole mB out of the store, enough to cover these moles if it holds that much.
     *
     * @return what was taken out, in moles
     */
    private double takeOut(double moles) {
        IFluidHandlerItem store = store(stack);
        if (store == null) {
            return 0;
        }
        double molesPerMb = molesPerMb(store);
        int wanted = (int) Math.min(store.getTankCapacity(0), Math.ceil(moles / molesPerMb));
        FluidStack drained = store.drain(wanted, FluidAction.EXECUTE);
        ItemStack changed = store.getContainer();
        if (drained.isEmpty() || !changed.is(stack.getItem())) {
            return 0;
        }
        copyComponents(changed, stack);
        return drained.getAmount() * molesPerMb;
    }

    /** A chest piece's store, or null if it has none that holds oxygen or nothing. */
    private static @Nullable IFluidHandlerItem store(ItemStack stack) {
        IFluidHandlerItem store = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (store == null || store.getTanks() < 1 || store.getTankCapacity(0) <= 0) {
            return null;
        }
        FluidStack held = store.getFluidInTank(0);
        return held.isEmpty() || held.is(OXYGEN) ? store : null;
    }

    private static double molesPerMb(IFluidHandlerItem store) {
        return FULL_MOLES / store.getTankCapacity(0);
    }

    /**
     * Makes a stack's components those of a changed copy of it. The capability works on a copy of the
     * stack it was asked for, and the stack worn has to stay the one worn.
     */
    private static void copyComponents(ItemStack from, ItemStack to) {
        if (from == to) {
            return;
        }
        List<DataComponentType<?>> gone = new ArrayList<>();
        for (DataComponentType<?> type : to.getComponents().keySet()) {
            if (!from.has(type)) {
                gone.add(type);
            }
        }
        gone.forEach(to::remove);
        for (TypedDataComponent<?> component : from.getComponents()) {
            set(to, component);
        }
    }

    private static <T> void set(ItemStack to, TypedDataComponent<T> component) {
        to.set(component.type(), component.value());
    }
}
