package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's item data components. */
public final class AtmosComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ZasAtmosMod.MOD_ID);

    /** The gas a canister item carries: its contents when it was mined, or a creative fill (CONT-017, CONT-086). */
    public static final Supplier<DataComponentType<GasParcel>> GAS = COMPONENTS.registerComponentType(
            "gas", b -> b.persistent(GasCodecs.PARCEL).networkSynchronized(GasCodecs.PARCEL_STREAM));

    private AtmosComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
