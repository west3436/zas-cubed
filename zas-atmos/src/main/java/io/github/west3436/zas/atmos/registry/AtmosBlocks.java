package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's blocks. Each block with an item also gets one in {@link AtmosItems}. */
public final class AtmosBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZasAtmosMod.MOD_ID);

    /** A pipe segment (CONT-001, CONT-007). */
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe", PipeBlock::new, metal());

    /** The area air controller, a wall panel (CONT-040, D-68). */
    public static final DeferredBlock<AreaAirControllerBlock> AREA_AIR_CONTROLLER =
            BLOCKS.registerBlock("area_air_controller", AreaAirControllerBlock::new, metal().strength(1.5f, 4.8f));

    /** The solid-fuel generator (CONT-061). */
    public static final DeferredBlock<GeneratorBlock> GENERATOR =
            BLOCKS.registerBlock("generator", GeneratorBlock::new, metal());

    private AtmosBlocks() {}

    /** The properties of a metal atmospherics part: as hard as a hopper, mined with a pickaxe. */
    public static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 4.8f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
