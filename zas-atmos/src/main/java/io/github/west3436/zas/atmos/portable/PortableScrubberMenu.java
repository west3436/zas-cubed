package io.github.west3436.zas.atmos.portable;

import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;

/** A portable scrubber's GUI (CONT-018, CONT-074): see {@link PortableGasMenu}. Its only button is its switch. */
public class PortableScrubberMenu extends PortableGasMenu {
    /** The client's menu. */
    public PortableScrubberMenu(int id, Inventory inventory) {
        this(
                id,
                inventory,
                new Batteries.Slot(() -> {}),
                new TankSlot(() -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL,
                null);
    }

    /** The server's menu, on a portable scrubber. */
    public PortableScrubberMenu(
            int id,
            Inventory inventory,
            IItemHandler battery,
            IItemHandler tank,
            ContainerData data,
            ContainerLevelAccess access,
            PortableScrubberBlockEntity scrubber) {
        super(
                AtmosMenus.PORTABLE_SCRUBBER.get(),
                id,
                inventory,
                battery,
                tank,
                data,
                DATA_COUNT,
                access,
                AtmosBlocks.PORTABLE_SCRUBBER.get(),
                scrubber);
    }
}
