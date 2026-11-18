package io.github.west3436.zas.compat.sable.gametest;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import io.github.west3436.zas.airflow.Airflow;
import io.github.west3436.zas.airflow.AirflowEffectsPayload;
import io.github.west3436.zas.airflow.AirflowParticlePlan;
import io.github.west3436.zas.airflow.AirflowSoundPlan;
import io.github.west3436.zas.airflow.AirflowSyncPayload;
import io.github.west3436.zas.airflow.VisibleFlow;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.relocation.Relocations;
import io.github.west3436.zas.atmos.client.DeviceLoops;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.ClientDeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload;
import io.github.west3436.zas.atmos.device.DeviceActivitySync;
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.hotspot.GasFireScan;
import io.github.west3436.zas.hotspot.HotspotBlocks;
import io.github.west3436.zas.provider.Frame;
import io.github.west3436.zas.provider.Frames;
import io.github.west3436.zas.sound.FireLoopGroups;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/**
 * What a player sees, hears and reads of a sub-level is where the world shows it, not in its plot
 * (COMPAT-101, COMPAT-102, API-068): a breach on board hisses, puffs and pushes at the hole on the
 * ship, the way the ship turns it, its hull is in the way of a player the flow carries, devices on
 * board are heard there, and coordinates on board are on the ship.
 */
@GameTestHolder(SableGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SableShownTests {
    private SableShownTests() {}

    /** Moves a sub-level 20 blocks east and 3 up, and turns it a quarter, as a ship flies. */
    private static void fly(Deck d, ServerSubLevel sub) {
        sub.logicalPose().orientation().rotateY(Math.PI / 2);
        sub.logicalPose().position().add(20, 3, 0);
        d.container()
                .physicsSystem()
                .getPipeline()
                .teleport(sub, sub.logicalPose().position(), sub.logicalPose().orientation());
        sub.updateLastPose();
        sub.updateBoundingBox();
    }

    private static Vec3 shown(ServerSubLevel sub, Vec3 plotPoint) {
        return sub.logicalPose().transformPosition(plotPoint);
    }

    /**
     * A hull block broken on a flying, turned ship: the player in the cabin, where the world shows
     * it, is sent the breach's openings, both to show and to push it; its client would play the hiss
     * and the wind at the hole where the ship shows it, start the puffs there going out the way the
     * ship turns the flow, and push the player towards the hole. Before, a player on board was sent
     * nothing, because the openings are in the plot.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void compat_102_a_breach_in_flight_hisses_and_puffs_at_the_hole(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 6);
        d.box();
        // About 300 kPa: well past the 65 kPa a player needs to be moved.
        ZoneInfo filled = d.fill(d.abs(2, 2, 2));
        d.settle(GasMutations.add(
                d.level,
                Vec3.atCenterOf(d.abs(2, 2, 2)),
                new GasParcel(
                        Map.of(StandardAir.NITROGEN, filled.mixture().totalMoles()),
                        filled.mixture().temperature()),
                Deck.SOURCE));
        ServerSubLevel sub = d.assemble();
        d.ticks(3);
        fly(d, sub);
        BlockPos hull = Deck.onBoard(sub, 4, 2, 2);
        d.level.setBlockAndUpdate(hull, Deck.AIR);
        FlowField field = FlowField.EMPTY;
        for (int i = 0; i < 12 && field.isEmpty(); i++) {
            d.tick();
            Airflow.step(d.level);
            field = Airflow.field(d.level);
        }
        h.assertFalse(field.isEmpty(), "the breach made no airflow");

        // The hole's face in the plot, on the cabin's side, and where the ship shows it.
        Vec3 hole = shown(sub, new Vec3(hull.getX(), hull.getY() + 0.5, hull.getZ() + 0.5));
        Vec3 cabin = shown(sub, Vec3.atCenterOf(Deck.onBoard(sub, 2, 2, 2)));
        FakePlayer player = d.player(cabin.subtract(0, 0.9, 0));
        h.assertFalse(Airflow.exempt(player), "the player isn't pushed by airflow");

        List<CustomPacketPayload> sent = new ArrayList<>();
        Airflow.sendHookForTest((p, payload) -> {
            if (p == player) {
                sent.add(payload);
            }
        });
        try {
            Airflow.showForTest(d.level, player, d.level.getGameTime());
            Airflow.syncForTest(d.level, player);
        } finally {
            Airflow.sendHookForTest(null);
        }
        List<VisibleFlow> flows = sent.stream()
                .filter(p -> p instanceof AirflowEffectsPayload)
                .map(p -> ((AirflowEffectsPayload) p).flows())
                .findFirst()
                .orElse(List.of());
        h.assertFalse(flows.isEmpty(), "the player on board was sent no airflow to show: " + sent);
        h.assertTrue(
                sent.stream()
                        .anyMatch(p -> p instanceof AirflowSyncPayload s
                                && !s.openings().isEmpty()),
                "the player on board was sent no openings to be pushed by: " + sent);

        // The hiss and the wind, at the hole where the ship shows it.
        Frame.Lookup frames = Frames.lookup(d.level);
        List<AirflowSoundPlan.Loop> loops = AirflowSoundPlan.loops(
                flows, cabin.x, cabin.y, cabin.z, AirflowConfig.mobKpa(), AirflowConfig.heavyKpa(), frames);
        h.assertFalse(loops.isEmpty(), "no sound plays");
        for (AirflowSoundPlan.Loop loop : loops) {
            double off = hole.distanceTo(new Vec3(loop.x(), loop.y(), loop.z()));
            h.assertTrue(off < 1.5, loop.sound() + " plays " + off + " blocks from the hole");
        }

        // The puffs start at the hole and go out the way the ship turns the plot's east.
        Vector3d out = sub.logicalPose().orientation().transform(new Vector3d(1, 0, 0));
        Random random = new Random(7);
        List<double[]> puffs = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            AirflowParticlePlan.plan(
                    flows,
                    cabin.x,
                    cabin.y,
                    cabin.z,
                    AirflowParticlePlan.ALL,
                    random,
                    frames,
                    (x, y, z, vx, vy, vz) -> puffs.add(new double[] {x, y, z, vx, vy, vz}));
        }
        h.assertFalse(puffs.isEmpty(), "no puffs start");
        for (double[] p : puffs) {
            double off = hole.distanceTo(new Vec3(p[0], p[1], p[2]));
            h.assertTrue(off < 1.5, "a puff starts " + off + " blocks from the hole");
            double speed = Math.sqrt(p[3] * p[3] + p[4] * p[4] + p[5] * p[5]);
            double along = (p[3] * out.x + p[4] * out.y + p[5] * out.z) / speed;
            h.assertTrue(along > 0.8, "a puff goes " + along + " of its speed out of the hole");
        }

        // And the flow carries the player towards the hole.
        Vec3 flow = Airflow.flowAt(player, field, FlowField.ANY_ZONE, 0, AirflowConfig.settings(), 0);
        h.assertTrue(flow != null, "the flow doesn't carry the player");
        double towards = flow.normalize().dot(hole.subtract(cabin).normalize());
        h.assertTrue(towards > 0.5, "the flow carries the player " + flow + ", the hole is at " + hole);
        h.succeed();
    }

    /**
     * A gas fire in a flying, turned ship's cabin: a listener in the cabin, where the world shows
     * it, finds the fire in the ship's plot and its loop plays at the fire where the ship shows it.
     * Between looks the loop is placed again as the ship flies and turns, and keeps its source at the
     * next look, so it plays on rather than starting again. A fire on the ground nearby plays where it
     * is. Before, the scan looked only around the listener in the world, so the cabin was silent.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fire_036_a_gas_fire_in_a_flying_cabin_is_heard_where_the_ship_shows_it(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 8);
        d.box();
        ServerSubLevel sub = d.assemble();
        fly(d, sub);
        BlockPos burning = Deck.onBoard(sub, 2, 1, 2);
        d.level.setBlock(burning, HotspotBlocks.GAS_FIRE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        Vec3 fire = shown(sub, Vec3.atCenterOf(burning));
        Vec3 ear = shown(sub, Vec3.atCenterOf(Deck.onBoard(sub, 2, 3, 3)));

        List<int[]> inTheWorld = new ArrayList<>();
        GasFireScan.forEach(
                d.level, ear.x, ear.y, ear.z, FireLoopGroups.RANGE, (x, y, z) -> inTheWorld.add(new int[] {x, y, z}));
        h.assertTrue(inTheWorld.isEmpty(), "the world around the cabin has a gas fire of its own");

        Frame.Lookup frames = Frames.lookup(d.level);
        FireLoopGroups groups = new FireLoopGroups();
        GasFireScan.around(d.level, ear.x, ear.y, ear.z, FireLoopGroups.RANGE, groups::add);
        List<FireLoopGroups.Group> chosen = groups.choose(ear.x, ear.y, ear.z, frames);
        h.assertValueEqual(chosen.size(), 1, "loops in the cabin");
        FireLoopGroups.Group loop = chosen.get(0);
        double off = fire.distanceTo(new Vec3(loop.x(), loop.y(), loop.z()));
        h.assertTrue(off < 1.5, "the fire loop plays " + off + " blocks from the fire on board");

        // The ship flies on and turns again: between looks the loop follows it.
        fly(d, sub);
        Vec3 moved = shown(sub, Vec3.atCenterOf(burning));
        h.assertTrue(moved.distanceTo(fire) > 10, "the fire didn't move with the ship: " + fire + " " + moved);
        List<FireLoopGroups.Group> followed = groups.follow(frames);
        h.assertValueEqual(followed.size(), 1, "loops following the ship");
        off = moved.distanceTo(new Vec3(
                followed.get(0).x(), followed.get(0).y(), followed.get(0).z()));
        h.assertTrue(off < 1.5, "the loop follows the ship to " + off + " blocks from the fire");

        // At the next look, from the cabin where it is now, it is the same loop.
        Vec3 earNow = shown(sub, Vec3.atCenterOf(Deck.onBoard(sub, 2, 3, 3)));
        GasFireScan.around(d.level, earNow.x, earNow.y, earNow.z, FireLoopGroups.RANGE, groups::add);
        List<FireLoopGroups.Group> next = groups.choose(earNow.x, earNow.y, earNow.z, frames);
        h.assertValueEqual(next.size(), 1, "loops in the cabin at the next look");
        h.assertValueEqual(next.get(0).source(), loop.source(), "the loop's source at the next look");
        off = moved.distanceTo(
                new Vec3(next.get(0).x(), next.get(0).y(), next.get(0).z()));
        h.assertTrue(off < 1.5, "the loop plays " + off + " blocks from the fire at the next look");

        // A fire on the ground, by where the ship was built, plays where it is.
        BlockPos ground = d.abs(2, 6, 2);
        d.level.setBlock(ground, HotspotBlocks.GAS_FIRE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        try {
            Vec3 by = Vec3.atCenterOf(ground).add(0, 0, 2);
            GasFireScan.around(d.level, by.x, by.y, by.z, FireLoopGroups.RANGE, groups::add);
            List<FireLoopGroups.Group> onTheGround = groups.choose(by.x, by.y, by.z, frames);
            h.assertValueEqual(onTheGround.size(), 1, "loops by the fire on the ground");
            h.assertTrue(
                    Vec3.atCenterOf(ground)
                                    .distanceTo(new Vec3(
                                            onTheGround.get(0).x(),
                                            onTheGround.get(0).y(),
                                            onTheGround.get(0).z()))
                            < 1e-6,
                    "the fire on the ground plays where it is");
            h.assertTrue(groups.follow(frames).isEmpty(), "a fire on the ground follows a ship");
        } finally {
            d.level.setBlock(ground, Deck.AIR, Block.UPDATE_CLIENTS);
            d.level.setBlock(burning, Deck.AIR, Block.UPDATE_CLIENTS);
        }
        h.succeed();
    }

    /**
     * A player the flow carries towards a hole one block high in a flying, turned ship's hull is put
     * in the swimming pose, as on the ground, and stays down inside the hole (FLOW-019). Carried at the
     * hull where it has no hole, it isn't. A hole on the ground still works with Sable there. Before,
     * the crawl check looked only where the world keeps blocks, which is empty sky by the cabin.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flow_019_a_player_carried_into_a_low_hole_on_a_flying_ship_crawls(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 9);
        d.box();
        ServerSubLevel sub = d.assemble();
        fly(d, sub);
        // A hole one block high in the east wall, level with the cabin's floor.
        BlockPos hole = Deck.onBoard(sub, 4, 1, 2);
        d.level.setBlockAndUpdate(hole, Deck.AIR);
        // The plot's east, the way the ship turns it, as the flow out of the hole is.
        Vector3d east = sub.logicalPose().orientation().transform(new Vector3d(1, 0, 0));
        Vec3 out = new Vec3(east.x, east.y, east.z);

        // The cabin's world space is empty sky: the level alone sees no wall by the hole.
        Vec3 byHole = shown(sub, new Vec3(hole.getX() - 0.5, hole.getY(), hole.getZ() + 0.5));
        FakePlayer probe = d.player(byHole);
        h.assertTrue(
                d.level.noCollision(
                        probe, probe.getDimensions(Pose.STANDING).makeBoundingBox(byHole.add(out.scale(0.5)))),
                "the level sees the hull where the world shows it");

        FakePlayer player = d.player(byHole);
        h.assertTrue(Airflow.crawl(player, out, false, 1.5), "the player by the hole on board didn't crawl");
        h.assertValueEqual(player.getForcedPose(), Pose.SWIMMING, "the forced pose by the hole");

        // Inside the hole, it stays down with no flow.
        player.moveTo(shown(sub, new Vec3(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5)));
        h.assertTrue(Airflow.crawl(player, null, true, 1.5), "the player stood up in the hole on board");

        // Carried at the west wall, which has no hole: it can't crawl into stone.
        Vector3d west = sub.logicalPose().orientation().transform(new Vector3d(-1, 0, 0));
        FakePlayer atWall = d.player(shown(sub, new Vec3(hole.getX() - 2.5, hole.getY(), hole.getZ() + 0.5)));
        h.assertFalse(
                Airflow.crawl(atWall, new Vec3(west.x, west.y, west.z), false, 1.5),
                "the player crawled into the ship's solid wall");
        h.assertTrue(atWall.getForcedPose() == null, "a forced pose at the solid wall");

        // A hole one block high in a wall on the ground, by where the ship was built.
        BlockPos ground = d.abs(10, 0, 2);
        for (int y = 1; y <= 3; y++) {
            d.level.setBlockAndUpdate(ground.above(y), Deck.STONE);
        }
        try {
            FakePlayer onTheGround = d.player(new Vec3(ground.getX() - 0.5, ground.getY(), ground.getZ() + 0.5));
            h.assertTrue(
                    Airflow.crawl(onTheGround, new Vec3(1, 0, 0), false, 1.5),
                    "the player by a hole on the ground didn't crawl");
        } finally {
            for (int y = 1; y <= 3; y++) {
                d.level.setBlockAndUpdate(ground.above(y), Deck.AIR);
            }
        }
        h.succeed();
    }

    /**
     * A device working on a flying, turned ship (CONT-125, CONT-127): the server tells a player in the
     * cabin, where the world shows it, about it, and the client hears it there, at the device where
     * the ship shows it. As the ship flies on, each tick's look places it where the ship is then. A
     * device on the ground is heard where it is. Before, both measured to the device in the ship's
     * plot, millions of blocks off, so devices on board were silent.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void cont_125_a_device_on_a_flying_ship_is_heard_where_the_ship_shows_it(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 10);
        d.box();
        ServerSubLevel sub = d.assemble();
        fly(d, sub);
        BlockPos device = Deck.onBoard(sub, 2, 1, 3);
        Vec3 shownDevice = shown(sub, Vec3.atCenterOf(device));
        Vec3 ear = shown(sub, Vec3.atCenterOf(Deck.onBoard(sub, 2, 2, 1)));
        long now = AtmosTicks.now(d.level);
        DeviceActivitySync.reportForTest(d.level, device, DeviceActivity.HARD, now);
        try {
            // The server tells the player in the cabin.
            FakePlayer player = d.player(ear.subtract(0, 1.62, 0));
            List<DeviceActivityPayload> sent = new ArrayList<>();
            DeviceActivitySync.syncAt(player, now, sent::add);
            h.assertValueEqual(sent.size(), 1, "messages to the player on board");
            h.assertValueEqual(
                    sent.get(0).changes(),
                    List.of(new DeviceActivityPayload.Change(device.asLong(), DeviceActivity.HARD)),
                    "what the player on board was told");

            // Its client hears the device where the ship shows it.
            ClientDeviceActivity table = new ClientDeviceActivity();
            table.accept(sent.get(0));
            List<Vec3> heard = new ArrayList<>();
            table.forEachHeard(d.level, ear, DeviceLoops.RANGE, (pos, level, at) -> heard.add(at));
            h.assertValueEqual(heard.size(), 1, "devices heard in the cabin");
            double off = heard.get(0).distanceTo(shownDevice);
            h.assertTrue(off < 1e-6, "the device is heard " + off + " blocks from where the ship shows it");

            // The ship flies on and turns: the next tick's look follows it.
            fly(d, sub);
            Vec3 moved = shown(sub, Vec3.atCenterOf(device));
            h.assertTrue(moved.distanceTo(shownDevice) > 10, "the device didn't move with the ship");
            Vec3 earNow = shown(sub, Vec3.atCenterOf(Deck.onBoard(sub, 2, 2, 1)));
            heard.clear();
            table.forEachHeard(d.level, earNow, DeviceLoops.RANGE, (pos, level, at) -> heard.add(at));
            h.assertValueEqual(heard.size(), 1, "devices heard in the cabin after it flew on");
            off = heard.get(0).distanceTo(moved);
            h.assertTrue(off < 1e-6, "the device is heard " + off + " blocks from the ship after it flew on");
            heard.clear();
            table.forEachHeard(d.level, ear, DeviceLoops.RANGE, (pos, level, at) -> heard.add(at));
            h.assertTrue(heard.isEmpty(), "the device is heard where the ship was");

            // A device on the ground is heard where it is.
            BlockPos onTheGround = d.abs(2, 8, 2);
            ClientDeviceActivity ground = new ClientDeviceActivity();
            ground.accept(new DeviceActivityPayload(
                    List.of(new DeviceActivityPayload.Change(onTheGround.asLong(), DeviceActivity.WORKING))));
            Vec3 by = Vec3.atCenterOf(onTheGround).add(3, 0, 0);
            ground.forEachHeard(d.level, by, DeviceLoops.RANGE, (pos, level, at) -> heard.add(at));
            h.assertValueEqual(heard, List.of(Vec3.atCenterOf(onTheGround)), "where the device on the ground is heard");
        } finally {
            DeviceActivitySync.reportForTest(d.level, device, DeviceActivity.IDLE, now);
        }
        h.succeed();
    }

    /**
     * Coordinates a player reads of a block on board, such as the analyzer's target, are where the
     * ship shows it, rounded to the block; a point in the world is where it is.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void api_068_a_point_on_board_is_named_where_the_ship_shows_it(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 7);
        d.box();
        ServerSubLevel sub = d.assemble();
        fly(d, sub);
        BlockPos onBoard = Deck.onBoard(sub, 2, 2, 2);
        Vec3 point = new Vec3(onBoard.getX() + 0.25, onBoard.getY() + 0.5, onBoard.getZ() + 0.75);
        Vec3 expected = shown(sub, point);
        Vec3 named = Relocations.shownAt(d.level, point);
        h.assertTrue(
                named.distanceTo(expected) < 1e-6, "a point on board is named " + named + ", shown at " + expected);
        h.assertTrue(named.distanceTo(point) > 1000, "a point on board is named in its plot: " + named);
        h.assertValueEqual(
                Relocations.shownAt(d.level, onBoard),
                BlockPos.containing(shown(sub, Vec3.atCenterOf(onBoard))),
                "the block on board");
        Vec3 inTheWorld = Vec3.atCenterOf(d.abs(2, 20, 2));
        h.assertValueEqual(Relocations.shownAt(d.level, inTheWorld), inTheWorld, "a point in the world");
        h.succeed();
    }
}
