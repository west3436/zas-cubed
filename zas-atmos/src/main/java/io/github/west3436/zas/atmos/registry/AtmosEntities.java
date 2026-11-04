package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's entities. */
public final class AtmosEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ZasAtmosMod.MOD_ID);

    /** A tank transfer valve whose timer has started (CONT-104), tracked as primed TNT is. */
    public static final Supplier<EntityType<PrimedTransferValve>> PRIMED_TANK_TRANSFER_VALVE = ENTITIES.register(
            "primed_tank_transfer_valve",
            () -> EntityType.Builder.<PrimedTransferValve>of(PrimedTransferValve::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.9f, 0.4f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build("primed_tank_transfer_valve"));

    private AtmosEntities() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
