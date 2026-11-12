package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockBlockEntity;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlockEntity;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.CentralControllerBlockEntity;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlockEntity;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlockEntity;
import io.github.west3436.zas.atmos.heater.AirConditionerBlockEntity;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlockEntity;
import io.github.west3436.zas.atmos.port.ConnectorPortBlockEntity;
import io.github.west3436.zas.atmos.portable.PortablePumpBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableScrubberBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.pump.VolumePumpBlockEntity;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlockEntity;
import io.github.west3436.zas.atmos.radiator.RadiatorAdaptorBlockEntity;
import io.github.west3436.zas.atmos.radiator.RadiatorPipeBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.tank.LargeTankBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.valve.ValveBlockEntity;
import io.github.west3436.zas.atmos.vent.PassiveVentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The content mod's block entity types. A device's block entity extends {@link
 * io.github.west3436.zas.atmos.device.AtmosDevice}, which steps it once per atmos tick.
 */
public final class AtmosBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ZasAtmosMod.MOD_ID);

    /** A pipe's, bare or in a casing: one type, so a pipe keeps its block entity when a casing goes on it. */
    public static final Supplier<BlockEntityType<PipeBlockEntity>> PIPE = BLOCK_ENTITIES.register(
            "pipe",
            () -> BlockEntityType.Builder.of(PipeBlockEntity::new, AtmosBlocks.pipes())
                    .build(null));

    public static final Supplier<BlockEntityType<PipeCapBlockEntity>> PIPE_CAP = BLOCK_ENTITIES.register(
            "pipe_cap",
            () -> BlockEntityType.Builder.of(PipeCapBlockEntity::new, AtmosBlocks.PIPE_CAP.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ConnectorPortBlockEntity>> CONNECTOR_PORT = BLOCK_ENTITIES.register(
            "connector_port",
            () -> BlockEntityType.Builder.of(ConnectorPortBlockEntity::new, AtmosBlocks.CONNECTOR_PORT.get())
                    .build(null));

    public static final Supplier<BlockEntityType<CanisterBlockEntity>> CANISTER = BLOCK_ENTITIES.register(
            "canister",
            () -> BlockEntityType.Builder.of(CanisterBlockEntity::new, AtmosBlocks.CANISTER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<VentBlockEntity>> VENT = BLOCK_ENTITIES.register(
            "vent",
            () -> BlockEntityType.Builder.of(VentBlockEntity::new, AtmosBlocks.VENT.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ScrubberBlockEntity>> SCRUBBER = BLOCK_ENTITIES.register(
            "scrubber",
            () -> BlockEntityType.Builder.of(ScrubberBlockEntity::new, AtmosBlocks.SCRUBBER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<PassiveVentBlockEntity>> PASSIVE_VENT = BLOCK_ENTITIES.register(
            "passive_vent",
            () -> BlockEntityType.Builder.of(PassiveVentBlockEntity::new, AtmosBlocks.PASSIVE_VENT.get())
                    .build(null));

    public static final Supplier<BlockEntityType<AreaAirControllerBlockEntity>> AREA_AIR_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "area_air_controller",
                    () -> BlockEntityType.Builder.of(
                                    AreaAirControllerBlockEntity::new, AtmosBlocks.AREA_AIR_CONTROLLER.get())
                            .build(null));

    public static final Supplier<BlockEntityType<CentralControllerBlockEntity>> CENTRAL_ATMOS_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "central_atmos_controller",
                    () -> BlockEntityType.Builder.of(
                                    CentralControllerBlockEntity::new, AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get())
                            .build(null));

    public static final Supplier<BlockEntityType<FireAlarmBlockEntity>> FIRE_ALARM = BLOCK_ENTITIES.register(
            "fire_alarm",
            () -> BlockEntityType.Builder.of(FireAlarmBlockEntity::new, AtmosBlocks.FIRE_ALARM.get())
                    .build(null));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITIES.register(
            "generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, AtmosBlocks.GENERATOR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<FirelockBlockEntity>> FIRELOCK = BLOCK_ENTITIES.register(
            "firelock",
            () -> BlockEntityType.Builder.of(FirelockBlockEntity::new, AtmosBlocks.FIRELOCK.get())
                    .build(null));

    // Airlocks (CONT-133 to CONT-142, D-84): one type for both sizes, on each airlock's origin
    public static final Supplier<BlockEntityType<AirlockBlockEntity>> AIRLOCK = BLOCK_ENTITIES.register(
            "airlock",
            () -> BlockEntityType.Builder.of(
                            AirlockBlockEntity::new, AtmosBlocks.AIRLOCK.get(), AtmosBlocks.LARGE_AIRLOCK.get())
                    .build(null));

    public static final Supplier<BlockEntityType<AirlockControllerBlockEntity>> AIRLOCK_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "airlock_controller",
                    () -> BlockEntityType.Builder.of(
                                    AirlockControllerBlockEntity::new, AtmosBlocks.AIRLOCK_CONTROLLER.get())
                            .build(null));

    public static final Supplier<BlockEntityType<AirSensorBlockEntity>> AIR_SENSOR = BLOCK_ENTITIES.register(
            "air_sensor",
            () -> BlockEntityType.Builder.of(AirSensorBlockEntity::new, AtmosBlocks.AIR_SENSOR.get())
                    .build(null));

    /** On both halves of a door with a firelock (CONT-137). */
    public static final Supplier<BlockEntityType<FirelockDoorBlockEntity>> FIRELOCK_DOOR = BLOCK_ENTITIES.register(
            "firelock_door",
            () -> BlockEntityType.Builder.of(FirelockDoorBlockEntity::new, AtmosBlocks.FIRELOCK_DOOR.get())
                    .build(null));

    public static final Supplier<BlockEntityType<HeaterBlockEntity>> HEATER = BLOCK_ENTITIES.register(
            "heater",
            () -> BlockEntityType.Builder.of(HeaterBlockEntity::new, AtmosBlocks.HEATER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<AirConditionerBlockEntity>> AIR_CONDITIONER = BLOCK_ENTITIES.register(
            "air_conditioner",
            () -> BlockEntityType.Builder.of(AirConditionerBlockEntity::new, AtmosBlocks.AIR_CONDITIONER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<PortableScrubberBlockEntity>> PORTABLE_SCRUBBER =
            BLOCK_ENTITIES.register(
                    "portable_scrubber",
                    () -> BlockEntityType.Builder.of(
                                    PortableScrubberBlockEntity::new, AtmosBlocks.PORTABLE_SCRUBBER.get())
                            .build(null));

    public static final Supplier<BlockEntityType<PortablePumpBlockEntity>> PORTABLE_PUMP = BLOCK_ENTITIES.register(
            "portable_pump",
            () -> BlockEntityType.Builder.of(PortablePumpBlockEntity::new, AtmosBlocks.PORTABLE_PUMP.get())
                    .build(null));

    public static final Supplier<BlockEntityType<RechargerBlockEntity>> RECHARGER = BLOCK_ENTITIES.register(
            "recharger",
            () -> BlockEntityType.Builder.of(RechargerBlockEntity::new, AtmosBlocks.RECHARGER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<IgniterBlockEntity>> IGNITER = BLOCK_ENTITIES.register(
            "igniter",
            () -> BlockEntityType.Builder.of(IgniterBlockEntity::new, AtmosBlocks.IGNITER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<PressurePumpBlockEntity>> PRESSURE_PUMP = BLOCK_ENTITIES.register(
            "pressure_pump",
            () -> BlockEntityType.Builder.of(PressurePumpBlockEntity::new, AtmosBlocks.PRESSURE_PUMP.get())
                    .build(null));

    public static final Supplier<BlockEntityType<VolumePumpBlockEntity>> VOLUME_PUMP = BLOCK_ENTITIES.register(
            "volume_pump",
            () -> BlockEntityType.Builder.of(VolumePumpBlockEntity::new, AtmosBlocks.VOLUME_PUMP.get())
                    .build(null));

    public static final Supplier<BlockEntityType<FilterBlockEntity>> FILTER = BLOCK_ENTITIES.register(
            "filter",
            () -> BlockEntityType.Builder.of(FilterBlockEntity::new, AtmosBlocks.FILTER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<MixerBlockEntity>> MIXER = BLOCK_ENTITIES.register(
            "mixer",
            () -> BlockEntityType.Builder.of(MixerBlockEntity::new, AtmosBlocks.MIXER.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ValveBlockEntity>> VALVE = BLOCK_ENTITIES.register(
            "valve",
            () -> BlockEntityType.Builder.of(
                            ValveBlockEntity::new, AtmosBlocks.MANUAL_VALVE.get(), AtmosBlocks.REDSTONE_VALVE.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER = BLOCK_ENTITIES.register(
            "electrolyzer",
            () -> BlockEntityType.Builder.of(ElectrolyzerBlockEntity::new, AtmosBlocks.ELECTROLYZER.get())
                    .build(null));

    // Radiators and heat exchangers (CONT-143 to CONT-148).

    public static final Supplier<BlockEntityType<RadiatorPipeBlockEntity>> RADIATOR_PIPE = BLOCK_ENTITIES.register(
            "radiator_pipe",
            () -> BlockEntityType.Builder.of(RadiatorPipeBlockEntity::new, AtmosBlocks.RADIATOR_PIPE.get())
                    .build(null));

    public static final Supplier<BlockEntityType<RadiatorAdaptorBlockEntity>> RADIATOR_ADAPTOR =
            BLOCK_ENTITIES.register(
                    "radiator_adaptor",
                    () -> BlockEntityType.Builder.of(
                                    RadiatorAdaptorBlockEntity::new, AtmosBlocks.RADIATOR_ADAPTOR.get())
                            .build(null));

    public static final Supplier<BlockEntityType<HeatExchangerBlockEntity>> HEAT_EXCHANGER = BLOCK_ENTITIES.register(
            "heat_exchanger",
            () -> BlockEntityType.Builder.of(HeatExchangerBlockEntity::new, AtmosBlocks.HEAT_EXCHANGER.get())
                    .build(null));

    // The tank transfer valve, placed (CONT-103, D-86).

    public static final Supplier<BlockEntityType<TransferValveBlockEntity>> TANK_TRANSFER_VALVE =
            BLOCK_ENTITIES.register(
                    "tank_transfer_valve",
                    () -> BlockEntityType.Builder.of(
                                    TransferValveBlockEntity::new, AtmosBlocks.TANK_TRANSFER_VALVE.get())
                            .build(null));

    // The tank holder (CONT-149 to CONT-151, D-84).

    public static final Supplier<BlockEntityType<TankHolderBlockEntity>> TANK_HOLDER = BLOCK_ENTITIES.register(
            "tank_holder",
            () -> BlockEntityType.Builder.of(TankHolderBlockEntity::new, AtmosBlocks.TANK_HOLDER.get())
                    .build(null));

    // The large gas tank: on its inlet half.

    public static final Supplier<BlockEntityType<LargeTankBlockEntity>> LARGE_GAS_TANK = BLOCK_ENTITIES.register(
            "large_gas_tank",
            () -> BlockEntityType.Builder.of(LargeTankBlockEntity::new, AtmosBlocks.LARGE_GAS_TANK.get())
                    .build(null));

    private AtmosBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
