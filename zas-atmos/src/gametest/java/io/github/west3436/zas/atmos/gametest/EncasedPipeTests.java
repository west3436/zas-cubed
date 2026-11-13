package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.pipe.EncasedPipeBlock;
import io.github.west3436.zas.atmos.pipe.FloorGrilleBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.pipe.PipeColor;
import io.github.west3436.zas.atmos.pipe.PipeItem;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.pipe.PipeVolumes;
import io.github.west3436.zas.atmos.registry.AtmosBlockEntities;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.WrenchItem;
import io.github.west3436.zas.change.ChangeTracker;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.geometry.Geometries;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Pipes in walls (#487): a pipe used on a station wall goes into it, and the wall with the pipe in it
 * is the wall for the air and a pipe for the pipes. Two rooms either side of it stay two zones with
 * their own gas, while one network runs through it. The pipe comes back out with a wrench, and the
 * block drops both when mined.
 *
 * <p>The floor grille (#486): a grating that is a full, gas-tight block, seen through, and takes a pipe
 * as a station wall does. A pipe used on it goes into it and it goes round a pipe it is used on, and
 * either way the rooms beside it stay apart. A wrench takes the pipe back out and leaves the grille.
 *
 * <p>Either way round, a sneaking click joins nothing: the pipe, the wall or the grille in hand is
 * placed against the block clicked, as against any other.
 *
 * <p>Each test builds in its own {@link Area} and drives its atmos ticks and device steps itself,
 * within one game tick, so they share a batch of their own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EncasedPipeTests {
    private static final String BATCH = "zas_atmos_encased_pipes";

    /** A gas the ambient doesn't hold, so only what a test puts in counts. */
    private static final ResourceLocation CO2 = PipeSaveTests.GAS;

    /** The x of the wall between the two rooms, which are 3 × 3 × 3 at x 1 to 3 and 5 to 7. */
    private static final int WALL = 4;

    private EncasedPipeTests() {}

    private static BlockState wall() {
        return AtmosBlocks.STATION_WALL.get().defaultBlockState();
    }

    private static BlockState inWall() {
        return AtmosBlocks.STATION_WALL_PIPE.get().defaultBlockState();
    }

    private static BlockState inWall(PipeColor color) {
        return inWall().setValue(PipeColor.PROPERTY, color);
    }

    private static BlockState grille() {
        return AtmosBlocks.FLOOR_GRILLE.get().defaultBlockState();
    }

    private static BlockState inGrille() {
        return AtmosBlocks.FLOOR_GRILLE_PIPE.get().defaultBlockState();
    }

    private static BlockState inGrille(PipeColor color) {
        return inGrille().setValue(PipeColor.PROPERTY, color);
    }

    private static BlockState cap(Direction facing) {
        return AtmosBlocks.PIPE_CAP.get().defaultBlockState().setValue(PipeCapBlock.FACING, facing);
    }

    /**
     * Two sealed rooms in a stone box, a station wall between them at x = {@link #WALL}. The west room
     * holds {@code west} more moles of nitrogen than the air it formed with, and the east 5 of oxygen,
     * so the two differ in pressure and in what they hold.
     */
    private static void rooms(Area a, double west) {
        a.box(0, 0, 8, 4);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                a.set(WALL, y, z, wall());
            }
        }
        a.fill(1, 3, 1, Map.of(N2, west));
        a.fill(7, 3, 1, Map.of(O2, 5.0));
        a.tick();
    }

    /** A room's zone and what it holds, at a cell no test builds in: x = 1 for the west room, 7 for the east. */
    private record Room(ZoneId id, double n2, double o2) {
        static Room of(Area a, int x) {
            ZoneInfo zone = a.zone(x, 3, 1);
            return new Room(zone.id(), zone.mixture().moles(N2), zone.mixture().moles(O2));
        }
    }

    /** Fails unless a room is the zone it was, with the nitrogen and oxygen it had and this much CO2. */
    private static void same(Area a, int x, Room before, double co2, String when) {
        ZoneInfo now = a.zone(x, 3, 1);
        String room = when + ": the " + (x < WALL ? "west" : "east") + " room's ";
        a.h.assertValueEqual(now.id(), before.id(), room + "zone");
        a.close(now.mixture().moles(N2), before.n2(), room + "N2");
        a.close(now.mixture().moles(O2), before.o2(), room + "O2");
        a.close(now.mixture().moles(CO2), co2, room + "CO2");
    }

    private static Player holding(GameTestHelper h, GameType mode, ItemStack stack) {
        Player player = h.makeMockPlayer(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    private static UseOnContext click(Area a, Player player, int x, int y, int z, Direction face, boolean sneak) {
        BlockPos pos = a.abs(x, y, z);
        player.setShiftKeyDown(sneak);
        Vec3 hit = Vec3.atCenterOf(pos).relative(face, 0.5);
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
    }

    /** A use of the item in hand on a face of a block, as the game makes it once the block has passed. */
    private static InteractionResult use(Area a, Player player, int x, int y, int z, Direction face, boolean sneak) {
        return player.getMainHandItem().useOn(click(a, player, x, y, z, face, sneak));
    }

    /** A use of the item in hand that the block gets first, as the game gives it one when not sneaking. */
    private static ItemInteractionResult useOnBlock(Area a, Player player, int x, int y, int z, Direction face) {
        UseOnContext click = click(a, player, x, y, z, face, false);
        return a.get(x, y, z)
                .useItemOn(
                        player.getMainHandItem(),
                        a.level,
                        player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(click.getClickLocation(), face, click.getClickedPos(), false));
    }

    /** A player whose clicks go through the game's own path, which is what tells a sneaking click from a plain one. */
    private static ServerPlayer visitor(Area a, ItemStack held) {
        FakePlayer player = new FakePlayer(a.level, new GameProfile(UUID.randomUUID(), "zas_encased_pipes"));
        player.moveTo(Vec3.atBottomCenterOf(a.abs(0, 5, 0)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /** A right-click on a face of a block with what is in hand, as the game makes it: the block first, unless sneaking. */
    private static InteractionResult rightClick(
            Area a, ServerPlayer player, int x, int y, int z, Direction face, boolean sneak) {
        BlockPos pos = a.abs(x, y, z);
        player.setShiftKeyDown(sneak);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
        InteractionResult result =
                player.gameMode.useItemOn(player, a.level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
        return result;
    }

    /** A click with the wrench in hand, which acts before the block does. */
    private static InteractionResult wrench(Area a, Player player, int x, int y, int z, Direction face, boolean sneak) {
        return player.getMainHandItem().onItemUseFirst(click(a, player, x, y, z, face, sneak));
    }

    /**
     * The pipe at a place, loaded now if a player's click has just made it: the game would load it at
     * the start of the next tick, and these tests run within one.
     */
    private static PipeBlockEntity pipe(Area a, int x, int y, int z) {
        PipeBlockEntity pipe = a.entity(x, y, z);
        if (!DeviceTicker.isScheduled(pipe)) {
            pipe.onLoad();
        }
        return pipe;
    }

    private static CompoundTag throughDisk(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            return NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- The blocks ----------------------------------------------------------------------------------

    /**
     * A wall with a pipe in it is its wall in every state: a full gas-tight block with no cell and a U
     * of 0, the same geometry as the wall's, so that swapping one for the other changes nothing for the
     * zones. Its hardness, blast resistance, tool and sound are the wall's, it hides what is behind it,
     * and it has no item: picking it gives the wall. Its block entity is the pipe's.
     */
    @GameTest(template = "empty")
    public static void a_wall_with_a_pipe_in_it_is_its_wall(GameTestHelper h) {
        StationBlockTests.block(h, AtmosBlocks.STATION_WALL_PIPE.get(), 0, 6, 3, BlockTags.NEEDS_STONE_TOOL);
        StationBlockTests.block(
                h, AtmosBlocks.REINFORCED_STATION_WALL_PIPE.get(), 0, 1200, 25, BlockTags.NEEDS_DIAMOND_TOOL);
        Map<Block, EncasedPipeBlock> walls = Map.of(
                AtmosBlocks.STATION_WALL.get(), AtmosBlocks.STATION_WALL_PIPE.get(),
                AtmosBlocks.REINFORCED_STATION_WALL.get(), AtmosBlocks.REINFORCED_STATION_WALL_PIPE.get());
        h.assertValueEqual(
                AtmosBlocks.encasedPipes().size(),
                walls.size() + 1,
                "the blocks a pipe goes into: the walls, and the grille");
        for (Map.Entry<Block, EncasedPipeBlock> pair : walls.entrySet()) {
            Block wall = pair.getKey();
            EncasedPipeBlock piped = pair.getValue();
            String name = piped.getDescriptionId();
            h.assertTrue(piped.shell() == wall, name + "'s wall");
            h.assertTrue(EncasedPipeBlock.in(wall) == piped, "what a pipe makes of " + wall.getDescriptionId());
            h.assertTrue(EncasedPipeBlock.of(wall.asItem()) == piped, "what " + wall.asItem() + " makes of a pipe");
            h.assertTrue(piped.asItem() == Items.AIR, name + " has an item");
            h.assertTrue(piped instanceof PipeBlock, name + " isn't a pipe");
            for (BlockState state : piped.getStateDefinition().getPossibleStates()) {
                h.assertTrue(
                        Geometries.table().geometry(state) == Geometries.table().geometry(wall.defaultBlockState()),
                        state + " hasn't its wall's geometry");
                h.assertTrue(state.canOcclude(), state + " is seen through");
                h.assertFalse(state.propagatesSkylightDown(h.getLevel(), BlockPos.ZERO), state + " lets light by");
                h.assertFalse(piped.leaks(state), state + " has an open end");
                h.assertTrue(AtmosBlockEntities.PIPE.get().isValid(state), state + " can't hold a pipe's block entity");
            }
            BlockState state = piped.defaultBlockState();
            h.assertTrue(state.getSoundType() == SoundType.METAL, name + "'s sound");
            h.assertTrue(
                    piped.getCloneItemStack(h.getLevel(), BlockPos.ZERO, state).is(wall.asItem()),
                    "picking " + name + " doesn't give its wall");
        }
        h.assertTrue(
                EncasedPipeBlock.in(AtmosBlocks.REINFORCED_GLASS_BLOCK.get()) == null, "a pipe goes into the glass");
        h.assertTrue(EncasedPipeBlock.of(Items.STONE) == null, "stone goes round a pipe");
        h.succeed();
    }

    /**
     * What a restart keeps: the block's state, with its dye and its arms, and the pipe's block entity
     * with its share of the gas, read back from what it wrote as a pipe's.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_in_a_wall_is_saved_with_its_dye_and_its_gas(GameTestHelper h) {
        Area a = new Area(h, 4877);
        a.clear(0, 0, 6, 4);
        a.place(2, 2, 2, cap(Direction.EAST));
        a.place(3, 2, 2, inWall(PipeColor.CYAN));
        a.place(4, 2, 2, cap(Direction.WEST));
        BlockPos pos = a.abs(3, 2, 2);
        BlockState state = a.get(3, 2, 2);
        h.assertTrue(
                state.getValue(PipeBlock.CONNECTIONS.get(Direction.EAST))
                        && state.getValue(PipeBlock.CONNECTIONS.get(Direction.WEST)),
                "the pipe in the wall doesn't join its caps");
        PipeBlockEntity pipe = a.entity(3, 2, 2);
        pipe.network().gas().add(new GasParcel(Map.of(CO2, 21.0), T));
        double share = 21.0 * PipeVolumes.PIPE / pipe.network().volume();

        BlockState read = NbtUtils.readBlockState(
                a.level.holderLookup(Registries.BLOCK), throughDisk(NbtUtils.writeBlockState(state)));
        h.assertTrue(read == state, "the state read back is " + read + ", not " + state);
        CompoundTag saved = throughDisk(pipe.saveWithFullMetadata(a.level.registryAccess()));
        h.assertValueEqual(saved.getString("id"), "zas_atmos:pipe", "the block entity's id");
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, saved, a.level.registryAccess());
        h.assertTrue(loaded instanceof PipeBlockEntity, "what loads is " + loaded);
        a.close(((PipeBlockEntity) loaded).share().moles(CO2), share, "the share that loads");
        h.assertFalse(((PipeBlockEntity) loaded).isOpen(), "the pipe that loads has an open end");

        a.clear(0, 0, 6, 4);
        h.succeed();
    }

    // ---- Putting a pipe into a wall ----------------------------------------------------------------

    /**
     * A pipe used on a station wall, or a reinforced one, goes into it with its dye, and one is used
     * up. The wall is never marked for the zones, and the rooms either side, at different pressures,
     * stay the zones they were with the gas they had. Sneaking, the pipe is placed against the wall,
     * and on any other block it is placed against it as ever.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_used_on_a_station_wall_goes_into_it_and_the_rooms_stay_apart(GameTestHelper h) {
        Area a = new Area(h, 4870);
        rooms(a, 400);
        a.set(WALL, 3, 3, AtmosBlocks.REINFORCED_STATION_WALL.get().defaultBlockState());
        a.tick();
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        double apart =
                a.zone(1, 3, 1).mixture().pressure() - a.zone(7, 3, 1).mixture().pressure();
        h.assertTrue(apart > 20, "the rooms are only " + apart + " kPa apart, so the test proves little");
        Player player = holding(h, GameType.SURVIVAL, PipeItem.of(AtmosItems.PIPE.get(), PipeColor.RED, 4));

        BlockPos pos = a.abs(WALL, 1, 1);
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "the wall is still marked from being built");
        h.assertTrue(use(a, player, WALL, 1, 1, Direction.WEST, false).consumesAction(), "the pipe didn't go in");
        BlockState piped = a.get(WALL, 1, 1);
        h.assertTrue(piped.is(AtmosBlocks.STATION_WALL_PIPE.get()), "the wall is now " + piped);
        h.assertValueEqual(piped.getValue(PipeColor.PROPERTY), PipeColor.RED, "the dye of the pipe in the wall");
        h.assertValueEqual(PipeBlock.connections(piped), 0, "the arms of a pipe with nothing beside it");
        h.assertValueEqual(player.getMainHandItem().getCount(), 3, "the pipes left in hand");
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "a pipe going into the wall marked it for the zones");
        h.assertFalse(pipe(a, WALL, 1, 1).isOpen(), "the pipe in the wall has an open end");

        h.assertTrue(
                use(a, player, WALL, 3, 3, Direction.EAST, false).consumesAction(), "the second pipe didn't go in");
        h.assertTrue(
                a.get(WALL, 3, 3).is(AtmosBlocks.REINFORCED_STATION_WALL_PIPE.get()),
                "the reinforced wall is now " + a.get(WALL, 3, 3));
        h.assertValueEqual(player.getMainHandItem().getCount(), 2, "the pipes left in hand");
        h.assertFalse(ChangeTracker.isDirty(a.level, a.abs(WALL, 3, 3)), "the reinforced wall was marked");
        pipe(a, WALL, 3, 3);

        for (int i = 0; i < 6; i++) {
            a.step();
            same(a, 1, west, 0, "step " + i);
            same(a, 7, east, 0, "step " + i);
        }

        // Sneaking, it goes against the wall, which stays a wall.
        h.assertTrue(use(a, player, WALL, 2, 2, Direction.WEST, true).consumesAction(), "the sneaking use");
        h.assertTrue(a.get(WALL, 2, 2) == wall(), "sneaking, the pipe went into the wall");
        BlockState against = a.get(WALL - 1, 2, 2);
        h.assertTrue(against.is(AtmosBlocks.PIPE.get()), "sneaking, no pipe was placed against the wall: " + against);
        h.assertValueEqual(against.getValue(PipeColor.PROPERTY), PipeColor.RED, "the dye of the pipe placed");
        h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the pipes left in hand");
        // And stone takes no pipe.
        h.assertTrue(use(a, player, 2, 0, 2, Direction.UP, false).consumesAction(), "the use on stone");
        h.assertTrue(a.get(2, 0, 2) == Area.STONE, "the stone is now " + a.get(2, 0, 2));
        h.assertTrue(a.get(2, 1, 2).is(AtmosBlocks.PIPE.get()), "no pipe was placed on the stone");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the pipes left in hand");

        a.clear(-1, -1, 9, 5);
        player.discard();
        h.succeed();
    }

    /**
     * The issue's case: a canister on a port in each room, and pipes from each to the wall between
     * them. With a pipe put into the wall the two runs are one network, the pipe in the wall a 70 L
     * member like any other, and the west canister's gas reaches the east one. Through a dozen steps
     * the rooms stay the zones they were, each with its own gas and none of the pipes': nothing crosses
     * but through the pipe.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_through_a_wall_joins_the_runs_either_side_and_the_wall_stays_shut(GameTestHelper h) {
        Area a = new Area(h, 4871);
        rooms(a, 400);
        for (int x : new int[] {2, 6}) {
            a.place(x, 1, 2, GasDeviceTests.pipe());
            a.place(x, 2, 2, GasDeviceTests.port());
            a.place(x, 3, 2, GasDeviceTests.canister());
        }
        a.place(3, 1, 2, GasDeviceTests.pipe());
        a.place(5, 1, 2, GasDeviceTests.pipe());
        a.tick();
        PipeNodeEntity westCanister = a.entity(2, 3, 2);
        PipeNodeEntity eastCanister = a.entity(6, 3, 2);
        h.assertFalse(westCanister.network().members().contains(eastCanister), "the canisters share a network already");
        westCanister.network().gas().add(new GasParcel(Map.of(CO2, 100.0), T));
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);

        Player player = holding(h, GameType.SURVIVAL, new ItemStack(AtmosItems.PIPE.get()));
        h.assertTrue(use(a, player, WALL, 1, 2, Direction.WEST, false).consumesAction(), "the pipe didn't go in");
        BlockState piped = a.get(WALL, 1, 2);
        h.assertTrue(piped.is(AtmosBlocks.STATION_WALL_PIPE.get()), "the wall is now " + piped);
        for (Direction d : Direction.values()) {
            h.assertValueEqual(
                    piped.getValue(PipeBlock.CONNECTIONS.get(d)),
                    d.getAxis() == Direction.Axis.X,
                    "whether the pipe in the wall joins on its " + d + " face");
        }
        h.assertTrue(GasDeviceTests.joined(a.get(3, 1, 2), Direction.EAST), "the west pipe doesn't join the wall's");
        h.assertTrue(GasDeviceTests.joined(a.get(5, 1, 2), Direction.WEST), "the east pipe doesn't join the wall's");

        PipeNetwork net = pipe(a, WALL, 1, 2).network();
        h.assertTrue(
                net.members().contains(westCanister) && net.members().contains(eastCanister),
                "the canisters aren't on the network through the wall");
        double volume = 5 * PipeVolumes.PIPE + 2 * PipeVolumes.PORT + 2 * PipeVolumes.CANISTER;
        a.close(net.volume(), volume, "the network's volume");
        a.close(net.gas().moles(CO2), 100, "the network's CO2");
        a.close(eastCanister.share().moles(CO2), 100 * PipeVolumes.CANISTER / volume, "the east canister's CO2");

        for (int i = 0; i < 12; i++) {
            a.step();
            same(a, 1, west, 0, "step " + i);
            same(a, 7, east, 0, "step " + i);
        }
        net = pipe(a, WALL, 1, 2).network();
        a.close(net.volume(), volume, "the network's volume after 12 steps");
        a.close(net.gas().moles(CO2), 100, "the network's CO2 after 12 steps");

        a.clear(-1, -1, 9, 5);
        player.discard();
        h.succeed();
    }

    /**
     * A pipe in a wall has no open end, where a bare pipe with fewer than two connections has one
     * (CONT-004): alone, and then with one capped pipe on it, it keeps gas at ten times the rooms'
     * pressure through its steps, and neither room gets any.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_in_a_wall_has_no_open_end(GameTestHelper h) {
        Area a = new Area(h, 4872);
        rooms(a, 400);
        a.place(WALL, 2, 2, inWall());
        PipeBlockEntity pipe = a.entity(WALL, 2, 2);
        pipe.network().gas().add(new GasParcel(Map.of(CO2, 30.0), T));
        h.assertTrue(pipe.network().gas().pressure() > 1000, "the pipe isn't above the rooms' pressure");
        h.assertFalse(pipe.isOpen(), "alone in its wall, the pipe has an open end");
        h.assertTrue(AtmosBlocks.PIPE.get().leaks(GasDeviceTests.pipe()), "a bare pipe alone has no open end");
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(pipe.network().gas().moles(CO2), 30, "the CO2 of a pipe alone in its wall");
        a.close(a.zone(1, 3, 1).mixture().moles(CO2), 0, "the west room's CO2");
        a.close(a.zone(7, 3, 1).mixture().moles(CO2), 0, "the east room's CO2");

        a.place(3, 2, 2, GasDeviceTests.pipe());
        a.place(2, 2, 2, cap(Direction.EAST));
        h.assertValueEqual(PipeBlock.connections(a.get(WALL, 2, 2)), 1, "the arms of the pipe in the wall");
        h.assertFalse(pipe.isOpen(), "with one connection, the pipe in the wall has an open end");
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(pipe.network().gas().moles(CO2), 30, "the CO2 of the run that ends in the wall");
        a.close(pipe.network().volume(), 3 * PipeVolumes.PIPE, "the run's volume: two pipes and a cap");
        a.close(a.zone(1, 3, 1).mixture().moles(CO2), 0, "the west room's CO2, the run built");
        a.close(a.zone(7, 3, 1).mixture().moles(CO2), 0, "the east room's CO2, the run built");

        a.clear(-1, -1, 9, 5);
        h.succeed();
    }

    /**
     * The other order: a station wall used on a pipe goes round it. The pipe keeps its dye, its arms,
     * its block entity and its network, so its gas never moves, and one wall is used up. A second wall
     * does nothing to it, a dye still dyes it, and the reinforced wall makes the reinforced block.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_station_wall_used_on_a_pipe_goes_round_it(GameTestHelper h) {
        Area a = new Area(h, 4873);
        a.box(0, 0, 6, 4);
        a.fill(1, 3, 1, Map.of(N2, 10.0));
        a.place(1, 2, 2, cap(Direction.EAST));
        a.place(2, 2, 2, GasDeviceTests.pipe());
        a.place(3, 2, 2, GasDeviceTests.pipe().setValue(PipeColor.PROPERTY, PipeColor.BLUE));
        a.place(4, 2, 2, GasDeviceTests.pipe());
        a.place(5, 2, 2, cap(Direction.WEST));
        a.tick();
        PipeBlockEntity before = a.entity(3, 2, 2);
        PipeNetwork net = before.network();
        net.gas().add(new GasParcel(Map.of(CO2, 20.0), T));
        BlockState bare = a.get(3, 2, 2);
        Player player = holding(h, GameType.SURVIVAL, new ItemStack(AtmosItems.STATION_WALL.get(), 2));

        h.assertTrue(useOnBlock(a, player, 3, 2, 2, Direction.UP).consumesAction(), "the wall didn't go on");
        BlockState cased = a.get(3, 2, 2);
        h.assertTrue(cased.is(AtmosBlocks.STATION_WALL_PIPE.get()), "the pipe is now " + cased);
        h.assertValueEqual(cased.getValue(PipeColor.PROPERTY), PipeColor.BLUE, "the dye of the pipe in the wall");
        for (Direction d : Direction.values()) {
            h.assertValueEqual(
                    cased.getValue(PipeBlock.CONNECTIONS.get(d)),
                    bare.getValue(PipeBlock.CONNECTIONS.get(d)),
                    "the arm on the " + d + " face");
        }
        h.assertTrue(a.level.getBlockEntity(a.abs(3, 2, 2)) == before, "the pipe got a new block entity");
        h.assertFalse(before.isRemoved(), "the pipe's block entity was removed");
        h.assertTrue(before.getBlockState() == cased, "the block entity's state is " + before.getBlockState());
        h.assertTrue(before.network() == net && net.valid(), "the pipe's network was rebuilt");
        a.close(net.gas().moles(CO2), 20, "the network's CO2");
        h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the walls left in hand");

        // A pipe takes one casing.
        h.assertValueEqual(
                useOnBlock(a, player, 3, 2, 2, Direction.UP),
                ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                "a second wall on the pipe");
        h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the walls left in hand");
        // It is still dyed as a pipe is.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        h.assertTrue(useOnBlock(a, player, 3, 2, 2, Direction.UP).consumesAction(), "the dye didn't take");
        h.assertValueEqual(a.get(3, 2, 2).getValue(PipeColor.PROPERTY), PipeColor.RED, "the dye of the pipe, dyed");
        h.assertTrue(a.level.getBlockEntity(a.abs(3, 2, 2)) == before, "dyeing replaced the block entity");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.REINFORCED_STATION_WALL.get()));
        h.assertTrue(useOnBlock(a, player, 4, 2, 2, Direction.UP).consumesAction(), "the reinforced wall didn't go on");
        h.assertTrue(
                a.get(4, 2, 2).is(AtmosBlocks.REINFORCED_STATION_WALL_PIPE.get()), "the pipe is now " + a.get(4, 2, 2));
        h.assertTrue(player.getMainHandItem().isEmpty(), "the reinforced wall wasn't used up");

        for (int i = 0; i < 4; i++) {
            a.step();
        }
        a.close(before.network().gas().moles(CO2), 20, "the run's CO2 after its steps");
        a.close(a.zone(1, 3, 1).mixture().moles(CO2), 0, "the room's CO2");

        a.clear(-1, -1, 7, 5);
        player.discard();
        h.succeed();
    }

    // ---- Taking it out, and breaking it ---------------------------------------------------------------

    /**
     * A wrench has nothing to turn on a pipe in a wall. Sneaking, it takes the pipe out of the face
     * clicked and leaves the wall, never marked for the zones: the pipe drops in its dye, and its gas
     * goes into the room on that side and none into the other. It refuses while the pipe is more than
     * 2 atm over the room on that side (CONT-091), so a pipe that won't come out into the thin room
     * comes out into the dense one. In creative nothing drops.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_wrench_takes_the_pipe_out_of_the_face_clicked_and_leaves_the_wall(GameTestHelper h) {
        Area a = new Area(h, 4874);
        rooms(a, 3000);
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        BlockPos pos = a.abs(WALL, 2, 2);
        Player player = holding(h, GameType.SURVIVAL, new ItemStack(AtmosItems.WRENCH.get()));

        a.place(WALL, 2, 2, inWall(PipeColor.GREEN));
        a.<PipeBlockEntity>entity(WALL, 2, 2).network().gas().add(new GasParcel(Map.of(CO2, 2.0), T));
        BlockState piped = a.get(WALL, 2, 2);
        h.assertValueEqual(
                wrench(a, player, WALL, 2, 2, Direction.WEST, false), InteractionResult.FAIL, "a plain click");
        h.assertTrue(a.get(WALL, 2, 2) == piped, "a plain click changed the block");
        WrenchTests.watchDrops();
        h.assertTrue(wrench(a, player, WALL, 2, 2, Direction.WEST, true).consumesAction(), "the pipe's removal");
        h.assertTrue(a.get(WALL, 2, 2) == wall(), "what is left is " + a.get(WALL, 2, 2));
        h.assertTrue(a.level.getBlockEntity(pos) == null, "the wall kept the pipe's block entity");
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "taking the pipe out marked the wall for the zones");
        List<ItemEntity> drops = WrenchTests.takeDrops();
        h.assertValueEqual(drops.size(), 1, "the items dropped: " + drops);
        h.assertTrue(
                drops.get(0).getItem().is(AtmosItems.PIPE.get()),
                "what dropped is " + drops.get(0).getItem());
        h.assertValueEqual(PipeItem.color(drops.get(0).getItem()), PipeColor.GREEN, "the dropped pipe's dye");
        h.assertTrue(
                drops.get(0).getX() < pos.getX(),
                "the pipe dropped at x " + drops.get(0).getX() + ", not on the west side");
        a.tick();
        same(a, 1, west, 2, "the pipe out of the west face");
        same(a, 7, east, 0, "the pipe out of the west face");

        // Between the two rooms' limits, it won't come out of the east face and will out of the west.
        a.place(WALL, 2, 2, inWall());
        PipeNetwork net = a.<PipeBlockEntity>entity(WALL, 2, 2).network();
        double thin = a.zone(7, 3, 1).mixture().pressure();
        double dense = a.zone(1, 3, 1).mixture().pressure();
        double kpa = thin + WrenchItem.LOCK_KPA + 40;
        h.assertTrue(kpa < dense + WrenchItem.LOCK_KPA - 40, "the rooms are too close: " + thin + " and " + dense);
        PipeBurstTests.fillTo(net, kpa);
        double held = net.gas().moles(CO2);
        a.close(
                WrenchItem.overAmbient(a.level, pos, Direction.EAST),
                WrenchItem.LOCK_KPA + 40,
                "the pipe's pressure over the east room's");
        a.close(WrenchItem.overAmbient(a.level, pos, Direction.WEST), kpa - dense, "the pipe's pressure over the west");
        wrench(a, player, WALL, 2, 2, Direction.EAST, true);
        h.assertTrue(a.get(WALL, 2, 2).is(AtmosBlocks.STATION_WALL_PIPE.get()), "the pipe came out over the limit");
        Optional<Component> refusal = WrenchItem.remove(a.level, pos, player, player.getMainHandItem(), Direction.EAST);
        h.assertTrue(
                refusal.isPresent()
                        && refusal.get().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("item.zas_atmos.wrench.pressurized"),
                "the refusal: " + refusal);
        a.close(a.<PipeBlockEntity>entity(WALL, 2, 2).network().gas().moles(CO2), held, "what the pipe holds, refused");
        WrenchTests.watchDrops();
        wrench(a, player, WALL, 2, 2, Direction.WEST, true);
        h.assertTrue(a.get(WALL, 2, 2) == wall(), "the pipe didn't come out of the west face");
        drops = WrenchTests.takeDrops();
        h.assertTrue(
                drops.size() == 1 && ItemStack.matches(drops.get(0).getItem(), new ItemStack(AtmosItems.PIPE.get())),
                "an undyed pipe doesn't drop as a new one: " + drops);
        a.tick();
        same(a, 1, west, 2 + held, "the second pipe out of the west face");
        same(a, 7, east, 0, "the second pipe out of the west face");

        // In creative, the pipe comes out and nothing drops.
        a.place(WALL, 2, 2, inWall());
        Player creative = holding(h, GameType.CREATIVE, new ItemStack(AtmosItems.WRENCH.get()));
        WrenchTests.watchDrops();
        wrench(a, creative, WALL, 2, 2, Direction.EAST, true);
        h.assertTrue(a.get(WALL, 2, 2) == wall(), "in creative, the pipe didn't come out");
        h.assertTrue(WrenchTests.takeDrops().isEmpty(), "in creative, something dropped");

        a.clear(-1, -1, 9, 5);
        player.discard();
        creative.discard();
        h.succeed();
    }

    /**
     * Mined, a wall with a pipe in it drops the wall and the pipe, the pipe in its dye and an undyed
     * one as a new pipe. Its gas is released as a mined pipe's is (CONT-009): the wall gone, the two
     * rooms are open to each other, and between them they hold what the pipe held.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void mined_it_drops_the_wall_and_the_pipe_and_releases_its_gas(GameTestHelper h) {
        Area a = new Area(h, 4875);
        rooms(a, 400);
        BlockPos pos = a.abs(WALL, 2, 2);
        Player player = holding(h, GameType.SURVIVAL, new ItemStack(Items.DIAMOND_PICKAXE));

        BlockState undyed = AtmosBlocks.STATION_WALL_PIPE.get().defaultBlockState();
        List<ItemStack> drops = Block.getDrops(undyed, a.level, pos, null, player, player.getMainHandItem());
        h.assertValueEqual(drops.size(), 2, "the drops of a station wall with a pipe: " + drops);
        h.assertTrue(
                drops.stream().anyMatch(s -> ItemStack.matches(s, new ItemStack(AtmosItems.STATION_WALL.get()))),
                "no station wall among " + drops);
        h.assertTrue(
                drops.stream().anyMatch(s -> ItemStack.matches(s, new ItemStack(AtmosItems.PIPE.get()))),
                "no new pipe among " + drops);

        BlockState dyed = AtmosBlocks.REINFORCED_STATION_WALL_PIPE
                .get()
                .defaultBlockState()
                .setValue(PipeColor.PROPERTY, PipeColor.YELLOW);
        a.place(WALL, 2, 2, dyed);
        PipeBlockEntity pipe = a.entity(WALL, 2, 2);
        pipe.network().gas().add(new GasParcel(Map.of(CO2, 4.0), T));
        drops = Block.getDrops(dyed, a.level, pos, pipe, player, player.getMainHandItem());
        h.assertValueEqual(drops.size(), 2, "the drops of a reinforced wall with a pipe: " + drops);
        h.assertTrue(
                drops.stream().anyMatch(s -> s.is(AtmosItems.REINFORCED_STATION_WALL.get()) && s.getCount() == 1),
                "no reinforced wall among " + drops);
        h.assertTrue(
                drops.stream()
                        .anyMatch(s -> s.is(AtmosItems.PIPE.get())
                                && s.getCount() == 1
                                && PipeItem.color(s) == PipeColor.YELLOW),
                "no yellow pipe among " + drops);
        // A wooden pickaxe is the wrong tool for it, as for its wall.
        h.assertFalse(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(dyed), "a wooden pickaxe mines it");

        a.level.destroyBlock(pos, false);
        h.assertTrue(a.get(WALL, 2, 2).isAir(), "the block is still there");
        a.tick();
        a.tick();
        a.close(GasCensus.total(a, CO2, new ChunkPos(pos)), 4, "the CO2 in the rooms, the wall mined");

        a.clear(-1, -1, 9, 5);
        player.discard();
        h.succeed();
    }

    /**
     * A pipe in a wall bursts as a pipe does (CONT-108), measured against the room its gas would reach.
     * Just below 100 atm over that room nothing happens. Just above, the pipe goes and the wall stays:
     * the room on that side gets everything, the other none, and they are the zones they were.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_that_bursts_in_a_wall_leaves_the_wall(GameTestHelper h) {
        Area a = new Area(h, 4876);
        rooms(a, 400);
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        BlockPos pos = a.abs(WALL, 2, 2);
        a.place(WALL, 2, 2, inWall());
        PipeNetwork net = a.<PipeBlockEntity>entity(WALL, 2, 2).network();
        // The wall's middle is as near one room as the other, and the west comes first (API-015).
        double room = Atmospheres.at(a.level, Vec3.atCenterOf(pos)).pressure();
        a.close(room, a.zone(1, 3, 1).mixture().pressure(), "the pressure beside the pipe");

        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA - 1);
        double below = net.gas().moles(CO2);
        a.step();
        a.step();
        h.assertTrue(a.get(WALL, 2, 2).is(AtmosBlocks.STATION_WALL_PIPE.get()), "the pipe burst below the threshold");
        a.close(net.gas().moles(CO2), below, "the pipe's CO2 just below the threshold");

        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA + 1);
        double all = net.gas().moles(CO2);
        h.assertTrue(all > 100, "the pipe holds too little to tell: " + all + " mol");
        a.step();
        a.tick();
        h.assertTrue(a.get(WALL, 2, 2) == wall(), "what the burst left is " + a.get(WALL, 2, 2));
        h.assertTrue(a.level.getBlockEntity(pos) == null, "the wall kept the pipe's block entity");
        same(a, 1, west, all, "the burst");
        same(a, 7, east, 0, "the burst");

        a.clear(-1, -1, 9, 5);
        h.succeed();
    }

    /**
     * A run through the wall spans two rooms at different pressures, which a bare run can't: a bare
     * pipe leaves the gap it passes through open. Between 100 atm over the thinner room and 100 atm
     * over the fuller, only the segments in the thinner room are over the limit (CONT-108): one of
     * them bursts and that room gets the whole network's gas. The pipe in the wall is measured against
     * the fuller room here, so it stays, and so do the wall, the run in the fuller room and that room.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_run_through_a_wall_bursts_in_the_room_it_is_further_over(GameTestHelper h) {
        Area a = new Area(h, 4878);
        rooms(a, 3000);
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        double fuller = a.zone(1, 3, 1).mixture().pressure();
        double thinner = a.zone(7, 3, 1).mixture().pressure();
        h.assertTrue(fuller - thinner > 20, "the rooms are too alike to tell: " + fuller + " and " + thinner + " kPa");
        a.place(WALL, 2, 2, inWall());
        for (int x : new int[] {3, 2, 5, 6}) {
            a.place(x, 2, 2, GasDeviceTests.pipe());
        }
        a.place(1, 2, 2, cap(Direction.EAST));
        a.place(7, 2, 2, cap(Direction.WEST));
        PipeNetwork net = a.<PipeBlockEntity>entity(WALL, 2, 2).network();
        a.close(net.volume(), 7 * PipeVolumes.PIPE, "the run's volume: five pipes and two caps");

        PipeBurstTests.fillTo(net, (fuller + thinner) / 2 + PipeBurst.THRESHOLD_KPA);
        double all = net.gas().moles(CO2);
        a.step();
        a.tick();
        h.assertTrue(a.get(WALL, 2, 2).is(AtmosBlocks.STATION_WALL_PIPE.get()), "the pipe in the wall burst");
        int left = 0;
        for (int x = 1; x <= 7; x++) {
            boolean there = a.level.getBlockEntity(a.abs(x, 2, 2)) instanceof PipeNodeEntity;
            h.assertTrue(there || x > WALL, "the segment at x " + x + ", in the fuller room, burst");
            left += there ? 1 : 0;
        }
        h.assertValueEqual(left, 6, "the segments left of seven");
        same(a, 1, west, 0, "the burst");
        a.close(a.zone(7, 3, 1).mixture().moles(CO2), all, "the thinner room's CO2");
        a.close(GasCensus.total(a, CO2, new ChunkPos(a.abs(WALL, 2, 2))), all, "the CO2 in all");

        a.clear(-1, -1, 9, 5);
        h.succeed();
    }

    // ---- Either way round, through the game's own clicks (#487, #486) -------------------------------

    /**
     * A pipe and its casing are joined in either order, and sneaking joins nothing. Each click here goes
     * through the game's own path, which asks the block first unless the player sneaks: a station wall
     * or a floor grille on a pipe, and a pipe on a station wall or a floor grille. A plain click makes
     * the one block of the two, the same whichever was there first, and uses one item up. A sneaking
     * click places what is in hand against the block clicked, as against any other, and leaves that
     * block as it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void either_way_round_a_plain_click_joins_a_pipe_and_its_casing_and_a_sneaking_click_places_against(
            GameTestHelper h) {
        Area a = new Area(h, 4868);
        a.clear(0, 0, 8, 4);
        // Four pipes at z 1 and four casings at z 3, none beside another. Each is clicked on its top.
        for (int x = 1; x <= 7; x += 2) {
            a.place(x, 2, 1, GasDeviceTests.pipe());
            a.set(x, 2, 3, x < 4 ? wall() : grille());
        }
        BlockState pipe = a.get(1, 2, 1);
        h.assertValueEqual(PipeBlock.connections(pipe), 0, "the arms of a pipe with nothing beside it");
        ServerPlayer player = visitor(a, ItemStack.EMPTY);
        record Casing(int x, ItemStack item, BlockState block, BlockState piped) {}
        List<Casing> casings = List.of(
                new Casing(1, new ItemStack(AtmosItems.STATION_WALL.get(), 2), wall(), inWall()),
                new Casing(5, new ItemStack(AtmosItems.FLOOR_GRILLE.get(), 2), grille(), inGrille()));
        for (Casing c : casings) {
            String name = c.block().getBlock().getDescriptionId();

            // The casing on a pipe: it goes round it, and sneaking it is placed on top of it.
            player.setItemInHand(InteractionHand.MAIN_HAND, c.item());
            h.assertTrue(
                    rightClick(a, player, c.x(), 2, 1, Direction.UP, false).consumesAction(),
                    name + " on a pipe did nothing");
            h.assertTrue(a.get(c.x(), 2, 1) == c.piped(), name + " on a pipe made " + a.get(c.x(), 2, 1));
            h.assertTrue(a.get(c.x(), 3, 1).isAir(), name + " on a pipe was placed as well");
            h.assertValueEqual(player.getMainHandItem().getCount(), 1, name + ": what is left in hand");
            h.assertTrue(
                    rightClick(a, player, c.x() + 2, 2, 1, Direction.UP, true).consumesAction(),
                    name + " on a pipe, sneaking, did nothing");
            h.assertTrue(
                    a.get(c.x() + 2, 2, 1) == pipe, name + ", sneaking, changed the pipe: " + a.get(c.x() + 2, 2, 1));
            h.assertTrue(
                    a.get(c.x() + 2, 3, 1) == c.block(),
                    name + ", sneaking, wasn't placed against the pipe: " + a.get(c.x() + 2, 3, 1));
            h.assertTrue(player.getMainHandItem().isEmpty(), name + ": what is left in hand, both used");

            // A pipe on the casing: it goes into it, and sneaking it is placed on top of it.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.PIPE.get(), 2));
            h.assertTrue(
                    rightClick(a, player, c.x(), 2, 3, Direction.UP, false).consumesAction(),
                    "a pipe on " + name + " did nothing");
            h.assertTrue(a.get(c.x(), 2, 3) == c.piped(), "a pipe on " + name + " made " + a.get(c.x(), 2, 3));
            h.assertTrue(a.get(c.x(), 3, 3).isAir(), "a pipe on " + name + " was placed as well");
            h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the pipes left in hand");
            h.assertTrue(
                    rightClick(a, player, c.x() + 2, 2, 3, Direction.UP, true).consumesAction(),
                    "a pipe on " + name + ", sneaking, did nothing");
            h.assertTrue(
                    a.get(c.x() + 2, 2, 3) == c.block(),
                    "a pipe, sneaking, changed " + name + ": " + a.get(c.x() + 2, 2, 3));
            h.assertTrue(
                    a.get(c.x() + 2, 3, 3).is(AtmosBlocks.PIPE.get()),
                    "a pipe, sneaking, wasn't placed against " + name + ": " + a.get(c.x() + 2, 3, 3));
            h.assertTrue(player.getMainHandItem().isEmpty(), "the pipes left in hand, both used");

            // Whichever was there first, it is the one block.
            h.assertTrue(a.get(c.x(), 2, 1) == a.get(c.x(), 2, 3), name + ": the two orders made two blocks");
        }

        a.clear(0, 0, 8, 4);
        h.succeed();
    }

    // ---- The floor grille (#486) ------------------------------------------------------------------

    /**
     * A floor grille is a full block for the air and for whoever walks on it, with a pipe in it or
     * without: gas-tight on every face with no cell, and a whole cube to stand on. The two have one
     * geometry, so a pipe going in or coming out changes nothing for the zones. It has no heat data of
     * its own, so it passes heat as any plain block does, where a station block passes none. It is seen
     * through and lets light through, as glass does, and shows no face against its own kind. It is as
     * hard as a pipe and mined with any pickaxe. The grille has an item, which places it. With a pipe
     * in it, it is the pipe's block, never leaks and has no item: picking it gives the grille.
     */
    @GameTest(template = "empty")
    public static void a_floor_grille_is_a_full_block_that_is_seen_through_with_a_pipe_in_it_or_without(
            GameTestHelper h) {
        FloorGrilleBlock grille = AtmosBlocks.FLOOR_GRILLE.get();
        EncasedPipeBlock piped = AtmosBlocks.FLOOR_GRILLE_PIPE.get();
        BlockItem item = AtmosItems.FLOOR_GRILLE.get();
        h.assertTrue(item.getBlock() == grille, "the floor grille's item places " + item.getBlock());
        h.assertTrue(piped.shell() == grille, "the grille of a pipe under one is " + piped.shell());
        h.assertTrue(EncasedPipeBlock.in(grille) == piped, "what a pipe makes of a floor grille");
        h.assertTrue(EncasedPipeBlock.of(item) == piped, "what a floor grille makes of a pipe");
        h.assertTrue(piped.asItem() == Items.AIR, "the grille with a pipe in it has an item");
        ServerLevel level = h.getLevel();
        BlockState empty = grille.defaultBlockState();
        BlockState pipe = AtmosBlocks.PIPE.get().defaultBlockState();
        List<BlockState> states = new ArrayList<>(piped.getStateDefinition().getPossibleStates());
        states.add(empty);
        for (BlockState state : states) {
            Geometry g = Geometries.table().geometry(state);
            h.assertTrue(g == Geometries.table().geometry(empty), state + " hasn't the empty grille's geometry");
            for (Face f : Face.values()) {
                h.assertTrue(g.isFaceFull(f), state + " doesn't seal its " + f + " face");
            }
            h.assertValueEqual(g.cellCount(), 0, "the cells of " + state);
            h.assertTrue(Double.isNaN(g.transmittance()), state + " has a U of its own: " + g.transmittance());
            h.assertTrue(
                    state.getCollisionShape(level, BlockPos.ZERO) == Shapes.block(),
                    state + " isn't a cube to stand on");
            h.assertFalse(state.canOcclude(), state + " hides what is behind it");
            h.assertTrue(state.propagatesSkylightDown(level, BlockPos.ZERO), state + " stops the sky's light");
            h.assertValueEqual(state.getLightBlock(level, BlockPos.ZERO), 0, "the light " + state + " takes");
            h.assertValueEqual(state.getShadeBrightness(level, BlockPos.ZERO), 1.0f, "the shade " + state + " casts");
            h.assertTrue(
                    state.getVisualShape(level, BlockPos.ZERO, CollisionContext.empty())
                            .isEmpty(),
                    state + " stops a line of sight");
            h.assertFalse(state.isRedstoneConductor(level, BlockPos.ZERO), state + " conducts redstone");
            h.assertFalse(
                    state.isValidSpawn(level, BlockPos.ZERO, EntityType.ZOMBIE), state + " lets a zombie spawn on it");
            h.assertTrue(state.skipRendering(empty, Direction.EAST), state + " shows its face beside a grille");
            h.assertTrue(
                    state.skipRendering(piped.defaultBlockState(), Direction.EAST),
                    state + " shows its face beside a grille with a pipe");
            h.assertFalse(state.skipRendering(wall(), Direction.EAST), state + " hides its face beside a wall");
            if (state.getBlock() == piped) {
                h.assertFalse(piped.leaks(state), state + " has an open end");
                h.assertTrue(AtmosBlockEntities.PIPE.get().isValid(state), state + " can't hold a pipe's block entity");
            }
        }
        for (BlockState state : List.of(empty, piped.defaultBlockState())) {
            String name = state.getBlock().getDescriptionId();
            h.assertValueEqual(
                    state.getDestroySpeed(level, BlockPos.ZERO),
                    pipe.getDestroySpeed(level, BlockPos.ZERO),
                    name + "'s hardness");
            h.assertValueEqual(
                    state.getBlock().getExplosionResistance(),
                    AtmosBlocks.PIPE.get().getExplosionResistance(),
                    name + "'s blast resistance");
            h.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), "a pickaxe doesn't mine " + name);
            h.assertTrue(
                    new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state),
                    "a wooden pickaxe won't do for " + name);
            h.assertFalse(new ItemStack(Items.WOODEN_AXE).isCorrectToolForDrops(state), "an axe will do for " + name);
            h.assertTrue(state.getSoundType() == SoundType.METAL, name + "'s sound");
            h.assertTrue(
                    state.getBlock()
                            .getCloneItemStack(level, BlockPos.ZERO, state)
                            .is(item),
                    "picking " + name + " doesn't give the grille");
        }
        h.assertFalse(inWall().skipRendering(inWall(), Direction.EAST), "a wall with a pipe hides nothing of its kind");
        h.succeed();
    }

    /** Four iron bars make four floor grilles. */
    @GameTest(template = "empty")
    public static void floor_grilles_are_crafted_from_iron_bars(GameTestHelper h) {
        RecipeHolder<?> recipe = h.getLevel()
                .getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(ZasAtmosMod.MOD_ID, "floor_grille"))
                .orElse(null);
        h.assertTrue(recipe != null, "there is no floor grille recipe");
        ItemStack result = recipe.value().getResultItem(h.getLevel().registryAccess());
        h.assertTrue(
                ItemStack.matches(result, new ItemStack(AtmosItems.FLOOR_GRILLE.get(), 4)),
                "the recipe makes " + result);
        List<Ingredient> ingredients = recipe.value().getIngredients().stream()
                .filter(i -> !i.isEmpty())
                .toList();
        h.assertValueEqual(ingredients.size(), 4, "the recipe's ingredients");
        for (Ingredient ingredient : ingredients) {
            h.assertTrue(ingredient.test(new ItemStack(Items.IRON_BARS)), "an ingredient isn't iron bars");
            h.assertFalse(ingredient.test(new ItemStack(Items.IRON_INGOT)), "an ingredient takes an iron ingot");
        }
        h.succeed();
    }

    /**
     * A floor grille with no pipe in it seals as a wall does. Two rooms are open to each other by a gap
     * in the wall between them, and are one zone. A player places a grille in the gap: the rooms part
     * into two zones, and gas let into one stays out of the other. It has no block entity. Mined, it
     * drops itself, and the gas crosses again.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_floor_grille_with_no_pipe_in_it_closes_the_gap_between_two_rooms(GameTestHelper h) {
        Area a = new Area(h, 4866);
        a.box(0, 0, 8, 4);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                if (y != 2 || z != 2) {
                    a.set(WALL, y, z, Area.STONE);
                }
            }
        }
        a.fill(1, 3, 1, Map.of(N2, 10.0));
        a.tick();
        h.assertValueEqual(a.zone(1, 3, 1).id(), a.zone(7, 3, 1).id(), "the zone either side of the gap");
        BlockPos pos = a.abs(WALL, 2, 2);

        // Placed as a block is: against the top of the stone under the gap.
        ServerPlayer player = visitor(a, new ItemStack(AtmosItems.FLOOR_GRILLE.get(), 2));
        h.assertTrue(
                rightClick(a, player, WALL, 1, 2, Direction.UP, false).consumesAction(), "the grille wasn't placed");
        h.assertTrue(a.get(WALL, 2, 2) == grille(), "what stands in the gap is " + a.get(WALL, 2, 2));
        h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the grilles left in hand");
        h.assertTrue(a.level.getBlockEntity(pos) == null, "a grille with no pipe in it has a block entity");
        a.tick();
        a.tick();
        h.assertFalse(
                a.zone(1, 3, 1).id().equals(a.zone(7, 3, 1).id()), "the rooms are one zone still, a grille in the gap");
        double east = a.zone(7, 3, 1).mixture().moles(N2);
        a.fill(1, 3, 1, Map.of(N2, 500.0));
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(a.zone(7, 3, 1).mixture().moles(N2), east, "the east room's N2, the gap shut");
        double apart =
                a.zone(1, 3, 1).mixture().pressure() - a.zone(7, 3, 1).mixture().pressure();
        h.assertTrue(apart > 20, "the rooms are only " + apart + " kPa apart");

        List<ItemStack> drops =
                Block.getDrops(grille(), a.level, pos, null, player, new ItemStack(Items.WOODEN_PICKAXE));
        h.assertTrue(
                drops.size() == 1 && ItemStack.matches(drops.get(0), new ItemStack(AtmosItems.FLOOR_GRILLE.get())),
                "the drops of a grille with no pipe in it: " + drops);
        a.level.destroyBlock(pos, false);
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        h.assertTrue(
                a.zone(7, 3, 1).mixture().moles(N2) > east + 1,
                "the grille mined, the east room got no gas: "
                        + a.zone(7, 3, 1).mixture().moles(N2) + " mol of N2");

        a.clear(-1, -1, 9, 5);
        h.succeed();
    }

    /**
     * The grille first: a pipe used on a floor grille goes into it with its dye, and one is used up. It
     * is the block a grille used on that pipe would have made. The grille is never marked for the
     * zones, and the rooms either side, at different pressures, stay the zones they were with the gas
     * they had. Sneaking, the pipe is placed against the grille, which stays as it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_used_on_a_floor_grille_goes_into_it_and_the_rooms_stay_apart(GameTestHelper h) {
        Area a = new Area(h, 4867);
        rooms(a, 400);
        a.set(WALL, 1, 1, grille());
        a.set(WALL, 3, 3, grille());
        a.tick();
        a.tick();
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        double apart =
                a.zone(1, 3, 1).mixture().pressure() - a.zone(7, 3, 1).mixture().pressure();
        h.assertTrue(apart > 20, "the rooms are only " + apart + " kPa apart, so the test proves little");
        Player player = holding(h, GameType.SURVIVAL, PipeItem.of(AtmosItems.PIPE.get(), PipeColor.RED, 2));

        BlockPos pos = a.abs(WALL, 1, 1);
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "the grille is still marked from being built");
        h.assertTrue(use(a, player, WALL, 1, 1, Direction.WEST, false).consumesAction(), "the pipe didn't go in");
        BlockState piped = a.get(WALL, 1, 1);
        h.assertTrue(piped == inGrille(PipeColor.RED), "the grille is now " + piped);
        h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the pipes left in hand");
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "a pipe going into the grille marked it for the zones");
        h.assertFalse(pipe(a, WALL, 1, 1).isOpen(), "the pipe in the grille has an open end");
        for (int i = 0; i < 6; i++) {
            a.step();
            same(a, 1, west, 0, "step " + i);
            same(a, 7, east, 0, "step " + i);
        }

        // Sneaking, it goes against the grille, which stays a grille with no pipe in it.
        h.assertTrue(use(a, player, WALL, 3, 3, Direction.WEST, true).consumesAction(), "the sneaking use");
        h.assertTrue(a.get(WALL, 3, 3) == grille(), "sneaking, the pipe went into the grille");
        BlockState against = a.get(WALL - 1, 3, 3);
        h.assertTrue(against.is(AtmosBlocks.PIPE.get()), "sneaking, no pipe was placed against the grille: " + against);
        h.assertValueEqual(against.getValue(PipeColor.PROPERTY), PipeColor.RED, "the dye of the pipe placed");
        h.assertTrue(player.getMainHandItem().isEmpty(), "the pipes left in hand");

        a.clear(-1, -1, 9, 5);
        player.discard();
        h.succeed();
    }

    /**
     * The pipe first, and the issue's case. A run passes through a gap in the wall between two rooms,
     * and the bare pipe leaves the gap open: the rooms are one zone. A floor grille used on the pipe in
     * the gap goes round it, and one grille is used up. The rooms part into two zones, and gas let into
     * one stays out of the other. The pipe keeps its dye, its arms, its block entity and its network
     * with its gas. A second grille does nothing, and a grille goes round no end cap and no pipe in a
     * wall. Sneaking, the grille is placed against the pipe, a block of its own, and the pipe stays bare.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_floor_grille_used_on_a_pipe_goes_round_it_and_closes_the_gap(GameTestHelper h) {
        Area a = new Area(h, 4860);
        a.box(0, 0, 8, 4);
        for (int y = 1; y <= 3; y++) {
            for (int z = 1; z <= 3; z++) {
                if (y != 2 || z != 2) {
                    a.set(WALL, y, z, Area.STONE);
                }
            }
        }
        a.place(1, 2, 2, cap(Direction.EAST));
        for (int x = 2; x <= 6; x++) {
            a.place(
                    x,
                    2,
                    2,
                    GasDeviceTests.pipe().setValue(PipeColor.PROPERTY, x == WALL ? PipeColor.ORANGE : PipeColor.NONE));
        }
        a.place(7, 2, 2, cap(Direction.WEST));
        a.place(WALL, 1, 1, inWall());
        a.fill(1, 3, 1, Map.of(N2, 10.0));
        a.tick();
        h.assertValueEqual(a.zone(1, 3, 1).id(), a.zone(7, 3, 1).id(), "the zone either side of a bare pipe in a gap");
        PipeBlockEntity before = a.entity(WALL, 2, 2);
        PipeNetwork net = before.network();
        a.close(net.volume(), 7 * PipeVolumes.PIPE, "the run's volume: five pipes and two caps");
        net.gas().add(new GasParcel(Map.of(CO2, 20.0), T));
        BlockState bare = a.get(WALL, 2, 2);
        Player player = holding(h, GameType.SURVIVAL, new ItemStack(AtmosItems.FLOOR_GRILLE.get(), 3));

        h.assertTrue(useOnBlock(a, player, WALL, 2, 2, Direction.UP).consumesAction(), "the grille didn't go on");
        BlockState cased = a.get(WALL, 2, 2);
        h.assertTrue(cased.is(AtmosBlocks.FLOOR_GRILLE_PIPE.get()), "the pipe is now " + cased);
        h.assertValueEqual(
                cased.getValue(PipeColor.PROPERTY), PipeColor.ORANGE, "the dye of the pipe under the grille");
        for (Direction d : Direction.values()) {
            h.assertValueEqual(
                    cased.getValue(PipeBlock.CONNECTIONS.get(d)),
                    bare.getValue(PipeBlock.CONNECTIONS.get(d)),
                    "the arm on the " + d + " face");
        }
        h.assertTrue(a.level.getBlockEntity(a.abs(WALL, 2, 2)) == before, "the pipe got a new block entity");
        h.assertFalse(before.isRemoved(), "the pipe's block entity was removed");
        h.assertTrue(before.network() == net && net.valid(), "the pipe's network was rebuilt");
        a.close(net.gas().moles(CO2), 20, "the network's CO2");
        h.assertValueEqual(player.getMainHandItem().getCount(), 2, "the grilles left in hand");

        // The gap is shut: two zones, and what goes into one stays out of the other.
        a.tick();
        a.tick();
        h.assertFalse(
                a.zone(1, 3, 1).id().equals(a.zone(7, 3, 1).id()),
                "the rooms are one zone still, the gap under a grille");
        double east = a.zone(7, 3, 1).mixture().moles(N2);
        a.fill(1, 3, 1, Map.of(N2, 500.0));
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(a.zone(7, 3, 1).mixture().moles(N2), east, "the east room's N2");
        double apart =
                a.zone(1, 3, 1).mixture().pressure() - a.zone(7, 3, 1).mixture().pressure();
        h.assertTrue(apart > 20, "the rooms are only " + apart + " kPa apart");
        a.close(before.network().gas().moles(CO2), 20, "the run's CO2 after its steps");
        a.close(a.zone(1, 3, 1).mixture().moles(CO2), 0, "the west room's CO2");
        a.close(a.zone(7, 3, 1).mixture().moles(CO2), 0, "the east room's CO2");

        // A pipe takes one casing, and only a bare pipe takes a grille.
        for (int[] at : new int[][] {{WALL, 2, 2}, {1, 2, 2}, {WALL, 1, 1}}) {
            BlockState was = a.get(at[0], at[1], at[2]);
            h.assertValueEqual(
                    useOnBlock(a, player, at[0], at[1], at[2], Direction.UP),
                    ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION,
                    "a grille on " + was);
            h.assertTrue(a.get(at[0], at[1], at[2]) == was, "a grille changed " + was);
        }
        h.assertValueEqual(player.getMainHandItem().getCount(), 2, "the grilles left in hand");

        // Sneaking, the grille is placed against the pipe, and the pipe stays as it was.
        BlockState run = a.get(6, 2, 2);
        h.assertTrue(use(a, player, 6, 2, 2, Direction.UP, true).consumesAction(), "the sneaking use");
        h.assertTrue(a.get(6, 2, 2) == run, "sneaking, the grille went round the pipe: " + a.get(6, 2, 2));
        h.assertTrue(a.get(6, 3, 2) == grille(), "sneaking, no grille was placed against the pipe: " + a.get(6, 3, 2));
        h.assertValueEqual(player.getMainHandItem().getCount(), 1, "the grilles left in hand");

        a.clear(-1, -1, 9, 5);
        player.discard();
        h.succeed();
    }

    /**
     * A pipe under a grille has no open end, where a bare pipe with fewer than two connections has one
     * (CONT-004): alone, and then with one end cap on it, it keeps its gas through its steps.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_under_a_floor_grille_has_no_open_end(GameTestHelper h) {
        Area a = new Area(h, 4861);
        a.box(0, 0, 6, 4);
        a.fill(1, 3, 1, Map.of(N2, 10.0));
        a.place(3, 2, 2, inGrille());
        a.tick();
        PipeBlockEntity pipe = a.entity(3, 2, 2);
        pipe.network().gas().add(new GasParcel(Map.of(CO2, 30.0), T));
        h.assertTrue(pipe.network().gas().pressure() > 1000, "the pipe isn't above the room's pressure");
        h.assertFalse(pipe.isOpen(), "alone under its grille, the pipe has an open end");
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(pipe.network().gas().moles(CO2), 30, "the CO2 of a pipe alone under its grille");
        a.place(2, 2, 2, cap(Direction.EAST));
        h.assertValueEqual(PipeBlock.connections(a.get(3, 2, 2)), 1, "the arms of the pipe under the grille");
        h.assertFalse(pipe.isOpen(), "with one connection, the pipe under the grille has an open end");
        for (int i = 0; i < 6; i++) {
            a.step();
        }
        a.close(pipe.network().gas().moles(CO2), 30, "the CO2 of the run that ends under the grille");
        a.close(a.zone(1, 3, 1).mixture().moles(CO2), 0, "the room's CO2");

        a.clear(-1, -1, 7, 5);
        h.succeed();
    }

    /**
     * A wrench does as it does in a wall. It has nothing to do on a grille with no pipe in it, and
     * nothing to turn on a pipe under one. Sneaking, it takes the pipe out of the face clicked and
     * leaves the grille, never marked for the zones: the pipe drops in its dye, and its gas goes into
     * the room on that side and none into the other. It refuses while the pipe is more than 2 atm over
     * the room on that side (CONT-091), so a pipe that won't come out into the thin room comes out into
     * the dense one. In creative nothing drops.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_wrench_takes_the_pipe_out_of_a_floor_grille_and_leaves_the_grille(GameTestHelper h) {
        Area a = new Area(h, 4862);
        rooms(a, 3000);
        a.set(WALL, 2, 2, grille());
        a.tick();
        a.tick();
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        BlockPos pos = a.abs(WALL, 2, 2);
        Player player = holding(h, GameType.SURVIVAL, new ItemStack(AtmosItems.WRENCH.get()));

        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "the grille is still marked from being built");
        h.assertValueEqual(
                wrench(a, player, WALL, 2, 2, Direction.WEST, true),
                InteractionResult.PASS,
                "a sneaking click on a grille with no pipe in it");
        h.assertTrue(a.get(WALL, 2, 2) == grille(), "the wrench changed a grille with no pipe in it");

        a.place(WALL, 2, 2, inGrille(PipeColor.GREEN));
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "a pipe put into the grille marked it for the zones");
        a.<PipeBlockEntity>entity(WALL, 2, 2).network().gas().add(new GasParcel(Map.of(CO2, 2.0), T));
        BlockState piped = a.get(WALL, 2, 2);
        h.assertValueEqual(
                wrench(a, player, WALL, 2, 2, Direction.WEST, false), InteractionResult.FAIL, "a plain click");
        h.assertTrue(a.get(WALL, 2, 2) == piped, "a plain click changed the block");
        WrenchTests.watchDrops();
        h.assertTrue(wrench(a, player, WALL, 2, 2, Direction.WEST, true).consumesAction(), "the pipe's removal");
        h.assertTrue(a.get(WALL, 2, 2) == grille(), "what is left is " + a.get(WALL, 2, 2));
        h.assertTrue(a.level.getBlockEntity(pos) == null, "the grille kept the pipe's block entity");
        h.assertFalse(ChangeTracker.isDirty(a.level, pos), "taking the pipe out marked the grille for the zones");
        List<ItemEntity> drops = WrenchTests.takeDrops();
        h.assertValueEqual(drops.size(), 1, "the items dropped: " + drops);
        h.assertTrue(
                drops.get(0).getItem().is(AtmosItems.PIPE.get()),
                "what dropped is " + drops.get(0).getItem());
        h.assertValueEqual(PipeItem.color(drops.get(0).getItem()), PipeColor.GREEN, "the dropped pipe's dye");
        h.assertTrue(
                drops.get(0).getX() < pos.getX(),
                "the pipe dropped at x " + drops.get(0).getX() + ", not on the west side");
        a.tick();
        same(a, 1, west, 2, "the pipe out of the west face");
        same(a, 7, east, 0, "the pipe out of the west face");

        // Between the two rooms' limits, it won't come out of the east face and will out of the west.
        a.place(WALL, 2, 2, inGrille());
        PipeNetwork net = a.<PipeBlockEntity>entity(WALL, 2, 2).network();
        double thin = a.zone(7, 3, 1).mixture().pressure();
        double dense = a.zone(1, 3, 1).mixture().pressure();
        double kpa = thin + WrenchItem.LOCK_KPA + 40;
        h.assertTrue(kpa < dense + WrenchItem.LOCK_KPA - 40, "the rooms are too close: " + thin + " and " + dense);
        PipeBurstTests.fillTo(net, kpa);
        double held = net.gas().moles(CO2);
        a.close(
                WrenchItem.overAmbient(a.level, pos, Direction.EAST),
                WrenchItem.LOCK_KPA + 40,
                "the pipe's pressure over the east room's");
        wrench(a, player, WALL, 2, 2, Direction.EAST, true);
        h.assertTrue(a.get(WALL, 2, 2).is(AtmosBlocks.FLOOR_GRILLE_PIPE.get()), "the pipe came out over the limit");
        Optional<Component> refusal = WrenchItem.remove(a.level, pos, player, player.getMainHandItem(), Direction.EAST);
        h.assertTrue(
                refusal.isPresent()
                        && refusal.get().getContents() instanceof TranslatableContents t
                        && t.getKey().equals("item.zas_atmos.wrench.pressurized"),
                "the refusal: " + refusal);
        a.close(a.<PipeBlockEntity>entity(WALL, 2, 2).network().gas().moles(CO2), held, "what the pipe holds, refused");
        WrenchTests.watchDrops();
        wrench(a, player, WALL, 2, 2, Direction.WEST, true);
        h.assertTrue(a.get(WALL, 2, 2) == grille(), "the pipe didn't come out of the west face");
        drops = WrenchTests.takeDrops();
        h.assertTrue(
                drops.size() == 1 && ItemStack.matches(drops.get(0).getItem(), new ItemStack(AtmosItems.PIPE.get())),
                "an undyed pipe doesn't drop as a new one: " + drops);
        a.tick();
        same(a, 1, west, 2 + held, "the second pipe out of the west face");
        same(a, 7, east, 0, "the second pipe out of the west face");

        // In creative, the pipe comes out and nothing drops.
        a.place(WALL, 2, 2, inGrille());
        Player creative = holding(h, GameType.CREATIVE, new ItemStack(AtmosItems.WRENCH.get()));
        WrenchTests.watchDrops();
        wrench(a, creative, WALL, 2, 2, Direction.EAST, true);
        h.assertTrue(a.get(WALL, 2, 2) == grille(), "in creative, the pipe didn't come out");
        h.assertTrue(WrenchTests.takeDrops().isEmpty(), "in creative, something dropped");

        a.clear(-1, -1, 9, 5);
        player.discard();
        creative.discard();
        h.succeed();
    }

    /**
     * Mined, a pipe under a grille drops the grille and the pipe, the pipe in its dye and an undyed one
     * as a new pipe, and its gas is released as a mined pipe's is (CONT-009).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void mined_a_floor_grille_drops_with_the_pipe_in_it_and_releases_its_gas(GameTestHelper h) {
        Area a = new Area(h, 4863);
        a.box(0, 0, 6, 4);
        a.fill(1, 3, 1, Map.of(N2, 10.0));
        a.tick();
        BlockPos pos = a.abs(3, 2, 2);
        Player player = holding(h, GameType.SURVIVAL, new ItemStack(Items.WOODEN_PICKAXE));

        List<ItemStack> drops = Block.getDrops(inGrille(), a.level, pos, null, player, player.getMainHandItem());
        h.assertValueEqual(drops.size(), 2, "the drops of a pipe under a grille: " + drops);
        h.assertTrue(
                drops.stream().anyMatch(s -> ItemStack.matches(s, new ItemStack(AtmosItems.FLOOR_GRILLE.get()))),
                "no floor grille among " + drops);
        h.assertTrue(
                drops.stream().anyMatch(s -> ItemStack.matches(s, new ItemStack(AtmosItems.PIPE.get()))),
                "no new pipe among " + drops);

        BlockState dyed = inGrille(PipeColor.LIME);
        a.place(3, 2, 2, dyed);
        PipeBlockEntity pipe = a.entity(3, 2, 2);
        pipe.network().gas().add(new GasParcel(Map.of(CO2, 4.0), T));
        drops = Block.getDrops(dyed, a.level, pos, pipe, player, player.getMainHandItem());
        h.assertValueEqual(drops.size(), 2, "the drops of a dyed pipe under a grille: " + drops);
        h.assertTrue(
                drops.stream()
                        .anyMatch(s -> s.is(AtmosItems.PIPE.get())
                                && s.getCount() == 1
                                && PipeItem.color(s) == PipeColor.LIME),
                "no lime pipe among " + drops);

        a.level.destroyBlock(pos, false);
        h.assertTrue(a.get(3, 2, 2).isAir(), "the block is still there");
        a.tick();
        a.tick();
        a.close(GasCensus.total(a, CO2, new ChunkPos(pos)), 4, "the CO2 in the room, the grille mined");

        a.clear(-1, -1, 7, 5);
        player.discard();
        h.succeed();
    }

    /**
     * A pipe under a grille bursts as a pipe in a wall does (CONT-108), measured against the room its
     * gas would reach. Just below 100 atm over that room nothing happens. Just above, the pipe goes and
     * the grille stays, with no pipe in it: the room on that side gets everything, the other none, and
     * they are the zones they were.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_pipe_that_bursts_under_a_floor_grille_leaves_the_grille(GameTestHelper h) {
        Area a = new Area(h, 4864);
        rooms(a, 400);
        a.set(WALL, 2, 2, grille());
        a.tick();
        a.tick();
        Room west = Room.of(a, 1);
        Room east = Room.of(a, 7);
        BlockPos pos = a.abs(WALL, 2, 2);
        a.place(WALL, 2, 2, inGrille());
        PipeNetwork net = a.<PipeBlockEntity>entity(WALL, 2, 2).network();
        // The grille's middle is as near one room as the other, and the west comes first (API-015).
        double room = Atmospheres.at(a.level, Vec3.atCenterOf(pos)).pressure();
        a.close(room, a.zone(1, 3, 1).mixture().pressure(), "the pressure beside the pipe");

        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA - 1);
        double below = net.gas().moles(CO2);
        a.step();
        a.step();
        h.assertTrue(a.get(WALL, 2, 2).is(AtmosBlocks.FLOOR_GRILLE_PIPE.get()), "the pipe burst below the threshold");
        a.close(net.gas().moles(CO2), below, "the pipe's CO2 just below the threshold");

        PipeBurstTests.fillTo(net, room + PipeBurst.THRESHOLD_KPA + 1);
        double all = net.gas().moles(CO2);
        h.assertTrue(all > 100, "the pipe holds too little to tell: " + all + " mol");
        WrenchTests.watchDrops();
        a.step();
        a.tick();
        h.assertTrue(a.get(WALL, 2, 2) == grille(), "what the burst left is " + a.get(WALL, 2, 2));
        h.assertTrue(a.level.getBlockEntity(pos) == null, "the grille kept the pipe's block entity");
        h.assertTrue(WrenchTests.takeDrops().isEmpty(), "the burst dropped something");
        same(a, 1, west, all, "the burst");
        same(a, 7, east, 0, "the burst");

        a.clear(-1, -1, 9, 5);
        h.succeed();
    }

    /**
     * What a restart keeps of a floor grille. With a pipe in it: its state, with the pipe's dye and its
     * arms, and the pipe's block entity with its share of the gas. With none: its state, and nothing
     * else, for it has no block entity.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void a_floor_grille_is_saved_with_the_pipe_in_it_or_with_none(GameTestHelper h) {
        Area a = new Area(h, 4865);
        a.clear(0, 0, 6, 4);
        a.place(2, 2, 2, cap(Direction.EAST));
        a.place(3, 2, 2, inGrille(PipeColor.PURPLE));
        a.place(4, 2, 2, cap(Direction.WEST));
        BlockPos pos = a.abs(3, 2, 2);
        BlockState state = a.get(3, 2, 2);
        h.assertValueEqual(PipeBlock.connections(state), 2, "the arms of the pipe under the grille");
        PipeBlockEntity pipe = a.entity(3, 2, 2);
        pipe.network().gas().add(new GasParcel(Map.of(CO2, 21.0), T));
        double share = 21.0 * PipeVolumes.PIPE / pipe.network().volume();

        BlockState read = NbtUtils.readBlockState(
                a.level.holderLookup(Registries.BLOCK), throughDisk(NbtUtils.writeBlockState(state)));
        h.assertTrue(read == state, "the state read back is " + read + ", not " + state);
        CompoundTag saved = throughDisk(pipe.saveWithFullMetadata(a.level.registryAccess()));
        h.assertValueEqual(saved.getString("id"), "zas_atmos:pipe", "the block entity's id");
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, saved, a.level.registryAccess());
        h.assertTrue(loaded instanceof PipeBlockEntity, "what loads is " + loaded);
        a.close(((PipeBlockEntity) loaded).share().moles(CO2), share, "the share that loads");
        h.assertFalse(((PipeBlockEntity) loaded).isOpen(), "the pipe that loads has an open end");

        a.set(3, 4, 2, grille());
        BlockState empty = a.get(3, 4, 2);
        h.assertTrue(a.level.getBlockEntity(a.abs(3, 4, 2)) == null, "a grille with no pipe in it has a block entity");
        CompoundTag written = throughDisk(NbtUtils.writeBlockState(empty));
        h.assertValueEqual(written.getString("Name"), "zas_atmos:floor_grille", "the name the grille is saved by");
        read = NbtUtils.readBlockState(a.level.holderLookup(Registries.BLOCK), written);
        h.assertTrue(read == empty, "the grille read back is " + read + ", not " + empty);

        a.clear(0, 0, 6, 4);
        h.succeed();
    }
}
