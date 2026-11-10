package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.airflow.AirflowEffectsPayload;
import io.github.west3436.zas.airflow.AirflowImpulsePayload;
import io.github.west3436.zas.airflow.AirflowSyncPayload;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceActivityPayload;
import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.debug.DebugLayer;
import io.github.west3436.zas.debug.DebugOverlay;
import io.github.west3436.zas.debug.DebugOverlayPayload;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.gas.GasDefinitionsPayload;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.ListenerAirPayload;
import io.github.west3436.zas.life.BreathSyncPayload;
import io.github.west3436.zas.reaction.ReactionDefinitionsPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What a client is told (DATA-005, D-53). Two recording clients stay on the server for real game
 * ticks, so every sync reaches them by its real path: one in a sealed room with thin air, a device
 * and then a breach, and one in the open far away. The batch is this test's own, as they are on the
 * server's player lists throughout, and it drives atmos ticks while building.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClientSyncScopeTests {
    private static final String BATCH = "zas_atmos_client_sync";

    /**
     * Every payload the two mods may send a player with no window open and no overlay on: its own
     * state, airflow and devices near it, gas particles, and the gas and reaction definitions.
     */
    private static final Set<Class<?>> ALLOWED = Set.of(
            AtmosHudPayload.class,
            ListenerAirPayload.class,
            BreathSyncPayload.class,
            AirflowSyncPayload.class,
            AirflowImpulsePayload.class,
            AirflowEffectsPayload.class,
            DeviceActivityPayload.class,
            GasJet.class,
            GasDefinitionsPayload.class,
            ReactionDefinitionsPayload.class);

    /** How far past the push's range an opening may be sent: the sync's margin of 8, and a fall. */
    private static final double SYNC_SLACK = 10;

    /** What the batch's end undoes, even if the test failed or timed out. */
    private static final List<Runnable> CLEANUP = new ArrayList<>();

    private ClientSyncScopeTests() {}

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        CLEANUP.forEach(Runnable::run);
        CLEANUP.clear();
    }

    /** Loads a site's chunks until the batch ends, and clears it then. */
    private static void hold(Site s) {
        int cx = s.origin.getX() >> 4;
        int cz = s.origin.getZ() >> 4;
        s.level.setChunkForced(cx, cz, true);
        s.level.setChunkForced(cx + 1, cz, true);
        CLEANUP.add(() -> {
            s.clear();
            s.level.setChunkForced(cx, cz, false);
            s.level.setChunkForced(cx + 1, cz, false);
        });
    }

    private static ListeningPlayer join(ServerLevel level, Vec3 at, boolean operator) {
        ListeningPlayer l = ListeningPlayer.client(level, at, operator);
        l.join();
        CLEANUP.add(() -> {
            DebugOverlay.setLayers(l.player(), 0);
            l.leave();
        });
        return l;
    }

    /** The payloads from the two mods, which are what DATA-005 limits. */
    private static List<CustomPacketPayload> ours(ListeningPlayer l) {
        return l.payloads().stream()
                .filter(p -> p.type().id().getNamespace().startsWith("zas"))
                .toList();
    }

    private static void onlyAllowed(GameTestHelper h, ListeningPlayer l, String who) {
        for (CustomPacketPayload p : ours(l)) {
            if (!(p instanceof DebugOverlayPayload)) {
                h.assertTrue(
                        ALLOWED.contains(p.getClass()),
                        who + " was sent " + p.type().id());
            }
        }
    }

    /**
     * A player is sent its own state, and the airflow and devices near it, and nothing else, and none
     * of it reaches a player far away. Debug data goes only to an operator with the overlay on, and
     * stops when it goes off. A player without the permission gets none even if it was switched on.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void data_005_a_player_is_sent_only_its_own_state_and_what_is_near_it(GameTestHelper h) {
        Site s = new Site(h, 961);
        Site open = new Site(h, 964);
        hold(s);
        hold(open);
        s.clear();
        open.clear();

        // A sealed room with 30 kPa of nitrogen, which is thin and has no oxygen to breathe.
        s.room(0, 8);
        s.ticks(2);
        s.settle(GasMutations.add(
                s.level,
                s.point(4, 2, 2),
                new GasParcel(Map.of(StandardAir.NITROGEN, 1.0), StandardAir.TEMPERATURE),
                Site.SOURCE));
        s.empty(4, 2, 2);
        s.addKpa(4, 2, 2, StandardAir.NITROGEN, 30);
        TestDevice.Entity device = s.place(2, 1, 3, TestDevice.block().defaultBlockState(), TestDevice.Entity.class);
        device.moves = 8;

        ListeningPlayer inside = join(s.level, s.point(5, 1, 2), true);
        ListeningPlayer away = join(s.level, open.point(4, 1, 2), false);
        Runnable tick = () -> {
            inside.tick();
            away.tick();
        };

        h.startSequence()
                // Its own state, and the device beside it.
                .thenWaitUntil(() -> {
                    tick.run();
                    h.assertTrue(
                            inside.payloads(AtmosHudPayload.class).stream().anyMatch(p -> p.oxygen() > 0),
                            "the player with no oxygen wasn't sent an alert");
                    h.assertTrue(
                            inside.payloads(ListenerAirPayload.class).stream()
                                    .anyMatch(p -> Math.abs(p.kPa() - 30) <= 1),
                            "the player in 30 kPa wasn't sent its pressure");
                    h.assertTrue(
                            inside.payloads(BreathSyncPayload.class).stream().anyMatch(BreathSyncPayload::hypoxic),
                            "the player with no oxygen wasn't told it can't breathe");
                    h.assertFalse(
                            inside.payloads(DeviceActivityPayload.class).isEmpty(),
                            "the player wasn't told about the device beside it");
                })
                // Airflow, near it.
                .thenExecute(() -> {
                    h.assertTrue(
                            inside.payloads(AirflowSyncPayload.class).stream()
                                    .allMatch(p -> p.openings().isEmpty()),
                            "airflow was sent before the breach");
                    s.set(8, 2, 2, Site.AIR);
                })
                .thenWaitUntil(() -> {
                    tick.run();
                    Vec3 at = inside.player().position();
                    boolean any = false;
                    for (AirflowSyncPayload p : inside.payloads(AirflowSyncPayload.class)) {
                        for (FlowField.Opening o : p.openings()) {
                            any = true;
                            double distance = o.distance(at.x, at.y, at.z);
                            h.assertTrue(
                                    distance <= p.settings().range() + SYNC_SLACK,
                                    "an opening " + distance + " blocks away was sent");
                        }
                    }
                    h.assertTrue(any, "the player beside a breach was sent no openings");
                    h.assertTrue(
                            inside.payloads(AirflowEffectsPayload.class).stream()
                                    .anyMatch(p -> !p.flows().isEmpty()),
                            "the player beside a breach wasn't sent the airflow to show");
                })
                // Debug data: none so far. Then the operator's overlay, and one nobody may have.
                .thenExecute(() -> {
                    h.assertTrue(inside.payloads(DebugOverlayPayload.class).isEmpty(), "debug data with no overlay");
                    h.assertTrue(away.payloads(DebugOverlayPayload.class).isEmpty(), "debug data to a bystander");
                    DebugOverlay.setLayers(inside.player(), DebugLayer.ALL);
                    DebugOverlay.setLayers(away.player(), DebugLayer.ALL);
                })
                .thenWaitUntil(() -> {
                    tick.run();
                    h.assertTrue(
                            inside.payloads(DebugOverlayPayload.class).stream()
                                    .anyMatch(p -> !p.zones().isEmpty()),
                            "the operator with the overlay on wasn't sent the zones");
                    h.assertValueEqual(DebugOverlay.layers(away.player()), 0, "the overlay without the permission");
                })
                .thenExecute(() -> {
                    h.assertTrue(
                            away.payloads(DebugOverlayPayload.class).stream()
                                    .allMatch(p -> p.layers() == 0
                                            && p.zones().isEmpty()
                                            && p.cells().isEmpty()),
                            "debug data went to a player without the permission");
                    DebugOverlay.setLayers(inside.player(), 0);
                    List<DebugOverlayPayload> sent = inside.payloads(DebugOverlayPayload.class);
                    h.assertValueEqual(sent.get(sent.size() - 1).layers(), 0, "the last debug data's layers");
                })
                // Nothing goes after it's off.
                .thenExecuteFor(45, tick)
                .thenExecute(() -> {
                    List<DebugOverlayPayload> sent = inside.payloads(DebugOverlayPayload.class);
                    h.assertValueEqual(sent.get(sent.size() - 1).layers(), 0, "debug data after the overlay went off");

                    onlyAllowed(h, inside, "the player in the room");
                    onlyAllowed(h, away, "the player far away");

                    // Far away, it hears nothing of the room, the breach or the device.
                    for (CustomPacketPayload p : ours(away)) {
                        boolean aboutTheRoom = p instanceof DeviceActivityPayload
                                || p instanceof AirflowImpulsePayload
                                || p instanceof AirflowSyncPayload x
                                        && !x.openings().isEmpty()
                                || p instanceof AirflowEffectsPayload y
                                        && !y.flows().isEmpty()
                                || p instanceof AtmosHudPayload z && z.oxygen() > 0
                                || p instanceof ListenerAirPayload a && a.kPa() < 45;
                        h.assertFalse(aboutTheRoom, "the player far away was sent " + p);
                    }
                })
                .thenSucceed();
    }
}
