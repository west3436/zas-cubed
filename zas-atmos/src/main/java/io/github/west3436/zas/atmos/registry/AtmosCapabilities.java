package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.controller.ControllerSync;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The content mod's capabilities and network payloads. The generator gives FE and takes fuel
 * (CONT-061); the area air controller's buffer takes FE, from cables that push it as well as from
 * the blocks it pulls from (CONT-062). Devices expose no FE capability (CONT-069). A battery is an
 * FE item, its charge in its {@code zas_atmos:energy} component, so any mod's charger and machine can
 * use it (CONT-111).
 */
public final class AtmosCapabilities {
    /** The network protocol version of the content mod's payloads. */
    public static final String PROTOCOL = "1";

    private AtmosCapabilities() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(AtmosCapabilities::capabilities);
        modBus.addListener(AtmosCapabilities::payloads);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK, AtmosBlockEntities.GENERATOR.get(), (be, side) -> be.energy());
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, AtmosBlockEntities.GENERATOR.get(), (be, side) -> be.fuel());
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                AtmosBlockEntities.AREA_AIR_CONTROLLER.get(),
                (be, side) -> be.buffer());
        AtmosItems.batteries().forEach(battery -> {
            int capacity = battery.get().capacity();
            event.registerItem(
                    Capabilities.EnergyStorage.ITEM,
                    (stack, context) -> new ComponentEnergyStorage(
                            stack, AtmosComponents.ENERGY.get(), capacity, capacity, capacity),
                    battery.get());
        });
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL).playToClient(ControllerSync.TYPE, ControllerSync.CODEC, ControllerSync::handle);
    }
}
