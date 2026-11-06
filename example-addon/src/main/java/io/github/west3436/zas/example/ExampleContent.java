package io.github.west3436.zas.example;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The blocks and items the examples hang on. They're ordinary content: what's specific to ZAS³ is
 * attached to them in {@link ExampleCapabilities}. Their models are vanilla's, so nothing shows when a
 * shutter opens, and they're in no creative tab: use {@code /give}.
 */
public final class ExampleContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ExampleAddonMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ExampleAddonMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExampleAddonMod.MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ExampleAddonMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ExampleAddonMod.MOD_ID);

    public static final DeferredBlock<GasTankBlock> GAS_TANK = BLOCKS.registerBlock(
            "gas_tank", GasTankBlock::new, BlockBehaviour.Properties.of().strength(2.0f));

    /** A shutter ZAS³ reads the shape of (GEO-043). */
    public static final DeferredBlock<ShutterBlock> SHUTTER = BLOCKS.registerBlock(
            "shutter", ShutterBlock::new, BlockBehaviour.Properties.of().strength(2.0f));

    /** The same block, with a seal provider that answers for it (API-023). */
    public static final DeferredBlock<ShutterBlock> AIRLOCK = BLOCKS.registerBlock(
            "airlock", ShutterBlock::new, BlockBehaviour.Properties.of().strength(2.0f));

    public static final Supplier<BlockEntityType<GasTankBlockEntity>> GAS_TANK_ENTITY = BLOCK_ENTITIES.register(
            "gas_tank",
            () -> BlockEntityType.Builder.of(GasTankBlockEntity::new, GAS_TANK.get())
                    .build(null));

    public static final Supplier<BlockEntityType<ShutterBlockEntity>> SHUTTER_ENTITY = BLOCK_ENTITIES.register(
            "shutter",
            () -> BlockEntityType.Builder.of(ShutterBlockEntity::new, SHUTTER.get(), AIRLOCK.get())
                    .build(null));

    /** The moles of O₂ left in an {@link #OXYGEN_TANK}. */
    public static final Supplier<DataComponentType<Double>> OXYGEN_MOLES = COMPONENTS.registerComponentType(
            "oxygen_moles", builder -> builder.persistent(Codec.DOUBLE).networkSynchronized(ByteBufCodecs.DOUBLE));

    public static final DeferredItem<ArmorItem> PRESSURE_HELMET = ITEMS.registerItem(
            "pressure_helmet", properties -> new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET, properties));

    public static final DeferredItem<Item> OXYGEN_TANK = ITEMS.registerItem(
            "oxygen_tank",
            properties -> new Item(
                    properties.stacksTo(1).component(OXYGEN_MOLES.get(), ExampleLifeSupport.OxygenTank.FULL_MOLES)));

    /**
     * A gas tank filling. Its sound is vanilla's, played through {@code sounds.json}, and {@link
     * ExampleClient} has ZAS³ fade it in thin air.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_HISS =
            SOUNDS.register("tank_hiss", SoundEvent::createVariableRangeEvent);

    static {
        ITEMS.registerSimpleBlockItem(GAS_TANK);
        ITEMS.registerSimpleBlockItem(SHUTTER);
        ITEMS.registerSimpleBlockItem(AIRLOCK);
    }

    private ExampleContent() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
    }
}
