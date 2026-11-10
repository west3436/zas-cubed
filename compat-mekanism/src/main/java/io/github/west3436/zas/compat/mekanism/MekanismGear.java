package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleContainer;
import mekanism.api.gear.IModuleHelper;
import mekanism.api.gear.ModuleData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Mekanism's breathing gear as ZAS³ life support (MEK-030, MEK-031, MEK-034): what a player wearing
 * it gives itself, through {@link ILifeSupport#ENTITY}.
 *
 * <ul>
 *   <li><b>Scuba gear</b> (MEK-030). A scuba mask with a scuba tank that is switched on and holds
 *       oxygen is running, by the test Mekanism itself makes each tick. A running set seals the
 *       wearer's breathing, filters it, and is an internal supply that is {@linkplain
 *       IInternalSupply#alwaysOn() always on}: the tank's own switch is the only one. ZAS³ then
 *       takes the wearer to breathe the tank's oxygen, so it leaves the air meter alone, and
 *       Mekanism, finding the meter full, takes nothing from the tank beyond the 1 mB a tick it
 *       takes from any running tank.
 *   <li><b>Its protection</b> (MEK-031). Scuba gear protects against no pressure or temperature,
 *       as in Mekanism, unless {@link MekanismSettings#scubaProtects} says it does: then the mask
 *       and tank worn together protect the whole body against every pressure and temperature,
 *       running or not.
 *   <li><b>The Inhalation Purification Unit</b> (MEK-034). Installed and enabled in a worn helmet
 *       that holds energy, it filters the wearer's breath. It supplies no oxygen and seals
 *       nothing.
 * </ul>
 *
 * <p>The MekaSuit's own protection (MEK-032) is data, in the adapter's data pack: its pieces have no
 * {@link ILifeSupport#ITEM} capability, so the data applies and each piece covers its share of the
 * body. The Electrolytic Breathing Unit is left to Mekanism, which gives it nothing to do out of
 * water and rain.
 *
 * <p><b>Why the player and not the items.</b> An item's capability is asked about one stack and
 * can't see who wears it or what else is worn, and a scuba mask is nothing without its tank. It
 * would also take the place of any protection data naming the item. What a player gives itself is
 * asked with the player, covers the whole body, and adds to what the worn pieces give. Mekanism
 * feeds only players from a scuba tank, so only players are given this.
 *
 * <p><b>The breath.</b> The tank's oxygen is delivered at {@link #DISTRIBUTION_KPA}, as ZAS³'s own
 * oxygen tanks deliver it. A chemical is an amount, not a pressure: 1 mB is so many litres at one
 * atmosphere ({@link ChemicalGases}), so the tank's contents read as gas at one atmosphere, and it
 * delivers for as long as it holds anything, as in Mekanism. Each breath ZAS³ draws is counted as
 * part of what Mekanism already takes, which is some 1,500 times as much, so {@link
 * IInternalSupply#draw} hands the breath over and takes nothing more from the tank.
 *
 * <p>Mekanism's items, its data component and its capability are found by id, so nothing here
 * names a class of Mekanism's outside its API. Each lookup reads two armor slots and at most one
 * component and one capability, so ZAS³ can ask at every check. Server thread.
 */
public final class MekanismGear {
    /** Mekanism's scuba mask. */
    public static final ResourceLocation SCUBA_MASK = mekanism("scuba_mask");

    /** Mekanism's scuba tank. */
    public static final ResourceLocation SCUBA_TANK = mekanism("scuba_tank");

    /** The data component that holds whether a scuba tank is switched on. */
    public static final ResourceLocation SCUBA_TANK_MODE = mekanism("scuba_tank_mode");

    /** The Inhalation Purification Unit, a module of the MekaSuit's helmet. */
    public static final ResourceLocation PURIFICATION_UNIT = mekanism("inhalation_purification_unit");

    /** The pressure a running scuba tank delivers its oxygen at, in kPa: an O₂ tank's (LIFE-044). */
    public static final double DISTRIBUTION_KPA = 21.3;

    /**
     * Mekanism's chemical capability on items, by its name: the capability registry gives back the
     * one Mekanism made.
     */
    private static final ItemCapability<IChemicalHandler, Void> CHEMICAL =
            ItemCapability.createVoid(mekanism("chemical_handler"), IChemicalHandler.class);

    /** Scuba gear's rating when {@link MekanismSettings#scubaProtects} is on: everything. */
    private static final double NO_LIMIT = Double.POSITIVE_INFINITY;

    private MekanismGear() {}

    private static ResourceLocation mekanism(String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    /** Gives players the life support of the gear they wear. Called from {@link MekanismCompat#init}'s listener. */
    static void register(RegisterCapabilitiesEvent event) {
        event.registerEntity(ILifeSupport.ENTITY, EntityType.PLAYER, (player, context) -> of(player));
    }

    /**
     * What the Mekanism gear an entity wears gives it.
     *
     * @param wearer the entity
     * @return its life support, or null if what it wears gives none now
     */
    @ServerThread
    public static @Nullable ILifeSupport of(LivingEntity wearer) {
        ItemStack head = wearer.getItemBySlot(EquipmentSlot.HEAD);
        if (head.isEmpty()) {
            return null;
        }
        if (head.getItemHolder().is(SCUBA_MASK)) {
            ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
            if (!chest.getItemHolder().is(SCUBA_TANK)) {
                return null;
            }
            boolean running = switchedOn(chest) && !oxygen(chest).isEmpty();
            boolean protects = MekanismSettings.scubaProtects();
            return running || protects ? new Worn(running ? chest : null, running, protects) : null;
        }
        return purifies(head) ? new Worn(null, true, false) : null;
    }

    /**
     * Whether an entity's scuba gear is running: it wears the mask and the tank, the tank is
     * switched on, and it holds oxygen (MEK-030).
     *
     * @param wearer the entity
     * @return true if it breathes from its scuba tank
     */
    @ServerThread
    public static boolean scubaRunning(LivingEntity wearer) {
        ItemStack chest = wearer.getItemBySlot(EquipmentSlot.CHEST);
        return wearer.getItemBySlot(EquipmentSlot.HEAD).getItemHolder().is(SCUBA_MASK)
                && chest.getItemHolder().is(SCUBA_TANK)
                && switchedOn(chest)
                && !oxygen(chest).isEmpty();
    }

    /**
     * Whether a helmet filters its wearer's breath: an Inhalation Purification Unit is installed in
     * it and enabled, and the helmet holds energy (MEK-034).
     *
     * @param head the item in the head slot
     * @return true if it filters
     */
    @ServerThread
    public static boolean purifies(ItemStack head) {
        if (head.isEmpty()) {
            return false;
        }
        IModuleContainer modules = IModuleHelper.INSTANCE.getModuleContainer(head);
        if (modules == null) {
            return false;
        }
        Holder<ModuleData<?>> unit = Ids.PURIFICATION_UNIT;
        if (unit == null) {
            return false;
        }
        IModule<?> module = modules.getIfEnabled(unit);
        return module != null && module.getContainerEnergy(head) > 0;
    }

    private static boolean switchedOn(ItemStack tank) {
        DataComponentType<?> mode = Ids.TANK_MODE;
        return mode != null && Boolean.TRUE.equals(tank.get(mode));
    }

    /** The gas a scuba tank holds, by the chemical map, at standard air's temperature; empty if it holds none. */
    private static GasParcel oxygen(ItemStack tank) {
        IChemicalHandler handler = tank.getCapability(CHEMICAL);
        if (handler == null) {
            return GasParcel.EMPTY;
        }
        int tanks = handler.getChemicalTanks();
        if (tanks == 1) {
            return ChemicalGases.gas(handler.getChemicalInTank(0), StandardAir.TEMPERATURE);
        }
        Map<ResourceLocation, Double> moles = new HashMap<>();
        for (int i = 0; i < tanks; i++) {
            ChemicalGases.gas(handler.getChemicalInTank(i), StandardAir.TEMPERATURE)
                    .moles()
                    .forEach((gas, n) -> moles.merge(gas, n, Double::sum));
        }
        return moles.isEmpty() ? GasParcel.EMPTY : new GasParcel(moles, StandardAir.TEMPERATURE);
    }

    /**
     * What is found by id, once Mekanism has registered it: looked up when first needed, which is
     * on a running server.
     */
    private static final class Ids {
        /** The tank's mode component, or null if this Mekanism has none by that name. */
        static final @Nullable DataComponentType<?> TANK_MODE =
                BuiltInRegistries.DATA_COMPONENT_TYPE.get(SCUBA_TANK_MODE);

        /** The purification unit, or null if this Mekanism has none by that name. */
        static final @Nullable Holder<ModuleData<?>> PURIFICATION_UNIT = MekanismAPI.MODULE_REGISTRY
                .getHolder(MekanismGear.PURIFICATION_UNIT)
                .<Holder<ModuleData<?>>>map(holder -> holder)
                .orElse(null);
    }

    /**
     * The life support of the gear worn.
     *
     * @param tank the running scuba tank, or null if none runs
     * @param filters whether the breath is filtered
     * @param protects whether it protects against pressure and temperature
     */
    private record Worn(@Nullable ItemStack tank, boolean filters, boolean protects) implements ILifeSupport {
        @Override
        public double minPressure() {
            return protects ? 0 : ILifeSupport.super.minPressure();
        }

        @Override
        public double maxPressure() {
            return protects ? NO_LIMIT : ILifeSupport.super.maxPressure();
        }

        @Override
        public double minTemperature() {
            return protects ? 0 : ILifeSupport.super.minTemperature();
        }

        @Override
        public double maxTemperature() {
            return protects ? NO_LIMIT : ILifeSupport.super.maxTemperature();
        }

        @Override
        public boolean sealedBreathing() {
            return tank != null;
        }

        @Override
        public boolean gasFiltering() {
            return filters;
        }

        @Override
        public Optional<IInternalSupply> internalSupply() {
            return tank == null ? Optional.empty() : Optional.of(new ScubaSupply(tank));
        }
    }

    /**
     * A running scuba tank as an internal supply.
     *
     * @param tank the tank, read at each call
     */
    private record ScubaSupply(ItemStack tank) implements IInternalSupply {
        @Override
        public GasMixture contents() {
            GasParcel held = oxygen(tank);
            double moles = held.totalMoles();
            if (!(moles > 0)) {
                return new GasMixture(Map.of(), StandardAir.TEMPERATURE, StandardAir.CELL_VOLUME);
            }
            // The volume it fills at one atmosphere, which is what an amount of a chemical means.
            double litres = moles * GasMixture.GAS_CONSTANT * held.temperature() / StandardAir.PRESSURE;
            return new GasMixture(held.moles(), held.temperature(), litres);
        }

        @Override
        public double distributionPressure() {
            return DISTRIBUTION_KPA;
        }

        @Override
        public GasParcel draw(double moles) {
            GasParcel held = oxygen(tank);
            double total = held.totalMoles();
            if (!(moles > 0) || !(total > 0)) {
                return GasParcel.EMPTY;
            }
            double share = Math.min(1, moles / total);
            Map<ResourceLocation, Double> breath = new HashMap<>();
            held.moles().forEach((gas, n) -> breath.put(gas, n * share));
            return new GasParcel(breath, held.temperature());
        }

        @Override
        public boolean alwaysOn() {
            return true;
        }
    }
}
