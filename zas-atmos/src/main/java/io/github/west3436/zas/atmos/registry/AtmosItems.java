package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.canister.CanisterItem;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.GearMaterials;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import java.util.Map;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
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
    public static final DeferredItem<BlockItem> AREA_AIR_CONTROLLER =
            ITEMS.registerSimpleBlockItem(AtmosBlocks.AREA_AIR_CONTROLLER);
    public static final DeferredItem<BlockItem> GENERATOR = ITEMS.registerSimpleBlockItem(AtmosBlocks.GENERATOR);

    /** Links parts to an area air controller (CONT-042). */
    public static final DeferredItem<MultitoolItem> MULTITOOL = ITEMS.registerItem("multitool", MultitoolItem::new);

    // ---- Breathing gear (S-08) ----

    /** An O2 tank (CONT-030): 70 L of O2 at about 6 atm, 17.5 mol. */
    public static final DeferredItem<GasTankItem> OXYGEN_TANK = ITEMS.registerItem(
            "oxygen_tank",
            p -> new GasTankItem(
                    p.component(AtmosComponents.GAS.get(), oxygen(17.5)), 70, GasTankItem.OXYGEN_DISTRIBUTION_KPA));

    /** An emergency O2 tank (CONT-030): 2 L of O2 at 3 atm. */
    public static final DeferredItem<GasTankItem> EMERGENCY_OXYGEN_TANK = ITEMS.registerItem(
            "emergency_oxygen_tank",
            p -> new GasTankItem(
                    p.component(
                            AtmosComponents.GAS.get(),
                            GasTankItem.fill(Map.of(StandardAir.OXYGEN, 1.0), 3 * StandardAir.PRESSURE, 2)),
                    2,
                    GasTankItem.OXYGEN_DISTRIBUTION_KPA));

    /** An air tank (CONT-030): 70 L of standard air at 6 atm. */
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

    /** An empty tank (CONT-030): 70 L, filled from a canister (CONT-039). */
    public static final DeferredItem<GasTankItem> EMPTY_TANK = ITEMS.registerItem(
            "empty_tank", p -> new GasTankItem(p, 70, GasTankItem.OXYGEN_DISTRIBUTION_KPA));

    /** The breath mask (CONT-031): seals the breathing, for internals. */
    public static final DeferredItem<SuitItem> BREATH_MASK = ITEMS.registerItem(
            "breath_mask",
            p -> new SuitItem(GearMaterials.BREATH_MASK, ArmorItem.Type.HELMET, p, SuitItem.Rating.MASK, false));

    /** The softsuit (CONT-032): its helmet seals the breathing, and its chestplate has a tank slot (CONT-090). */
    public static final DeferredItem<SuitItem> SOFTSUIT_HELMET = ITEMS.registerItem(
            "softsuit_helmet",
            p -> new SuitItem(
                    GearMaterials.SOFTSUIT, ArmorItem.Type.HELMET, p, SuitItem.Rating.softsuit(true), false));

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
            p -> new SuitItem(
                    GearMaterials.SOFTSUIT, ArmorItem.Type.BOOTS, p, SuitItem.Rating.softsuit(false), false));

    private static GasParcel oxygen(double moles) {
        return new GasParcel(Map.of(StandardAir.OXYGEN, moles), GasTankItem.FILL_K);
    }

    private AtmosItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
