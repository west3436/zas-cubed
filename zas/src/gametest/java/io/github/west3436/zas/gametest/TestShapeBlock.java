package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * {@code zas:test_shape_block}, a block whose collision shape is whatever a test told it for that
 * position, as a block's data might decide it (GEO-014, GEO-043). Registered from the GameTest source
 * set only, so never in the mod jar.
 *
 * <p>Anywhere it wasn't told, the origin included, its shape is {@link #ACROSS_Z}. So the cells of its
 * block state divide it along z, and a test's shape that divides it along x has different cells from
 * those even when it has as many.
 */
@EventBusSubscriber(modid = ZasMod.MOD_ID)
public final class TestShapeBlock {
    /** The test block's id. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "test_shape_block");

    /** A wall across z through the block's middle, leaving two cells. */
    static final VoxelShape ACROSS_Z = Block.box(0, 0, 7, 16, 16, 9);

    private static final Map<BlockPos, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final Set<BlockPos> BROKEN = ConcurrentHashMap.newKeySet();

    private static Block block;

    private TestShapeBlock() {}

    static BlockState state() {
        return block.defaultBlockState();
    }

    /** The block's shape at {@code pos} from now on. */
    static void shape(BlockPos pos, VoxelShape shape) {
        SHAPES.put(pos.immutable(), shape);
    }

    /** Makes the block's shape at {@code pos} throw from now on, or stop throwing. */
    static void broken(BlockPos pos, boolean broken) {
        if (broken) {
            BROKEN.add(pos.immutable());
        } else {
            BROKEN.remove(pos);
        }
    }

    private static VoxelShape at(BlockPos pos) {
        if (BROKEN.contains(pos)) {
            throw new IllegalStateException("a test's broken shape");
        }
        return SHAPES.getOrDefault(pos, ACROSS_Z);
    }

    private static final class Shaped extends Block {
        Shaped() {
            super(BlockBehaviour.Properties.of().noLootTable().dynamicShape().noOcclusion());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return at(pos);
        }

        @Override
        protected VoxelShape getCollisionShape(
                BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return at(pos);
        }
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, ID, () -> {
            block = new Shaped();
            return block;
        });
    }
}
