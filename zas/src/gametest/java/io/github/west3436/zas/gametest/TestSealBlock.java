package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.ISealProvider;
import io.github.west3436.zas.api.capability.SealGeometry;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * {@code zas:test_seal_block}, a full block whose {@link ISealProvider#BLOCK} answers what a test
 * told it for that position (API-023), registered from the GameTest source set only, so never in the
 * mod jar. It remembers how often it was asked at each position, and by which thread the last time.
 */
@EventBusSubscriber(modid = ZasMod.MOD_ID)
public final class TestSealBlock {
    /** The test block's id. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "test_seal_block");

    private static Block block;

    private record Asked(int times, Thread by) {}

    private static final Map<BlockPos, Optional<SealGeometry>> ANSWERS = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Asked> ASKED = new ConcurrentHashMap<>();

    private TestSealBlock() {}

    static BlockState state() {
        return block.defaultBlockState();
    }

    /** What the provider answers at {@code pos} from now on; null for no answer, leaving the shape's geometry. */
    static void answer(BlockPos pos, SealGeometry geometry) {
        ANSWERS.put(pos.immutable(), Optional.ofNullable(geometry));
    }

    /** How often the provider has been asked at {@code pos}. */
    static int asked(BlockPos pos) {
        Asked a = ASKED.get(pos);
        return a == null ? 0 : a.times();
    }

    /** The thread that asked last at {@code pos}. */
    static Thread askedBy(BlockPos pos) {
        Asked a = ASKED.get(pos);
        return a == null ? null : a.by();
    }

    private static ISealProvider provider(BlockPos at) {
        BlockPos pos = at.immutable();
        return () -> {
            ASKED.merge(
                    pos,
                    new Asked(1, Thread.currentThread()),
                    (old, now) -> new Asked(old.times() + now.times(), now.by()));
            return ANSWERS.getOrDefault(pos, Optional.empty());
        };
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, ID, () -> {
            block = new Block(BlockBehaviour.Properties.of().noLootTable());
            return block;
        });
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(ISealProvider.BLOCK, (level, pos, state, blockEntity, context) -> provider(pos), block);
    }
}
