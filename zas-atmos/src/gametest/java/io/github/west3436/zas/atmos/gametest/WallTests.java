package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.ss13.api.structure.Dismantlable;
import io.github.west3436.ss13.api.structure.Dismantled;
import io.github.west3436.ss13.api.structure.Dismantling;
import io.github.west3436.ss13.api.structure.StructureTags;
import io.github.west3436.ss13.api.tool.ToolTags;
import io.github.west3436.ss13.registry.FrameworkItems;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * This mod's walls, glass and helmets as SS13 Framework's tools find them (#540, ss13-framework#25):
 * its station walls are in {@code ss13_framework:walls}, the reinforced one and its pipe variant in
 * {@code reinforced_walls}, its reinforced glass in {@code windows}, and a tool that takes a wall with
 * a pipe in it apart leaves the bare pipe in its network. Its suit helmets shield the eyes from a
 * welder's arc.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallTests {
    private static final String BATCH = "zas_atmos_walls";

    /** A gas the ambient doesn't hold, so only what the test puts in counts. */
    private static final ResourceLocation CO2 = PipeSaveTests.GAS;

    private WallTests() {}

    /** The framework's tags hold this mod's walls, glass and helmets, and nothing else of its blocks. */
    @GameTest(template = "empty")
    public static void the_walls_glass_and_helmets_are_in_the_frameworks_tags(GameTestHelper h) {
        for (Supplier<? extends Block> wall : List.<Supplier<? extends Block>>of(
                AtmosBlocks.STATION_WALL,
                AtmosBlocks.STATION_WALL_PIPE,
                AtmosBlocks.REINFORCED_STATION_WALL,
                AtmosBlocks.REINFORCED_STATION_WALL_PIPE)) {
            h.assertTrue(wall.get().defaultBlockState().is(StructureTags.WALLS), wall.get() + " isn't a wall");
        }
        for (Supplier<? extends Block> reinforced : List.<Supplier<? extends Block>>of(
                AtmosBlocks.REINFORCED_STATION_WALL, AtmosBlocks.REINFORCED_STATION_WALL_PIPE)) {
            h.assertTrue(
                    reinforced.get().defaultBlockState().is(StructureTags.REINFORCED_WALLS),
                    reinforced.get() + " isn't a reinforced wall");
        }
        for (Supplier<? extends Block> plain :
                List.<Supplier<? extends Block>>of(AtmosBlocks.STATION_WALL, AtmosBlocks.STATION_WALL_PIPE)) {
            h.assertFalse(
                    plain.get().defaultBlockState().is(StructureTags.REINFORCED_WALLS),
                    plain.get() + " is a reinforced wall");
        }
        BlockState glass = AtmosBlocks.REINFORCED_GLASS_BLOCK.get().defaultBlockState();
        h.assertTrue(glass.is(StructureTags.WINDOWS), "the reinforced glass isn't a window");
        h.assertFalse(glass.is(StructureTags.WALLS), "the reinforced glass is a wall");
        for (Supplier<? extends Block> grille :
                List.<Supplier<? extends Block>>of(AtmosBlocks.FLOOR_GRILLE, AtmosBlocks.FLOOR_GRILLE_PIPE)) {
            h.assertFalse(grille.get().defaultBlockState().is(StructureTags.WALLS), grille.get() + " is a wall");
        }
        for (Supplier<? extends Item> helmet :
                List.<Supplier<? extends Item>>of(AtmosItems.SOFTSUIT_HELMET, AtmosItems.HARDSUIT_HELMET)) {
            h.assertTrue(
                    new ItemStack(helmet.get()).is(ToolTags.WELDING_PROTECTION), helmet.get() + " lets a welder blind");
        }
        for (Supplier<? extends Item> mask :
                List.<Supplier<? extends Item>>of(AtmosItems.BREATH_MASK, AtmosItems.GAS_MASK)) {
            h.assertFalse(new ItemStack(mask.get()).is(ToolTags.WELDING_PROTECTION), mask.get() + " shields the eyes");
        }
        h.succeed();
    }

    /**
     * Taken apart, as a welder does through {@link Dismantling}, a wall with a dyed pipe in it leaves the
     * bare pipe, in its dye with its arms, and drops the wall: the pipe is the same block entity in the
     * same network, which keeps its gas. A reinforced wall and a floor grille do the same, the grille
     * though it is no wall. A station wall with no pipe comes apart into its loot, and a stone block
     * doesn't come apart at all.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void taken_apart_a_wall_with_a_pipe_in_it_leaves_the_pipe_in_its_network(GameTestHelper h) {
        Area a = new Area(h, 6610);
        a.box(0, 0, 8, 4);
        a.fill(1, 3, 1, Map.of(N2, 10.0));
        a.place(1, 2, 2, cap(Direction.EAST));
        a.place(
                2,
                2,
                2,
                AtmosBlocks.STATION_WALL_PIPE.get().defaultBlockState().setValue(PipeColor.PROPERTY, PipeColor.BLUE));
        a.place(3, 2, 2, AtmosBlocks.REINFORCED_STATION_WALL_PIPE.get().defaultBlockState());
        a.place(4, 2, 2, AtmosBlocks.FLOOR_GRILLE_PIPE.get().defaultBlockState());
        a.place(5, 2, 2, cap(Direction.WEST));
        a.place(6, 2, 2, AtmosBlocks.STATION_WALL.get().defaultBlockState());
        a.tick();
        PipeBlockEntity first = a.entity(2, 2, 2);
        PipeNetwork net = first.network();
        net.gas().add(new GasParcel(Map.of(CO2, 20.0), T));
        ItemStack welder = new ItemStack(FrameworkItems.WELDER.get());

        // What the wall says it leaves and drops, before the welder asks it to come apart.
        BlockPos at = a.abs(2, 2, 2);
        BlockState cased = a.get(2, 2, 2);
        Dismantlable dismantlable = a.level.getCapability(Dismantlable.BLOCK, at);
        h.assertTrue(dismantlable != null, "the wall with a pipe in it isn't Dismantlable");
        Dismantled result = dismantlable.dismantled(a.level, at, cased, null, welder);
        h.assertTrue(result.left().is(AtmosBlocks.PIPE.get()), "it would leave " + result.left());
        h.assertTrue(
                result.drops().size() == 1 && result.drops().get(0).is(AtmosItems.STATION_WALL.get()),
                "it would drop " + result.drops());

        for (int x = 2; x <= 4; x++) {
            BlockState before = a.get(x, 2, 2);
            PipeBlockEntity pipe = a.entity(x, 2, 2);
            h.assertTrue(Dismantling.canTakeApart(a.level, a.abs(x, 2, 2)), "it can't take " + before + " apart");
            h.assertTrue(
                    Dismantling.takeApart(a.level, a.abs(x, 2, 2), null, welder),
                    "it didn't take " + before + " apart");
            BlockState bare = a.get(x, 2, 2);
            h.assertTrue(bare.is(AtmosBlocks.PIPE.get()), before + " left " + bare);
            h.assertValueEqual(
                    bare.getValue(PipeColor.PROPERTY), before.getValue(PipeColor.PROPERTY), "the dye of " + bare);
            for (Direction d : Direction.values()) {
                h.assertValueEqual(
                        bare.getValue(PipeBlock.CONNECTIONS.get(d)),
                        before.getValue(PipeBlock.CONNECTIONS.get(d)),
                        "the arm on the " + d + " face of " + before);
            }
            h.assertTrue(a.level.getBlockEntity(a.abs(x, 2, 2)) == pipe, "the pipe got a new block entity");
            h.assertFalse(pipe.isRemoved(), "the pipe's block entity was removed");
        }
        h.assertTrue(first.network() == net && net.valid(), "the run's network was rebuilt");
        a.close(net.gas().moles(CO2), 20, "the run's CO2");

        BlockPos wall = a.abs(6, 2, 2);
        h.assertTrue(Dismantling.takeApart(a.level, wall, null, welder), "the plain wall didn't come apart");
        h.assertTrue(a.get(6, 2, 2).isAir(), "the plain wall left " + a.get(6, 2, 2));
        h.assertFalse(Dismantling.takeApart(a.level, a.abs(0, 2, 2), null, welder), "the stone came apart");

        for (int i = 0; i < 3; i++) {
            a.step();
        }
        a.close(first.network().gas().moles(CO2), 20, "the run's CO2 after its steps");

        a.clear(-1, -1, 9, 5);
        h.succeed();
    }

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }
}
