package io.github.west3436.zas.atmos.compat.cc;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.heater.ThermoMachineBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Starts the CC: Tweaked adapter (COMPAT-020 to COMPAT-022). {@code zas}'s adapter loader calls
 * {@link #init} by name, and only with CC: Tweaked installed in a version its row supports
 * (COMPAT-001, COMPAT-002), so this class may name CC: Tweaked's.
 *
 * <p>{@code init} runs while {@code zas} is being built, perhaps before {@code zas_atmos} and before
 * CC: Tweaked, so it only asks to be told when capabilities are registered. By then every mod is
 * built: if the content is on (CONT-155), each device with a window gets its peripheral, through
 * CC: Tweaked's block capability, and the zone events start being listened for. With the content
 * off there are no devices, and nothing is registered.
 */
public final class CcCompat {
    private static volatile boolean registered;

    private CcCompat() {}

    /**
     * The adapter loader's way in.
     *
     * @param modBus the mod bus the capabilities are registered on
     * @param container unused
     */
    public static void init(IEventBus modBus, ModContainer container) {
        modBus.addListener(CcCompat::register);
    }

    /** Whether the peripherals were registered: false before capabilities are, and with the content off. Any thread. */
    public static boolean registered() {
        return registered;
    }

    private static void register(RegisterCapabilitiesEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        device(event, AtmosBlockEntities.CANISTER, CanisterPeripheral::new);
        device(event, AtmosBlockEntities.VENT, VentPeripheral::new);
        device(event, AtmosBlockEntities.SCRUBBER, ScrubberPeripheral::new);
        device(event, AtmosBlockEntities.PRESSURE_PUMP, PumpPeripheral.Pressure::new);
        device(event, AtmosBlockEntities.VOLUME_PUMP, PumpPeripheral.Volume::new);
        device(event, AtmosBlockEntities.FILTER, SidePortPeripheral.Filter::new);
        device(event, AtmosBlockEntities.MIXER, SidePortPeripheral.Mixer::new);
        device(event, AtmosBlockEntities.ELECTROLYZER, ElectrolyzerPeripheral::new);
        device(event, AtmosBlockEntities.HEATER, heater -> thermo("zas_heater", heater));
        device(event, AtmosBlockEntities.AIR_CONDITIONER, cooler -> thermo("zas_air_conditioner", cooler));
        device(event, AtmosBlockEntities.PORTABLE_SCRUBBER, PortablePeripheral.Scrubber::new);
        device(event, AtmosBlockEntities.PORTABLE_PUMP, PortablePeripheral.Pump::new);
        device(event, AtmosBlockEntities.GENERATOR, GeneratorPeripheral::new);
        device(event, AtmosBlockEntities.AREA_AIR_CONTROLLER, AreaAirControllerPeripheral::new);
        device(event, AtmosBlockEntities.AIRLOCK_CONTROLLER, AirlockControllerPeripheral::new);
        // The console is four blocks with one block entity, in its lower left block: any of them is
        // the peripheral, as any of them opens its window.
        event.registerBlock(
                PeripheralCapability.get(),
                (level, pos, state, blockEntity, side) -> {
                    CentralControllerBlockEntity central = CentralControllerBlock.entity(level, pos, state);
                    return central == null ? null : new CentralControllerPeripheral(central);
                },
                AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get());
        ZoneEvents.register(NeoForge.EVENT_BUS);
        registered = true;
    }

    private static IPeripheral thermo(String type, ThermoMachineBlockEntity machine) {
        return new PortablePeripheral.Thermo(type, machine);
    }

    /** A device's peripheral, the same on every side. */
    private static <T extends BlockEntity> void device(
            RegisterCapabilitiesEvent event,
            Supplier<BlockEntityType<T>> type,
            Function<? super T, ? extends IPeripheral> peripheral) {
        BlockCapability<IPeripheral, Direction> capability = PeripheralCapability.get();
        event.registerBlockEntity(capability, type.get(), (device, side) -> peripheral.apply(device));
    }
}
