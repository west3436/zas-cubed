package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * A piece of breathing gear worn in an armor slot: a softsuit or hardsuit piece (CONT-032,
 * CONT-036), the breath mask (CONT-031), the gas mask (CONT-088) or the magnetic boots ({@link
 * MagneticBootsItem}, CONT-033). What it protects against is its
 * {@link Rating}, which its {@link ILifeSupport} capability gives ZAS³ (API-022). A hardsuit piece
 * also slows its wearer ({@link Encumbrance}, CONT-089).
 *
 * <p>A chestplate with a tank slot (CONT-090) holds one gas tank, as {@link
 * AtmosComponents#SUIT_TANK}, and gives it to internals first (LIFE-028). Like a bundle, it takes a
 * tank right-clicked onto it, swapping out the one it held, and gives its tank up to an empty
 * cursor right-clicking it.
 */
public class SuitItem extends ArmorItem {
    /**
     * What a piece of gear protects against (LIFE-020, LIFE-021, LIFE-025, API-022).
     *
     * @param minPressure the lowest pressure it protects against, kPa
     * @param maxPressure the highest, kPa
     * @param minTemperature the lowest temperature it protects against, K
     * @param maxTemperature the highest, K
     * @param sealedBreathing whether it seals the breathing, for internals (head slot only)
     * @param gasFiltering whether it filters the {@code #zas:toxic} gases out of the breath (head slot only)
     */
    public record Rating(
            double minPressure,
            double maxPressure,
            double minTemperature,
            double maxTemperature,
            boolean sealedBreathing,
            boolean gasFiltering) {
        /** The softsuit's (CONT-032): down to vacuum, 0 to 500 K, no high-pressure protection. */
        public static Rating softsuit(boolean helmet) {
            return new Rating(0, StandardAir.PRESSURE, 0, 500, helmet, false);
        }

        /** The hardsuit's (CONT-036): every pressure and every temperature. */
        public static Rating hardsuit(boolean helmet) {
            return new Rating(0, Double.POSITIVE_INFINITY, 0, Double.POSITIVE_INFINITY, helmet, false);
        }

        /** The breath mask's (CONT-031): a seal, and no protection. */
        public static final Rating MASK = new Rating(
                StandardAir.PRESSURE,
                StandardAir.PRESSURE,
                StandardAir.TEMPERATURE,
                StandardAir.TEMPERATURE,
                true,
                false);

        /** The gas mask's (CONT-088): a seal and the filter, and no other protection. */
        public static final Rating GAS_MASK = new Rating(
                StandardAir.PRESSURE,
                StandardAir.PRESSURE,
                StandardAir.TEMPERATURE,
                StandardAir.TEMPERATURE,
                true,
                true);
    }

    private final Rating rating;
    private final boolean tankSlot;

    /**
     * @param material its armor material
     * @param type its armor slot
     * @param properties its properties
     * @param rating what it protects against
     * @param tankSlot whether it has a tank slot (CONT-090)
     */
    public SuitItem(Holder<ArmorMaterial> material, Type type, Properties properties, Rating rating, boolean tankSlot) {
        super(material, type, properties.stacksTo(1));
        this.rating = rating;
        this.tankSlot = tankSlot;
    }

    /** What it protects against. */
    public Rating rating() {
        return rating;
    }

    /** Whether it has a tank slot. */
    public boolean hasTankSlot() {
        return tankSlot;
    }

    /** How much a stack of it resists airflow, 0 to 1 (FLOW-012): none, but for magnetic boots that are on. */
    protected double airflowResistance(ItemStack stack) {
        return 0;
    }

    // ---- The tank slot -----------------------------------------------------------------------

    /** The tank in a suit stack's tank slot, or an empty stack. */
    public static ItemStack tank(ItemStack suit) {
        ItemContainerContents held = suit.get(AtmosComponents.SUIT_TANK.get());
        return held == null ? ItemStack.EMPTY : held.copyOne();
    }

    /** Puts a tank in a suit stack's tank slot, or empties it. */
    public static void setTank(ItemStack suit, ItemStack tank) {
        if (tank.isEmpty()) {
            suit.remove(AtmosComponents.SUIT_TANK.get());
        } else {
            suit.set(AtmosComponents.SUIT_TANK.get(), ItemContainerContents.fromItems(List.of(tank.copyWithCount(1))));
        }
    }

    private boolean takes(ItemStack stack) {
        return tankSlot && stack.getItem() instanceof GasTankItem;
    }

    /** The suit carried onto a slot: right-clicked onto a tank it takes it, onto an empty slot it puts its tank there. */
    @Override
    public boolean overrideStackedOnOther(ItemStack suit, Slot slot, ClickAction action, Player player) {
        if (!tankSlot || action != ClickAction.SECONDARY || suit.getCount() != 1) {
            return false;
        }
        ItemStack other = slot.getItem();
        ItemStack held = tank(suit);
        if (other.isEmpty()) {
            if (held.isEmpty() || !slot.mayPlace(held)) {
                return false;
            }
            slot.set(held);
            setTank(suit, ItemStack.EMPTY);
            sound(player, false);
            return true;
        }
        if (!takes(other) || !slot.allowModification(player) || !held.isEmpty()) {
            return false;
        }
        setTank(suit, slot.safeTake(1, 1, player));
        sound(player, true);
        return true;
    }

    /** A tank right-clicked onto the suit goes into its slot, swapping its tank out; an empty cursor takes its tank. */
    @Override
    public boolean overrideOtherStackedOnMe(
            ItemStack suit, ItemStack carried, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (!tankSlot || action != ClickAction.SECONDARY || suit.getCount() != 1 || !slot.allowModification(player)) {
            return false;
        }
        ItemStack held = tank(suit);
        if (carried.isEmpty()) {
            if (held.isEmpty()) {
                return false;
            }
            setTank(suit, ItemStack.EMPTY);
            access.set(held);
            sound(player, false);
            return true;
        }
        if (!takes(carried) || carried.getCount() != 1) {
            return false;
        }
        setTank(suit, carried.copy());
        access.set(held);
        sound(player, true);
        return true;
    }

    private static void sound(Player player, boolean in) {
        player.playSound(in ? SoundEvents.BUNDLE_INSERT : SoundEvents.BUNDLE_REMOVE_ONE, 0.8f, 1.0f);
    }

    /**
     * A chestplate crafted into another item, as the softsuit's is into the hardsuit's, leaves the
     * tank it held in the grid, as a bucket is left.
     */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack suit) {
        return tankSlot && !tank(suit).isEmpty();
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack suit) {
        return tankSlot ? tank(suit) : ItemStack.EMPTY;
    }

    // ---- The tank's atmos tick ---------------------------------------------------------------

    /** The tank in the slot has its atmos tick while the suit is worn or carried (CONT-100). */
    @Override
    public void inventoryTick(ItemStack suit, Level level, Entity entity, int slot, boolean selected) {
        tankAtmosTick(suit, level, entity);
    }

    /** The tank in the slot has its atmos tick while the suit lies dropped (CONT-100). */
    @Override
    public boolean onEntityItemUpdate(ItemStack suit, ItemEntity entity) {
        tankAtmosTick(suit, entity.level(), entity);
        return false;
    }

    private void tankAtmosTick(ItemStack suit, Level level, Entity holder) {
        if (tankSlot && level instanceof ServerLevel server && AtmosTicks.due(server, holder)) {
            ItemStack tank = tank(suit);
            if (tank.getItem() instanceof GasTankItem && GasTankItem.atmosTick(tank, server, holder)) {
                setTank(suit, tank);
            }
        }
    }

    // ---- Life support ------------------------------------------------------------------------

    /** A gear stack's life support: its rating, and the tank in its slot as an internal supply. */
    public static ILifeSupport lifeSupport(ItemStack stack) {
        SuitItem item = (SuitItem) stack.getItem();
        Rating r = item.rating;
        return new ILifeSupport() {
            @Override
            public double minPressure() {
                return r.minPressure();
            }

            @Override
            public double maxPressure() {
                return r.maxPressure();
            }

            @Override
            public double minTemperature() {
                return r.minTemperature();
            }

            @Override
            public double maxTemperature() {
                return r.maxTemperature();
            }

            @Override
            public boolean sealedBreathing() {
                return r.sealedBreathing();
            }

            @Override
            public boolean gasFiltering() {
                return r.gasFiltering();
            }

            @Override
            public double airflowResistance() {
                return item.airflowResistance(stack);
            }

            @Override
            public Optional<IInternalSupply> internalSupply() {
                if (!item.tankSlot) {
                    return Optional.empty();
                }
                ItemStack tank = tank(stack);
                if (!(tank.getItem() instanceof GasTankItem)) {
                    return Optional.empty();
                }
                return Optional.of(GasTankItem.supply(tank, () -> setTank(stack, tank)));
            }
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (!tankSlot) {
            return;
        }
        ItemStack tank = tank(stack);
        if (tank.isEmpty()) {
            tooltip.add(Component.translatable("item.zas_atmos.softsuit_chestplate.no_tank")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.zas_atmos.softsuit_chestplate.tank", tank.getHoverName())
                .withStyle(ChatFormatting.GRAY));
        GasTankItem.lines(tank, tooltip);
    }
}
