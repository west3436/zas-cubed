package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.life.HazardHandler;
import io.github.west3436.zas.api.life.HazardHandlers;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.Internals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hazard handlers (LIFE-032), which another mod registers for a gas or a gas tag, and the guard around
 * them (NFR-013). Each test registers its handlers only while it runs, within one game tick, so no
 * other test's entity reaches them. It drives the checks itself with {@link Breathers#checkAt}, in its
 * own area ({@link Rooms}, areas 740 and up).
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HazardHandlerTests {
    private static final ResourceLocation TEST_GAS = ResourceLocation.fromNamespaceAndPath("zas_gametest", "test_gas");
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation N2O = ResourceLocation.fromNamespaceAndPath("zas", "nitrous_oxide");
    private static final ResourceLocation HANDLER = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "life_032");
    private static final ResourceLocation OTHER =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "life_032_other");

    private static final double N2_KPA = StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION;
    private static final double O2_KPA = StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION;

    private HazardHandlerTests() {}

    /** A sealed room from x0 to x0 + 4 holding standard air and these partial pressures in kPa, at 293.15 K. */
    private static void room(Rooms r, int x0, Map<ResourceLocation, Double> kPa) {
        r.clear(x0, x0 + 5);
        r.shell(x0, x0 + 4);
        r.settle(GasMutations.remove(
                r.level,
                r.point(x0 + 2, 2, 2),
                Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                Rooms.source("life_032")));
        Map<ResourceLocation, Double> moles = new HashMap<>();
        moles.put(StandardAir.NITROGEN, moles(N2_KPA));
        moles.put(StandardAir.OXYGEN, moles(O2_KPA));
        kPa.forEach((gas, p) -> moles.put(gas, moles(p)));
        r.settle(GasMutations.add(
                r.level,
                r.point(x0 + 2, 2, 2),
                new GasParcel(moles, StandardAir.TEMPERATURE),
                Rooms.source("life_032")));
    }

    /** The moles for a partial pressure in a 27-cell room at 293.15 K. */
    private static double moles(double kPa) {
        return kPa * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
    }

    private static <T extends Mob> T mob(GameTestHelper h, Rooms r, EntityType<T> type, int x0) {
        T e = type.create(r.level);
        h.assertTrue(e != null, "couldn't create a " + type);
        e.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
        e.setNoAi(true);
        r.level.addFreshEntity(e);
        return e;
    }

    private static FakePlayer player(Rooms r, int x0) {
        FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_life_032"));
        p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
        return p;
    }

    /** Two checks 2 s apart; the second charges 2 s. */
    private static void twoSeconds(LivingEntity e, long t) {
        Breathers.checkAt(e, t);
        Breathers.checkAt(e, t + 40);
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(Math.abs(actual - expected) <= tolerance, what + ": expected " + expected + ", got " + actual);
    }

    /** Runs a body with a handler registered for just that time. */
    private static void withGas(ResourceLocation gas, HazardHandler handler, Runnable body) {
        HazardHandlers.register(HANDLER, gas, handler);
        try {
            body.run();
        } finally {
            HazardHandlers.unregister(HANDLER);
        }
    }

    /**
     * A handler for a test gas applies at a check, with what it needs to know: to a breather in the
     * gas, whose breath holds it, and to a zombie, which doesn't breathe but has it around it. It deals
     * its own damage, on the server thread, for the seconds the check charges. In a room without the gas
     * it's not called, and once unregistered it's not called (LIFE-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_032_a_handler_for_a_gas_applies(GameTestHelper h) {
        Rooms r = new Rooms(h, 740);
        room(r, 0, Map.of(TEST_GAS, 1.0));
        room(r, 6, Map.of());
        Pig pig = mob(h, r, EntityType.PIG, 0);
        Zombie zombie = mob(h, r, EntityType.ZOMBIE, 0);
        Pig clean = mob(h, r, EntityType.PIG, 6);
        List<HazardExposure> seen = new ArrayList<>();
        boolean[] onServerThread = {true};
        long t = r.level.getGameTime();
        try {
            withGas(
                    TEST_GAS,
                    e -> {
                        seen.add(e);
                        onServerThread[0] &=
                                Thread.currentThread() == r.level.getServer().getRunningThread();
                        e.entity().hurt(r.level.damageSources().magic(), (float) (1.5 * e.seconds()));
                    },
                    () -> {
                        twoSeconds(pig, t);
                        twoSeconds(zombie, t);
                        twoSeconds(clean, t);
                    });
            h.assertValueEqual(seen.size(), 2, "the calls: one each for the two in the gas");
            h.assertTrue(onServerThread[0], "not called on the server thread");

            HazardExposure breather = seen.get(0);
            h.assertTrue(breather.entity() == pig, "the first call's entity");
            h.assertValueEqual(breather.gas(), TEST_GAS, "the gas");
            close(h, breather.seconds(), 2, 0, "the seconds");
            close(h, breather.surroundingPressure(), 1, 1e-6, "the pressure around the pig");
            close(h, breather.breathedPressure(), 1, 1e-6, "the pressure the pig breathes");
            h.assertTrue(breather.verdict().breath().isPresent(), "the pig's breath");

            HazardExposure nonBreather = seen.get(1);
            h.assertTrue(nonBreather.entity() == zombie, "the second call's entity");
            close(h, nonBreather.surroundingPressure(), 1, 1e-6, "the pressure around the zombie");
            close(h, nonBreather.breathedPressure(), 0, 0, "the pressure the zombie breathes");

            close(h, pig.getMaxHealth() - pig.getHealth(), 3, 1e-4, "the handler's damage to the pig");
            close(h, clean.getMaxHealth() - clean.getHealth(), 0, 0, "the pig in clean air");

            seen.clear();
            Breathers.checkAt(pig, t + 80);
            h.assertValueEqual(seen.size(), 0, "the calls once unregistered");
        } finally {
            pig.discard();
            zombie.discard();
            clean.discard();
        }
        h.succeed();
    }

    /**
     * A handler for a tag is called once for each of the tag's gases in the breath or around, with
     * that gas, and for no gas outside it. A gas named by one handler and in another's tag calls both,
     * in order of id (LIFE-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_032_a_handler_for_a_tag_applies_to_each_gas_in_it(GameTestHelper h) {
        Rooms r = new Rooms(h, 741);
        room(r, 0, Map.of(CO, 0.01, N2O, 0.01));
        room(r, 6, Map.of());
        Pig pig = mob(h, r, EntityType.PIG, 0);
        Pig clean = mob(h, r, EntityType.PIG, 6);
        List<String> calls = new ArrayList<>();
        long t = r.level.getGameTime();
        HazardHandlers.register(HANDLER, GasTags.TOXIC, e -> calls.add(HANDLER.getPath() + " " + e.gas()));
        HazardHandlers.register(OTHER, CO, e -> calls.add(OTHER.getPath() + " " + e.gas()));
        try {
            twoSeconds(pig, t);
            twoSeconds(clean, t);
            h.assertValueEqual(
                    calls,
                    List.of(HANDLER.getPath() + " " + CO, HANDLER.getPath() + " " + N2O, OTHER.getPath() + " " + CO),
                    "the calls, for each gas of the tag");
        } finally {
            HazardHandlers.unregister(HANDLER);
            HazardHandlers.unregister(OTHER);
            pig.discard();
            clean.discard();
        }
        h.succeed();
    }

    /**
     * A handler hears of a gas whichever way it reaches the entity. Behind a filter, a toxic gas is
     * around a player but not in its breath; from a tank, a gas is in its breath and not around it
     * (LIFE-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_032_a_handler_sees_what_protection_leaves_in_the_breath(GameTestHelper h) {
        Rooms r = new Rooms(h, 742);
        room(r, 0, Map.of(CO, 0.01));
        room(r, 6, Map.of());
        List<HazardExposure> seen = new ArrayList<>();
        long t = r.level.getGameTime();

        FakePlayer filtered = player(r, 0);
        filtered.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        withGas(CO, seen::add, () -> twoSeconds(filtered, t));
        h.assertValueEqual(seen.size(), 1, "the calls behind a filter");
        close(h, seen.get(0).surroundingPressure(), 0.01, 1e-6, "the gas around it");
        close(h, seen.get(0).breathedPressure(), 0, 0, "the gas in its filtered breath");

        seen.clear();
        FakePlayer onTank = player(r, 6);
        onTank.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        onTank.setItemInHand(
                InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 0.9, TEST_GAS, 0.1), 21.3));
        Internals.set(onTank, true);
        withGas(TEST_GAS, seen::add, () -> twoSeconds(onTank, t));
        h.assertValueEqual(seen.size(), 1, "the calls on a tank");
        close(h, seen.get(0).surroundingPressure(), 0, 0, "the tank's gas around it");
        h.assertTrue(seen.get(0).breathedPressure() > 0, "the tank's gas wasn't in its breath");
        h.succeed();
    }

    /**
     * With its eyes in a fluid an entity has no atmosphere around it, so a handler hears only of what's
     * in its breath: a tank's gas for a player on internals, and nothing for a pig drowning beside it,
     * or for the room's gas (LIFE-032, LIFE-047).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_032_a_handler_hears_of_a_tanks_gas_with_eyes_in_a_fluid(GameTestHelper h) {
        Rooms r = new Rooms(h, 745);
        room(r, 0, Map.of(CO, 0.01));
        r.set(2, 1, 2, Blocks.WATER.defaultBlockState());
        r.set(2, 2, 2, Blocks.WATER.defaultBlockState());
        FakePlayer diver = player(r, 0);
        diver.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        diver.setItemInHand(
                InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(StandardAir.OXYGEN, 0.9, TEST_GAS, 0.1), 21.3));
        Internals.set(diver, true);
        Pig pig = mob(h, r, EntityType.PIG, 0);
        // A tick finds what their eyes are in.
        diver.baseTick();
        pig.baseTick();
        List<HazardExposure> seen = new ArrayList<>();
        long t = r.level.getGameTime();
        HazardHandlers.register(HANDLER, TEST_GAS, seen::add);
        HazardHandlers.register(OTHER, CO, seen::add);
        try {
            twoSeconds(diver, t);
            twoSeconds(pig, t);
            h.assertValueEqual(
                    pig.getData(Breathers.STATE).verdict().source(), BreathSource.FLUID, "the pig's verdict");
            h.assertValueEqual(seen.size(), 1, "the calls: the tank's gas alone");
            HazardExposure exposure = seen.get(0);
            h.assertTrue(exposure.entity() == diver, "the call's entity");
            h.assertValueEqual(exposure.gas(), TEST_GAS, "the gas");
            h.assertValueEqual(exposure.verdict().source(), BreathSource.INTERNALS, "the diver's verdict");
            h.assertTrue(exposure.around().isEmpty(), "an atmosphere at its eyes");
            close(h, exposure.surroundingPressure(), 0, 0, "the gas around it");
            h.assertTrue(exposure.breathedPressure() > 0, "the tank's gas wasn't in its breath");
        } finally {
            HazardHandlers.unregister(HANDLER);
            HazardHandlers.unregister(OTHER);
            pig.discard();
        }
        h.succeed();
    }

    /**
     * Not everyone is called for: an exempt player (LIFE-037), an immune entity with nothing to check
     * (LIFE-015), or a check that charges no time. A handler that kills ends that entity's check, so
     * the next isn't called for a dead one (LIFE-032).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void life_032_a_handler_isnt_called_for_the_exempt_or_the_dead(GameTestHelper h) {
        Rooms r = new Rooms(h, 743);
        room(r, 0, Map.of(TEST_GAS, 1.0, CO, 0.01));
        Pig pig = mob(h, r, EntityType.PIG, 0);
        IronGolem golem = mob(h, r, EntityType.IRON_GOLEM, 0);
        FakePlayer creative = player(r, 0);
        creative.setGameMode(GameType.CREATIVE);
        List<LivingEntity> seen = new ArrayList<>();
        long t = r.level.getGameTime();
        try {
            withGas(TEST_GAS, e -> seen.add(e.entity()), () -> {
                Breathers.checkAt(pig, t);
                h.assertValueEqual(seen.size(), 0, "the calls at a first check, which charges no time");
                Breathers.checkAt(pig, t + 40);
                h.assertValueEqual(seen.size(), 1, "the calls at the next");
                seen.clear();
                twoSeconds(creative, t);
                h.assertValueEqual(seen.size(), 0, "the calls for a creative player");
                twoSeconds(golem, t);
                h.assertValueEqual(seen.size(), 0, "the calls for an immune mob");
            });

            seen.clear();
            HazardHandlers.register(HANDLER, GasTags.TOXIC, e -> {
                seen.add(e.entity());
                e.entity().kill();
            });
            HazardHandlers.register(OTHER, TEST_GAS, e -> seen.add(e.entity()));
            try {
                Breathers.checkAt(pig, t + 80);
                h.assertValueEqual(seen.size(), 1, "the calls in a check whose first handler killed it");
            } finally {
                HazardHandlers.unregister(HANDLER);
                HazardHandlers.unregister(OTHER);
            }
        } finally {
            pig.discard();
            golem.discard();
        }
        h.succeed();
    }

    /**
     * A handler that throws is skipped for the rest of that check, here at its first of two gases, and
     * called again at the next. The handler after it still runs for both, and so does ZAS³'s own hazard.
     * One error is logged for it, one more a minute later, and it counts as soon as it stops (NFR-013).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nfr_013_a_hazard_handler_that_throws_is_skipped_and_logged_once_a_minute(GameTestHelper h) {
        Rooms r = new Rooms(h, 744);
        room(r, 0, Map.of(CO, 0.01, N2O, 0.01));
        Pig pig = mob(h, r, EntityType.PIG, 0);
        boolean[] failing = {true};
        int[] tried = {0};
        int[] good = {0};
        String source = "Hazard handler " + HANDLER;
        // HANDLER sorts before OTHER, by id.
        HazardHandlers.register(HANDLER, GasTags.TOXIC, e -> {
            tried[0]++;
            if (failing[0]) {
                throw new IllegalStateException("the test handler failing");
            }
        });
        HazardHandlers.register(OTHER, GasTags.TOXIC, e -> good[0]++);
        long t = r.level.getGameTime();
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            Breathers.checkAt(pig, t);
            for (int i = 1; i <= 3; i++) {
                Breathers.checkAt(pig, t + 40L * i);
                h.assertValueEqual(tried[0], i, "the calls to it: one a check, not one a gas");
                h.assertValueEqual(good[0], 2 * i, "the calls to the handler after it");
            }
            h.assertValueEqual(log.naming(source), 1, "errors logged about it");

            FaultLog.aMinutePasses();
            Breathers.checkAt(pig, t + 160);
            h.assertValueEqual(log.naming(source), 2, "errors logged about it a minute later");

            failing[0] = false;
            tried[0] = 0;
            Breathers.checkAt(pig, t + 200);
            h.assertValueEqual(tried[0], 2, "the calls to it, for both gases, once it stops");
            h.assertValueEqual(log.naming(source), 2, "errors logged about it once it stops");
        } finally {
            HazardHandlers.unregister(HANDLER);
            HazardHandlers.unregister(OTHER);
            pig.discard();
        }
        h.succeed();
    }
}
