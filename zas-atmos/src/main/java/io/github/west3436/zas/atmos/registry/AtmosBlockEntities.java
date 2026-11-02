package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.power.GeneratorBlockEntity;
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

    public static final Supplier<BlockEntityType<AreaAirControllerBlockEntity>> AREA_AIR_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "area_air_controller",
                    () -> BlockEntityType.Builder.of(
                                    AreaAirControllerBlockEntity::new, AtmosBlocks.AREA_AIR_CONTROLLER.get())
                            .build(null));

    public static final Supplier<BlockEntityType<GeneratorBlockEntity>> GENERATOR = BLOCK_ENTITIES.register(
            "generator",
            () -> BlockEntityType.Builder.of(GeneratorBlockEntity::new, AtmosBlocks.GENERATOR.get())
                    .build(null));

    private AtmosBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
