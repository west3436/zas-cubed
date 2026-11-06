package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.event.HazardTickEvent;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.HazardType;
import io.github.west3436.zas.life.LifeDamage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The hazard tick event (API-033): every hazard that's dealt fires it, a cancelled one deals nothing
 * and a modified one deals the changed amount. Each test runs within one game tick and registers its
 * listeners only for that time, so they hear nothing from other tests. Areas 730 and up ({@link Rooms}).
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HazardTickEventTests {
    private static final ResourceLocation TEST_GAS = ResourceLocation.fromNamespaceAndPath("zas_gametest", "test_gas");

    private HazardTickEventTests() {}

    private static <T extends Mob> T mob(GameTestHelper h, Rooms r, EntityType<T> type) {
        T e = type.create(r.level);
        h.assertTrue(e != null, "couldn't create a " + type);
        e.moveTo(Vec3.atBottomCenterOf(r.abs(2, 1, 2)));
        e.setNoAi(true);
        r.level.addFreshEntity(e);
        return e;
    }

    /** A cell of nitrogen at a pressure in kPa and a temperature. */
    private static GasMixture at(double kPa, double temperature) {
        double moles = kPa * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * temperature);
        return new GasMixture(Map.of(StandardAir.NITROGEN, moles), temperature, StandardAir.CELL_VOLUME);
    }

    /** A breath of the test gas at 3 kPa, which its profile makes Glowing and 1 HP/s once 6 s have passed. */
    private static GasMixture testGas() {
        double moles = 3 * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        return new GasMixture(Map.of(TEST_GAS, moles), StandardAir.TEMPERATURE, StandardAir.CELL_VOLUME);
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    /** Runs a body with a listener on the bus for just that time, hearing only one entity. */
    private static void listening(
            LivingEntity entity, EventPriority priority, Consumer<HazardTickEvent> listener, Runnable body) {
        Consumer<HazardTickEvent> mine = e -> {
            if (e.entity() == entity) {
                listener.accept(e);
            }
        };
        NeoForge.EVENT_BUS.addListener(priority, true, HazardTickEvent.class, mine);
        try {
            body.run();
        } finally {
            NeoForge.EVENT_BUS.unregister(mine);
        }
    }

    /** One of the four hazards a check's exposure deals, at conditions where it alone deals 1 HP/s. */
    private record Case(Hazard hazard, GasMixture around, ResourceKey<DamageType> damageType) {}

    private static List<Case> exposures() {
        return List.of(
                new Case(Hazard.LOW_PRESSURE, at(15, StandardAir.TEMPERATURE), LifeDamage.DECOMPRESSION),
                new Case(Hazard.HIGH_PRESSURE, at(825, StandardAir.TEMPERATURE), LifeDamage.BAROTRAUMA),
                new Case(Hazard.COLD, at(StandardAir.PRESSURE, 150), LifeDamage.FROSTBITE),
                new Case(Hazard.HEAT, at(StandardAir.PRESSURE, 370), LifeDamage.BURNS));
    }

    /**
     * A cancelled tick of each hazard a check's exposure deals does no damage, and an uncancelled one
     * deals what it says. The event names the hazard and its damage, on the server thread (API-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_033_a_cancelled_pressure_or_temperature_tick_deals_no_damage(GameTestHelper h) {
        Rooms r = new Rooms(h, 730);
        for (Case c : exposures()) {
            Zombie spared = mob(h, r, EntityType.ZOMBIE);
            Zombie plain = mob(h, r, EntityType.ZOMBIE);
            List<HazardTickEvent> heard = new ArrayList<>();
            boolean[] onServerThread = {true};
            try {
                listening(
                        spared,
                        EventPriority.NORMAL,
                        e -> {
                            heard.add(e);
                            onServerThread[0] &= Thread.currentThread()
                                    == r.level.getServer().getRunningThread();
                            e.setCanceled(true);
                        },
                        () -> Breathers.exposeForTest(spared, c.around(), 2));
                Breathers.exposeForTest(plain, c.around(), 2);
                h.assertValueEqual(heard.size(), 1, c.hazard() + "'s ticks");
                h.assertTrue(onServerThread[0], c.hazard() + " not fired on the server thread");
                h.assertValueEqual(heard.get(0).hazard(), c.hazard(), "the event's hazard");
                close(h, heard.get(0).originalDamage(), 2, 1e-9, c.hazard() + "'s damage");
                close(h, lost(spared), 0, 0, c.hazard() + " cancelled");
                close(h, lost(plain), 2, 1e-4, c.hazard() + " not cancelled");
                h.assertTrue(plain.getLastDamageSource().is(c.damageType()), c.hazard() + "'s damage type");
            } finally {
                spared.discard();
                plain.discard();
            }
        }
        h.succeed();
    }

    /** A modified tick of each hazard a check's exposure deals deals the changed amount. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_033_a_modified_pressure_or_temperature_tick_deals_the_changed_amount(GameTestHelper h) {
        Rooms r = new Rooms(h, 731);
        for (Case c : exposures()) {
            Zombie z = mob(h, r, EntityType.ZOMBIE);
            try {
                listening(
                        z,
                        EventPriority.NORMAL,
                        e -> e.setDamage(e.damage() * 0.25),
                        () -> Breathers.exposeForTest(z, c.around(), 2));
                close(h, lost(z), 0.5, 1e-4, c.hazard() + " at a quarter");
                h.assertTrue(z.getLastDamageSource().is(c.damageType()), c.hazard() + "'s damage type");
            } finally {
                z.discard();
            }
        }
        h.succeed();
    }

    /**
     * A cancelled toxic gas tick does no damage and applies no effect; a modified one deals the changed
     * amount and still applies its effect, and so does one set to 0.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_033_toxic_gas_ticks_cancel_and_modify(GameTestHelper h) {
        Rooms r = new Rooms(h, 732);
        Zombie cancelled = mob(h, r, EntityType.ZOMBIE);
        Zombie modified = mob(h, r, EntityType.ZOMBIE);
        Zombie zeroed = mob(h, r, EntityType.ZOMBIE);
        Zombie plain = mob(h, r, EntityType.ZOMBIE);
        try {
            List<HazardTickEvent> heard = new ArrayList<>();
            listening(
                    cancelled,
                    EventPriority.NORMAL,
                    e -> {
                        heard.add(e);
                        e.setCanceled(true);
                    },
                    () -> Breathers.breatheForTest(cancelled, testGas(), 6));
            h.assertValueEqual(heard.size(), 1, "the toxic gas's ticks");
            h.assertValueEqual(heard.get(0).hazard(), Hazard.TOXIC_GAS, "the event's hazard");
            close(h, heard.get(0).originalDamage(), 6, 1e-9, "its damage");
            close(h, lost(cancelled), 0, 0, "damage when cancelled");
            h.assertFalse(cancelled.hasEffect(MobEffects.GLOWING), "its effect when cancelled");

            listening(
                    modified,
                    EventPriority.NORMAL,
                    e -> e.setDamage(1.5),
                    () -> Breathers.breatheForTest(modified, testGas(), 6));
            close(h, lost(modified), 1.5, 1e-4, "damage when modified");
            h.assertTrue(modified.hasEffect(MobEffects.GLOWING), "its effect when modified");
            h.assertTrue(modified.getLastDamageSource().is(LifeDamage.TOXIC_GAS), "the damage type");

            listening(
                    zeroed,
                    EventPriority.NORMAL,
                    e -> e.setDamage(0),
                    () -> Breathers.breatheForTest(zeroed, testGas(), 6));
            close(h, lost(zeroed), 0, 0, "damage when set to 0");
            h.assertTrue(zeroed.hasEffect(MobEffects.GLOWING), "its effect when set to 0");

            Breathers.breatheForTest(plain, testGas(), 6);
            close(h, lost(plain), 6, 1e-4, "damage with no listener");
        } finally {
            cancelled.discard();
            modified.discard();
            zeroed.discard();
            plain.discard();
        }
        h.succeed();
    }

    /** A cancelled hypoxia tick does no damage, a modified one deals the changed amount, and the meter stays empty. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_033_hypoxia_ticks_cancel_and_modify(GameTestHelper h) {
        Rooms r = new Rooms(h, 733);
        r.clear(0, 5);
        r.shell(0, 4);
        r.settle(GasMutations.remove(
                r.level,
                r.point(2, 2, 2),
                Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                Rooms.source("api_033")));
        // A room at a livable pressure and no oxygen, so only hypoxia harms.
        double moles = StandardAir.PRESSURE * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        r.settle(GasMutations.add(
                r.level,
                r.point(2, 2, 2),
                new GasParcel(Map.of(StandardAir.NITROGEN, moles), StandardAir.TEMPERATURE),
                Rooms.source("api_033")));
        Pig pig = mob(h, r, EntityType.PIG);
        try {
            for (int i = 0; i < 300; i++) {
                pig.baseTick();
            }
            h.assertValueEqual(pig.getAirSupply(), 0, "the meter after 15 s");
            float full = pig.getHealth();

            List<HazardTickEvent> heard = new ArrayList<>();
            listening(
                    pig,
                    EventPriority.NORMAL,
                    e -> {
                        heard.add(e);
                        e.setCanceled(true);
                    },
                    () -> {
                        for (int i = 0; i < 40; i++) {
                            pig.baseTick();
                        }
                    });
            h.assertValueEqual(heard.size(), 2, "hypoxia's ticks in 2 s");
            h.assertValueEqual(heard.get(0).hazard(), Hazard.HYPOXIA, "the event's hazard");
            close(h, heard.get(0).originalDamage(), 2, 1e-9, "its damage");
            close(h, pig.getHealth(), full, 0, "health when cancelled");
            h.assertValueEqual(pig.getAirSupply(), 0, "the meter stays empty");

            listening(pig, EventPriority.NORMAL, e -> e.setDamage(0.5), () -> {
                for (int i = 0; i < 20; i++) {
                    pig.baseTick();
                }
            });
            close(h, pig.getHealth(), full - 0.5, 1e-4, "health when modified");
            h.assertTrue(pig.getLastDamageSource().is(LifeDamage.HYPOXIA), "the damage type");
        } finally {
            pig.discard();
        }
        h.succeed();
    }

    /**
     * The call airflow impact is dealt through (FLOW-005) fires the same event: cancelled it deals
     * nothing, modified it deals the changed amount, and with no damage to deal it fires nothing. Pigs,
     * since armor applies to this hazard and a zombie has some of its own.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_033_airflow_impact_ticks_cancel_and_modify(GameTestHelper h) {
        Rooms r = new Rooms(h, 736);
        Pig cancelled = mob(h, r, EntityType.PIG);
        Pig modified = mob(h, r, EntityType.PIG);
        try {
            List<HazardTickEvent> heard = new ArrayList<>();
            double[] dealt = {-1};
            listening(
                    cancelled,
                    EventPriority.NORMAL,
                    e -> {
                        heard.add(e);
                        e.setCanceled(true);
                    },
                    () -> {
                        LifeDamage.deal(cancelled, HazardType.AIRFLOW_IMPACT, 0);
                        dealt[0] = LifeDamage.deal(cancelled, HazardType.AIRFLOW_IMPACT, 3);
                    });
            h.assertValueEqual(heard.size(), 1, "the ticks, with 0 damage asked for once");
            h.assertValueEqual(heard.get(0).hazard(), Hazard.AIRFLOW_IMPACT, "the event's hazard");
            close(h, dealt[0], 0, 0, "what a cancelled tick says it dealt");
            close(h, lost(cancelled), 0, 0, "damage when cancelled");

            listening(modified, EventPriority.NORMAL, e -> e.setDamage(1), () -> {
                dealt[0] = LifeDamage.deal(modified, HazardType.AIRFLOW_IMPACT, 3);
            });
            close(h, dealt[0], 1, 0, "what a modified tick says it dealt");
            close(h, lost(modified), 1, 1e-4, "damage when modified");
            h.assertTrue(modified.getLastDamageSource().is(LifeDamage.AIRFLOW_IMPACT), "the damage type");
        } finally {
            cancelled.discard();
            modified.discard();
        }
        h.succeed();
    }

    /**
     * Listeners run in priority order on the same event: a later one sees an earlier one's change, one
     * that doesn't ask to hear cancelled events doesn't, one that does can take the cancelling back.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void api_033_listeners_share_the_tick_in_priority_order(GameTestHelper h) {
        Rooms r = new Rooms(h, 734);
        Zombie z = mob(h, r, EntityType.ZOMBIE);
        Consumer<HazardTickEvent> first = e -> {
            if (e.entity() == z) {
                e.setDamage(1);
                e.setCanceled(true);
            }
        };
        boolean[] plainHeard = {false};
        Consumer<HazardTickEvent> plain = e -> {
            if (e.entity() == z) {
                plainHeard[0] = true;
            }
        };
        double[] seen = {-1};
        Consumer<HazardTickEvent> last = e -> {
            if (e.entity() == z) {
                seen[0] = e.damage();
                e.setCanceled(false);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HazardTickEvent.class, first);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, HazardTickEvent.class, plain);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, HazardTickEvent.class, last);
        try {
            Breathers.exposeForTest(z, at(15, StandardAir.TEMPERATURE), 2);
            h.assertFalse(plainHeard[0], "a listener heard a cancelled tick it didn't ask for");
            close(h, seen[0], 1, 0, "what the last listener saw");
            close(h, lost(z), 1, 1e-4, "the damage once the cancelling is taken back");
        } finally {
            NeoForge.EVENT_BUS.unregister(first);
            NeoForge.EVENT_BUS.unregister(plain);
            NeoForge.EVENT_BUS.unregister(last);
            z.discard();
        }
        h.succeed();
    }

    /**
     * A listener that throws is skipped for that tick (NFR-013), here by setting damage that can't be:
     * the damage stands as the listeners before it left it, the listeners after it still hear it, and
     * one error is logged a minute.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nfr_013_a_hazard_tick_listener_that_throws_is_skipped_and_logged_once_a_minute(
            GameTestHelper h) {
        Rooms r = new Rooms(h, 735);
        Zombie z = mob(h, r, EntityType.ZOMBIE);
        int[] after = {0};
        Consumer<HazardTickEvent> first = e -> {
            if (e.entity() == z) {
                e.setDamage(e.damage() / 2);
            }
        };
        Consumer<HazardTickEvent> bad = e -> {
            if (e.entity() == z) {
                e.setDamage(Double.NaN);
            }
        };
        Consumer<HazardTickEvent> last = e -> {
            if (e.entity() == z) {
                after[0]++;
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, HazardTickEvent.class, first);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, HazardTickEvent.class, bad);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, HazardTickEvent.class, last);
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            for (int i = 1; i <= 3; i++) {
                Breathers.exposeForTest(z, at(15, StandardAir.TEMPERATURE), 2);
                h.assertValueEqual(after[0], i, "what the listener after it heard");
                close(h, lost(z), i, 1e-4, "the damage after " + i + " ticks: half of 2 HP each");
            }
            h.assertValueEqual(log.naming("HazardTickEvent"), 1, "errors logged about it");

            FaultLog.aMinutePasses();
            Breathers.exposeForTest(z, at(15, StandardAir.TEMPERATURE), 2);
            h.assertValueEqual(log.naming("HazardTickEvent"), 2, "errors logged about it a minute later");
        } finally {
            NeoForge.EVENT_BUS.unregister(first);
            NeoForge.EVENT_BUS.unregister(bad);
            NeoForge.EVENT_BUS.unregister(last);
            z.discard();
        }
        h.succeed();
    }
}
