package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.airflow.Airflow;
import io.github.west3436.zas.airflow.AirflowClass;
import io.github.west3436.zas.airflow.AirflowImpulsePayload;
import io.github.west3436.zas.airflow.AirflowReceiver;
import io.github.west3436.zas.airflow.AirflowSyncPayload;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Airflow in a real level (FLOW-001 to FLOW-004, FLOW-009, FLOW-011, FLOW-014, FLOW-017).
 *
 * <p>Each test runs within one game tick, so nothing moves the entities between the push and the
 * check: it drives atmos ticks itself, then one game tick of airflow ({@link Airflow#step}), and
 * reads the velocities the push left. The breach is a vacuum room with a hole in its east wall,
 * open to the overworld's air, so air rushes in: outside, entities are drawn to the hole; inside,
 * carried away from it into the room.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AirflowTests {
    /** The hole in the east wall of the breached room. */
    private static final int HOLE_X = 20;

    private static final int Y = 2;
    private static final int Z = 3;

    private AirflowTests() {}

    /** A test's area and the entities it made, which it discards when done. */
    private static final class Site implements AutoCloseable {
        final GameTestHelper h;
        final Rooms r;
        final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> restore = new ArrayList<>();

        Site(GameTestHelper h, int index) {
            this.h = h;
            this.r = new Rooms(h, index);
            List<ChunkPos> chunks = new ArrayList<>();
            for (int x = 0; x <= 48; x += 16) {
                chunks.add(r.chunk(x));
            }
            r.holdLoaded(chunks);
        }

        /**
         * Builds a sealed room from x 0 to {@link #HOLE_X}, empties it to a vacuum, then opens a
         * hole in its east wall and runs atmos ticks until airflow has openings.
         */
        void breach() {
            r.clear(0, 48);
            r.box(0, 0, HOLE_X, 6);
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(10, Y, Z),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("airflow")));
            h.assertTrue(r.zoned(10, Y, Z), "the room didn't zone");
            r.set(HOLE_X, Y, Z, Rooms.AIR);
            for (int i = 0; i < 6 && !hasOpenings(); i++) {
                r.tick();
                Airflow.step(r.level);
            }
            h.assertTrue(hasOpenings(), "the breach has no airflow");
        }

        boolean hasOpenings() {
            for (FlowField.Opening o : Airflow.field(r.level).openings()) {
                if (Math.abs(o.hx() - r.abs(HOLE_X, Y, Z).getX()) <= 2
                        && o.hy() == r.abs(0, Y, 0).getY()
                        && o.hz() == r.abs(0, 0, Z).getZ()) {
                    return true;
                }
            }
            return false;
        }

        /** The breach's largest pressure difference, kPa. */
        double difference() {
            double max = 0;
            for (FlowField.Opening o : Airflow.field(r.level).openings()) {
                if (Math.abs(o.hx() - r.abs(HOLE_X, Y, Z).getX()) <= 2) {
                    max = Math.max(max, o.difference());
                }
            }
            return max;
        }

        /** A still item centred at area (x + 0.5, Y + 0.5, Z + 0.5). */
        ItemEntity item(double x) {
            Vec3 at = r.point(0, Y, Z).add(x, 0, 0);
            ItemEntity e = new ItemEntity(r.level, at.x, at.y - 0.125, at.z, new ItemStack(Items.STONE), 0, 0, 0);
            e.setNoGravity(true);
            e.setPickUpDelay(32767);
            r.level.addFreshEntity(e);
            entities.add(e);
            return e;
        }

        <T extends Entity> T spawn(EntityType<T> type, double x) {
            T e = type.create(r.level);
            h.assertTrue(e != null, "couldn't create a " + type);
            Vec3 at = r.point(0, Y, Z).add(x, 0, 0);
            e.moveTo(at.x, at.y - e.getBbHeight() / 2, at.z);
            e.setDeltaMovement(Vec3.ZERO);
            if (e instanceof Mob mob) {
                mob.setNoAi(true);
            }
            e.setNoGravity(true);
            r.level.addFreshEntity(e);
            entities.add(e);
            return e;
        }

        FakePlayer player(double x) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_airflow"));
            Vec3 at = r.point(0, Y, Z).add(x, 0, 0);
            p.moveTo(at.x, at.y - p.getBbHeight() / 2, at.z);
            p.setDeltaMovement(Vec3.ZERO);
            return p;
        }

        /** A field of one opening at area x {@code x}, from (x + 1) to x, at {@code kpa}. */
        FlowField field(int x, double kpa) {
            BlockPos high = r.abs(x + 1, Y, Z);
            BlockPos low = r.abs(x, Y, Z);
            return FlowField.of(List.of(new FlowEdge(
                    1,
                    FlowEdge.AMBIENT,
                    kpa,
                    0,
                    new long[] {CellIds.of(high.getX(), high.getY(), high.getZ())},
                    new long[] {CellIds.of(low.getX(), low.getY(), low.getZ())})));
        }

        void enabled(boolean on) {
            boolean before = AirflowConfig.ENABLED.get();
            restore.add(() -> AirflowConfig.ENABLED.set(before));
            AirflowConfig.ENABLED.set(on);
        }

        void disabledDimensions(List<String> dims) {
            List<? extends String> before = AirflowConfig.DISABLED_DIMENSIONS.get();
            restore.add(() -> AirflowConfig.DISABLED_DIMENSIONS.set(before));
            AirflowConfig.DISABLED_DIMENSIONS.set(dims);
        }

        void maxEntities(int cap) {
            int before = AirflowConfig.MAX_ENTITIES.get();
            restore.add(() -> AirflowConfig.MAX_ENTITIES.set(before));
            AirflowConfig.MAX_ENTITIES.set(cap);
        }

        void maxSpeed(double speed) {
            double before = AirflowConfig.MAX_SPEED.get();
            restore.add(() -> AirflowConfig.MAX_SPEED.set(before));
            AirflowConfig.MAX_SPEED.set(speed);
        }

        @Override
        public void close() {
            for (Entity e : entities) {
                e.discard();
            }
            for (int i = restore.size() - 1; i >= 0; i--) {
                restore.get(i).run();
            }
            Airflow.sendHookForTest(null);
        }
    }

    private static double vx(Entity e) {
        return e.getDeltaMovement().x;
    }

    /** Within 9 blocks of a breach items move, on both sides; beyond, they don't (FLOW-001). */
    @GameTest(template = "empty")
    public static void flow_001_items_within_range_move_and_beyond_dont(GameTestHelper h) {
        try (Site s = new Site(h, 110)) {
            s.breach();
            ItemEntity outsideNear = s.item(HOLE_X + 4);
            ItemEntity outsideFar = s.item(HOLE_X + 13);
            ItemEntity insideNear = s.item(HOLE_X - 4);
            ItemEntity insideFar = s.item(HOLE_X - 13);
            Airflow.step(s.r.level);
            h.assertTrue(vx(outsideNear) < -0.01, "the item outside wasn't drawn to the hole: " + vx(outsideNear));
            h.assertTrue(vx(insideNear) < -0.01, "the item inside wasn't carried in: " + vx(insideNear));
            h.assertValueEqual(outsideFar.getDeltaMovement(), Vec3.ZERO, "the item 13 blocks outside");
            h.assertValueEqual(insideFar.getDeltaMovement(), Vec3.ZERO, "the item 13 blocks inside");
            h.assertTrue(Airflow.processedLastTick(s.r.level) >= 2, "airflow looked at fewer than the two near items");
        }
        h.succeed();
    }

    /**
     * An item accelerates towards a breach, faster at a higher difference, and never beyond the
     * cap (FLOW-002).
     */
    @GameTest(template = "empty")
    public static void flow_002_faster_at_higher_difference_and_capped(GameTestHelper h) {
        try (Site s = new Site(h, 111)) {
            FlowSettings settings = AirflowConfig.settings();
            ItemEntity low = s.item(4);
            ItemEntity high = s.item(4);
            Airflow.apply(low, s.field(0, 40), FlowField.ANY_ZONE, AirflowClass.LIGHT.threshold(), settings);
            Airflow.apply(high, s.field(0, 80), FlowField.ANY_ZONE, AirflowClass.LIGHT.threshold(), settings);
            h.assertTrue(vx(low) < 0, "40 kPa didn't push towards the opening");
            h.assertTrue(vx(high) < vx(low), "80 kPa didn't push harder than 40: " + vx(high) + " vs " + vx(low));

            // Pushed again and again at 10 atm next to the opening, it reaches the cap and stops there.
            s.maxSpeed(0.3);
            FlowSettings capped = AirflowConfig.settings();
            ItemEntity e = s.item(1);
            FlowField strong = s.field(0, 1013);
            for (int i = 0; i < 60; i++) {
                Airflow.apply(e, strong, FlowField.ANY_ZONE, AirflowClass.LIGHT.threshold(), capped);
            }
            double speed = e.getDeltaMovement().length();
            h.assertTrue(speed > 0.1, "the item didn't speed up: " + speed);
            h.assertTrue(speed <= 0.3 + 1e-9, "the item passed the cap: " + speed);
        }
        h.succeed();
    }

    /** Each class moves at its threshold and not just below (FLOW-003, FLOW-011). */
    @GameTest(template = "empty")
    public static void flow_011_classes_move_at_their_thresholds(GameTestHelper h) {
        try (Site s = new Site(h, 112)) {
            FlowSettings settings = AirflowConfig.settings();
            List<Entity> members = List.of(
                    s.item(4),
                    s.spawn(EntityType.EXPERIENCE_ORB, 4),
                    s.spawn(EntityType.ARROW, 4),
                    s.spawn(EntityType.PIG, 4),
                    s.player(4),
                    s.spawn(EntityType.MINECART, 4),
                    s.spawn(EntityType.BOAT, 4),
                    s.spawn(EntityType.ARMOR_STAND, 4));
            AirflowClass[] expected = {
                AirflowClass.LIGHT,
                AirflowClass.LIGHT,
                AirflowClass.LIGHT,
                AirflowClass.MOB,
                AirflowClass.MOB,
                AirflowClass.HEAVY,
                AirflowClass.HEAVY,
                AirflowClass.HEAVY
            };
            double[] kpa = {20, 20, 20, 65, 65, 85, 85, 85};
            for (int i = 0; i < members.size(); i++) {
                Entity e = members.get(i);
                AirflowClass cls = AirflowClass.of(e);
                h.assertValueEqual(cls, expected[i], e.getType().toShortString() + "'s class");
                h.assertValueEqual(cls.threshold(), kpa[i], e.getType().toShortString() + "'s threshold");
                e.setDeltaMovement(Vec3.ZERO);
                Airflow.apply(e, s.field(0, kpa[i] - 0.5), FlowField.ANY_ZONE, cls.threshold(), settings);
                h.assertValueEqual(
                        e.getDeltaMovement(), Vec3.ZERO, e.getType().toShortString() + " just below " + kpa[i]);
                Airflow.apply(e, s.field(0, kpa[i] + 0.5), FlowField.ANY_ZONE, cls.threshold(), settings);
                h.assertTrue(vx(e) < 0, e.getType().toShortString() + " didn't move just above " + kpa[i]);
            }
            h.assertValueEqual(
                    AirflowClass.of(EntityType.ENDER_DRAGON.create(s.r.level)), AirflowClass.IMMUNE, "the dragon");
        }
        h.succeed();
    }

    /** A leashed mob, a rider and a player sneaking on the ground stay put (FLOW-004). */
    @GameTest(template = "empty")
    public static void flow_004_anchored_entities_stay_put(GameTestHelper h) {
        try (Site s = new Site(h, 113)) {
            FlowSettings settings = AirflowConfig.settings();
            FlowField field = s.field(0, 101.325);
            double mob = AirflowClass.MOB.threshold();

            Mob free = s.spawn(EntityType.PIG, 4);
            Mob leashed = s.spawn(EntityType.PIG, 4);
            Entity holder = s.spawn(EntityType.PIG, 5);
            leashed.setLeashedTo(holder, true);
            h.assertTrue(leashed.isLeashed(), "the pig isn't leashed");
            Entity cart = s.spawn(EntityType.MINECART, 4);
            Mob rider = s.spawn(EntityType.PIG, 4);
            h.assertTrue(rider.startRiding(cart, true), "the pig didn't get in the cart");

            Airflow.apply(free, field, FlowField.ANY_ZONE, mob, settings);
            Airflow.apply(leashed, field, FlowField.ANY_ZONE, mob, settings);
            Airflow.apply(rider, field, FlowField.ANY_ZONE, mob, settings);
            h.assertTrue(vx(free) < 0, "the free pig didn't move");
            h.assertValueEqual(leashed.getDeltaMovement(), Vec3.ZERO, "the leashed pig");
            h.assertValueEqual(rider.getDeltaMovement(), Vec3.ZERO, "the riding pig");

            FakePlayer standing = s.player(4);
            standing.setOnGround(true);
            FakePlayer sneaking = s.player(4);
            sneaking.setOnGround(true);
            sneaking.setShiftKeyDown(true);
            FakePlayer sneakingInAir = s.player(4);
            sneakingInAir.setOnGround(false);
            sneakingInAir.setShiftKeyDown(true);
            Airflow.apply(standing, field, FlowField.ANY_ZONE, mob, settings);
            Airflow.apply(sneaking, field, FlowField.ANY_ZONE, mob, settings);
            Airflow.apply(sneakingInAir, field, FlowField.ANY_ZONE, mob, settings);
            h.assertTrue(vx(standing) < 0, "the standing player didn't move");
            h.assertValueEqual(sneaking.getDeltaMovement(), Vec3.ZERO, "the player sneaking on the ground");
            h.assertTrue(vx(sneakingInAir) < 0, "the player sneaking in the air didn't move");
        }
        h.succeed();
    }

    /** Airflow switched off globally, or in the dimension, moves nothing (FLOW-014). */
    @GameTest(template = "empty")
    public static void flow_014_switches_turn_airflow_off(GameTestHelper h) {
        try (Site s = new Site(h, 114)) {
            s.breach();
            ItemEntity e = s.item(HOLE_X - 4);

            s.enabled(false);
            s.r.tick();
            Airflow.step(s.r.level);
            h.assertTrue(Airflow.field(s.r.level).isEmpty(), "airflow kept a field while off");
            h.assertTrue(s.r.dimension().pipeline().flows().isEmpty(), "the engine exported flows while off");
            h.assertValueEqual(e.getDeltaMovement(), Vec3.ZERO, "the item with airflow off");

            AirflowConfig.ENABLED.set(true);
            s.disabledDimensions(List.of(s.r.level.dimension().location().toString()));
            s.r.tick();
            Airflow.step(s.r.level);
            h.assertTrue(
                    s.r.dimension().pipeline().flows().isEmpty(), "the engine exported flows in a disabled dimension");
            h.assertValueEqual(e.getDeltaMovement(), Vec3.ZERO, "the item in a disabled dimension");

            AirflowConfig.DISABLED_DIMENSIONS.set(List.of("minecraft:the_end"));
            s.r.tick();
            Airflow.step(s.r.level);
            h.assertTrue(vx(e) < 0, "the item didn't move once airflow was back on: " + s.difference() + " kPa");
        }
        h.succeed();
    }

    /**
     * A player near a breach is sent the openings and, at the onset, a one-off impulse (FLOW-009);
     * the client's path pushes the player with them (FLOW-017).
     */
    @GameTest(template = "empty")
    public static void flow_009_player_gets_the_impulse_and_the_client_pushes(GameTestHelper h) {
        try (Site s = new Site(h, 115)) {
            s.breach();
            FakePlayer player = s.player(HOLE_X + 3);
            List<CustomPacketPayload> sent = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> {
                if (to == player) {
                    sent.add(p);
                }
            });
            Airflow.syncForTest(s.r.level, player);
            AirflowSyncPayload sync = null;
            AirflowImpulsePayload impulse = null;
            for (CustomPacketPayload p : sent) {
                if (p instanceof AirflowSyncPayload x) {
                    sync = x;
                } else if (p instanceof AirflowImpulsePayload x) {
                    impulse = x;
                }
            }
            h.assertTrue(sync != null && !sync.openings().isEmpty(), "no openings were sent: " + sent);
            h.assertTrue(impulse != null, "no impulse was sent: " + s.difference() + " kPa");
            h.assertTrue(impulse.vx() < 0, "the impulse isn't towards the hole: " + impulse);

            // Only at the onset.
            sent.clear();
            Airflow.syncForTest(s.r.level, player);
            h.assertFalse(sent.stream().anyMatch(p -> p instanceof AirflowImpulsePayload), "a second impulse");

            // The client's path: the impulse, then the continuous push from the synced openings.
            Airflow.applyImpulse(player, impulse);
            h.assertTrue(vx(player) < 0, "the impulse didn't move the player");
            player.setDeltaMovement(Vec3.ZERO);
            AirflowReceiver client = new AirflowReceiver();
            client.accept(sync);
            h.assertTrue(client.tick(player) != null && vx(player) < 0, "the client's push didn't move the player");

            // A payload with no openings ends it.
            client.accept(AirflowSyncPayload.none(s.r.level.dimension().location()));
            h.assertFalse(client.active(), "the client kept airflow after it ended");
        }
        h.succeed();
    }

    /**
     * Airflow moves at most the configured number of entities each game tick, and the rest take
     * turns, so every one is moved within a few ticks (FLOW-013).
     */
    @GameTest(template = "empty")
    public static void flow_013_the_cap_bounds_entities_per_tick(GameTestHelper h) {
        try (Site s = new Site(h, 116)) {
            s.breach();
            s.maxEntities(10);
            List<ItemEntity> items = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                items.add(s.item(HOLE_X - 2 - (i % 6)));
            }
            Airflow.step(s.r.level);
            int inRange = Airflow.inRangeLastTick(s.r.level);
            h.assertTrue(inRange >= 30, "fewer than the 30 items were in range: " + inRange);
            h.assertValueEqual(Airflow.processedLastTick(s.r.level), 10, "entities looked at in one tick");
            long moved = items.stream().filter(e -> vx(e) < 0).count();
            h.assertTrue(moved <= 10, "more items moved than the cap: " + moved);
            for (int i = 0; i < inRange / 10 + 1; i++) {
                Airflow.step(s.r.level);
                h.assertTrue(Airflow.processedLastTick(s.r.level) <= 10, "the cap was passed");
            }
            h.assertTrue(items.stream().allMatch(e -> vx(e) < 0), "some items never took their turn");
        }
        h.succeed();
    }
}
