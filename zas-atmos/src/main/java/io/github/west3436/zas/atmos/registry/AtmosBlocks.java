package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.port.ConnectorPortBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.vent.VentBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's blocks. Each block with an item also gets one in {@link AtmosItems}. */
public final class AtmosBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZasAtmosMod.MOD_ID);

    /** A pipe segment (CONT-001, CONT-007). */
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe", PipeBlock::new, metal());

    /** A pipe end cap, which closes a pipe's open end (CONT-004). */
    public static final DeferredBlock<PipeCapBlock> PIPE_CAP =
            BLOCKS.registerBlock("pipe_cap", PipeCapBlock::new, metal());

    /** A connector port, which a canister stands on to join its network (CONT-083). */
    public static final DeferredBlock<ConnectorPortBlock> CONNECTOR_PORT =
            BLOCKS.registerBlock("connector_port", ConnectorPortBlock::new, metal());

    /** A gas canister (CONT-017). */
    public static final DeferredBlock<CanisterBlock> CANISTER =
            BLOCKS.registerBlock("canister", CanisterBlock::new, metal());

    /** A vent (CONT-014). */
    public static final DeferredBlock<VentBlock> VENT = BLOCKS.registerBlock("vent", VentBlock::new, metal());
    /** The area air controller, a wall panel (CONT-040, D-68). */
    public static final DeferredBlock<AreaAirControllerBlock> AREA_AIR_CONTROLLER =
            BLOCKS.registerBlock("area_air_controller", AreaAirControllerBlock::new, metal().strength(1.5f, 4.8f));

    /** The solid-fuel generator (CONT-061). */
    public static final DeferredBlock<GeneratorBlock> GENERATOR =
            BLOCKS.registerBlock("generator", GeneratorBlock::new, metal());

    /** A standalone firelock, one block wide and two high (CONT-043). As hard as an iron door. */
    public static final DeferredBlock<FirelockBlock> FIRELOCK = BLOCKS.registerBlock(
            "firelock", FirelockBlock::new, metal().strength(5.0f, 6.0f).pushReaction(PushReaction.BLOCK));

    /** The heater (CONT-019). */
    public static final DeferredBlock<HeaterBlock> HEATER = BLOCKS.registerBlock("heater", HeaterBlock::new, metal());

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
