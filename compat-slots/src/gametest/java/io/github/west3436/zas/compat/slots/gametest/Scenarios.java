package io.github.west3436.zas.compat.slots.gametest;

import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.config.ProtectionConfig;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Shielding;
import io.github.west3436.zas.life.WornProtection;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * What the tests of the Curios and Accessories adapters check, written once and run against each
 * target through its {@link Kit}: the two adapters are held to the same behaviour. The first three
 * need nothing of the content mod; the last three use its tanks, masks and suits.
 */
final class Scenarios {
    private Scenarios() {}

    private static ItemStack stack(Supplier<? extends Item> item) {
        return new ItemStack(item.get());
    }

    /** An O2 tank delivering at a pressure of its own, by which a test knows it again. */
    private static ItemStack tank(double distributionKpa) {
        ItemStack tank = stack(AtmosItems.OXYGEN_TANK);
        GasTankItem.setDistributionPressure(tank, distributionKpa);
        return tank;
    }

    // ---- With or without the content ----------------------------------------------------------

    /** The target is the one the build asked for, zas started its adapter, and the adapter registered. */
    static void adapterStarted(GameTestHelper h, Kit kit) {
        Status status = Slots.status(h, kit.modId());
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(Adapters.isActive(kit.modId()), "zas says the adapter is off");
        h.assertTrue(kit.adapterActive(), "the adapter says it didn't start");
        h.assertTrue(Slots.registered(kit.provider()), kit.provider() + " isn't registered");
    }

    /**
     * With a target older than the adapter supports (COMPAT-002): zas leaves the adapter off, so it
     * registers no slots, and the server runs on. The one warning about the version is zas's, and its
     * own tests cover its text; this checks what the warning says happened.
     *
     * @param modId the target's mod id
     * @param provider the id the adapter would register its provider under
     * @param adapterActive whether the adapter says it started
     */
    static void adapterOff(GameTestHelper h, String modId, ResourceLocation provider, boolean adapterActive) {
        Status status = Slots.status(h, modId);
        h.assertValueEqual(status.state(), State.UNSUPPORTED, "the adapter's state");
        h.assertFalse(Adapters.isActive(modId), "zas says the adapter is on");
        h.assertFalse(adapterActive, "the adapter says it started");
        h.assertFalse(Slots.registered(provider), provider + " is registered");
    }

    /**
     * The adapter gives what an entity wears in the target's slots: the stacks themselves, each
     * once, in the target's order, a head slot's as a head piece and any other's as not. Cosmetic
     * slots are left out, and so is a slot that is switched off, where the target has such a
     * switch. An entity without slots is given nothing.
     */
    static void givesWhatIsWorn(GameTestHelper h, Kit kit) {
        LivingEntity wearer = kit.wearer(h);
        List<String> types = kit.slotTypes(wearer);
        h.assertTrue(types.contains(kit.back()), "the wearer has no back slot: " + types);
        h.assertTrue(types.containsAll(kit.headTypes()), "the wearer lacks a head slot: " + types);
        h.assertValueEqual(Slots.worn(wearer).stacks().size(), 0, "items given with every slot empty");

        kit.equip(h, wearer, kit.back(), new ItemStack(Items.STICK));
        for (String head : kit.headTypes()) {
            kit.equip(h, wearer, head, new ItemStack(Items.CARVED_PUMPKIN));
        }
        List<ItemStack> expected = new ArrayList<>();
        List<ExtraSlotKind> kinds = new ArrayList<>();
        for (String type : types) {
            if (type.equals(kit.back()) || kit.headTypes().contains(type)) {
                expected.add(kit.worn(h, wearer, type));
                kinds.add(kit.headTypes().contains(type) ? ExtraSlotKind.HEAD : ExtraSlotKind.OTHER);
            }
        }
        Slots.Worn worn = Slots.worn(wearer);
        h.assertValueEqual(worn.stacks().size(), expected.size(), "items given");
        for (int i = 0; i < expected.size(); i++) {
            h.assertTrue(worn.stacks().get(i) == expected.get(i), "item " + i + " isn't the stack in its slot");
        }
        h.assertValueEqual(worn.kinds(), kinds, "the slots' kinds");

        kit.equipCosmetic(h, wearer, kit.back(), new ItemStack(Items.DIAMOND));
        h.assertValueEqual(Slots.worn(wearer).stacks().size(), expected.size(), "items given with a cosmetic one");

        if (kit.setActive(wearer, kit.back(), false)) {
            Slots.Worn off = Slots.worn(wearer);
            h.assertValueEqual(off.stacks().size(), expected.size() - 1, "items given with the back slot off");
            h.assertFalse(off.kinds().contains(ExtraSlotKind.OTHER), "the switched-off slot's item is given");
            h.assertTrue(kit.setActive(wearer, kit.back(), true), "the back slot didn't switch on again");
            h.assertValueEqual(Slots.worn(wearer).stacks().size(), expected.size(), "items given with it on again");
        }

        Pig pig = EntityType.PIG.create(h.getLevel());
        h.assertTrue(pig != null, "couldn't create a pig");
        h.assertValueEqual(kit.slotTypes(pig), List.of(), "a pig's slots");
        h.assertValueEqual(Slots.worn(pig).stacks().size(), 0, "items given for a pig");
    }

    /**
     * The slot data comes and goes with the content (COMPAT-043, CONT-155): with it on, the target's
     * tags for the back, belt and head slots hold the content's items and a player has the three
     * slots; with it off, no tag holds anything, and a player has only the slots the target gives
     * by itself.
     */
    static void dataFollowsTheContent(GameTestHelper h, Kit kit) {
        boolean content = Slots.content();
        h.assertValueEqual(
                BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath("zas_atmos", "oxygen_tank")),
                content,
                "the content's items are registered");
        for (String type : List.of(kit.back(), kit.belt(), kit.head())) {
            h.assertValueEqual(
                    Slots.tagged(Slots.tag(kit.modId(), type)), content, "#" + kit.modId() + ":" + type + " has items");
        }
        FakePlayer p = Slots.player(h, "zas_slots_data");
        List<String> types = kit.slotTypes(p);
        h.assertValueEqual(
                types.containsAll(List.of(kit.back(), kit.belt(), kit.head())),
                content || !kit.slotsFromContent(),
                "a player has the back, belt and head slots: " + types);
    }

    // ---- With the content ---------------------------------------------------------------------

    /**
     * Internals look in the target's slots after the armor slots and before the offhand, in the
     * target's own order (LIFE-028). A tank on the back is breathed from where it is: the draw comes
     * out of the stack in the slot, the HUD reads its pressure, and the action bar says "worn".
     */
    static void internalsOrder(GameTestHelper h, Kit kit) {
        FakePlayer p = Slots.player(h, "zas_slots_order");
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.BREATH_MASK));
        ItemStack suit = stack(AtmosItems.SOFTSUIT_CHESTPLATE);
        SuitItem.setTank(suit, tank(30));
        p.setItemSlot(EquipmentSlot.CHEST, suit);
        kit.equip(h, p, kit.back(), tank(33));
        kit.equip(h, p, kit.belt(), tank(34));
        p.setItemInHand(InteractionHand.OFF_HAND, tank(40));
        p.getInventory().setItem(0, tank(50));
        List<String> types = kit.slotTypes(p);
        boolean backFirst = types.indexOf(kit.back()) < types.indexOf(kit.belt());
        Runnable back = () -> kit.equip(h, p, kit.back(), ItemStack.EMPTY);
        Runnable belt = () -> kit.equip(h, p, kit.belt(), ItemStack.EMPTY);
        Slots.order(
                h,
                p,
                backFirst ? new double[] {30, 33, 34, 40, 50} : new double[] {30, 34, 33, 40, 50},
                new Runnable[] {
                    () -> p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY),
                    backFirst ? back : belt,
                    backFirst ? belt : back,
                    () -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY),
                    () -> p.getInventory().setItem(0, ItemStack.EMPTY)
                });

        kit.equip(h, p, kit.back(), stack(AtmosItems.OXYGEN_TANK));
        p.setItemInHand(InteractionHand.OFF_HAND, stack(AtmosItems.AIR_TANK));
        h.assertTrue(Internals.toggle(p), "toggling didn't switch them on");
        Internals.Active active = Internals.active(p);
        h.assertTrue(active != null, "internals don't work from the back slot");
        Slots.close(h, active.supply().distributionPressure(), 21.3, 0, "the supply found (the O2 tank's 21.3 kPa)");
        h.assertValueEqual(Internals.status(p).getString(), Slots.onFromWorn(), "the status");
        h.assertValueEqual(HudLevels.internals(p), AtmosHudPayload.INTERNALS_ON, "the indicator");
        Breathability v = BreathingRules.assess(p).verdict();
        h.assertValueEqual(v.source(), BreathSource.INTERNALS, "the breath's source");
        h.assertValueEqual(v.reason(), BreathReason.BREATHABLE, "the verdict");

        double before = GasTankItem.contents(kit.worn(h, p, kit.back())).totalMoles();
        Internals.breathe(p.serverLevel(), p, active.supply(), 100);
        ItemStack drawn = kit.worn(h, p, kit.back());
        Slots.close(
                h,
                before - GasTankItem.contents(drawn).totalMoles(),
                Internals.drawRate(21.3, StandardAir.TEMPERATURE) * 100,
                1e-12,
                "the worn tank's draw");
        Slots.close(
                h,
                GasTankItem.contents(p.getOffhandItem()).totalMoles(),
                GasTankItem.contents(stack(AtmosItems.AIR_TANK)).totalMoles(),
                0,
                "the offhand tank's draw");
        Slots.close(h, HudLevels.tankPressure(p), GasTankItem.contents(drawn).pressure(), 0.01, "the tank reading");
    }

    /**
     * With two targets on one server: a tank on the back in each is found after the armor and
     * before the offhand, the first target's before the second's, and a breath is drawn from the
     * first alone.
     */
    static void tanksInBoth(GameTestHelper h, FakePlayer p, Kit first, Kit second) {
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.BREATH_MASK));
        first.equip(h, p, first.back(), tank(33));
        second.equip(h, p, second.back(), tank(34));
        p.setItemInHand(InteractionHand.OFF_HAND, tank(40));
        h.assertTrue(Internals.toggle(p), "toggling didn't switch them on");
        Internals.Active active = Internals.active(p);
        h.assertTrue(active != null, "internals don't work with a tank in each mod's back slot");
        double full = GasTankItem.contents(tank(33)).totalMoles();
        Internals.breathe(p.serverLevel(), p, active.supply(), 100);
        h.assertTrue(
                GasTankItem.contents(first.worn(h, p, first.back())).totalMoles() < full,
                "nothing was drawn from the first tank");
        Slots.close(
                h,
                GasTankItem.contents(second.worn(h, p, second.back())).totalMoles(),
                full,
                0,
                "what the second tank holds");
        Slots.order(h, p, new double[] {33, 34, 40}, new Runnable[] {
            () -> first.equip(h, p, first.back(), ItemStack.EMPTY),
            () -> second.equip(h, p, second.back(), ItemStack.EMPTY),
            () -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY)
        });
    }

    /**
     * A mask in the target's head slot is the head piece for the seal and the filter, and nothing
     * more (COMPAT-042): a hardsuit helmet there seals and covers none of the body, where in the
     * armor head slot it covers the head's share. On the back a mask does nothing. With a helmet in
     * the armor head slot and a mask in the target's, each counts for what it gives: the softsuit
     * helmet's seal and cover, and the gas mask's filter. {@code /zas life} says the same.
     */
    static void headPiece(GameTestHelper h, Kit kit) {
        FakePlayer p = Slots.player(h, "zas_slots_mask");
        h.assertFalse(WornProtection.sealedBreathing(p), "bare: sealed");

        kit.equip(h, p, kit.back(), stack(AtmosItems.GAS_MASK));
        h.assertFalse(WornProtection.sealedBreathing(p), "a mask on the back seals");
        h.assertFalse(WornProtection.gasFiltering(p), "a mask on the back filters");
        kit.equip(h, p, kit.back(), ItemStack.EMPTY);

        kit.equip(h, p, kit.head(), stack(AtmosItems.BREATH_MASK));
        h.assertTrue(WornProtection.sealedBreathing(p), "the breath mask in the head slot doesn't seal");
        h.assertFalse(WornProtection.gasFiltering(p), "the breath mask filters");
        h.assertTrue(HudLevels.triggered(p), "the HUD doesn't show with a mask in the head slot");
        kit.equip(h, p, kit.head(), stack(AtmosItems.GAS_MASK));
        h.assertTrue(WornProtection.sealedBreathing(p), "the gas mask in the head slot doesn't seal");
        h.assertTrue(WornProtection.gasFiltering(p), "the gas mask in the head slot doesn't filter");
        h.assertTrue(WornProtection.of(p) == Shielding.NONE, "a mask in the head slot covers some of the body");

        // A helmet rated for vacuum: no cover from the target's slot, the head's share from the armor slot.
        kit.equip(h, p, kit.head(), stack(AtmosItems.HARDSUIT_HELMET));
        h.assertTrue(WornProtection.sealedBreathing(p), "the hardsuit helmet in the head slot doesn't seal");
        h.assertTrue(WornProtection.of(p) == Shielding.NONE, "a helmet in the head slot covers some of the body");
        Slots.close(h, WornProtection.of(p).pressureFactor(0), 1, 0, "the pressure let through, helmet in the slot");
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.HARDSUIT_HELMET));
        double share = ProtectionConfig.coverage(EquipmentSlot.HEAD);
        Slots.close(
                h,
                WornProtection.of(p).pressureFactor(0),
                WornProtection.pressureFactorFor(share),
                1e-12,
                "the pressure let through, helmets in both");

        // The softsuit helmet seals and doesn't filter; the gas mask under it filters.
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.SOFTSUIT_HELMET));
        kit.equip(h, p, kit.head(), ItemStack.EMPTY);
        h.assertTrue(WornProtection.sealedBreathing(p), "the softsuit helmet doesn't seal");
        h.assertFalse(WornProtection.gasFiltering(p), "the softsuit helmet filters");
        kit.equip(h, p, kit.head(), stack(AtmosItems.GAS_MASK));
        h.assertTrue(WornProtection.sealedBreathing(p), "helmet and mask: sealed");
        h.assertTrue(WornProtection.gasFiltering(p), "helmet and mask: filtered");
        Slots.close(
                h,
                WornProtection.of(p).pressureFactor(0),
                WornProtection.pressureFactorFor(share),
                1e-12,
                "the pressure let through, helmet and mask");

        // The mask in the head slot and a tank on the back: internals, with nothing in an armor slot.
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        kit.equip(h, p, kit.head(), stack(AtmosItems.BREATH_MASK));
        kit.equip(h, p, kit.back(), stack(AtmosItems.OXYGEN_TANK));
        h.assertTrue(Internals.toggle(p), "toggling didn't switch them on");
        Breathability v = BreathingRules.assess(p).verdict();
        h.assertValueEqual(v.source(), BreathSource.INTERNALS, "the breath's source");
        h.assertTrue(v.protection().contains(Protection.SEALED_BREATHING), "no SEALED_BREATHING: " + v);
        h.assertFalse(v.protection().contains(Protection.GAS_FILTERING), "GAS_FILTERING from a breath mask: " + v);

        // What an operator is told: /zas life names the tank's breath and the mask's seal.
        List<String> said = Slots.life(p);
        h.assertTrue(said.get(0).endsWith(": breathable, breathing from internals"), "/zas life's first line: " + said);
        h.assertTrue(
                said.get(1).endsWith("protection: sealed breathing"), "/zas life's protection, breath mask: " + said);
        kit.equip(h, p, kit.head(), stack(AtmosItems.GAS_MASK));
        String filtered = Slots.life(p).get(1);
        h.assertTrue(
                filtered.contains("sealed breathing") && filtered.contains("gas filtering"),
                "/zas life's protection, gas mask: " + filtered);
    }

    /**
     * The target lets the content's tanks into its back and belt slots and its masks into the head
     * slot, by the tags the content ships (COMPAT-043), and neither into the other's.
     */
    static void gearFitsItsSlots(GameTestHelper h, Kit kit) {
        FakePlayer p = Slots.player(h, "zas_slots_fit");
        List<Supplier<? extends Item>> tanks = List.of(
                AtmosItems.OXYGEN_TANK, AtmosItems.EMERGENCY_OXYGEN_TANK, AtmosItems.AIR_TANK, AtmosItems.EMPTY_TANK);
        List<Supplier<? extends Item>> masks = List.of(AtmosItems.BREATH_MASK, AtmosItems.GAS_MASK);
        for (Supplier<? extends Item> tank : tanks) {
            ItemStack s = stack(tank);
            h.assertTrue(s.is(Slots.tag(kit.modId(), kit.back())), s + " isn't tagged for the back");
            h.assertTrue(s.is(Slots.tag(kit.modId(), kit.belt())), s + " isn't tagged for the belt");
            h.assertTrue(kit.fits(p, kit.back(), s), s + " doesn't fit the back slot");
            h.assertTrue(kit.fits(p, kit.belt(), s), s + " doesn't fit the belt slot");
            h.assertFalse(kit.fits(p, kit.head(), s), s + " fits the head slot");
        }
        for (Supplier<? extends Item> mask : masks) {
            ItemStack s = stack(mask);
            h.assertTrue(s.is(Slots.tag(kit.modId(), kit.head())), s + " isn't tagged for the head");
            h.assertTrue(kit.fits(p, kit.head(), s), s + " doesn't fit the head slot");
            h.assertFalse(kit.fits(p, kit.back(), s), s + " fits the back slot");
            h.assertFalse(kit.fits(p, kit.belt(), s), s + " fits the belt slot");
        }
        ItemStack stick = new ItemStack(Items.STICK);
        for (String type : List.of(kit.back(), kit.belt(), kit.head())) {
            h.assertFalse(kit.fits(p, type, stick), "a stick fits the " + type + " slot");
        }
    }
}
