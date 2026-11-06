package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.airflow.Airflow;
import io.github.west3436.zas.airflow.AirflowClass;
import io.github.west3436.zas.airflow.AirflowEffectsPayload;
import io.github.west3436.zas.airflow.AirflowImpulsePayload;
import io.github.west3436.zas.airflow.AirflowReceiver;
import io.github.west3436.zas.airflow.AirflowSyncPayload;
import io.github.west3436.zas.airflow.HeldFlows;
import io.github.west3436.zas.airflow.VisibleFlow;
import io.github.west3436.zas.airflow.VisibleFlows;
import io.github.west3436.zas.airflow.VisibleOpening;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AirflowConfig;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.engine.classify.CellIds;
import io.github.west3436.zas.engine.flow.FlowEdge;
import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowImpact;
import io.github.west3436.zas.engine.flow.FlowSettings;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.life.LifeDamage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Airflow in a real level (FLOW-001 to FLOW-005, FLOW-009, FLOW-011, FLOW-012, FLOW-014, FLOW-017 to
 * FLOW-019), and what the server sends clients to show it (CLIENT-003, DATA-015).
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

    /** The stone pillar outside the hole, of {@link Site#pillar}, whose east face is at x + 1. */
    private static final int PILLAR_X = HOLE_X + 4;

    private static final int Y = 2;
    private static final int Z = 3;

    private AirflowTests() {}

    /** A test's area and the entities it made, which it discards when done. */
    private static final class Site implements AutoCloseable {
        final GameTestHelper h;
        final Rooms r;
        final List<Entity> entities = new ArrayList<>();
        private final List<Runnable> restore = new ArrayList<>();
        private int holeY = Y;

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
            breach(Y);
        }

        /** {@link #breach()} with the hole at area y {@code y}: 1 is level with the room's floor. */
        void breach(int y) {
            holeY = y;
            r.clear(0, 48);
            r.box(0, 0, HOLE_X, 6);
            emptyRoom();
            h.assertTrue(r.zoned(10, Y, Z), "the room didn't zone");
            open();
            h.assertTrue(hasOpenings(), "the breach has no airflow");
        }

        /** Takes all the gas out of the room. */
        void emptyRoom() {
            r.settle(GasMutations.remove(
                    r.level,
                    r.point(10, Y, Z),
                    Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                    Rooms.source("airflow")));
        }

        /** Builds the room with this share of its air left, then opens the hole in its east wall. */
        void breachKeeping(double keep) {
            r.clear(0, 48);
            r.box(0, 0, HOLE_X, 6);
            r.settle(GasMutations.remove(
                    r.level, r.point(10, Y, Z), Map.of(StandardAir.NITROGEN, 1e-3), Rooms.source("airflow")));
            Map<ResourceLocation, Double> take = new HashMap<>();
            r.zone(10, Y, Z).mixture().moles().forEach((gas, moles) -> take.put(gas, moles * (1 - keep)));
            r.settle(GasMutations.remove(r.level, r.point(10, Y, Z), take, Rooms.source("airflow")));
            open();
        }

        /** Opens the hole and runs atmos ticks for airflow to find it, if it is to. */
        private void open() {
            r.set(HOLE_X, holeY, Z, Rooms.AIR);
            for (int i = 0; i < 6 && !hasOpenings(); i++) {
                r.tick();
                Airflow.step(r.level);
            }
        }

        /**
         * The airflow the server tells a player to show, if it would tell it anything, as an atmos
         * tick would at game tick {@code now}. Null for nothing.
         */
        AirflowEffectsPayload show(ServerPlayer player, long now) {
            List<AirflowEffectsPayload> sent = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> {
                if (to == player && p instanceof AirflowEffectsPayload x) {
                    sent.add(x);
                }
            });
            Airflow.showForTest(r.level, player, now);
            Airflow.sendHookForTest(null);
            h.assertTrue(sent.size() <= 1, "more than one payload at once: " + sent);
            return sent.isEmpty() ? null : sent.get(0);
        }

        boolean hasOpenings() {
            for (FlowField.Opening o : Airflow.field(r.level).openings()) {
                if (Math.abs(o.hx() - r.abs(HOLE_X, holeY, Z).getX()) <= 2
                        && o.hy() == r.abs(0, holeY, 0).getY()
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

        /**
         * A player standing on the hole's floor level, east of it in line with it, its centre
         * {@code d} blocks from the hole's east face.
         */
        FakePlayer standing(BlockPos hole, double d) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_airflow"));
            p.moveTo(hole.getX() + 1 + d, hole.getY(), hole.getZ() + 0.5);
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

        /** A stone pillar outside the hole, in line with it: the flow carries what is further out into it. */
        void pillar() {
            pillar(PILLAR_X);
        }

        void pillar(int x) {
            for (int y = Y - 1; y <= Y + 1; y++) {
                r.set(x, y, Z, Rooms.STONE);
            }
        }

        /**
         * A game tick of a mob thrown along x at this speed: the airflow that ended the last game
         * tick, the mob's own move, then the airflow that sees what the move did.
         */
        void fling(Mob mob, double vx) {
            mob.setDeltaMovement(vx, 0, 0);
            Airflow.step(r.level);
            mob.move(MoverType.SELF, mob.getDeltaMovement());
            Airflow.step(r.level);
        }

        /** A game tick after a player's move to absolute x reached the server, its feet at area y 1. */
        void playerAt(FakePlayer player, double x) {
            BlockPos at = r.abs(0, Y - 1, Z);
            player.setPos(x, at.getY(), at.getZ() + 0.5);
            Airflow.crawlForTest(r.level, player);
            Airflow.step(r.level);
        }

        void impactScale(double scale) {
            double before = HazardConfig.SCALE.get(HazardType.AIRFLOW_IMPACT).get();
            restore.add(() -> HazardConfig.SCALE.get(HazardType.AIRFLOW_IMPACT).set(before));
            HazardConfig.SCALE.get(HazardType.AIRFLOW_IMPACT).set(scale);
        }

        void impactEnabled(boolean on) {
            boolean before = HazardConfig.ENABLED.get(HazardType.AIRFLOW_IMPACT).get();
            restore.add(
                    () -> HazardConfig.ENABLED.get(HazardType.AIRFLOW_IMPACT).set(before));
            HazardConfig.ENABLED.get(HazardType.AIRFLOW_IMPACT).set(on);
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
     * Worn airflow resistance r scales the push by (1 − r) (FLOW-012). The GameTest's suit piece
     * (r = 0.5) halves it, a zombie's own r = 0.25 takes a quarter off it (API-022), and an anvil,
     * given r = 1 by the test pack's protection data, stops it.
     * The test checks these for mobs the server pushes beside a breach, and for players: the sync
     * carries the resistance, the client's push uses it, and it scales the onset impulse.
     */
    @GameTest(template = "empty")
    public static void flow_012_resistance_scales_the_push(GameTestHelper h) {
        try (Site s = new Site(h, 117)) {
            s.breach();
            h.assertTrue(s.difference() >= AirflowClass.MOB.threshold(), "the breach is too weak: " + s.difference());

            // Mobs, pushed by the server.
            Zombie bare = s.spawn(EntityType.ZOMBIE, HOLE_X - 3);
            Zombie half = s.spawn(EntityType.ZOMBIE, HOLE_X - 3);
            half.setItemSlot(EquipmentSlot.FEET, new ItemStack(TestLifeSupport.item()));
            Zombie still = s.spawn(EntityType.ZOMBIE, HOLE_X - 3);
            still.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.ANVIL));
            Zombie own = s.spawn(EntityType.ZOMBIE, HOLE_X - 3);
            own.addTag(TestLifeSupport.ENTITY_TAG);
            close(h, Airflow.resistance(bare), 0, "the bare zombie's resistance");
            close(h, Airflow.resistance(half), 0.5, "the suited zombie's resistance");
            close(h, Airflow.resistance(still), 1, "the anvil-shod zombie's resistance");
            close(h, Airflow.resistance(own), 0.25, "the resistance a zombie gives itself");
            Airflow.step(s.r.level);
            h.assertTrue(vx(bare) < -0.01, "the bare zombie wasn't pushed: " + vx(bare));
            close(h, vx(half), vx(bare) / 2, "the suited zombie's push");
            close(h, vx(own), vx(bare) * 0.75, "the push on a zombie with its own resistance");
            h.assertValueEqual(still.getDeltaMovement(), Vec3.ZERO, "the anvil-shod zombie");

            // Players, pushed by their clients.
            FakePlayer plain = s.player(HOLE_X + 3);
            FakePlayer suited = s.player(HOLE_X + 3);
            suited.setItemSlot(EquipmentSlot.FEET, new ItemStack(TestLifeSupport.item()));
            FakePlayer shod = s.player(HOLE_X + 3);
            shod.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.ANVIL));
            Map<ServerPlayer, List<CustomPacketPayload>> sent = new HashMap<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) ->
                    sent.computeIfAbsent(to, k -> new ArrayList<>()).add(p));
            Vec3[] pushed = new Vec3[3];
            AirflowImpulsePayload[] impulses = new AirflowImpulsePayload[3];
            List<FakePlayer> players = List.of(plain, suited, shod);
            double[] r = {0, 0.5, 1};
            for (int i = 0; i < 3; i++) {
                FakePlayer p = players.get(i);
                Airflow.syncForTest(s.r.level, p);
                AirflowSyncPayload sync = null;
                for (CustomPacketPayload payload : sent.getOrDefault(p, List.of())) {
                    if (payload instanceof AirflowSyncPayload x) {
                        sync = x;
                    } else if (payload instanceof AirflowImpulsePayload x) {
                        impulses[i] = x;
                    }
                }
                h.assertTrue(sync != null && !sync.openings().isEmpty(), "player " + i + " wasn't sent the breach");
                close(h, sync.resistance(), r[i], "player " + i + "'s resistance sent");
                AirflowReceiver client = new AirflowReceiver();
                client.accept(sync);
                pushed[i] = client.tick(p);
            }
            h.assertTrue(pushed[0] != null && pushed[0].x < -0.01, "the plain player's client didn't push it");
            close(h, pushed[1].x, pushed[0].x / 2, "the suited player's push");
            h.assertTrue(pushed[2] == null, "the anvil-shod player's client pushed it: " + pushed[2]);
            h.assertValueEqual(shod.getDeltaMovement(), Vec3.ZERO, "the anvil-shod player");
            h.assertTrue(impulses[0] != null, "no impulse for the plain player");
            close(h, impulses[1].vx(), impulses[0].vx() / 2, "the suited player's impulse");
            h.assertTrue(impulses[2] == null, "the anvil-shod player got an impulse: " + impulses[2]);
        }
        h.succeed();
    }

    /**
     * Creative players and spectators beside a breach aren't moved (FLOW-018): the push leaves them
     * still, and the server sends them neither the openings nor the impulse, while a survival
     * player beside them gets both.
     */
    @GameTest(template = "empty")
    public static void flow_018_creative_players_and_spectators_aren_t_moved(GameTestHelper h) {
        try (Site s = new Site(h, 118)) {
            s.breach();
            FlowSettings settings = AirflowConfig.settings();
            FlowField field = Airflow.field(s.r.level);
            double mob = AirflowClass.MOB.threshold();
            h.assertTrue(s.difference() >= mob, "the breach is too weak: " + s.difference());
            FakePlayer survival = s.player(HOLE_X + 3);
            FakePlayer creative = s.player(HOLE_X + 3);
            h.assertTrue(creative.setGameMode(GameType.CREATIVE), "the player didn't go creative");
            FakePlayer spectator = s.player(HOLE_X + 3);
            h.assertTrue(spectator.setGameMode(GameType.SPECTATOR), "the player didn't go spectator");
            h.assertTrue(Airflow.exempt(creative) && Airflow.exempt(spectator), "the exemption");
            h.assertFalse(Airflow.exempt(survival), "the survival player is exempt");

            Airflow.apply(survival, field, FlowField.ANY_ZONE, mob, settings);
            Airflow.apply(creative, field, FlowField.ANY_ZONE, mob, settings);
            Airflow.apply(spectator, field, FlowField.ANY_ZONE, mob, settings);
            h.assertTrue(vx(survival) < 0, "the survival player didn't move");
            h.assertValueEqual(creative.getDeltaMovement(), Vec3.ZERO, "the creative player");
            h.assertValueEqual(spectator.getDeltaMovement(), Vec3.ZERO, "the spectator");
            h.assertTrue(Airflow.flowAt(creative, field, FlowField.ANY_ZONE, mob, settings, 0) == null, "flowAt");

            Map<ServerPlayer, List<CustomPacketPayload>> sent = new HashMap<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) ->
                    sent.computeIfAbsent(to, k -> new ArrayList<>()).add(p));
            for (FakePlayer p : List.of(survival, creative, spectator)) {
                Airflow.syncForTest(s.r.level, p);
            }
            List<CustomPacketPayload> toSurvival = sent.getOrDefault(survival, List.of());
            h.assertTrue(
                    toSurvival.stream()
                            .anyMatch(p -> p instanceof AirflowSyncPayload x
                                    && !x.openings().isEmpty()),
                    "the survival player wasn't sent the breach: " + toSurvival);
            h.assertTrue(
                    toSurvival.stream().anyMatch(p -> p instanceof AirflowImpulsePayload),
                    "the survival player got no impulse");
            h.assertTrue(sent.getOrDefault(creative, List.of()).isEmpty(), "sent to the creative player");
            h.assertTrue(sent.getOrDefault(spectator, List.of()).isEmpty(), "sent to the spectator");
        }
        h.succeed();
    }

    /**
     * A player airflow carries towards a hole one block high is put in the swimming pose, by the
     * server from 1.5 blocks off and by its client from 1 block, and let up once it can stand
     * (FLOW-019). A player in the open, or a creative one, isn't; nor is one whose pose something
     * else forces.
     */
    @GameTest(template = "empty")
    public static void flow_019_a_player_carried_into_a_low_hole_crawls(GameTestHelper h) {
        try (Site s = new Site(h, 119)) {
            s.breach(1);
            h.assertTrue(s.difference() >= AirflowClass.MOB.threshold(), "the breach is too weak: " + s.difference());
            BlockPos hole = s.r.abs(HOLE_X, 1, Z);

            // The server, 1.3 blocks from the hole's far face.
            FakePlayer server = s.standing(hole, 1.3);
            h.assertTrue(Airflow.crawlForTest(s.r.level, server), "the server didn't crawl the player");
            h.assertValueEqual(server.getForcedPose(), Pose.SWIMMING, "the server's forced pose");
            h.assertValueEqual(server.getPose(), Pose.SWIMMING, "the server's pose");

            // Inside the hole, it stays down even with no flow; out in the open, it is let up.
            server.moveTo(hole.getX() + 0.5, hole.getY(), hole.getZ() + 0.5);
            h.assertTrue(Airflow.crawl(server, null, true, 1.5), "the player stood up in the hole");
            server.moveTo(hole.getX() + 5.5, hole.getY(), hole.getZ() + 0.5);
            h.assertFalse(Airflow.crawlForTest(s.r.level, server), "the player kept crawling in the open");
            h.assertTrue(server.getForcedPose() == null, "the forced pose wasn't let up");

            // Too far off, in creative, or with a pose something else forces: no crawl.
            FakePlayer far = s.standing(hole, 3.5);
            h.assertFalse(Airflow.crawlForTest(s.r.level, far), "the player crawled 3.5 blocks off");
            FakePlayer creative = s.standing(hole, 1.3);
            h.assertTrue(creative.setGameMode(GameType.CREATIVE), "the player didn't go creative");
            h.assertFalse(Airflow.crawlForTest(s.r.level, creative), "the creative player crawled");
            FakePlayer sleeper = s.standing(hole, 1.3);
            sleeper.setForcedPose(Pose.SLEEPING);
            h.assertFalse(Airflow.crawlForTest(s.r.level, sleeper), "crawled over another forced pose");
            h.assertValueEqual(sleeper.getForcedPose(), Pose.SLEEPING, "the other forced pose");

            // The client, from the synced openings: 1.5 blocks off it waits, 1 block off it crawls.
            List<AirflowSyncPayload> syncs = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> {
                if (p instanceof AirflowSyncPayload x) {
                    syncs.add(x);
                }
            });
            FakePlayer local = s.standing(hole, 1.6);
            Airflow.syncForTest(s.r.level, local);
            h.assertTrue(!syncs.isEmpty() && !syncs.getLast().openings().isEmpty(), "no openings were sent");
            AirflowReceiver client = new AirflowReceiver();
            client.accept(syncs.getLast());
            client.tick(local);
            h.assertFalse(client.crawling(), "the client crawled 1.6 blocks off");
            local.moveTo(hole.getX() + 1.9, hole.getY(), hole.getZ() + 0.5);
            local.setDeltaMovement(Vec3.ZERO);
            client.tick(local);
            h.assertTrue(client.crawling(), "the client didn't crawl 0.9 blocks off");
            h.assertValueEqual(local.getForcedPose(), Pose.SWIMMING, "the client's forced pose");
            client.accept(AirflowSyncPayload.none(s.r.level.dimension().location()));
            local.moveTo(hole.getX() + 5.5, hole.getY(), hole.getZ() + 0.5);
            client.tick(local);
            h.assertFalse(client.crawling(), "the client kept crawling after airflow ended");
            h.assertTrue(local.getForcedPose() == null, "the client's forced pose wasn't let up");
        }
        h.succeed();
    }

    /**
     * A player carried towards a hole one block high in a wall, one block above the floor it
     * stands on, crawls too, for the flow to lift it in (FLOW-019); widened to a doorway two blocks
     * high from the floor, the hole is one it can walk through, and it doesn't.
     */
    @GameTest(template = "empty")
    public static void flow_019_a_player_crawls_into_a_hole_above_the_floor(GameTestHelper h) {
        try (Site s = new Site(h, 120)) {
            s.breach();
            h.assertTrue(s.difference() >= AirflowClass.MOB.threshold(), "the breach is too weak: " + s.difference());
            BlockPos floor = s.r.abs(HOLE_X, Y - 1, Z);
            FakePlayer below = s.standing(floor, 1.3);
            h.assertTrue(Airflow.crawlForTest(s.r.level, below), "the player under the hole didn't crawl");
            h.assertValueEqual(below.getPose(), Pose.SWIMMING, "the pose under the hole");

            s.r.set(HOLE_X, Y - 1, Z, Rooms.AIR);
            FakePlayer door = s.standing(floor, 1.3);
            h.assertFalse(Airflow.crawlForTest(s.r.level, door), "the player crawled into a doorway");
            h.assertTrue(door.getForcedPose() == null, "a forced pose at the doorway");
        }
        h.succeed();
    }

    /**
     * The server tells every player within 32 blocks of a breach where it is and which way it
     * flows, and none beyond (CLIENT-003, DATA-015). It tells them whichever zone they are in, and
     * in creative or spectator mode, though it pushes neither kind.
     */
    @GameTest(template = "empty")
    public static void client_003_players_within_32_blocks_are_sent_the_breach(GameTestHelper h) {
        try (Site s = new Site(h, 121)) {
            s.breach();
            // A sealed room of its own, past the air outside the hole.
            s.r.box(26, 0, 34, 6);
            s.r.settle(GasMutations.remove(
                    s.r.level, s.r.point(30, Y, Z), Map.of(StandardAir.NITROGEN, 1e-3), Rooms.source("airflow")));
            h.assertTrue(s.r.zoned(30, Y, Z), "the sealed room didn't zone");
            Airflow.step(s.r.level);
            long now = s.r.level.getGameTime();

            AirflowEffectsPayload sent = s.show(s.player(HOLE_X + 3), now);
            h.assertTrue(sent != null && sent.flows().size() == 1, "beside the breach: " + sent);
            VisibleFlow flow = sent.flows().get(0);
            h.assertTrue(Math.abs(flow.kpa() - 101) <= 2, "the difference: " + flow.kpa());
            BlockPos hole = s.r.abs(HOLE_X, Y, Z);
            h.assertValueEqual(flow.openings().size(), 1, "the openings of a one-block hole");
            VisibleOpening opening = flow.openings().get(0);
            h.assertTrue(
                    Math.abs(opening.x() - hole.getX()) <= 1
                            && opening.y() == hole.getY()
                            && opening.z() == hole.getZ(),
                    "not at the hole: " + opening);
            h.assertValueEqual(opening.nx(), -1, "the flow's direction, into the room");
            h.assertTrue(
                    flow.molesPerSecond() > 1 && flow.molesPerSecond() < 100,
                    "the flow through the hole: " + flow.molesPerSecond());

            ByteBuf buf = Unpooled.buffer();
            AirflowEffectsPayload.STREAM_CODEC.encode(buf, sent);
            h.assertValueEqual(buf.readableBytes(), 16, "the bytes for one hole");

            h.assertTrue(s.show(s.player(HOLE_X + 28), now) != null, "28 blocks away");
            h.assertTrue(s.show(s.player(HOLE_X - 15), now) != null, "inside the breached room");
            h.assertTrue(s.show(s.player(HOLE_X + 34), now) == null, "34 blocks away");

            FakePlayer walledOff = s.player(30);
            h.assertTrue(s.show(walledOff, now) != null, "in a zone not beside the edge");
            List<CustomPacketPayload> pushes = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> pushes.add(p));
            Airflow.syncForTest(s.r.level, walledOff);
            h.assertTrue(pushes.isEmpty(), "a player in another zone was sent the push: " + pushes);

            FakePlayer creative = s.player(HOLE_X + 3);
            h.assertTrue(creative.setGameMode(GameType.CREATIVE), "the player didn't go creative");
            h.assertTrue(s.show(creative, now) != null, "a creative player");
            FakePlayer spectator = s.player(HOLE_X + 3);
            h.assertTrue(spectator.setGameMode(GameType.SPECTATOR), "the player didn't go spectator");
            h.assertTrue(s.show(spectator, now) != null, "a spectator");
        }
        h.succeed();
    }

    /**
     * An edge at FLOW-011's lowest threshold shows, though it is too weak to move a player
     * (CLIENT-003); one below it doesn't.
     */
    @GameTest(template = "empty")
    public static void client_003_an_edge_shows_from_the_lowest_class_threshold(GameTestHelper h) {
        try (Site weak = new Site(h, 122)) {
            weak.breachKeeping(0.6);
            h.assertTrue(weak.hasOpenings(), "40 kPa has no airflow");
            FakePlayer player = weak.player(HOLE_X + 3);
            AirflowEffectsPayload sent = weak.show(player, weak.r.level.getGameTime());
            h.assertTrue(sent != null && sent.flows().size() == 1, "a 40 kPa edge: " + sent);
            int kpa = sent.flows().get(0).kpa();
            h.assertTrue(kpa > AirflowClass.LIGHT.threshold() && kpa < AirflowClass.MOB.threshold(), "kPa: " + kpa);
            List<CustomPacketPayload> pushes = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> pushes.add(p));
            Airflow.syncForTest(weak.r.level, player);
            h.assertTrue(pushes.isEmpty(), "a 40 kPa edge pushed a player: " + pushes);
        }
        try (Site faint = new Site(h, 123)) {
            faint.breachKeeping(0.9);
            h.assertFalse(faint.hasOpenings(), "10 kPa has airflow");
            FakePlayer player = faint.player(HOLE_X + 3);
            h.assertTrue(faint.show(player, faint.r.level.getGameTime()) == null, "a 10 kPa edge was sent");
        }
        h.succeed();
    }

    /**
     * A steady breach is sent once, and held by the client with no further packets but a rare
     * resend; when it is sealed, the end is sent, and nothing after (DATA-015).
     */
    @GameTest(template = "empty")
    public static void data_015_a_steady_breach_is_held_until_it_ends(GameTestHelper h) {
        try (Site s = new Site(h, 124)) {
            s.breach();
            FakePlayer player = s.player(HOLE_X + 5);
            Object here = s.r.level.dimension().location();
            HeldFlows client = new HeldFlows();
            long sentAt = s.r.level.getGameTime();
            long now = sentAt;
            AirflowEffectsPayload first = s.show(player, now);
            h.assertTrue(first != null && !first.flows().isEmpty(), "the breach wasn't sent: " + first);
            client.accept(first.flows(), here);

            // Kept at a vacuum, the difference holds to the kPa, tick after tick.
            int period = 20;
            for (int i = 0; i < 6; i++) {
                s.emptyRoom();
                Airflow.step(s.r.level);
                h.assertTrue(s.hasOpenings(), "the breach ended on its own");
                now += period;
                for (int t = 0; t < period; t++) {
                    client.tick(here);
                }
                AirflowEffectsPayload later = s.show(player, now);
                h.assertTrue(later == null, "sent again with nothing changed: " + later);
                h.assertValueEqual(client.flows(), first.flows(), "what the client holds");
            }

            // The resend, unchanged.
            AirflowEffectsPayload resend = s.show(player, sentAt + VisibleFlows.RESEND_TICKS);
            h.assertTrue(resend != null, "no resend");
            h.assertValueEqual(resend.flows(), first.flows(), "the resend");

            // Sealed, the edge goes, and the client is told.
            s.r.set(HOLE_X, Y, Z, Rooms.STONE);
            for (int i = 0; i < 6 && s.hasOpenings(); i++) {
                s.r.tick();
                Airflow.step(s.r.level);
            }
            h.assertFalse(s.hasOpenings(), "the sealed hole still has airflow");
            AirflowEffectsPayload end = s.show(player, sentAt + VisibleFlows.RESEND_TICKS + period);
            h.assertTrue(end != null && end.flows().isEmpty(), "the end wasn't sent: " + end);
            client.accept(end.flows(), here);
            h.assertTrue(client.flows().isEmpty(), "the client kept showing it");
            h.assertTrue(s.show(player, sentAt + 100 * VisibleFlows.RESEND_TICKS) == null, "sent after the end");
        }
        h.succeed();
    }

    /**
     * A listed player is told at most once per atmos tick, even if what it should show changes
     * between them, and told at the next one (DATA-015).
     */
    @GameTest(template = "empty")
    public static void data_015_at_most_once_per_atmos_tick(GameTestHelper h) {
        try (Site s = new Site(h, 125)) {
            s.breach();
            FakePlayer player = s.player(HOLE_X + 5);
            List<AirflowEffectsPayload> sent = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> {
                if (to == player && p instanceof AirflowEffectsPayload x) {
                    sent.add(x);
                }
            });
            // Listed for the body of this game tick only, as nothing else runs in it.
            s.r.level.players().add(player);
            try {
                s.r.tick();
                Airflow.step(s.r.level);
                h.assertValueEqual(sent.size(), 1, "payloads at an atmos tick");
                h.assertFalse(sent.get(0).flows().isEmpty(), "the first payload was empty");

                // It moves out of range, but no atmos tick has come.
                Vec3 away = s.r.point(HOLE_X + 40, Y, Z);
                player.moveTo(away.x, away.y, away.z);
                Airflow.step(s.r.level);
                Airflow.step(s.r.level);
                h.assertValueEqual(sent.size(), 1, "payloads with no atmos tick");

                s.r.tick();
                Airflow.step(s.r.level);
                h.assertValueEqual(sent.size(), 2, "payloads at the next atmos tick");
                h.assertTrue(sent.get(1).flows().isEmpty(), "out of range wasn't sent as an end");
            } finally {
                s.r.level.players().remove(player);
            }
        }
        h.succeed();
    }

    /** Airflow switched off ends what clients are showing (FLOW-014, DATA-015). */
    @GameTest(template = "empty")
    public static void data_015_airflow_off_ends_what_clients_show(GameTestHelper h) {
        try (Site s = new Site(h, 126)) {
            s.breach();
            FakePlayer player = s.player(HOLE_X + 5);
            h.assertTrue(s.show(player, s.r.level.getGameTime()) != null, "the breach wasn't sent");
            List<AirflowEffectsPayload> sent = new ArrayList<>();
            Airflow.sendHookForTest((ServerPlayer to, CustomPacketPayload p) -> {
                if (to == player && p instanceof AirflowEffectsPayload x) {
                    sent.add(x);
                }
            });
            s.enabled(false);
            Airflow.step(s.r.level);
            h.assertValueEqual(sent.size(), 1, "payloads when airflow went off");
            h.assertTrue(sent.get(0).flows().isEmpty(), "the end wasn't empty");
        }
        h.succeed();
    }

    /** A respawned or reconnected player is a new object, which holds nothing, so is sent it again. */
    @GameTest(template = "empty")
    public static void data_015_a_new_player_object_is_sent_it_again(GameTestHelper h) {
        try (Site s = new Site(h, 127)) {
            s.breach();
            UUID id = UUID.randomUUID();
            Vec3 at = s.r.point(HOLE_X + 5, Y, Z);
            FakePlayer before = new FakePlayer(s.r.level, new GameProfile(id, "zas_airflow"));
            before.moveTo(at.x, at.y, at.z);
            long now = s.r.level.getGameTime();
            h.assertTrue(s.show(before, now) != null, "the breach wasn't sent");
            h.assertTrue(s.show(before, now) == null, "sent twice");
            FakePlayer after = new FakePlayer(s.r.level, new GameProfile(id, "zas_airflow"));
            after.moveTo(at.x, at.y, at.z);
            h.assertTrue(s.show(after, now) != null, "the new player object wasn't sent it");
        }
        h.succeed();
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) <= 1e-9, what + ": expected " + expected + ", got " + actual);
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

    private static void near(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < 1e-3, what + ": " + actual + ", expected " + expected);
    }

    /**
     * A mob flung into a block takes vanilla's fly-into-wall damage, speed lost × 10 − 3, of the
     * airflow impact type (FLOW-005). The test throws it at set speeds faster than the flow, which
     * so leaves them alone, and moves it as its own tick would.
     */
    @GameTest(template = "empty")
    public static void flow_005_a_mob_flung_into_a_block_takes_the_formula_s_damage(GameTestHelper h) {
        try (Site s = new Site(h, 128)) {
            s.breach();
            s.pillar();
            Pig at09 = s.spawn(EntityType.PIG, PILLAR_X + 1.4);
            Pig at12 = s.spawn(EntityType.PIG, PILLAR_X + 1.4);
            Airflow.step(s.r.level);
            h.assertTrue(vx(at09) < -0.01, "the flow doesn't carry the pig: " + vx(at09));

            at09.setDeltaMovement(-0.9, 0, 0);
            at12.setDeltaMovement(-1.2, 0, 0);
            Airflow.step(s.r.level);
            near(h, at09.getHealth(), 10, "the pig's health in the air");
            at09.move(MoverType.SELF, at09.getDeltaMovement());
            at12.move(MoverType.SELF, at12.getDeltaMovement());
            h.assertTrue(at09.horizontalCollision, "the pig didn't reach the pillar");
            Airflow.step(s.r.level);
            near(h, at09.getHealth(), 10 - (0.9 * 10 - 3), "the health of the pig at 0.9 blocks per tick");
            near(h, at12.getHealth(), 10 - (1.2 * 10 - 3), "the health of the pig at 1.2 blocks per tick");
            h.assertTrue(at09.getLastDamageSource().is(LifeDamage.AIRFLOW_IMPACT), "the damage type");
        }
        h.succeed();
    }

    /**
     * A mob the flow holds against a block isn't hit again for it, however hard the flow presses,
     * nor is one that was against the block when the flow took hold of it. One that comes away and
     * is thrown back is (FLOW-005).
     */
    @GameTest(template = "empty")
    public static void flow_005_a_mob_held_against_a_block_is_hit_once(GameTestHelper h) {
        try (Site s = new Site(h, 129)) {
            s.breach();
            s.pillar();
            Pig pig = s.spawn(EntityType.PIG, PILLAR_X + 1.4);
            Pig touching = s.spawn(EntityType.PIG, PILLAR_X + 0.95);
            Airflow.step(s.r.level);
            s.fling(pig, -0.9);
            s.fling(touching, -0.9);
            near(h, pig.getHealth(), 4, "the pig's health after the first blow");
            near(h, touching.getHealth(), 10, "the pig that was against the pillar from the start");

            for (int i = 0; i < 5; i++) {
                pig.invulnerableTime = 0;
                s.fling(pig, -0.9);
            }
            near(h, pig.getHealth(), 4, "the pig's health held against the pillar");

            pig.invulnerableTime = 0;
            pig.setHealth(pig.getMaxHealth());
            pig.setPos(pig.getX() + 0.45, pig.getY(), pig.getZ());
            s.fling(pig, -0.9);
            near(h, pig.getHealth(), 4, "the pig's health when thrown back");
        }
        h.succeed();
    }

    /** Losing up to 0.3 blocks per tick does no damage, and the hazard's settings apply (FLOW-005). */
    @GameTest(template = "empty")
    public static void flow_005_a_slow_knock_does_nothing_and_settings_apply(GameTestHelper h) {
        try (Site s = new Site(h, 130)) {
            s.breach();
            s.pillar();
            Pig slow = s.spawn(EntityType.PIG, PILLAR_X + 1.05);
            Airflow.step(s.r.level);
            h.assertTrue(
                    vx(slow) < -0.1 && vx(slow) > -FlowImpact.LEAST_SPEED_LOST,
                    "the flow's push isn't a slow knock: " + vx(slow));
            slow.move(MoverType.SELF, slow.getDeltaMovement());
            h.assertTrue(slow.horizontalCollision, "the pig didn't reach the pillar");
            Airflow.step(s.r.level);
            near(h, slow.getHealth(), 10, "the health of the pig that a slow knock hit");

            s.impactScale(0.5);
            Pig halved = s.spawn(EntityType.PIG, PILLAR_X + 1.4);
            Airflow.step(s.r.level);
            s.fling(halved, -0.9);
            near(h, halved.getHealth(), 10 - 3, "the pig's health at a scale of 0.5");

            s.impactEnabled(false);
            Pig off = s.spawn(EntityType.PIG, PILLAR_X + 1.4);
            Airflow.step(s.r.level);
            s.fling(off, -0.9);
            near(h, off.getHealth(), 10, "the pig's health with the hazard off");
        }
        h.succeed();
    }

    /**
     * A mob with its own tick, with nothing but the flow to move it, is carried into the pillar and
     * takes the damage for the speed it had (FLOW-005).
     */
    @GameTest(template = "empty")
    public static void flow_005_the_flow_alone_throws_a_mob_into_a_block(GameTestHelper h) {
        try (Site s = new Site(h, 131)) {
            s.breach();
            s.pillar(HOLE_X + 2);
            Pig pig = s.spawn(EntityType.PIG, HOLE_X + 6);
            pig.setNoAi(false);
            double expected = 0;
            for (int i = 0; i < 80 && pig.getHealth() == pig.getMaxHealth(); i++) {
                Airflow.step(s.r.level);
                if (pig.getHealth() == pig.getMaxHealth()) {
                    Vec3 v = pig.getDeltaMovement();
                    expected = FlowImpact.damage(FlowImpact.speedLost(v.x, v.z, true, false));
                    pig.tick();
                }
            }
            h.assertTrue(
                    pig.horizontalCollision,
                    "the flow didn't carry the pig to the pillar: it's at x "
                            + (pig.getX() - s.r.abs(0, 0, 0).getX())
                            + " at "
                            + pig.getDeltaMovement());
            near(h, pig.getMaxHealth() - pig.getHealth(), expected, "the damage to the pig");
            h.assertTrue(expected > 0, "the flow carried the pig too slowly to hurt it");
            h.assertTrue(pig.getLastDamageSource().is(LifeDamage.AIRFLOW_IMPACT), "the damage type");
        }
        h.succeed();
    }

    /**
     * A player moves on its own client, so the server judges an impact from the moves it receives:
     * the player covers ground, then stops beside a block, and is hit once (FLOW-005). A jump too
     * long for any flow, such as a teleport, isn't speed, and a player that stops with no block
     * beside it isn't hit.
     */
    @GameTest(template = "empty")
    public static void flow_005_a_player_flung_into_a_block_is_judged_by_its_moves(GameTestHelper h) {
        try (Site s = new Site(h, 132)) {
            s.breach();
            s.pillar();
            ServerLevel level = s.r.level;
            FakePlayer player = TestPlayers.hurtable(level, "zas_airflow");
            double wall = s.r.abs(PILLAR_X + 1, 0, 0).getX() + player.getBbWidth() / 2;
            for (int i = 3; i >= 0; i--) {
                s.playerAt(player, wall + i * 0.8);
                near(h, player.getHealth(), player.getMaxHealth(), "the player's health on its way");
            }
            s.playerAt(player, wall);
            near(h, player.getMaxHealth() - player.getHealth(), 0.8 * 10 - 3, "the damage to the player");
            h.assertTrue(player.getLastDamageSource().is(LifeDamage.AIRFLOW_IMPACT), "the damage type");

            float after = player.getHealth();
            for (int i = 0; i < 5; i++) {
                player.invulnerableTime = 0;
                s.playerAt(player, wall);
            }
            near(h, player.getHealth(), after, "the player's health held against the pillar");

            FakePlayer teleported = TestPlayers.hurtable(level, "zas_airflow");
            s.playerAt(teleported, wall + 3.5);
            s.playerAt(teleported, wall + 3.5);
            s.playerAt(teleported, wall);
            s.playerAt(teleported, wall);
            s.playerAt(teleported, wall);
            near(h, teleported.getHealth(), teleported.getMaxHealth(), "the player that teleported to the pillar");

            FakePlayer stopped = TestPlayers.hurtable(level, "zas_airflow");
            for (int i = 0; i < 3; i++) {
                s.playerAt(stopped, wall + 3 - i * 0.8);
            }
            s.playerAt(stopped, wall + 3 - 2 * 0.8);
            near(h, stopped.getHealth(), stopped.getMaxHealth(), "the player that stopped in the open");
        }
        h.succeed();
    }
}
