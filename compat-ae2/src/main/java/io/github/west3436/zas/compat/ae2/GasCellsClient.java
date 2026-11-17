package io.github.west3436.zas.compat.ae2;

import appeng.api.client.StorageCellModels;
import appeng.items.storage.BasicStorageCell;
import appeng.items.storage.StorageTier;
import appeng.items.tools.powered.AbstractPortableCell;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * How the gas cells look (COMPAT-075, COMPAT-076): the light on a cell, which AE2 colours by how full
 * the cell is, the portable cell's screen, and the cell in a drive or an ME chest. Client only.
 */
final class GasCellsClient {
    private GasCellsClient() {}

    static void init(IEventBus modBus) {
        modBus.addListener(GasCellsClient::colours);
        modBus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(GasCellsClient::driveModels));
    }

    private static void colours(RegisterColorHandlersEvent.Item event) {
        // AE2's own colouring: layer 1 is the light, and a portable cell's layer 2 its screen. Its
        // colours have no alpha, which the game reads as clear, so they are made opaque, as AE2 does.
        for (Item cell : GasCells.cells()) {
            event.register((stack, layer) -> FastColor.ARGB32.opaque(BasicStorageCell.getColor(stack, layer)), cell);
        }
        for (Item cell : GasCells.portableCells()) {
            event.register(
                    (stack, layer) -> FastColor.ARGB32.opaque(AbstractPortableCell.getColor(stack, layer)), cell);
        }
    }

    /** The cell as a drive or an ME chest shows it, one model for each size. */
    private static void driveModels() {
        int i = 0;
        for (Item cell : GasCells.cells()) {
            StorageTier tier = GasCells.TIERS.get(i++);
            StorageCellModels.registerModel(
                    cell, ResourceLocation.fromNamespaceAndPath("zas", "block/drive/gas_cell_" + tier.namePrefix()));
        }
    }
}
