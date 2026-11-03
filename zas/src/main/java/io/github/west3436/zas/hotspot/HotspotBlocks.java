package io.github.west3436.zas.hotspot;

import io.github.west3436.zas.ZasMod;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The framework's blocks: only {@code zas:gas_fire} (FIRE-018). */
public final class HotspotBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZasMod.MOD_ID);

    /** A hotspot burning in gas (FIRE-018). */
    public static final DeferredBlock<GasFireBlock> GAS_FIRE = BLOCKS.register(
            "gas_fire",
            () -> new GasFireBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .replaceable()
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .lightLevel(state -> 15)
                    .sound(SoundType.WOOL)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()));

    private HotspotBlocks() {}

    /** Registers the blocks on the mod event bus. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
