package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.HotspotTests.empty;
import static io.github.west3436.zas.gametest.HotspotTests.gasFire;
import static io.github.west3436.zas.gametest.HotspotTests.hydrogenInAir;
import static io.github.west3436.zas.gametest.HotspotTests.hydrogenRoom;
import static io.github.west3436.zas.gametest.HotspotTests.ignite;
import static io.github.west3436.zas.gametest.HotspotTests.room;
import static io.github.west3436.zas.gametest.HotspotTests.with;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.engine.scheduler.FireIntent;
import io.github.west3436.zas.hotspot.FireIntents;
import io.github.west3436.zas.hotspot.GasFireScan;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import io.github.west3436.zas.sound.FireLoopGroups;
import io.github.west3436.zas.sound.IgnitionWhoomps;
import io.github.west3436.zas.sound.ZasSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The sound of a gas fire (FIRE-036): what the server plays when gas catches, through the event a
 * server's sound fires, and what the client's loop would be asked for, by running its scan on a
 * server's level, which holds chunks of the same class. Each test has its own area, on a row of its
 * own at z = 13056.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireSoundTests {
    private record Whoomp(Vec3 at, float volume, SoundSource slider) {}

    private static final List<Whoomp> WHOOMPS = new ArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition e) -> {
            if (e.getSound() != null && e.getSound().value() == ZasSounds.FIRE_IGNITE.get()) {
                WHOOMPS.add(new Whoomp(e.getPosition(), e.getNewVolume(), e.getSource()));
            }
        });
    }

    private FireSoundTests() {}

    private static Rooms rooms(GameTestHelper h, int index) {
        return new Rooms(h, h.getLevel(), new BlockPos(12288 + 256 * index, 100, 13056));
    }

    /** Takes the whoomps played around a point: they aren't reported again. */
    private static List<Whoomp> whoomps(BlockPos near) {
        List<Whoomp> out = new ArrayList<>();
        for (Iterator<Whoomp> it = WHOOMPS.iterator(); it.hasNext(); ) {
            Whoomp w = it.next();
            if (w.at().distanceTo(Vec3.atCenterOf(near)) <= 64) {
                out.add(w);
                it.remove();
            }
        }
        return out;
    }

    private static Set<BlockPos> scan(Rooms r, BlockPos middle, double radius) {
        Set<BlockPos> found = new HashSet<>();
        GasFireScan.forEach(
                r.level,
                middle.getX() + 0.5,
                middle.getY() + 0.5,
                middle.getZ() + 0.5,
                radius,
                (x, y, z) -> found.add(new BlockPos(x, y, z)));
        return found;
    }

    /** What a listener at a point would ask for now, as {@code client.GasFireLoops} does. */
    private static List<FireLoopGroups.Group> loops(Rooms r, BlockPos ear) {
        FireLoopGroups groups = new FireLoopGroups();
        double x = ear.getX() + 0.5;
        double y = ear.getY() + 0.5;
        double z = ear.getZ() + 0.5;
        GasFireScan.forEach(r.level, x, y, z, FireLoopGroups.RANGE, groups::add);
        return groups.choose(x, y, z);
    }

    // ---- The whoomp --------------------------------------------------------------------------

    /**
     * One ignition plays one whoomp at its gas fire, on the Blocks slider, as loud as its fuel says, as
     * soon as the block is there. The fire then spreads through the room with no more.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_one_ignition_plays_one_whoomp_however_far_it_spreads(GameTestHelper h) {
        with(1, true, () -> {
            Rooms r = rooms(h, 0);
            ZoneInfo zone = hydrogenRoom(r, 0);
            double fuel = zone.mixture().moles(HotspotTests.H2);
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            h.assertTrue(gasFire(r, middle), "no gas fire");
            List<Whoomp> played = whoomps(middle);
            h.assertValueEqual(played.size(), 1, "whoomps from the ignition");
            h.assertValueEqual(played.get(0).at(), Vec3.atCenterOf(middle), "where it played");
            h.assertValueEqual(played.get(0).slider(), SoundSource.BLOCKS, "its slider");
            h.assertTrue(
                    Math.abs(played.get(0).volume() - IgnitionWhoomps.volume(fuel)) < 1e-3,
                    "its volume: " + played.get(0).volume() + " for " + fuel + " mol");
            r.ticks(4);
            h.assertTrue(scan(r, middle, 2).size() > 1, "the fire didn't spread");
            h.assertValueEqual(whoomps(middle).size(), 0, "whoomps as it spread");
            empty(r, 0);
            r.tick();
        });
        h.succeed();
    }

    /**
     * A fire relit in the same area stays quiet, and a room far enough away still plays its own. These
     * tests run within one game tick, so the quiet can't end here: {@code IgnitionWhoompsTest} checks that.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_a_fire_relit_nearby_stays_quiet(GameTestHelper h) {
        with(0, true, () -> {
            Rooms r = rooms(h, 1);
            hydrogenRoom(r, 0);
            BlockPos middle = r.abs(2, 2, 2);
            ignite(r, middle);
            h.assertValueEqual(whoomps(middle).size(), 1, "whoomps from the first lighting");
            // Until a tick has seen the block, taking it away would count as the world refusing it there.
            r.ticks(2);
            hydrogenRoom(r, 0);
            h.assertFalse(gasFire(r, middle), "the rebuilt room still burns");
            r.tick();
            ignite(r, middle);
            h.assertTrue(gasFire(r, middle), "it didn't relight");
            h.assertValueEqual(whoomps(middle).size(), 0, "whoomps from relighting");

            hydrogenRoom(r, 40);
            BlockPos elsewhere = r.abs(42, 2, 2);
            ignite(r, elsewhere);
            List<Whoomp> played = whoomps(elsewhere);
            h.assertValueEqual(played.size(), 1, "whoomps from a room 40 blocks away");
            h.assertValueEqual(played.get(0).at(), Vec3.atCenterOf(elsewhere), "where it played");
            empty(r, 0);
            empty(r, 40);
            r.tick();
        });
        h.succeed();
    }

    /** A vanilla fire in plain air makes a burning zone, but no gas caught, so it keeps vanilla's sound. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_a_vanilla_fire_plays_no_whoomp(GameTestHelper h) {
        with(1, true, () -> {
            Rooms r = rooms(h, 2);
            ZoneInfo zone = room(
                    r,
                    0,
                    3,
                    3,
                    3,
                    StandardAir.TEMPERATURE,
                    Map.of(StandardAir.NITROGEN, 0.79 * 101.325, StandardAir.OXYGEN, 0.21 * 101.325));
            BlockPos floor = r.abs(2, 1, 2);
            r.set(2, 1, 2, Blocks.FIRE.defaultBlockState());
            r.ticks(3);
            h.assertTrue(Fires.isBurning(r.level, zone.id()), "the zone isn't burning");
            h.assertValueEqual(whoomps(floor).size(), 0, "whoomps");
            h.assertValueEqual(scan(r, floor, 3).size(), 0, "gas fire blocks to play a loop for");
            r.set(2, 1, 2, Rooms.AIR);
            r.tick();
        });
        h.succeed();
    }

    /**
     * A vanilla fire in hydrogen plays nothing until the gas catches: then one whoomp, at the first gas
     * fire that spreads from it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_a_vanilla_fire_that_lights_gas_plays_one_whoomp(GameTestHelper h) {
        with(1, true, () -> {
            Rooms r = rooms(h, 3);
            hydrogenRoom(r, 0);
            BlockPos floor = r.abs(2, 1, 2);
            r.set(2, 1, 2, Blocks.FIRE.defaultBlockState());
            r.ticks(5);
            h.assertTrue(scan(r, floor, 3).size() > 0, "no gas fire spread from it");
            h.assertValueEqual(whoomps(floor).size(), 1, "whoomps");
            r.set(2, 1, 2, Rooms.AIR);
            empty(r, 0);
            r.tick();
        });
        h.succeed();
    }

    /** Only a block really placed counts: an intent the world refuses tells no one. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_a_refused_fire_plays_nothing(GameTestHelper h) {
        Rooms r = rooms(h, 4);
        r.clear(0, 8);
        r.set(1, 1, 1, Rooms.STONE);
        List<BlockPos> told = new ArrayList<>();
        FireIntents.Caught caught = (pos, fuel) -> {
            h.assertValueEqual(fuel, 5.0, "the fuel");
            told.add(pos);
        };
        long wall = r.abs(1, 1, 1).asLong();
        long open = r.abs(3, 1, 1).asLong();
        long spread = r.abs(5, 1, 1).asLong();
        FireIntents.apply(
                r.level,
                List.of(
                        new FireIntent(FireIntent.Kind.PLACE, wall, 1, true, 5),
                        new FireIntent(FireIntent.Kind.PLACE, open, 1, true, 5),
                        new FireIntent(FireIntent.Kind.PLACE, spread, 1)),
                c -> true,
                b -> false,
                caught);
        h.assertValueEqual(told, List.of(r.abs(3, 1, 1)), "who was told");
        r.clear(0, 8);
        h.succeed();
    }

    // ---- The loop ----------------------------------------------------------------------------

    /**
     * The scan finds gas fire blocks in the cube as they are now, over chunk and section borders: not
     * vanilla fire, nothing outside the cube, and nothing once they've gone.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_the_scan_finds_the_gas_fires_there_now(GameTestHelper h) {
        Rooms r = rooms(h, 5);
        r.clear(0, 40);
        BlockState fire = HotspotBlocks.GAS_FIRE.get().defaultBlockState();
        // The area's corner is on a chunk border, and y 100 is 4 blocks into its section.
        r.set(15, 11, 3, fire);
        r.set(16, 12, 3, fire);
        r.set(16, 1, 4, Blocks.FIRE.defaultBlockState());
        r.set(36, 12, 3, fire);
        BlockPos middle = r.abs(16, 6, 3);
        h.assertValueEqual(scan(r, middle, 8), Set.of(r.abs(15, 11, 3), r.abs(16, 12, 3)), "gas fires in the cube");
        h.assertValueEqual(scan(r, middle, 6), Set.of(r.abs(15, 11, 3), r.abs(16, 12, 3)), "its edge is included");
        h.assertValueEqual(scan(r, middle, 5.4), Set.of(r.abs(15, 11, 3)), "one block further is out");
        r.set(15, 11, 3, Rooms.AIR);
        r.set(16, 12, 3, Rooms.AIR);
        h.assertValueEqual(scan(r, middle, 8), Set.of(), "gas fires once they've gone");
        r.set(16, 1, 4, Rooms.AIR);
        r.set(36, 12, 3, Rooms.AIR);
        h.succeed();
    }

    /** A chunk nobody holds shows no fire, and looking doesn't bring it back: a chunk that left stays gone. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_the_scan_loads_no_chunk(GameTestHelper h) {
        Rooms r = rooms(h, 6);
        BlockPos far = new BlockPos(3_000_000, 100, 3_000_000);
        h.assertFalse(r.level.hasChunkAt(far), "the far chunk was loaded already");
        h.assertValueEqual(scan(r, far, FireLoopGroups.RANGE), Set.of(), "fires in a chunk nobody holds");
        h.assertFalse(r.level.hasChunkAt(far), "the scan loaded a chunk");
        h.succeed();
    }

    /** A real fire asks for a few loops while it burns, however big, and for none once it's out. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void fire_036_a_fire_asks_for_a_few_loops_until_it_goes_out(GameTestHelper h) {
        with(1, true, () -> {
            Rooms r = rooms(h, 7);
            room(r, 0, 30, 3, 30, StandardAir.TEMPERATURE, hydrogenInAir(0.10));
            BlockPos middle = r.abs(15, 2, 15);
            h.assertValueEqual(loops(r, middle).size(), 0, "loops before it lights");
            ignite(r, middle);
            r.ticks(8);
            List<FireLoopGroups.Group> burning = loops(r, middle);
            h.assertTrue(!burning.isEmpty(), "no loops while it burns");
            h.assertTrue(
                    burning.size() <= FireLoopGroups.MAX_SOURCES,
                    "a fire of " + scan(r, middle, FireLoopGroups.RANGE).size() + " blocks asked for " + burning.size()
                            + " loops");
            empty(r, 0);
            r.ticks(3);
            h.assertValueEqual(loops(r, middle).size(), 0, "loops once it's out");
        });
        h.succeed();
    }
}
