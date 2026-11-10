package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync;
import io.github.west3436.zas.atmos.canister.CanisterSync;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralSync;
import io.github.west3436.zas.atmos.controller.ControllerSync;
import io.github.west3436.zas.atmos.device.ClientDeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlock;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.MagneticBootsItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.gear.TankSettings;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.pipe.PipeConnector;
import io.github.west3436.zas.atmos.pipe.PipeGasHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The content mod's capabilities and network payloads. The generator gives FE and takes fuel
 * (CONT-061); the area air controller's buffer takes FE, from cables that push it as well as from
 * the blocks it pulls from (CONT-062), and so does the central atmospherics controller's
 * (CONT-115). Devices expose no FE capability (CONT-069). The electrolyzer takes water through
 * the fluid capability on its back (CONT-056). Every pipe part is a gas handler on the faces
 * where a pipe joins it (API-021). The breathing gear gives ZAS³ its life support (API-022). A
 * battery is an FE item, its charge in its {@code zas_atmos:energy} component, so any mod's
 * charger and machine can use it (CONT-111).
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
        // Any of the console's four blocks takes FE into the buffer its lower left one holds (CONT-115).
        event.registerBlock(
                Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, be, side) -> {
                    CentralControllerBlockEntity c = CentralControllerBlock.entity(level, pos, state);
                    return c == null ? null : c.buffer();
                },
                AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get());
        // The electrolyzer's water inlet is its back, and fluid pipes join it nowhere else (CONT-056).
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                AtmosBlockEntities.ELECTROLYZER.get(),
                (be, side) -> side == ElectrolyzerBlock.inletFace(be.getBlockState()) ? be.tank() : null);
        // Every pipe part is a gas handler where it would join one, so another mod's pipes and
        // machines join ours as ours join theirs (API-021, API-025).
        event.registerBlock(
                IGasHandler.BLOCK,
                PipeGasHandler::of,
                AtmosBlocks.BLOCKS.getEntries().stream()
                        .map(DeferredHolder::get)
                        .filter(PipeConnector.class::isInstance)
                        .toArray(Block[]::new));
        // Breathing gear (S-08): the tanks are internal supplies, the mask and suit protect (API-022).
        event.registerItem(
                ILifeSupport.ITEM,
                (stack, ctx) -> GasTankItem.lifeSupport(stack),
                AtmosItems.OXYGEN_TANK.get(),
                AtmosItems.EMERGENCY_OXYGEN_TANK.get(),
                AtmosItems.AIR_TANK.get(),
                AtmosItems.EMPTY_TANK.get());
        event.registerItem(
                ILifeSupport.ITEM,
                (stack, ctx) -> SuitItem.lifeSupport(stack),
                AtmosItems.gear().stream().map(DeferredItem::get).toArray(Item[]::new));
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
        event.registrar(PROTOCOL)
                .playToClient(ControllerSync.TYPE, ControllerSync.CODEC, ControllerSync::handle)
                .playToClient(CentralSync.TYPE, CentralSync.CODEC, CentralSync::handle)
                .playToClient(CanisterSync.TYPE, CanisterSync.CODEC, CanisterSync::handle)
                .playToClient(GasJet.TYPE, GasJet.CODEC, GasJet::handle)
                .playToClient(DeviceActivityPayload.TYPE, DeviceActivityPayload.CODEC, ClientDeviceActivity::handle)
                .playToServer(TankSettings.TYPE, TankSettings.CODEC, TankSettings::handle)
                .playToServer(
                        MagneticBootsItem.TogglePayload.TYPE,
                        MagneticBootsItem.TogglePayload.CODEC,
                        MagneticBootsItem::handleToggle)
                .playToClient(AnalyzerSync.TYPE, AnalyzerSync.CODEC, AnalyzerSync::handle);
    }
}
