package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.gear.TankSettings;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.gametest.TestPlayers;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.Internals;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The breathing gear: gas tanks (CONT-030, LIFE-044), filling them from a canister (CONT-039), the
 * breath mask and softsuit (CONT-031, CONT-032, LIFE-021) and the suit's tank slot (CONT-090,
 * LIFE-028). Those that need a place build in their own {@link Site} (areas 40 and up). S-08 is in
 * {@link StationTests}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearTests {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final double T = StandardAir.TEMPERATURE;
    private static final double ATM = StandardAir.PRESSURE;

    private GearTests() {}

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    private static ItemStack stack(java.util.function.Supplier<? extends Item> item) {
        return new ItemStack(item.get());
    }

    private static ILifeSupport support(GameTestHelper h, ItemStack stack) {
        ILifeSupport s = stack.getCapability(ILifeSupport.ITEM);
        h.assertTrue(s != null, stack + " has no life support");
        return s;
    }

    private static FakePlayer player(Site s, String name) {
        FakePlayer p = FakePlayerFactory.get(s.level, new GameProfile(UUID.randomUUID(), name));
        p.moveTo(Vec3.atBottomCenterOf(s.abs(2, 1, 2)));
        return p;
    }

    // ---- CONT-030, LIFE-044 ---------------------------------------------------------------------

    /**
     * Each tank holds a real mixture in its volume (CONT-030): the O2 tank 17.5 mol of O2 in 70 L,
     * about 6 atm; the air tank air at 6 atm in 70 L; the emergency tank 2 L, crafted empty, and in
     * the creative tab O2 at 3 atm; the empty tank 70 L of nothing. Each tank is an internal
     * supply, and a draw comes out of the stack.
     */
    @GameTest(template = "empty")
    public static void cont_030_tanks(GameTestHelper h) {
        ItemStack o2 = stack(AtmosItems.OXYGEN_TANK);
        GasMixture m = GasTankItem.contents(o2);
        close(h, m.volume(), 70, 0, "the O2 tank's volume");
        close(h, m.moles(O2), 17.5, 1e-12, "the O2 tank's O2");
        close(h, m.pressure() / ATM, 6, 0.05, "the O2 tank's atm");
        close(h, GasTankItem.distributionPressure(o2), 21.3, 0, "the O2 tank's distribution pressure");

        ItemStack air = stack(AtmosItems.AIR_TANK);
        m = GasTankItem.contents(air);
        close(h, m.volume(), 70, 0, "the air tank's volume");
        close(h, m.pressure(), 6 * ATM, 1e-9, "the air tank's pressure");
        close(h, m.composition().get(O2), StandardAir.OXYGEN_FRACTION, 1e-12, "the air tank's O2 share");
        close(h, GasTankItem.distributionPressure(air), 101.3, 0, "the air tank's distribution pressure");

        ItemStack emergency = stack(AtmosItems.EMERGENCY_OXYGEN_TANK);
        m = GasTankItem.contents(emergency);
        close(h, m.volume(), 2, 0, "the emergency tank's volume");
        h.assertTrue(m.isEmpty(), "a crafted emergency tank isn't empty");
        GasTankItem.setGas(emergency, AtmosItems.emergencyFill());
        m = GasTankItem.contents(emergency);
        close(h, m.pressure(), 3 * ATM, 1e-9, "the filled emergency tank's pressure");
        close(h, m.composition().get(O2), 1, 0, "the filled emergency tank's O2 share");
        close(h, GasTankItem.distributionPressure(emergency), 21.3, 0, "the emergency tank's distribution pressure");

        ItemStack empty = stack(AtmosItems.EMPTY_TANK);
        close(h, GasTankItem.contents(empty).volume(), 70, 0, "the empty tank's volume");
        h.assertTrue(GasTankItem.contents(empty).isEmpty(), "the empty tank isn't empty");

        // Each tank is an internal supply; a draw comes out of the stack.
        for (ItemStack tank : new ItemStack[] {air, emergency, empty}) {
            h.assertTrue(support(h, tank).internalSupply().isPresent(), tank + " isn't an internal supply");
        }
        IInternalSupply supply = support(h, air).internalSupply().orElseThrow();
        close(h, supply.distributionPressure(), 101.3, 0, "the air tank's supply pressure");
        double before = supply.contents().totalMoles();
        GasParcel taken = supply.draw(1);
        close(h, taken.totalMoles(), 1, 1e-12, "the draw");
        close(h, GasTankItem.contents(air).totalMoles(), before - 1, 1e-9, "the air tank after the draw");
        close(h, taken.moles(O2), StandardAir.OXYGEN_FRACTION, 1e-12, "the draw's O2");
        h.succeed();
    }

    /**
     * The distribution pressure defaults to 21.3 kPa for O2 tanks and 101.3 kPa for air tanks, and
     * the settings screen's buttons move it within 0–304 kPa; the server refuses an unknown button
     * or hand, or a hand without a tank (LIFE-044).
     */
    @GameTest(template = "empty")
    public static void life_044_distribution_pressure(GameTestHelper h) {
        close(h, GasTankItem.distributionPressure(stack(AtmosItems.OXYGEN_TANK)), 21.3, 0, "the O2 tank's default");
        close(h, GasTankItem.distributionPressure(stack(AtmosItems.AIR_TANK)), 101.3, 0, "the air tank's default");
        close(
                h,
                GasTankItem.distributionPressure(stack(AtmosItems.EMERGENCY_OXYGEN_TANK)),
                21.3,
                0,
                "the emergency tank's default");
        ItemStack o2 = stack(AtmosItems.OXYGEN_TANK);
        int main = InteractionHand.MAIN_HAND.ordinal();
        for (int i = 0; i < 3; i++) {
            h.assertTrue(TankSettings.apply(o2, new TankSettings(main, TankSettings.DOWN_10)), "-10 refused");
        }
        close(h, GasTankItem.distributionPressure(o2), 0, 0, "turned down past 0");
        for (int i = 0; i < 31; i++) {
            TankSettings.apply(o2, new TankSettings(main, TankSettings.UP_10));
        }
        close(h, GasTankItem.distributionPressure(o2), 304, 0, "turned up past 304");
        TankSettings.apply(o2, new TankSettings(main, TankSettings.DOWN_1));
        close(h, GasTankItem.distributionPressure(o2), 303, 1e-9, "-1");
        TankSettings.apply(o2, new TankSettings(main, TankSettings.DEFAULT));
        close(h, GasTankItem.distributionPressure(o2), 21.3, 0, "back to the default");
        TankSettings.apply(o2, new TankSettings(main, TankSettings.UP_1));
        close(h, GasTankItem.distributionPressure(o2), 22.3, 1e-9, "+1");
        h.assertFalse(TankSettings.apply(o2, new TankSettings(main, 6)), "an unknown button was taken");
        h.assertFalse(TankSettings.apply(o2, new TankSettings(main, -1)), "a negative button was taken");
        h.assertFalse(TankSettings.apply(o2, new TankSettings(2, TankSettings.UP_1)), "an unknown hand was taken");
        h.assertFalse(
                TankSettings.apply(stack(AtmosItems.MULTITOOL), new TankSettings(main, TankSettings.UP_1)),
                "a multitool was set");
        close(h, GasTankItem.distributionPressure(o2), 22.3, 1e-9, "after the refused presses");
        TankSettings.apply(o2, new TankSettings(main, TankSettings.CLOSE));
        close(h, GasTankItem.distributionPressure(o2), 0, 0, "closed");
        h.succeed();
    }

    /** The press's handler changes the tank in the sender's hand, and refuses a spectator or a dead sender (NFR-011). */
    @GameTest(template = "empty")
    public static void nfr_011_only_a_living_player_sets_the_tank_they_hold(GameTestHelper h) {
        FakePlayer sender = TestPlayers.hurtable(h.getLevel(), "zas_tank_sender");
        ItemStack o2 = stack(AtmosItems.OXYGEN_TANK);
        sender.setItemInHand(InteractionHand.MAIN_HAND, o2);
        TankSettings up = new TankSettings(InteractionHand.MAIN_HAND.ordinal(), TankSettings.UP_10);
        double start = GasTankItem.distributionPressure(o2);

        TankSettings.handle(up, TestPlayers.sentBy(sender));
        close(h, GasTankItem.distributionPressure(o2), start + 10, 1e-9, "the press on the held tank");

        sender.setGameMode(GameType.SPECTATOR);
        TankSettings.handle(up, TestPlayers.sentBy(sender));
        close(h, GasTankItem.distributionPressure(o2), start + 10, 1e-9, "a spectator's press");

        sender.setGameMode(GameType.SURVIVAL);
        sender.setHealth(0);
        TankSettings.handle(up, TestPlayers.sentBy(sender));
        close(h, GasTankItem.distributionPressure(o2), start + 10, 1e-9, "a dead player's press");
        h.succeed();
    }

    // ---- CONT-039 ------------------------------------------------------------------------------

    /**
     * A tank used on a canister fills from it to the release pressure (CONT-039, CONT-084), and the
     * canister loses what the tank gains. An empty tank filled with O2 becomes an O2 tank; filled
     * with air, an air tank; with anything else it stays the plain tank, named a gas tank while it
     * holds gas. A tank at the release pressure takes nothing more. The emergency tank stays one.
     */
    @GameTest(template = "empty")
    public static void cont_039_tanks_fill_from_a_canister(GameTestHelper h) {
        Site s = new Site(h, 40);
        try {
            s.clear();
            ItemStack item = AtmosItems.CANISTER.get().filled(O2, CanisterColor.BLUE);
            CanisterBlockEntity c = s.place(
                    2,
                    1,
                    2,
                    AtmosBlocks.CANISTER.get().defaultBlockState().setValue(CanisterColor.PROPERTY, CanisterColor.BLUE),
                    CanisterBlockEntity.class);
            c.applyComponentsFromItemStack(item);
            c.setReleasePressure(500);
            double canister = c.network().gas().totalMoles();

            Player hand = h.makeMockPlayer(GameType.SURVIVAL);
            hand.setItemInHand(InteractionHand.MAIN_HAND, stack(AtmosItems.EMPTY_TANK));
            BlockPos pos = s.abs(2, 1, 2);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            s.level.getBlockState(pos).useItemOn(hand.getMainHandItem(), s.level, hand, InteractionHand.MAIN_HAND, hit);
            ItemStack filled = hand.getMainHandItem();
            h.assertTrue(filled.is(AtmosItems.OXYGEN_TANK.get()), "the filled empty tank is " + filled);
            GasMixture m = GasTankItem.contents(filled);
            close(h, m.pressure(), 500, 1e-9, "the tank's pressure after filling");
            close(h, canister - c.network().gas().totalMoles(), m.totalMoles(), 1e-9, "the canister's loss");
            close(h, GasTankItem.distributionPressure(filled), 21.3, 0, "the new O2 tank's distribution pressure");

            // At the release pressure, nothing more.
            double held = m.totalMoles();
            s.level.getBlockState(pos).useItemOn(filled, s.level, hand, InteractionHand.MAIN_HAND, hit);
            close(h, GasTankItem.contents(hand.getMainHandItem()).totalMoles(), held, 0, "a second fill");
            hand.discard();

            // The emergency tank fills and stays one.
            ItemStack emergency = stack(AtmosItems.EMERGENCY_OXYGEN_TANK);
            h.assertTrue(c.fillTank(emergency) > 0, "the emergency tank took nothing");
            h.assertTrue(GasTankItem.ofItsKind(emergency) == emergency, "the emergency tank changed kind");
            close(h, GasTankItem.contents(emergency).pressure(), 500, 1e-9, "the emergency tank's pressure");

            // The kinds a filled tank becomes.
            ItemStack withAir = stack(AtmosItems.EMPTY_TANK);
            GasTankItem.add(withAir, new GasParcel(Map.of(N2, 7.9, O2, 2.1), T));
            h.assertTrue(GasTankItem.ofItsKind(withAir).is(AtmosItems.AIR_TANK.get()), "air didn't make an air tank");
            ItemStack withN2 = stack(AtmosItems.EMPTY_TANK);
            GasTankItem.add(withN2, new GasParcel(Map.of(N2, 5.0), T));
            ItemStack plain = GasTankItem.ofItsKind(withN2);
            h.assertTrue(plain.is(AtmosItems.EMPTY_TANK.get()), "N2 made " + plain);
            h.assertTrue(
                    plain.getHoverName().getContents() instanceof TranslatableContents t
                            && t.getKey().equals("item.zas_atmos.empty_tank.filled"),
                    "a plain tank of N2 is named " + plain.getHoverName().getString());
            ItemStack o2ToAir = stack(AtmosItems.OXYGEN_TANK);
            GasTankItem.add(o2ToAir, new GasParcel(Map.of(N2, 66.0), T));
            ItemStack changed = GasTankItem.ofItsKind(o2ToAir);
            h.assertTrue(changed.is(AtmosItems.AIR_TANK.get()), "O2 topped up with N2 to air made " + changed);
            close(h, GasTankItem.contents(changed).moles(N2), 66, 1e-9, "the gas kept through the change of kind");
        } finally {
            s.clear();
        }
        h.succeed();
    }

    // ---- CONT-031, CONT-032 -------------------------------------------------------------------------

    /**
     * The breath mask only seals the breathing (CONT-031). The softsuit protects down to vacuum and
     * from 0 to 500 K, not above 1 atm, and its helmet seals the breathing (CONT-032): a fully suited
     * player takes no harm in vacuum or at 450 K, and is hurt at 600 kPa.
     */
    @GameTest(template = "empty")
    public static void cont_032_softsuit_and_mask_ratings(GameTestHelper h) {
        ILifeSupport mask = support(h, stack(AtmosItems.BREATH_MASK));
        h.assertTrue(mask.sealedBreathing(), "the breath mask doesn't seal");
        close(h, mask.minPressure(), ATM, 0, "the mask's low-pressure rating");
        close(h, mask.maxTemperature(), T, 0, "the mask's heat rating");

        Map<EquipmentSlot, java.util.function.Supplier<SuitItem>> suit = Map.of(
                EquipmentSlot.HEAD, AtmosItems.SOFTSUIT_HELMET,
                EquipmentSlot.CHEST, AtmosItems.SOFTSUIT_CHESTPLATE,
                EquipmentSlot.LEGS, AtmosItems.SOFTSUIT_LEGGINGS,
                EquipmentSlot.FEET, AtmosItems.SOFTSUIT_BOOTS);
        suit.forEach((slot, item) -> {
            ILifeSupport piece = support(h, new ItemStack(item.get()));
            close(h, piece.minPressure(), 0, 0, slot + ": low pressure");
            close(h, piece.maxPressure(), ATM, 0, slot + ": high pressure");
            close(h, piece.minTemperature(), 0, 0, slot + ": cold");
            close(h, piece.maxTemperature(), 500, 0, slot + ": heat");
            h.assertValueEqual(piece.sealedBreathing(), slot == EquipmentSlot.HEAD, slot + ": the seal");
            h.assertValueEqual(item.get().getEquipmentSlot(), slot, slot + ": its slot");
        });

        Site s = new Site(h, 41);
        FakePlayer p = player(s, "zas_softsuit");
        suit.forEach((slot, item) -> p.setItemSlot(slot, new ItemStack(item.get())));
        EnvironmentHazards.Exposure vacuum =
                EnvironmentHazards.exposure(p, new GasMixture(Map.of(), T, StandardAir.CELL_VOLUME));
        close(h, vacuum.decompression(), 0, 0, "decompression in vacuum");
        EnvironmentHazards.Exposure hot = EnvironmentHazards.exposure(p, nitrogen(ATM, 450));
        close(h, hot.burns(), 0, 0, "burns at 450 K");
        EnvironmentHazards.Exposure crushed = EnvironmentHazards.exposure(p, nitrogen(600, T));
        h.assertTrue(crushed.barotrauma() > 0, "no barotrauma at 600 kPa");
        h.succeed();
    }

    private static GasMixture nitrogen(double kPa, double temperature) {
        double moles = kPa * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * temperature);
        return new GasMixture(Map.of(N2, moles), temperature, StandardAir.CELL_VOLUME);
    }

    // ---- CONT-031, LIFE-021 ---------------------------------------------------------------------

    /**
     * Internals work with the breath mask and with the softsuit helmet on the head, and not with
     * nothing there or with only the rest of the suit (LIFE-021, CONT-031).
     */
    @GameTest(template = "empty")
    public static void cont_031_internals_with_each_sealed_head(GameTestHelper h) {
        Site s = new Site(h, 42);
        FakePlayer p = player(s, "zas_sealed_heads");
        p.setItemInHand(InteractionHand.OFF_HAND, stack(AtmosItems.OXYGEN_TANK));
        Internals.set(p, true);
        h.assertTrue(Internals.active(p) == null, "internals work bare-headed");
        p.setItemSlot(EquipmentSlot.CHEST, stack(AtmosItems.SOFTSUIT_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, stack(AtmosItems.SOFTSUIT_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, stack(AtmosItems.SOFTSUIT_BOOTS));
        h.assertTrue(Internals.active(p) == null, "internals work with a suit but no helmet");
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.BREATH_MASK));
        h.assertTrue(Internals.active(p) != null, "internals don't work with a breath mask");
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.SOFTSUIT_HELMET));
        h.assertTrue(Internals.active(p) != null, "internals don't work with the softsuit helmet");
        h.succeed();
    }

    // ---- CLIENT-001, CLIENT-009 ------------------------------------------------------------------

    /**
     * The breath mask and the softsuit helmet show the HUD, and the rest of the suit doesn't
     * (CLIENT-001). An O2 tank in the offhand shows its pressure, about 6 atm, and internals go from
     * off to on (CLIENT-009).
     */
    @GameTest(template = "empty")
    public static void client_001_the_mask_and_helmet_show_the_hud(GameTestHelper h) {
        Site s = new Site(h, 44);
        FakePlayer p = player(s, "zas_hud_heads");
        h.assertFalse(HudLevels.triggered(p), "the HUD shows bare-headed");
        p.setItemSlot(EquipmentSlot.CHEST, stack(AtmosItems.SOFTSUIT_CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, stack(AtmosItems.SOFTSUIT_LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, stack(AtmosItems.SOFTSUIT_BOOTS));
        h.assertFalse(HudLevels.triggered(p), "the HUD shows with a suit but no helmet");
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.BREATH_MASK));
        h.assertTrue(HudLevels.triggered(p), "the HUD doesn't show with a breath mask");
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.SOFTSUIT_HELMET));
        h.assertTrue(HudLevels.triggered(p), "the HUD doesn't show with the softsuit helmet");

        h.assertValueEqual(HudLevels.tankPressure(p), AtmosHudPayload.NO_TANK, "no tank");
        p.setItemInHand(InteractionHand.OFF_HAND, stack(AtmosItems.OXYGEN_TANK));
        close(h, HudLevels.tankPressure(p), 6 * ATM, 5, "the O2 tank's pressure");
        h.assertValueEqual(HudLevels.internals(p), AtmosHudPayload.INTERNALS_OFF, "internals off");
        Internals.set(p, true);
        h.assertValueEqual(HudLevels.internals(p), AtmosHudPayload.INTERNALS_ON, "internals on");
        h.succeed();
    }

    // ---- CONT-090, LIFE-028 ---------------------------------------------------------------------

    /**
     * The softsuit chestplate has a tank slot (CONT-090): a tank right-clicked onto it goes in, and
     * an empty cursor right-clicking it takes it out. Worn, internals draw from the slotted tank
     * before the offhand's and the hotbar's (LIFE-028), and the draw comes out of the tank in the
     * slot.
     */
    @GameTest(template = "empty")
    public static void cont_090_suit_tank_slot(GameTestHelper h) {
        Site s = new Site(h, 43);
        FakePlayer p = player(s, "zas_tank_slot");
        ItemStack chest = stack(AtmosItems.SOFTSUIT_CHESTPLATE);
        h.assertTrue(SuitItem.tank(chest).isEmpty(), "a new chestplate holds a tank");
        h.assertTrue(support(h, chest).internalSupply().isEmpty(), "an empty slot is a supply");

        Slot slot = new Slot(new SimpleContainer(1), 0, 0, 0);
        slot.set(chest);
        ItemStack[] cursor = {stack(AtmosItems.OXYGEN_TANK)};
        SlotAccess access = SlotAccess.of(() -> cursor[0], st -> cursor[0] = st);
        h.assertTrue(
                chest.getItem().overrideOtherStackedOnMe(chest, cursor[0], slot, ClickAction.SECONDARY, p, access),
                "the chestplate refused the tank");
        h.assertTrue(cursor[0].isEmpty(), "the cursor kept the tank");
        h.assertTrue(SuitItem.tank(chest).is(AtmosItems.OXYGEN_TANK.get()), "the slot doesn't hold the tank");
        h.assertFalse(
                chest.getItem()
                        .overrideOtherStackedOnMe(
                                chest, stack(AtmosItems.MULTITOOL), slot, ClickAction.SECONDARY, p, access),
                "the chestplate took a multitool");
        h.assertFalse(
                chest.getItem()
                        .overrideOtherStackedOnMe(
                                chest, stack(AtmosItems.AIR_TANK), slot, ClickAction.PRIMARY, p, access),
                "a left click put a tank in");

        // Worn, with an air tank in the offhand and the hotbar: the slot's O2 tank comes first.
        p.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.SOFTSUIT_HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, chest);
        p.setItemInHand(InteractionHand.OFF_HAND, stack(AtmosItems.AIR_TANK));
        p.getInventory().setItem(0, stack(AtmosItems.AIR_TANK));
        Internals.set(p, true);
        Internals.Active active = Internals.active(p);
        h.assertTrue(active != null, "internals don't work from the slot");
        close(h, active.supply().distributionPressure(), 21.3, 0, "the supply found (the O2 tank's 21.3 kPa)");
        double before = GasTankItem.contents(SuitItem.tank(p.getItemBySlot(EquipmentSlot.CHEST)))
                .totalMoles();
        Internals.breathe(s.level, p, active.supply(), 100);
        double after = GasTankItem.contents(SuitItem.tank(p.getItemBySlot(EquipmentSlot.CHEST)))
                .totalMoles();
        close(h, before - after, Internals.drawRate(21.3, T) * 100, 1e-12, "the slotted tank's draw");
        close(
                h,
                GasTankItem.contents(p.getOffhandItem()).totalMoles(),
                GasTankItem.contents(stack(AtmosItems.AIR_TANK)).totalMoles(),
                0,
                "the offhand tank's draw");

        // Out again, to an empty cursor; then the offhand comes first.
        ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST);
        Slot armor = new Slot(new SimpleContainer(1), 0, 0, 0);
        armor.set(worn);
        cursor[0] = ItemStack.EMPTY;
        h.assertTrue(
                worn.getItem().overrideOtherStackedOnMe(worn, cursor[0], armor, ClickAction.SECONDARY, p, access),
                "the chestplate kept its tank");
        h.assertTrue(cursor[0].is(AtmosItems.OXYGEN_TANK.get()), "the cursor didn't get the tank");
        close(h, GasTankItem.contents(cursor[0]).totalMoles(), after, 1e-12, "the tank taken out");
        h.assertTrue(SuitItem.tank(worn).isEmpty(), "the slot still holds a tank");
        close(h, Internals.find(p).orElseThrow().distributionPressure(), 101.3, 0, "then the offhand's air tank");
        h.succeed();
    }

    // ---- CONT-127 -------------------------------------------------------------------------------

    /**
     * A tank's seconds of breath: what it holds over its distribution pressure, at LIFE-043's 0.5 L per
     * 8 s. A breath that takes it under a minute plays its warning at its next atmos tick, once, at its
     * holder. More breaths don't repeat it, and one filled again can warn again (CONT-127).
     */
    @GameTest(template = "empty", batch = "zas_atmos_device_sounds")
    public static void cont_127_a_tank_warns_once_at_its_last_minute(GameTestHelper h) {
        SoundLog.listen();
        Site s = new Site(h, 809);
        FakePlayer p = player(s, "zas_cont_127_low");
        Vec3 at = p.position();
        ItemStack tank = stack(AtmosItems.OXYGEN_TANK);
        double line = 21.3 + GasTankItem.LOW_SECONDS * 21.3 * (0.5 / 8) / 70;
        GasParcel plenty = GasTankItem.fill(Map.of(StandardAir.OXYGEN, 1.0), line + 0.05, 70);
        GasTankItem.setGas(tank, plenty);
        close(h, GasTankItem.breathSeconds(tank), 60 + 0.05 * 70 / (21.3 * (0.5 / 8)), 1e-6, "seconds of breath");

        h.assertFalse(GasTankItem.atmosTick(tank, s.level, p), "a tank with plenty changed");
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "with plenty");

        double breath =
                0.1 * 70 / (GasMixture.GAS_CONSTANT * GasTankItem.contents(tank).temperature());
        IInternalSupply supply = support(h, tank).internalSupply().orElseThrow();
        supply.draw(breath);
        h.assertTrue(GasTankItem.breathSeconds(tank) < GasTankItem.LOW_SECONDS, "the breath left a minute or more");
        h.assertTrue(GasTankItem.atmosTick(tank, s.level, p), "the warned tank wasn't written back");
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "under a minute", AtmosSounds.TANK_LOW);

        h.assertFalse(GasTankItem.atmosTick(tank, s.level, p), "the tank changed a second time");
        supply.draw(breath);
        h.assertFalse(GasTankItem.atmosTick(tank, s.level, p), "another breath warned again");
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "still under a minute");

        GasTankItem.setGas(tank, plenty);
        supply.draw(breath);
        GasTankItem.atmosTick(tank, s.level, p);
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "filled and run down again", AtmosSounds.TANK_LOW);

        GasTankItem.setDistributionPressure(tank, 0);
        h.assertTrue(GasTankItem.breathSeconds(tank) == Double.POSITIVE_INFINITY, "a closed tank runs out");
        h.succeed();
    }

    /**
     * A tank's settings play its closing as its distribution pressure reaches 0, and its opening as it
     * leaves 0, and nothing for a change while it stays open or shut (CONT-127).
     */
    @GameTest(template = "empty", batch = "zas_atmos_device_sounds")
    public static void cont_127_a_tank_plays_as_its_supply_closes_and_opens(GameTestHelper h) {
        SoundLog.listen();
        Site s = new Site(h, 810);
        FakePlayer p = player(s, "zas_cont_127_valve");
        Vec3 at = p.position();
        ItemStack tank = stack(AtmosItems.OXYGEN_TANK);
        int main = InteractionHand.MAIN_HAND.ordinal();

        TankSettings.apply(p, tank, new TankSettings(main, TankSettings.UP_1));
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "turned up");
        TankSettings.apply(p, tank, new TankSettings(main, TankSettings.CLOSE));
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "closed", AtmosSounds.TANK_CLOSE);
        TankSettings.apply(p, tank, new TankSettings(main, TankSettings.CLOSE));
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "closed again");
        TankSettings.apply(p, tank, new TankSettings(main, TankSettings.UP_1));
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "raised from 0", AtmosSounds.TANK_OPEN);
        TankSettings.apply(p, tank, new TankSettings(main, TankSettings.DOWN_10));
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "lowered past 0", AtmosSounds.TANK_CLOSE);
        TankSettings.apply(p, tank, new TankSettings(main, TankSettings.DEFAULT));
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "back to the default", AtmosSounds.TANK_OPEN);

        h.assertFalse(TankSettings.apply(p, tank, new TankSettings(main, 6)), "an unknown button was taken");
        DeviceSoundTests.heard(h, at, SoundSource.PLAYERS, "an unknown button");
        h.succeed();
    }
}
