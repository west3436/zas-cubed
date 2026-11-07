package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.analyzer.AnalyzerItem;
import io.github.west3436.zas.atmos.battery.BatteryItem;
import io.github.west3436.zas.atmos.canister.CanisterItem;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.GearMaterials;
import io.github.west3436.zas.atmos.gear.MagneticBootsItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.inline.MirroredDeviceItem;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.portable.PortableGasItem;
import io.github.west3436.zas.atmos.tool.CrowbarItem;
import io.github.west3436.zas.atmos.tool.FireExtinguisherItem;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's items, block items included. Add new ones to {@link AtmosCreativeTab} too. */
public final class AtmosItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZasAtmosMod.MOD_ID);

    public static final DeferredItem<PipeItem> PIPE =
            ITEMS.registerItem("pipe", p -> new PipeItem(AtmosBlocks.PIPE.get(), p));
    public static final DeferredItem<PipeItem> PIPE_CAP =
            ITEMS.registerItem("pipe_cap", p -> new PipeItem(AtmosBlocks.PIPE_CAP.get(), p));
    public static final DeferredItem<BlockItem> CONNECTOR_PORT =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.CONNECTOR_PORT);
    public static final DeferredItem<CanisterItem> CANISTER =
            ITEMS.registerItem("canister", p -> new CanisterItem(AtmosBlocks.CANISTER.get(), p));
    public static final DeferredItem<BlockItem> VENT = ITEMS.registerSimpleBlockItem(AtmosBlocks.VENT);
    public static final DeferredItem<BlockItem> SCRUBBER = ITEMS.registerSimpleBlockItem(AtmosBlocks.SCRUBBER);
    public static final DeferredItem<BlockItem> PASSIVE_VENT = ITEMS.registerSimpleBlockItem(AtmosBlocks.PASSIVE_VENT);
    public static final DeferredItem<BlockItem> AREA_AIR_CONTROLLER =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.AREA_AIR_CONTROLLER);
    public static final DeferredItem<BlockItem> CENTRAL_ATMOS_CONTROLLER =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.CENTRAL_ATMOS_CONTROLLER);
    public static final DeferredItem<BlockItem> FIRE_ALARM = ITEMS.registerSimpleBlockItem(AtmosBlocks.FIRE_ALARM);
    public static final DeferredItem<BlockItem> GENERATOR = ITEMS.registerSimpleBlockItem(AtmosBlocks.GENERATOR);

    public static final DeferredItem<BlockItem> HEATER = ITEMS.registerSimpleBlockItem(AtmosBlocks.HEATER);

    public static final DeferredItem<BlockItem> AIR_CONDITIONER =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.AIR_CONDITIONER);

    public static final DeferredItem<PortableGasItem> PORTABLE_SCRUBBER =
            ITEMS.registerItem("portable_scrubber", p -> new PortableGasItem(AtmosBlocks.PORTABLE_SCRUBBER.get(), p));

    public static final DeferredItem<PortableGasItem> PORTABLE_PUMP =
            ITEMS.registerItem("portable_pump", p -> new PortableGasItem(AtmosBlocks.PORTABLE_PUMP.get(), p));

    public static final DeferredItem<DoubleHighBlockItem> FIRELOCK =
            ITEMS.registerItem("firelock", p -> new DoubleHighBlockItem(AtmosBlocks.FIRELOCK.get(), p));

    /** Forces firelocks open or shut (CONT-094). */
    public static final DeferredItem<CrowbarItem> CROWBAR = ITEMS.registerItem("crowbar", CrowbarItem::new);

    public static final DeferredItem<BlockItem> RECHARGER = ITEMS.registerSimpleBlockItem(AtmosBlocks.RECHARGER);
    public static final DeferredItem<BlockItem> IGNITER = ITEMS.registerSimpleBlockItem(AtmosBlocks.IGNITER);

    public static final DeferredItem<BlockItem> PRESSURE_PUMP =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.PRESSURE_PUMP);
    public static final DeferredItem<BlockItem> VOLUME_PUMP = ITEMS.registerSimpleBlockItem(AtmosBlocks.VOLUME_PUMP);

    public static final DeferredItem<BlockItem> FILTER = ITEMS.registerSimpleBlockItem(AtmosBlocks.FILTER);
    public static final DeferredItem<BlockItem> MIXER = ITEMS.registerSimpleBlockItem(AtmosBlocks.MIXER);

    // The same blocks, with their side ports on the other side (CONT-013, CONT-077).

    public static final DeferredItem<MirroredDeviceItem> FILTER_MIRRORED =
            ITEMS.registerItem("filter_mirrored", p -> new MirroredDeviceItem(AtmosBlocks.FILTER.get(), p));
    public static final DeferredItem<MirroredDeviceItem> MIXER_MIRRORED =
            ITEMS.registerItem("mixer_mirrored", p -> new MirroredDeviceItem(AtmosBlocks.MIXER.get(), p));

    /** The water electrolyzer (CONT-055). */
    public static final DeferredItem<BlockItem> ELECTROLYZER = ITEMS.registerSimpleBlockItem(AtmosBlocks.ELECTROLYZER);

    public static final DeferredItem<BlockItem> MANUAL_VALVE = ITEMS.registerSimpleBlockItem(AtmosBlocks.MANUAL_VALVE);
    public static final DeferredItem<BlockItem> REDSTONE_VALVE =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.REDSTONE_VALVE);

    // The batteries (CONT-111), which run the heater and other portables and charge in the recharger.

    public static final DeferredItem<BatteryItem> BATTERY =
            ITEMS.registerItem("battery", p -> new BatteryItem(100_000, p));
    public static final DeferredItem<BatteryItem> HIGH_CAPACITY_BATTERY =
            ITEMS.registerItem("high_capacity_battery", p -> new BatteryItem(1_000_000, p));
    public static final DeferredItem<BatteryItem> SUPER_CAPACITY_BATTERY =
            ITEMS.registerItem("super_capacity_battery", p -> new BatteryItem(2_000_000, p));

    /** The batteries, smallest first. */
    public static List<DeferredItem<BatteryItem>> batteries() {
        return List.of(BATTERY, HIGH_CAPACITY_BATTERY, SUPER_CAPACITY_BATTERY);
    }

    /** Links parts to an area air controller (CONT-042). */
    public static final DeferredItem<MultitoolItem> MULTITOOL = ITEMS.registerItem("multitool", MultitoolItem::new);

    /** Puts out the fires it is aimed at (CONT-037). */
    public static final DeferredItem<FireExtinguisherItem> FIRE_EXTINGUISHER =
            ITEMS.registerItem("fire_extinguisher", FireExtinguisherItem::new);

    /** Two gas tanks joined by a valve, with a timer (CONT-103, CONT-104). */
    public static final DeferredItem<TransferValveItem> TANK_TRANSFER_VALVE =
            ITEMS.registerItem("tank_transfer_valve", TransferValveItem::new);

    /** Reads the air where the player is, a point, a pipe network, a canister or a tank (CONT-034). */
    public static final DeferredItem<AnalyzerItem> ATMOS_ANALYZER =
            ITEMS.registerItem("atmos_analyzer", AnalyzerItem::new);

    // ---- Breathing gear (S-08) ----

    /**
     * An O2 tank (CONT-030): 70 L of O2, new at about 6 atm (17.5 mol). It has no recipe: an empty
     * tank filled with O2 from a canister becomes one (CONT-039, {@link GasTankItem#ofItsKind}).
     */
    public static final DeferredItem<GasTankItem> OXYGEN_TANK = ITEMS.registerItem(
            "oxygen_tank",
            p -> new GasTankItem(
                    p.component(AtmosComponents.GAS.get(), oxygen(17.5)), 70, GasTankItem.OXYGEN_DISTRIBUTION_KPA));

    /**
     * An emergency O2 tank (CONT-030): 2 L, crafted empty and filled from a canister (CONT-039). The
     * creative tab has one filled with O2 at 3 atm ({@link #emergencyFill()}).
     */
    public static final DeferredItem<GasTankItem> EMERGENCY_OXYGEN_TANK = ITEMS.registerItem(
            "emergency_oxygen_tank", p -> new GasTankItem(p, 2, GasTankItem.OXYGEN_DISTRIBUTION_KPA));

    /** An air tank (CONT-030): 70 L of air, new at 6 atm. Like the O2 tank, it is made by filling. */
    public static final DeferredItem<GasTankItem> AIR_TANK = ITEMS.registerItem(
            "air_tank",
            p -> new GasTankItem(
                    p.component(
                            AtmosComponents.GAS.get(),
                            GasTankItem.fill(
                                    Map.of(
                                            StandardAir.NITROGEN, StandardAir.NITROGEN_FRACTION,
                                            StandardAir.OXYGEN, StandardAir.OXYGEN_FRACTION),
                                    6 * StandardAir.PRESSURE,
                                    70)),
                    70,
                    GasTankItem.AIR_DISTRIBUTION_KPA));

    /** The empty tank (CONT-030): 70 L, crafted empty and filled from a canister (CONT-039). */
    public static final DeferredItem<GasTankItem> EMPTY_TANK =
            ITEMS.registerItem("empty_tank", p -> new GasTankItem(p, 70, GasTankItem.OXYGEN_DISTRIBUTION_KPA));

    /** The breath mask (CONT-031): seals the breathing, for internals. */
    public static final DeferredItem<SuitItem> BREATH_MASK = ITEMS.registerItem(
            "breath_mask",
            p -> new SuitItem(GearMaterials.BREATH_MASK, ArmorItem.Type.HELMET, p, SuitItem.Rating.MASK, false));

    /** The softsuit (CONT-032): its helmet seals the breathing, and its chestplate has a tank slot (CONT-090). */
    public static final DeferredItem<SuitItem> SOFTSUIT_HELMET = ITEMS.registerItem(
            "softsuit_helmet",
            p -> new SuitItem(GearMaterials.SOFTSUIT, ArmorItem.Type.HELMET, p, SuitItem.Rating.softsuit(true), false));

    public static final DeferredItem<SuitItem> SOFTSUIT_CHESTPLATE = ITEMS.registerItem(
            "softsuit_chestplate",
            p -> new SuitItem(
                    GearMaterials.SOFTSUIT, ArmorItem.Type.CHESTPLATE, p, SuitItem.Rating.softsuit(false), true));

    public static final DeferredItem<SuitItem> SOFTSUIT_LEGGINGS = ITEMS.registerItem(
            "softsuit_leggings",
            p -> new SuitItem(
                    GearMaterials.SOFTSUIT, ArmorItem.Type.LEGGINGS, p, SuitItem.Rating.softsuit(false), false));

    public static final DeferredItem<SuitItem> SOFTSUIT_BOOTS = ITEMS.registerItem(
            "softsuit_boots",
            p -> new SuitItem(GearMaterials.SOFTSUIT, ArmorItem.Type.BOOTS, p, SuitItem.Rating.softsuit(false), false));

    /** The gas mask (CONT-088): seals the breathing, for internals, and filters the toxic gases out of it. */
    public static final DeferredItem<SuitItem> GAS_MASK = ITEMS.registerItem(
            "gas_mask",
            p -> new SuitItem(GearMaterials.GAS_MASK, ArmorItem.Type.HELMET, p, SuitItem.Rating.GAS_MASK, false));

    /**
     * The hardsuit (CONT-036): every pressure and temperature. Its helmet seals the breathing, its
     * chestplate has a tank slot (CONT-090), and each piece slows its wearer (CONT-089).
     */
    public static final DeferredItem<SuitItem> HARDSUIT_HELMET = ITEMS.registerItem(
            "hardsuit_helmet",
            p -> new SuitItem(GearMaterials.HARDSUIT, ArmorItem.Type.HELMET, p, SuitItem.Rating.hardsuit(true), false));

    public static final DeferredItem<SuitItem> HARDSUIT_CHESTPLATE = ITEMS.registerItem(
            "hardsuit_chestplate",
            p -> new SuitItem(
                    GearMaterials.HARDSUIT, ArmorItem.Type.CHESTPLATE, p, SuitItem.Rating.hardsuit(false), true));

    public static final DeferredItem<SuitItem> HARDSUIT_LEGGINGS = ITEMS.registerItem(
            "hardsuit_leggings",
            p -> new SuitItem(
                    GearMaterials.HARDSUIT, ArmorItem.Type.LEGGINGS, p, SuitItem.Rating.hardsuit(false), false));

    public static final DeferredItem<SuitItem> HARDSUIT_BOOTS = ITEMS.registerItem(
            "hardsuit_boots",
            p -> new SuitItem(GearMaterials.HARDSUIT, ArmorItem.Type.BOOTS, p, SuitItem.Rating.hardsuit(false), false));

    /** Magnetic boots (CONT-033): a keybind switches them, and while on airflow can't move their wearer. */
    public static final DeferredItem<MagneticBootsItem> MAGNETIC_BOOTS =
            ITEMS.registerItem("magnetic_boots", MagneticBootsItem::new);

    /** The masks, suit pieces and boots, in the creative tab's order; each has a {@link SuitItem#lifeSupport}. */
    public static List<DeferredItem<? extends SuitItem>> gear() {
        return List.of(
                BREATH_MASK,
                GAS_MASK,
                SOFTSUIT_HELMET,
                SOFTSUIT_CHESTPLATE,
                SOFTSUIT_LEGGINGS,
                SOFTSUIT_BOOTS,
                HARDSUIT_HELMET,
                HARDSUIT_CHESTPLATE,
                HARDSUIT_LEGGINGS,
                HARDSUIT_BOOTS,
                MAGNETIC_BOOTS);
    }

    /** A filled emergency O2 tank's gas: O2 at 3 atm in 2 L (CONT-030). */
    public static GasParcel emergencyFill() {
        return GasTankItem.fill(Map.of(StandardAir.OXYGEN, 1.0), 3 * StandardAir.PRESSURE, 2);
    }

    private static GasParcel oxygen(double moles) {
        return new GasParcel(Map.of(StandardAir.OXYGEN, moles), GasTankItem.FILL_K);
    }

    // Crafting components, after vg's stock parts (D-72). Every one is crafted from vanilla
    // materials, so the content they go into still is (CONT-051). The recipes and the progression
    // are in docs/crafting.md (CONT-058).

    public static final DeferredItem<Item> CABLE_COIL = ITEMS.registerSimpleItem("cable_coil");
    public static final DeferredItem<Item> CIRCUIT_BOARD = ITEMS.registerSimpleItem("circuit_board");
    public static final DeferredItem<Item> ELECTRONICS = ITEMS.registerSimpleItem("electronics");
    public static final DeferredItem<Item> MACHINE_FRAME = ITEMS.registerSimpleItem("machine_frame");
    public static final DeferredItem<Item> CAPACITOR = ITEMS.registerSimpleItem("capacitor");
    public static final DeferredItem<Item> ADVANCED_CAPACITOR = ITEMS.registerSimpleItem("advanced_capacitor");
    public static final DeferredItem<Item> SUPER_CAPACITOR = ITEMS.registerSimpleItem("super_capacitor");
    public static final DeferredItem<Item> SCANNING_MODULE = ITEMS.registerSimpleItem("scanning_module");
    public static final DeferredItem<Item> MICRO_MANIPULATOR = ITEMS.registerSimpleItem("micro_manipulator");
    public static final DeferredItem<Item> MICRO_LASER = ITEMS.registerSimpleItem("micro_laser");
    public static final DeferredItem<Item> CONSOLE_SCREEN = ITEMS.registerSimpleItem("console_screen");
    public static final DeferredItem<Item> IGNITER_ASSEMBLY = ITEMS.registerSimpleItem("igniter_assembly");
    public static final DeferredItem<Item> TIMER_ASSEMBLY = ITEMS.registerSimpleItem("timer_assembly");
    public static final DeferredItem<Item> ANSIBLE_CRYSTAL = ITEMS.registerSimpleItem("ansible_crystal");
    public static final DeferredItem<Item> SUBSPACE_ANSIBLE = ITEMS.registerSimpleItem("subspace_ansible");
    public static final DeferredItem<Item> SUBSPACE_TRANSMITTER = ITEMS.registerSimpleItem("subspace_transmitter");
    public static final DeferredItem<Item> BLUESPACE_CRYSTAL = ITEMS.registerSimpleItem("bluespace_crystal");
    public static final DeferredItem<Item> PLASTEEL_SHEET = ITEMS.registerSimpleItem("plasteel_sheet");
    public static final DeferredItem<Item> REINFORCED_GLASS = ITEMS.registerSimpleItem("reinforced_glass");

    /** The crafting components, in the creative tab's order. */
    public static List<DeferredItem<Item>> components() {
        return List.of(
                CABLE_COIL,
                CIRCUIT_BOARD,
                ELECTRONICS,
                MACHINE_FRAME,
                CAPACITOR,
                ADVANCED_CAPACITOR,
                SUPER_CAPACITOR,
                SCANNING_MODULE,
                MICRO_MANIPULATOR,
                MICRO_LASER,
                CONSOLE_SCREEN,
                IGNITER_ASSEMBLY,
                TIMER_ASSEMBLY,
                ANSIBLE_CRYSTAL,
                SUBSPACE_ANSIBLE,
                SUBSPACE_TRANSMITTER,
                BLUESPACE_CRYSTAL,
                PLASTEEL_SHEET,
                REINFORCED_GLASS);
    }

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
