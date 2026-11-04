package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.battery.RechargerBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.filter.FilterBlockEntity;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlockEntity;
import io.github.west3436.zas.atmos.firelock.FirelockBlockEntity;
import io.github.west3436.zas.atmos.heater.AirConditionerBlockEntity;
import io.github.west3436.zas.atmos.heater.HeaterBlockEntity;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.mixer.MixerBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlockEntity;
import io.github.west3436.zas.atmos.port.ConnectorPortBlockEntity;
import io.github.west3436.zas.atmos.portable.PortableScrubberBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlockEntity;
import io.github.west3436.zas.atmos.valve.ValveBlockEntity;
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

    public static final Supplier<BlockEntityType<PipeBlockEntity>> PIPE = BLOCK_ENTITIES.register(
            "pipe",
            () -> BlockEntityType.Builder.of(PipeBlockEntity::new, AtmosBlocks.PIPE.get())
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

    public static final Supplier<BlockEntityType<AreaAirControllerBlockEntity>> AREA_AIR_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "area_air_controller",
                    () -> BlockEntityType.Builder.of(
                                    AreaAirControllerBlockEntity::new, AtmosBlocks.AREA_AIR_CONTROLLER.get())
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

    private AtmosBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
