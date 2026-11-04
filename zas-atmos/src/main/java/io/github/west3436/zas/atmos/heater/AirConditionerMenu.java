package io.github.west3436.zas.atmos.heater;

import io.github.west3436.zas.atmos.battery.Batteries;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;

/** An air conditioner's GUI (CONT-019, CONT-074): see {@link ThermoMachineMenu}. Its setpoint runs from 73 to 293 K. */
public class AirConditionerMenu extends ThermoMachineMenu {
    /** The client's menu. */
    public AirConditionerMenu(int id, Inventory inventory) {
        this(
                id,
                inventory,
                new Batteries.Slot(() -> {}),
                new SimpleContainerData(DATA_COUNT),
                ContainerLevelAccess.NULL,
                null);
    }

    /** The server's menu, on an air conditioner. */
    public AirConditionerMenu(
            int id,
            Inventory inventory,
            IItemHandler battery,
            ContainerData data,
            ContainerLevelAccess access,
            AirConditionerBlockEntity airConditioner) {
        super(
                AtmosMenus.AIR_CONDITIONER.get(),
                id,
                inventory,
                battery,
                data,
                access,
                AtmosBlocks.AIR_CONDITIONER.get(),
                airConditioner);
    }
}
