package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.ProtectionConfig;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Protections;
import io.github.west3436.zas.life.Shielding;
import io.github.west3436.zas.life.WornProtection;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Life support and internal supplies that throw (NFR-013), with the faulty items and entity tags of
 * {@link TestLifeSupport}. Each test builds in its own area ({@link Rooms}, areas 720 and up), and
 * runs within one game tick, so no other test moves the fault log's clock while it runs.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LifeSupportFaultTests {
    private static final String PIECE = "Life support of item " + TestLifeSupport.FAULTY_PIECE_ID;
    private static final String PART = "Life support of item " + TestLifeSupport.FAULTY_PART_ID;
    private static final String LOOKUP = "Life support of item " + TestLifeSupport.FAULTY_LOOKUP_ID;
    private static final String ZOMBIE = "Life support of entity minecraft:zombie";
    private static final String TANK = "Internal supply of item " + TestLifeSupport.FAULTY_TANK_ID;

    private LifeSupportFaultTests() {}

    /** A sealed room from x 0 to 4, of nitrogen at 15 kPa: enough for decompression at 1 HP/s, and nothing else. */
    private static void thinRoom(Rooms r) {
        r.clear(0, 5);
        r.shell(0, 4);
        r.settle(GasMutations.remove(
                r.level,
                r.point(2, 2, 2),
                Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                Rooms.source("nfr_013")));
        double moles = 15.0 * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        r.settle(GasMutations.add(
                r.level,
                r.point(2, 2, 2),
                new GasParcel(Map.of(StandardAir.NITROGEN, moles), StandardAir.TEMPERATURE),
                Rooms.source("nfr_013")));
    }

    private static Zombie zombie(GameTestHelper h, Rooms r) {
        Zombie z = EntityType.ZOMBIE.create(r.level);
        h.assertTrue(z != null, "couldn't create a zombie");
        z.moveTo(Vec3.atBottomCenterOf(r.abs(2, 1, 2)));
        z.setNoAi(true);
        r.level.addFreshEntity(z);
        return z;
    }

    private static FakePlayer player(Rooms r) {
        FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_nfr_013"));
        p.moveTo(Vec3.atBottomCenterOf(r.abs(2, 1, 2)));
        return p;
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    /**
     * A suit each of whose parts throws gives nothing: its wearer takes what a bare zombie takes, and
     * has no seal, filter, airflow resistance or tank, while a good piece worn beside it still counts.
     * Four checks log one error about it, and one more a minute later (NFR-013).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nfr_013_a_life_support_that_throws_is_skipped_and_logged_once_a_minute(GameTestHelper h) {
        Rooms r = new Rooms(h, 720);
        thinRoom(r);
        Zombie bare = zombie(h, r);
        Zombie faulty = zombie(h, r);
        Zombie mixed = zombie(h, r);
        for (EquipmentSlot slot : ProtectionConfig.SLOTS) {
            faulty.setItemSlot(slot, new ItemStack(TestLifeSupport.faultyPiece()));
        }
        mixed.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.faultyPiece()));
        mixed.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TestLifeSupport.item()));
        FakePlayer p = player(r);
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(TestLifeSupport.faultyPiece()));
        p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 1.0), 40));
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            long t = r.level.getGameTime();
            for (int i = 0; i < 4; i++) {
                Breathers.checkAt(bare, t + 40L * i);
                Breathers.checkAt(faulty, t + 40L * i);
            }
            close(h, lost(bare), 6, 1e-4, "the bare zombie's damage in 6 s");
            close(h, lost(faulty), lost(bare), 1e-4, "the faulty suit's wearer's damage");
            h.assertFalse(WornProtection.sealedBreathing(faulty), "its seal");
            h.assertFalse(WornProtection.gasFiltering(faulty), "its filter");
            close(h, WornProtection.airflowResistance(faulty), 0, 0, "its airflow resistance");
            h.assertTrue(Internals.find(faulty).isEmpty(), "its supply");

            double chest = ProtectionConfig.coverage(EquipmentSlot.CHEST);
            close(h, Shielding.of(mixed).pressureFactor(15), 1 - Math.pow(chest, 5), 1e-12, "beside a good piece");
            close(h, Internals.find(p).orElseThrow().distributionPressure(), 40, 0, "the next tank along");
            h.assertValueEqual(log.naming(PIECE), 1, "errors logged about it");

            FaultLog.aMinutePasses();
            Breathers.checkAt(faulty, t + 160);
            h.assertValueEqual(log.naming(PIECE), 2, "errors logged about it a minute later");
            close(h, lost(faulty), lost(bare) + 2, 1e-4, "its damage is still dealt");

            Zombie own = zombie(h, r);
            own.addTag(TestLifeSupport.FAULTY_TAG);
            h.assertFalse(WornProtection.sealedBreathing(own), "an entity's own faulty seal");
            h.assertValueEqual(log.naming(ZOMBIE), 1, "errors logged about an entity's own");
            own.discard();
        } finally {
            bare.discard();
            faulty.discard();
            mixed.discard();
        }
        h.succeed();
    }

    /** One part that throws gives its default, and the others still count (NFR-013). */
    @GameTest(template = "empty")
    public static void nfr_013_a_life_supports_other_parts_count_when_one_throws(GameTestHelper h) {
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            ILifeSupport part = Protections.of(new ItemStack(TestLifeSupport.faultyPart()));
            h.assertTrue(part != null, "the capability");
            close(h, part.minPressure(), StandardAir.PRESSURE, 0, "the part that throws");
            h.assertTrue(part.sealedBreathing(), "the part that doesn't");
            h.assertValueEqual(log.naming(PART), 1, "errors logged about it");
        }
        h.succeed();
    }

    /**
     * A lookup that throws counts as no capability, for an item and an entity, so data naming the item
     * gives its protection for that call, the next tank along is found, and one error is logged for
     * each source, and one more a minute later (NFR-013).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nfr_013_a_life_support_lookup_that_throws_counts_as_none(GameTestHelper h) {
        Rooms r = new Rooms(h, 721);
        ItemStack lookup = new ItemStack(TestLifeSupport.faultyLookup());
        Zombie z = zombie(h, r);
        z.addTag(TestLifeSupport.FAULTY_LOOKUP_TAG);
        FakePlayer p = player(r);
        p.setItemSlot(EquipmentSlot.CHEST, lookup.copy());
        p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 1.0), 40));
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            for (int i = 0; i < 3; i++) {
                ILifeSupport data = Protections.of(lookup);
                h.assertTrue(data != null, "the data naming the item");
                close(h, data.airflowResistance(), 0.75, 0, "the data's airflow resistance");
                h.assertTrue(Protections.of(z) == null, "an entity whose lookup throws");
            }
            close(h, Internals.find(p).orElseThrow().distributionPressure(), 40, 0, "the next tank along");
            h.assertValueEqual(log.naming(LOOKUP), 1, "errors logged about the item");
            h.assertValueEqual(log.naming(ZOMBIE), 1, "errors logged about the entity");

            FaultLog.aMinutePasses();
            Protections.of(lookup);
            Protections.of(z);
            h.assertValueEqual(log.naming(LOOKUP), 2, "errors logged about the item a minute later");
            h.assertValueEqual(log.naming(ZOMBIE), 2, "errors logged about the entity a minute later");
        } finally {
            z.discard();
        }
        h.succeed();
    }

    /**
     * A tank that throws delivers nothing, so the player breathes the air; one whose draw throws gives
     * no gas and loses none. One error is logged, one more a minute later, and the tank works again as
     * soon as it stops throwing (NFR-013).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nfr_013_an_internal_supply_that_throws_is_skipped_and_logged_once_a_minute(GameTestHelper h) {
        Rooms r = new Rooms(h, 722);
        FakePlayer p = player(r);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(TestLifeSupport.faultyTank()));
        Internals.set(p, true);
        TestLifeSupport.FaultySupply tank = TestLifeSupport.FAULTY_TANK;
        tank.refill();
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            h.assertTrue(Internals.active(p) != null, "the tank before it throws");

            tank.throwing(true, false, false);
            long t = r.level.getGameTime();
            for (int i = 0; i < 3; i++) {
                Breathers.checkAt(p, t + 40L * i);
                h.assertTrue(Internals.active(p) == null, "a tank whose contents throw delivered");
                h.assertValueEqual(
                        BreathingRules.assess(p).verdict().source(), BreathSource.ATMOSPHERE, "the breath's source");
            }

            tank.throwing(false, true, false);
            h.assertTrue(Internals.active(p) == null, "a tank whose distribution pressure throws delivered");
            h.assertTrue(Internals.status(p) != null, "the status of a tank that throws");

            tank.throwing(false, false, true);
            Internals.Active active = Internals.active(p);
            h.assertTrue(active != null, "a tank whose draw throws still offers its gas");
            h.assertTrue(Internals.breathe(r.level, p, active.supply(), 60).isEmpty(), "a draw that threw gave gas");
            close(h, tank.contents().totalMoles(), 1, 0, "the gas in a tank whose draw threw");
            h.assertValueEqual(log.naming(TANK), 1, "errors logged about it");

            FaultLog.aMinutePasses();
            Internals.breathe(r.level, p, active.supply(), 60);
            h.assertValueEqual(log.naming(TANK), 2, "errors logged about it a minute later");

            tank.throwing(false, false, false);
            h.assertFalse(Internals.breathe(r.level, p, active.supply(), 60).isEmpty(), "once it stops throwing");
            h.assertTrue(tank.contents().totalMoles() < 1, "the tank gave nothing once it stopped throwing");
            h.assertValueEqual(log.naming(TANK), 2, "errors logged about it once it stops");
        } finally {
            tank.throwing(false, false, false);
            tank.refill();
        }
        h.succeed();
    }
}
