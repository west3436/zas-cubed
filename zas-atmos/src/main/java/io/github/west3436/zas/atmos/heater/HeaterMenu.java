package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;

/** A heater's GUI (CONT-019, CONT-074): see {@link ThermoMachineMenu}. Its setpoint runs from 293 to 573 K. */
public class HeaterMenu extends ThermoMachineMenu {
    /** The client's menu. */
    public HeaterMenu(int id, Inventory inventory) {
        this(
                id,
                inventory,
                new Batteries.Slot(() -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL,
                null);
    }

    /** The server's menu, on a heater. */
    public HeaterMenu(
            int id,
            Inventory inventory,
            IItemHandler battery,
            ContainerData data,
            ContainerLevelAccess access,
            HeaterBlockEntity heater) {
        super(AtmosMenus.HEATER.get(), id, inventory, battery, data, access, AtmosBlocks.HEATER.get(), heater);
    }
}
