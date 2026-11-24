package io.github.west3436.zas.atmos.registry;

import com.mojang.serialization.Codec;
import io.github.west3436.ss13.device.ItemData;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.ttv.Signal;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Unit;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's item data components. Each saves with a format version ({@link ItemData}). */
public final class AtmosComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ZasAtmosMod.MOD_ID);

    /**
     * The gas a canister or portable item carries: its contents when it was mined, or a creative fill
     * (CONT-017, CONT-018, CONT-086).
     */
    public static final Supplier<DataComponentType<GasParcel>> GAS = COMPONENTS.registerComponentType(
            "gas",
            b -> b.persistent(ItemData.versioned(GasCodecs.PARCEL)).networkSynchronized(GasCodecs.PARCEL_STREAM));

    // ---- Breathing gear (S-08) ----

    /** A gas tank's distribution pressure in kPa, 0 to 304 (LIFE-044); without it, the tank's default. */
    public static final Supplier<DataComponentType<Double>> DISTRIBUTION_PRESSURE = COMPONENTS.registerComponentType(
            "distribution_pressure",
            b -> b.persistent(ItemData.versioned(Codec.DOUBLE)).networkSynchronized(ByteBufCodecs.DOUBLE));

    /**
     * A gas tank that has fallen to its last minute of breath and hasn't said so yet (CONT-127): set
     * by the breath that took it there, which doesn't know who is breathing, and cleared by the tank's
     * next atmos tick, which does.
     */
    public static final Supplier<DataComponentType<Unit>> TANK_LOW = COMPONENTS.registerComponentType(
            "tank_low",
            b -> b.persistent(ItemData.versioned(Codec.unit(Unit.INSTANCE)))
                    .networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    /**
     * A gas tank in the middle of a leak (CONT-038, CONT-127, CLIENT-018): the way its jet points, in
     * radians around the vertical. Set by the check that hisses as it starts, and cleared by the first
     * that finds the tank holding, so that one leak hisses once and keeps one direction.
     */
    public static final Supplier<DataComponentType<Float>> LEAKING = COMPONENTS.registerComponentType(
            "leaking", b -> b.persistent(ItemData.versioned(Codec.FLOAT)).networkSynchronized(ByteBufCodecs.FLOAT));

    /** The tank in a suit chestplate's tank slot (CONT-090): one stack, or none. */
    public static final Supplier<DataComponentType<ItemContainerContents>> SUIT_TANK = COMPONENTS.registerComponentType(
            "suit_tank",
            b -> b.persistent(ItemData.versioned(ItemContainerContents.CODEC))
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** Whether magnetic boots are on (CONT-033). Without it, off. */
    public static final Supplier<DataComponentType<Boolean>> MAGNETIZED = COMPONENTS.registerComponentType(
            "magnetized", b -> b.persistent(ItemData.versioned(Codec.BOOL)).networkSynchronized(ByteBufCodecs.BOOL));

    /** The sprays left in a fire extinguisher (CONT-037). */
    public static final Supplier<DataComponentType<Integer>> CHARGES = COMPONENTS.registerComponentType(
            "charges",
            b -> b.persistent(ItemData.versioned(ExtraCodecs.NON_NEGATIVE_INT))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    // ---- The tank transfer valve (CONT-103, CONT-104) ----

    /** A TTV's tanks: the first in slot 0, the second in slot 1, either empty. */
    public static final Supplier<DataComponentType<ItemContainerContents>> TTV_TANKS = COMPONENTS.registerComponentType(
            "ttv_tanks",
            b -> b.persistent(ItemData.versioned(ItemContainerContents.CODEC))
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** Whether a TTV's valve is open: its gas is in its second tank. Without it, closed. */
    public static final Supplier<DataComponentType<Boolean>> TTV_OPEN = COMPONENTS.registerComponentType(
            "ttv_open", b -> b.persistent(ItemData.versioned(Codec.BOOL)).networkSynchronized(ByteBufCodecs.BOOL));

    /** A TTV's timer setting in s, 0 to 600; without it, the default. */
    public static final Supplier<DataComponentType<Integer>> TTV_TIMER = COMPONENTS.registerComponentType(
            "ttv_timer",
            b -> b.persistent(ItemData.versioned(ExtraCodecs.NON_NEGATIVE_INT))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The trigger fitted to a TTV in place of its timer (CONT-184): a remote signaler or a proximity
     * sensor, in slot 0. Without it, its timer.
     */
    public static final Supplier<DataComponentType<ItemContainerContents>> TTV_TRIGGER =
            COMPONENTS.registerComponentType(
                    "ttv_trigger",
                    b -> b.persistent(ItemData.versioned(ItemContainerContents.CODEC))
                            .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    /** A remote signaler's frequency and code (CONT-185); without it, the default. */
    public static final Supplier<DataComponentType<Signal>> SIGNAL = COMPONENTS.registerComponentType(
            "signal", b -> b.persistent(ItemData.versioned(Signal.CODEC)).networkSynchronized(Signal.STREAM_CODEC));

    /** A proximity sensor's range in blocks (CONT-187); without it, the default. */
    public static final Supplier<DataComponentType<Integer>> PROXIMITY_RANGE = COMPONENTS.registerComponentType(
            "proximity_range",
            b -> b.persistent(ItemData.versioned(ExtraCodecs.POSITIVE_INT)).networkSynchronized(ByteBufCodecs.VAR_INT));

    private AtmosComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
