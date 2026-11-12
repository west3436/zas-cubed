package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlots;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Shielding;
import io.github.west3436.zas.life.WornProtection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The equipment slots other mods add (COMPAT-040 to COMPAT-042), with no such mod installed: a
 * provider the tests register stands in for one ({@link TestExtraSlots}), and the tanks and pieces
 * are {@link TestLifeSupport}'s. The adapters for Curios and Accessories have their own tests, which
 * run with those mods. Each test builds in its own area ({@link Rooms}, areas 2810 and up) and
 * runs within one game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtraSlotTests {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** 1 mol of O2 in the test tank's 10 L: about 244 kPa. */
    private static final Map<ResourceLocation, Double> FULL = Map.of(O2, 1.0);

    private ExtraSlotTests() {}

    /** A survival fake player standing at (x, 1, 2) of an area. */
    private static FakePlayer player(Rooms r, int x) {
        FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_extra_slots"));
        p.moveTo(Vec3.atBottomCenterOf(r.abs(x, 1, 2)));
        return p;
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    private static String on(String place) {
        return Component.translatable(
                        "message.zas.internals.on", Component.translatable("message.zas.internals.place." + place))
                .getString();
    }

    /** Takes the tanks an entity has one after another, each the first found, by their distribution pressures. */
    private static void order(GameTestHelper h, FakePlayer p, double[] expected, Runnable[] remove, String when) {
        for (int i = 0; i < expected.length; i++) {
            Optional<IInternalSupply> found = Internals.find(p);
            h.assertTrue(found.isPresent(), when + ": no tank found, step " + i);
            close(h, found.get().distributionPressure(), expected[i], 0, when + ": the tank found, step " + i);
            remove[i].run();
        }
        h.assertTrue(Internals.find(p).isEmpty(), when + ": a tank was left");
    }

    // ---- COMPAT-040, COMPAT-041 ------------------------------------------------------------------

    /**
     * Internals look in the slots other mods add after the armor slots and before the offhand
     * (COMPAT-040, COMPAT-041, LIFE-028): the providers in order of id, each one's slots in its own
     * order, whatever a slot's kind. With no provider, and for an entity a provider gives nothing,
     * the order is as it was. The first tank found counts even if it can't deliver (LIFE-046), and a
     * mob's extra slots come before its main hand.
     */
    @GameTest(template = "empty")
    public static void compat_040_a_tank_in_an_extra_slot_is_found_after_the_armor_and_before_the_offhand(
            GameTestHelper h) {
        Rooms r = new Rooms(h, 2810);
        r.clear(0, 8);
        FakePlayer p = player(r, 2);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        Runnable held = () -> {
            p.setItemSlot(EquipmentSlot.CHEST, TestLifeSupport.tank(FULL, 30));
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(FULL, 40));
            p.getInventory().setItem(0, TestLifeSupport.tank(FULL, 50));
        };
        Runnable[] removeHeld = {
            () -> p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY),
            () -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY),
            () -> p.getInventory().setItem(0, ItemStack.EMPTY)
        };

        // No provider: nothing is asked, and the order is the one it was.
        h.assertValueEqual(ExtraSlots.registered(), List.of(), "the providers registered before the test");
        held.run();
        order(h, p, new double[] {30, 40, 50}, removeHeld, "no provider");

        // Registered out of order: the registry sorts them by id.
        try (TestExtraSlots second = new TestExtraSlots("b_slots");
                TestExtraSlots first = new TestExtraSlots("a_slots")) {
            // A provider with nothing for this player: as with none.
            held.run();
            order(h, p, new double[] {30, 40, 50}, removeHeld, "empty slots");

            held.run();
            ItemStack back = TestLifeSupport.tank(FULL, 33);
            second.wear(p, ExtraSlotKind.OTHER, TestLifeSupport.tank(FULL, 36));
            first.wear(p, ExtraSlotKind.OTHER, back);
            first.wear(p, ExtraSlotKind.OTHER, ItemStack.EMPTY);
            first.wear(p, ExtraSlotKind.OTHER, new ItemStack(Items.STICK));
            first.wear(p, ExtraSlotKind.HEAD, TestLifeSupport.tank(FULL, 34));
            order(
                    h,
                    p,
                    new double[] {30, 33, 34, 36, 40, 50},
                    new Runnable[] {
                        removeHeld[0],
                        () -> back.setCount(0),
                        () -> first.clear(p),
                        () -> second.clear(p),
                        removeHeld[1],
                        removeHeld[2]
                    },
                    "two providers");

            // The status names the place, and a breath check draws from the stack in the slot.
            ItemStack belt = TestLifeSupport.tank(FULL, 21.3);
            first.wear(p, ExtraSlotKind.OTHER, belt);
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(FULL, 40));
            h.assertTrue(Internals.toggle(p), "toggling didn't switch them on");
            h.assertValueEqual(Internals.status(p).getString(), on("worn"), "the status");
            Internals.Active active = Internals.active(p);
            h.assertTrue(active != null, "no internals from a tank in an extra slot");
            close(h, active.supply().distributionPressure(), 21.3, 0, "the tank breathed from");
            h.assertValueEqual(HudLevels.internals(p), AtmosHudPayload.INTERNALS_ON, "the indicator");
            double fullKpa = GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE / TestLifeSupport.TANK_VOLUME;
            close(h, HudLevels.tankPressure(p), fullKpa, 0.01, "the tank reading");
            long t = r.level.getGameTime();
            Breathers.checkAt(p, t);
            Breathers.checkAt(p, t + 40);
            close(
                    h,
                    1 - TestLifeSupport.contents(belt).totalMoles(),
                    2 * Internals.drawRate(21.3, StandardAir.TEMPERATURE),
                    1e-9,
                    "the moles drawn from the worn tank in 2 s");
            close(h, TestLifeSupport.contents(p.getOffhandItem()).totalMoles(), 1, 0, "the offhand tank");
            close(h, HudLevels.tankPressure(p), TestLifeSupport.contents(belt).pressure(), 0.01, "the reading after");

            // An empty tank found first stops the search, and the player breathes the air.
            first.clear(p);
            first.wear(p, ExtraSlotKind.OTHER, TestLifeSupport.tank(Map.of(), 21.3));
            h.assertTrue(Internals.active(p) == null, "internals passed over the empty tank found first");
            h.assertValueEqual(
                    BreathingRules.assess(p).verdict().source(), BreathSource.ATMOSPHERE, "behind an empty tank");
            h.assertValueEqual(HudLevels.internals(p), AtmosHudPayload.INTERNALS_FAILING, "empty: the indicator");

            // A mob: an extra slot before its main hand, and after its armor.
            Zombie z = EntityType.ZOMBIE.create(r.level);
            h.assertTrue(z != null, "couldn't create a zombie");
            z.setItemInHand(InteractionHand.MAIN_HAND, TestLifeSupport.tank(FULL, 45));
            first.wear(z, ExtraSlotKind.OTHER, TestLifeSupport.tank(FULL, 35));
            close(h, Internals.find(z).orElseThrow().distributionPressure(), 35, 0, "the zombie's extra slot");
            z.setItemSlot(EquipmentSlot.CHEST, TestLifeSupport.tank(FULL, 31));
            close(h, Internals.find(z).orElseThrow().distributionPressure(), 31, 0, "the zombie's chest");
            z.discard();
        }
        h.assertValueEqual(ExtraSlots.registered(), List.of(), "the providers registered after the test");
        h.succeed();
    }

    // ---- COMPAT-042 --------------------------------------------------------------------------

    /**
     * An item with life support in an extra head slot counts as the head piece for sealed breathing
     * and gas filtering, and for nothing else (COMPAT-042): it covers none of the body and resists
     * no airflow, where the same item in the armor head slot covers the head's share. In a slot of
     * another kind it counts for nothing. With a piece in the armor head slot too, each counts for
     * what it gives: one seals and the other filters, either way round.
     */
    @GameTest(template = "empty")
    public static void compat_042_a_head_piece_in_an_extra_slot_seals_and_filters_and_covers_none_of_the_body(
            GameTestHelper h) {
        Rooms r = new Rooms(h, 2811);
        r.clear(0, 8);
        r.shell(0, 4);
        r.settle(GasMutations.remove(r.level, r.point(2, 2, 2), Map.of(N2, 1e6, O2, 1e6), Rooms.source("slots")));
        FakePlayer p = player(r, 2);
        try (TestExtraSlots slots = new TestExtraSlots("slots")) {
            h.assertFalse(WornProtection.sealedBreathing(p), "bare: sealed");
            h.assertFalse(WornProtection.gasFiltering(p), "bare: filtered");

            // In a slot of another kind the piece counts for nothing.
            slots.wear(p, ExtraSlotKind.OTHER, new ItemStack(TestLifeSupport.item()));
            h.assertFalse(WornProtection.sealedBreathing(p), "a piece on the back: sealed");
            h.assertFalse(WornProtection.gasFiltering(p), "a piece on the back: filtered");
            h.assertFalse(HudLevels.triggered(p), "a piece on the back shows the HUD");

            // In a head slot it seals and filters, and that is all it does.
            slots.clear(p);
            slots.wear(p, ExtraSlotKind.HEAD, new ItemStack(TestLifeSupport.item()));
            h.assertTrue(WornProtection.sealedBreathing(p), "a piece in an extra head slot doesn't seal");
            h.assertTrue(WornProtection.gasFiltering(p), "a piece in an extra head slot doesn't filter");
            h.assertTrue(HudLevels.triggered(p), "a sealing piece in an extra head slot doesn't show the HUD");
            h.assertTrue(WornProtection.of(p) == Shielding.NONE, "an extra head slot covers some of the body");
            close(h, WornProtection.airflowResistance(p), 0, 0, "airflow resistance from an extra slot");
            EnvironmentHazards.Exposure bare = BreathingRules.assess(p).exposure();
            h.assertTrue(bare != null, "no exposure in the vacuum");
            close(h, bare.effectivePressure(), 0, 1e-9, "effective pressure, the piece in an extra head slot");
            close(h, bare.decompression(), 1, 0, "decompression, the piece in an extra head slot");
            Breathability v = BreathingRules.assess(p).verdict();
            h.assertFalse(v.protection().contains(Protection.PRESSURE), "pressure protection from an extra slot: " + v);
            h.assertTrue(v.protection().contains(Protection.GAS_FILTERING), "no GAS_FILTERING: " + v);

            // The same piece in the armor head slot covers the head's share, 0.3.
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
            close(
                    h,
                    BreathingRules.assess(p).exposure().effectivePressure(),
                    StandardAir.PRESSURE * Math.pow(0.3, 5),
                    1e-6,
                    "effective pressure, the piece in the armor head slot");
            close(h, WornProtection.airflowResistance(p), 0.5, 0, "airflow resistance from the armor head slot");
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

            // Sealed by the extra head slot, internals breathe from a tank in another extra slot.
            ItemStack back = TestLifeSupport.tank(FULL, 21.3);
            slots.wear(p, ExtraSlotKind.OTHER, back);
            h.assertTrue(Internals.toggle(p), "toggling didn't switch them on");
            v = BreathingRules.assess(p).verdict();
            h.assertValueEqual(v.source(), BreathSource.INTERNALS, "the breath's source");
            h.assertValueEqual(v.reason(), BreathReason.BREATHABLE, "the verdict");
            h.assertTrue(v.protection().contains(Protection.SEALED_BREATHING), "no SEALED_BREATHING: " + v);
            h.assertValueEqual(Internals.status(p).getString(), on("worn"), "the status");
            Internals.set(p, false);

            // The armor head slot's piece and the extra head slot's each count for what they give.
            // By the GameTest datapack a carved pumpkin seals and a creeper head filters.
            slots.clear(p);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            h.assertTrue(WornProtection.sealedBreathing(p), "the pumpkin as a helmet doesn't seal");
            h.assertFalse(WornProtection.gasFiltering(p), "the pumpkin as a helmet filters");
            slots.wear(p, ExtraSlotKind.HEAD, new ItemStack(Items.CREEPER_HEAD));
            h.assertTrue(WornProtection.sealedBreathing(p), "helmet seals, mask filters: sealed");
            h.assertTrue(WornProtection.gasFiltering(p), "helmet seals, mask filters: filtered");
            slots.clear(p);
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CREEPER_HEAD));
            h.assertFalse(WornProtection.sealedBreathing(p), "the creeper head as a helmet seals");
            slots.wear(p, ExtraSlotKind.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            h.assertTrue(WornProtection.sealedBreathing(p), "helmet filters, mask seals: sealed");
            h.assertTrue(WornProtection.gasFiltering(p), "helmet filters, mask seals: filtered");

            // Two extra head slots: a hat that gives nothing doesn't hide the mask after it.
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            slots.clear(p);
            slots.wear(p, ExtraSlotKind.HEAD, new ItemStack(Items.STICK));
            slots.wear(p, ExtraSlotKind.HEAD, new ItemStack(Items.CREEPER_HEAD));
            slots.wear(p, ExtraSlotKind.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
            h.assertTrue(WornProtection.sealedBreathing(p), "the second head slot's seal");
            h.assertTrue(WornProtection.gasFiltering(p), "the first head slot's filter");
        }
        h.succeed();
    }

    // ---- The API's own reading, and NFR-013 ----------------------------------------------------

    /**
     * {@code ExtraSlots.forEach} gives every item in the order ZAS³ takes them, the stacks themselves
     * and each with its slot's kind, and nothing for an entity no provider has slots for.
     */
    @GameTest(template = "empty")
    public static void compat_040_the_api_gives_every_worn_item_in_order(GameTestHelper h) {
        Rooms r = new Rooms(h, 2812);
        FakePlayer p = player(r, 2);
        FakePlayer other = player(r, 3);
        ItemStack hat = new ItemStack(Items.CARVED_PUMPKIN);
        ItemStack back = TestLifeSupport.tank(FULL, 21.3);
        ItemStack belt = new ItemStack(Items.STICK);
        try (TestExtraSlots second = new TestExtraSlots("b_slots");
                TestExtraSlots first = new TestExtraSlots("a_slots")) {
            second.wear(p, ExtraSlotKind.OTHER, belt);
            first.wear(p, ExtraSlotKind.HEAD, hat);
            first.wear(p, ExtraSlotKind.OTHER, back);
            List<ItemStack> stacks = new ArrayList<>();
            List<ExtraSlotKind> kinds = new ArrayList<>();
            ExtraSlots.forEach(p, (stack, kind) -> {
                stacks.add(stack);
                kinds.add(kind);
            });
            h.assertValueEqual(stacks.size(), 3, "items given");
            h.assertTrue(stacks.get(0) == hat && stacks.get(1) == back && stacks.get(2) == belt, "the stacks given");
            h.assertValueEqual(
                    kinds, List.of(ExtraSlotKind.HEAD, ExtraSlotKind.OTHER, ExtraSlotKind.OTHER), "their kinds");
            // A visitor mustn't throw, so these count.
            int[] given = new int[1];
            ExtraSlots.forEach(other, (stack, kind) -> given[0]++);
            h.assertValueEqual(given[0], 0, "items for a player with no slots");
            h.assertValueEqual(first.asked + second.asked, 2, "times the providers gave the player's items");
        }
        int[] given = new int[1];
        ExtraSlots.forEach(p, (stack, kind) -> given[0]++);
        h.assertValueEqual(given[0], 0, "items with no provider registered");
        h.succeed();
    }

    /**
     * A provider that throws, an exception or a linkage error, gives nothing more for that check and
     * the next provider is still asked; what it gave before it threw counts. Each fault is logged at
     * most once a minute (NFR-013).
     */
    @GameTest(template = "empty")
    public static void nfr_013_an_extra_slot_provider_that_throws_is_skipped_and_logged_once_a_minute(
            GameTestHelper h) {
        Rooms r = new Rooms(h, 2813);
        FakePlayer p = player(r, 2);
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        try (TestExtraSlots good = new TestExtraSlots("c_good");
                TestExtraSlots linkage = new TestExtraSlots("b_linkage");
                TestExtraSlots faulty = new TestExtraSlots("a_faulty");
                FaultLog log = new FaultLog()) {
            faulty.wear(p, ExtraSlotKind.OTHER, new ItemStack(Items.STICK));
            faulty.wear(p, ExtraSlotKind.OTHER, TestLifeSupport.tank(FULL, 33));
            linkage.wear(p, ExtraSlotKind.OTHER, ItemStack.EMPTY);
            linkage.wear(p, ExtraSlotKind.OTHER, TestLifeSupport.tank(FULL, 35));
            good.wear(p, ExtraSlotKind.OTHER, TestLifeSupport.tank(FULL, 37));
            close(h, Internals.find(p).orElseThrow().distributionPressure(), 33, 0, "with nothing thrown");

            faulty.failing = new IllegalStateException("a GameTest's faulty provider");
            linkage.failing = new NoClassDefFoundError("a GameTest's provider built against another version");
            FaultLog.aMinutePasses();
            for (int i = 0; i < 4; i++) {
                close(h, Internals.find(p).orElseThrow().distributionPressure(), 37, 0, "with two providers throwing");
            }
            h.assertValueEqual(log.naming("Extra slot provider " + faulty.id()), 1, "errors about the first");
            h.assertValueEqual(log.naming("Extra slot provider " + linkage.id()), 1, "errors about the second");
            FaultLog.aMinutePasses();
            h.assertTrue(WornProtection.sealedBreathing(p), "the helmet's seal, with two providers throwing");
            h.assertValueEqual(log.naming("Extra slot provider " + faulty.id()), 1, "errors with no provider asked");
            Internals.find(p);
            h.assertValueEqual(log.naming("Extra slot provider " + faulty.id()), 2, "errors a minute later");

            // What a provider gave before it threw counts: the first item of the first one.
            faulty.clear(p);
            faulty.wear(p, ExtraSlotKind.OTHER, TestLifeSupport.tank(FULL, 32));
            close(h, Internals.find(p).orElseThrow().distributionPressure(), 32, 0, "given before the throw");
            int[] given = new int[1];
            ExtraSlots.forEach(p, (stack, kind) -> given[0]++);
            h.assertValueEqual(given[0], 3, "items the API gave: one from each provider");
        }
        h.succeed();
    }
}
