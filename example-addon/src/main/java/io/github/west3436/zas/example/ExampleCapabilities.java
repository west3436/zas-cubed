package io.github.west3436.zas.example;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.capability.ISealProvider;
import io.github.west3436.zas.api.capability.SealGeometry;
import java.util.Optional;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Every capability the addon provides, in one place. Without ZAS³ the capabilities still exist, and
 * nothing here but this addon's own tests reads them.
 */
final class ExampleCapabilities {
    private ExampleCapabilities() {}

    /** On the mod bus. A seal provider must register at any priority but the lowest; this is the normal one. */
    static void register(RegisterCapabilitiesEvent event) {
        // API-021: pipes and other mods fill and drain the tank. Answering null on some faces would
        // offer it only on the others.
        event.registerBlockEntity(IGasHandler.BLOCK, ExampleContent.GAS_TANK_ENTITY.get(), (tank, side) -> tank);

        // API-023: the airlock says for itself what it seals. ZAS³ keeps the answer, so
        // ShutterBlockEntity.setOpen calls markDirty.
        event.registerBlock(
                ISealProvider.BLOCK,
                (level, pos, state, blockEntity, context) -> () -> Optional.of(
                        blockEntity instanceof ShutterBlockEntity shutter && shutter.isOpen()
                                ? SealGeometry.OPEN
                                : SealGeometry.SEALED),
                ExampleContent.AIRLOCK.get());

        // API-022: a worn item, an item holding a supply of gas, and an entity's own protection.
        event.registerItem(
                ILifeSupport.ITEM,
                (stack, context) -> ExampleLifeSupport.PRESSURE_HELMET,
                ExampleContent.PRESSURE_HELMET.get());
        event.registerItem(
                ILifeSupport.ITEM,
                (stack, context) -> new ExampleLifeSupport.OxygenTank(stack),
                ExampleContent.OXYGEN_TANK.get());
        event.registerEntity(ILifeSupport.ENTITY, EntityType.STRAY, (stray, context) -> ExampleLifeSupport.COLD_PROOF);
    }
}
