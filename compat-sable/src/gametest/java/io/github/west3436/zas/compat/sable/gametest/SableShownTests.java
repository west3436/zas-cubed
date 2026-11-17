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
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.provider.Frame;
import io.github.west3436.zas.provider.Frames;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

/**
 * What a player sees, hears and reads of a sub-level is where the world shows it, not in its plot
 * (COMPAT-101, COMPAT-102, API-068): a breach on board hisses, puffs and pushes at the hole on the
 * ship, the way the ship turns it, and coordinates on board are on the ship.
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
