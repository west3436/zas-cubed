package io.github.west3436.zas.atmos.tool;

import io.github.west3436.ss13.api.structure.Dismantlable;
import io.github.west3436.zas.atmos.pipe.EncasedPipeBlock;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * What SS13 Framework's tools do to this mod's blocks: the multitool links them ({@link Multitool}),
 * the wrench turns and removes atmos parts ({@link Wrench}), the crowbar forces firelocks ({@link
 * Crowbar}), and a tool that takes walls apart, such as the welder, leaves the bare pipe of a pipe in
 * a casing ({@link EncasedPipeBlock#DISMANTLABLE}). The tools are the framework's items; ZAS³ only
 * offers each block's capabilities.
 */
public final class ToolProviders {
    private ToolProviders() {}

    /** Registers the listener. Called once, from the mod constructor, with the content on. */
    public static void register(IEventBus modBus) {
        modBus.addListener(ToolProviders::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        Multitool.register(event);
        Wrench.register(event);
        Crowbar.register(event);
        event.registerBlock(
                Dismantlable.BLOCK,
                (level, pos, state, be, context) -> EncasedPipeBlock.DISMANTLABLE,
                AtmosBlocks.encasedPipes().stream().map(DeferredBlock::get).toArray(Block[]::new));
    }
}
