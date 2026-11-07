package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.canister.CanisterColor;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.gear.Encumbrance;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.gear.SuitItem;
import io.github.west3436.zas.atmos.gear.TankSettings;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.gametest.TestPlayers;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.LifeDamage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The breathing gear: gas tanks (CONT-030, LIFE-044), filling them from a canister (CONT-039), the
 * breath and gas masks, softsuit and hardsuit (CONT-031, CONT-032, CONT-036, CONT-088, CONT-089,
 * LIFE-021) and the suits' tank slots (CONT-090, LIFE-028). Those that need a place build in their
 * own {@link Site} (areas 40 and up, and 2700 and up). S-08 is in {@link StationTests}.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GearTests {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
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
     * Internals work with the breath mask, the gas mask and each suit helmet on the head, and not with
     * nothing there or with only the rest of either suit (LIFE-021, CONT-031, CONT-088).
     */
    @GameTest(template = "empty")
    public static void cont_031_internals_with_each_sealed_head(GameTestHelper h) {
        Site s = new Site(h, 42);
        FakePlayer p = player(s, "zas_sealed_heads");
        p.setItemInHand(InteractionHand.OFF_HAND, stack(AtmosItems.OXYGEN_TANK));
        Internals.set(p, true);
        h.assertTrue(Internals.active(p) == null, "internals work bare-headed");
        for (String suit : new String[] {"softsuit", "hardsuit"}) {
            boolean soft = suit.equals("softsuit");
            p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            p.setItemSlot(
                    EquipmentSlot.CHEST, stack(soft ? AtmosItems.SOFTSUIT_CHESTPLATE : AtmosItems.HARDSUIT_CHESTPLATE));
            p.setItemSlot(
                    EquipmentSlot.LEGS, stack(soft ? AtmosItems.SOFTSUIT_LEGGINGS : AtmosItems.HARDSUIT_LEGGINGS));
            p.setItemSlot(EquipmentSlot.FEET, stack(soft ? AtmosItems.SOFTSUIT_BOOTS : AtmosItems.HARDSUIT_BOOTS));
            h.assertTrue(Internals.active(p) == null, "internals work with the " + suit + " but no helmet");
        }
        Map<String, java.util.function.Supplier<SuitItem>> heads = Map.of(
                "the breath mask", AtmosItems.BREATH_MASK,
                "the gas mask", AtmosItems.GAS_MASK,
                "the softsuit helmet", AtmosItems.SOFTSUIT_HELMET,
                "the hardsuit helmet", AtmosItems.HARDSUIT_HELMET);
        heads.forEach((name, item) -> {
            p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(item.get()));
            h.assertTrue(Internals.active(p) != null, "internals don't work with " + name);
        });
        h.succeed();
    }

    // ---- CONT-090, LIFE-028 ---------------------------------------------------------------------

    /**
     * The softsuit and hardsuit chestplates each have a tank slot (CONT-090): a tank right-clicked
     * onto one goes in, and an empty cursor right-clicking it takes it out. Worn, internals draw from
     * the slotted tank before the offhand's and the hotbar's (LIFE-028), and the draw comes out of
     * the tank in the slot.
     */
    @GameTest(template = "empty")
    public static void cont_090_suit_tank_slot(GameTestHelper h) {
        Site s = new Site(h, 43);
        tankSlot(h, player(s, "zas_tank_slot"), AtmosItems.SOFTSUIT_HELMET, AtmosItems.SOFTSUIT_CHESTPLATE);
        tankSlot(h, player(s, "zas_hardsuit_tank_slot"), AtmosItems.HARDSUIT_HELMET, AtmosItems.HARDSUIT_CHESTPLATE);
        h.succeed();
    }

    private static void tankSlot(
            GameTestHelper h,
            FakePlayer p,
            java.util.function.Supplier<SuitItem> helmet,
            java.util.function.Supplier<SuitItem> chestplate) {
        ItemStack chest = stack(chestplate);
        h.assertTrue(((SuitItem) chest.getItem()).hasTankSlot(), chest + " has no tank slot");
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
        p.setItemSlot(EquipmentSlot.HEAD, stack(helmet));
        p.setItemSlot(EquipmentSlot.CHEST, chest);
        p.setItemInHand(InteractionHand.OFF_HAND, stack(AtmosItems.AIR_TANK));
        p.getInventory().setItem(0, stack(AtmosItems.AIR_TANK));
        Internals.set(p, true);
        Internals.Active active = Internals.active(p);
        h.assertTrue(active != null, "internals don't work from the slot");
        close(h, active.supply().distributionPressure(), 21.3, 0, "the supply found (the O2 tank's 21.3 kPa)");
        double before = GasTankItem.contents(SuitItem.tank(p.getItemBySlot(EquipmentSlot.CHEST)))
                .totalMoles();
        Internals.breathe(p.serverLevel(), p, active.supply(), 100);
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
    }

    /**
     * A softsuit chestplate crafted into a hardsuit chestplate leaves the tank it held in the grid, as
     * a bucket is left, rather than losing it (CONT-090). An empty one leaves nothing.
     */
    @GameTest(template = "empty")
    public static void cont_090_crafting_a_chestplate_leaves_its_tank(GameTestHelper h) {
        ItemStack soft = stack(AtmosItems.SOFTSUIT_CHESTPLATE);
        ItemStack tank = stack(AtmosItems.OXYGEN_TANK);
        SuitItem.setTank(soft, tank);
        ItemStack plasteel = stack(AtmosItems.PLASTEEL_SHEET);
        List<ItemStack> grid = new ArrayList<>(List.of(
                plasteel,
                ItemStack.EMPTY,
                plasteel.copy(),
                plasteel.copy(),
                soft,
                plasteel.copy(),
                plasteel.copy(),
                plasteel.copy(),
                plasteel.copy()));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        ServerLevel level = h.getLevel();
        RecipeHolder<CraftingRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .orElse(null);
        h.assertTrue(recipe != null, "nothing crafts the hardsuit chestplate");
        h.assertTrue(
                recipe.value().assemble(input, level.registryAccess()).is(AtmosItems.HARDSUIT_CHESTPLATE.get()),
                "the recipe doesn't make a hardsuit chestplate");
        List<ItemStack> left = recipe.value().getRemainingItems(input);
        ItemStack back = left.get(4);
        h.assertTrue(back.is(AtmosItems.OXYGEN_TANK.get()), "the grid kept " + back + ", not the tank");
        close(
                h,
                GasTankItem.contents(back).totalMoles(),
                GasTankItem.contents(tank).totalMoles(),
                0,
                "its gas");
        for (int i = 0; i < left.size(); i++) {
            h.assertTrue(i == 4 || left.get(i).isEmpty(), "slot " + i + " kept " + left.get(i));
        }

        grid.set(4, stack(AtmosItems.SOFTSUIT_CHESTPLATE));
        input = CraftingInput.of(3, 3, grid);
        h.assertTrue(
                recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),
                "an empty one left something");
        h.succeed();
    }

    // ---- CONT-088 -------------------------------------------------------------------------------

    /**
     * A gas-masked player in a CO zone takes no CO harm but still suffers hypoxia without O2
     * (CONT-088). In a sealed room of air with 1 kPa of CO, its breath has no CO, it is
     * {@code BREATHABLE} with {@code GAS_FILTERING} listed, and two seconds there leave it unhurt,
     * while a bare player beside it is harmed. With the room's O2 taken away, the CO stays filtered,
     * but its breath is short of O2: its air meter drains, and then hypoxia hurts it.
     */
    @GameTest(template = "empty")
    public static void cont_088_a_gas_masked_player_takes_no_co_harm_but_still_suffers_hypoxia(GameTestHelper h) {
        ILifeSupport mask = support(h, stack(AtmosItems.GAS_MASK));
        h.assertTrue(mask.sealedBreathing() && mask.gasFiltering(), "the gas mask doesn't seal and filter");
        close(h, mask.minPressure(), ATM, 0, "the gas mask's low-pressure rating");
        close(h, mask.maxPressure(), ATM, 0, "the gas mask's high-pressure rating");
        close(h, mask.minTemperature(), T, 0, "the gas mask's cold rating");
        close(h, mask.maxTemperature(), T, 0, "the gas mask's heat rating");
        h.assertTrue(mask.internalSupply().isEmpty(), "the gas mask supplies gas");
        h.assertFalse(stack(AtmosItems.GAS_MASK).isDamageableItem(), "the gas mask wears out");

        Site s = new Site(h, 2700);
        try {
            s.clear();
            s.room(0, 4); // interior x 1–3, y 1–3, z 1–3
            s.settle(GasMutations.remove(s.level, s.point(2, 2, 2), Map.of(N2, 1e6, O2, 1e6), Site.SOURCE));
            double air = ATM * 27_000 / (GasMixture.GAS_CONSTANT * T);
            double co = 1.0 * 27_000 / (GasMixture.GAS_CONSTANT * T);
            s.settle(GasMutations.add(
                    s.level,
                    s.point(2, 2, 2),
                    new GasParcel(Map.of(N2, air * 0.79, O2, air * 0.21, CO, co), T),
                    Site.SOURCE));
            s.close(s.zone(2, 2, 2).mixture().partialPressure(CO), 1.0, 1e-9, "the room's CO");

            FakePlayer masked = TestPlayers.hurtable(s.level, "zas_gas_mask");
            masked.moveTo(Vec3.atBottomCenterOf(s.abs(2, 1, 2)));
            masked.setItemSlot(EquipmentSlot.HEAD, stack(AtmosItems.GAS_MASK));
            FakePlayer bare = TestPlayers.hurtable(s.level, "zas_gas_mask_bare");
            bare.moveTo(Vec3.atBottomCenterOf(s.abs(2, 1, 3)));

            // With O2: the CO is filtered out, and nothing harms the masked player.
            Breathability v = Breathing.of(masked);
            h.assertTrue(v.protection().contains(Protection.GAS_FILTERING), "the masked protection: " + v);
            h.assertFalse(v.hazards().contains(Hazard.TOXIC_GAS), "the masked hazards: " + v);
            h.assertValueEqual(v.reason(), BreathReason.BREATHABLE, "the masked player's breath");
            close(h, v.breath().orElseThrow().moles(CO), 0, 0, "CO in the masked breath");
            h.assertTrue(Breathing.of(bare).hazards().contains(Hazard.TOXIC_GAS), "the CO doesn't harm a bare player");
            long t0 = s.level.getGameTime();
            for (FakePlayer p : List.of(masked, bare)) {
                Breathers.checkAt(p, t0);
                Breathers.checkAt(p, t0 + 40);
            }
            close(h, masked.getHealth(), masked.getMaxHealth(), 0, "the masked player's health in the CO");
            h.assertTrue(
                    masked.getActiveEffects().isEmpty(), "the masked player's effects: " + masked.getActiveEffects());
            h.assertTrue(bare.getHealth() < bare.getMaxHealth(), "the CO didn't harm the bare player");

            // Without O2: still no CO, but nothing to breathe.
            s.removeGas(2, 2, 2, O2);
            s.close(s.zone(2, 2, 2).mixture().partialPressure(CO), 1.0, 1e-9, "the room's CO without O2");
            Breathability gasping = Breathers.checkAt(masked, t0 + 80).verdict();
            h.assertValueEqual(gasping.reason(), BreathReason.LOW_OXYGEN, "the masked player's breath without O2");
            h.assertFalse(gasping.hazards().contains(Hazard.TOXIC_GAS), "the masked hazards without O2: " + gasping);
            h.assertTrue(gasping.protection().contains(Protection.GAS_FILTERING), "the masked protection: " + gasping);
            close(h, gasping.breath().orElseThrow().moles(CO), 0, 0, "CO in the breath without O2");
            close(h, masked.getHealth(), masked.getMaxHealth(), 0, "the masked player's health from the CO");
            int air0 = masked.getAirSupply();
            for (int i = 0; i < AtmosTicks.PERIOD; i++) {
                masked.baseTick();
            }
            h.assertTrue(masked.getAirSupply() < air0, "the air meter didn't drain: " + masked.getAirSupply());
            masked.setAirSupply(0);
            masked.invulnerableTime = 0;
            for (int i = 0; i < 2 * AtmosTicks.PERIOD && masked.getHealth() >= masked.getMaxHealth(); i++) {
                masked.baseTick();
            }
            h.assertTrue(masked.getHealth() < masked.getMaxHealth(), "no hypoxia without O2");
            h.assertTrue(
                    masked.getLastDamageSource() != null
                            && masked.getLastDamageSource().is(LifeDamage.HYPOXIA),
                    "the harm wasn't hypoxia: " + masked.getLastDamageSource());
        } finally {
            s.clear();
        }
        h.succeed();
    }

    // ---- CONT-036 -------------------------------------------------------------------------------

    /**
     * Each hardsuit piece is rated for every pressure and temperature, and the helmet seals
     * (CONT-036). A hardsuited player is protected in vacuum, at 2,000 kPa and at 1,500 K, and in
     * the cold, where a bare player beside it is harmed. The pieces have no durability, as the
     * softsuit's haven't.
     */
    @GameTest(template = "empty")
    public static void cont_036_a_hardsuited_player_is_protected_at_every_extreme(GameTestHelper h) {
        Map<EquipmentSlot, java.util.function.Supplier<SuitItem>> suit = Map.of(
                EquipmentSlot.HEAD, AtmosItems.HARDSUIT_HELMET,
                EquipmentSlot.CHEST, AtmosItems.HARDSUIT_CHESTPLATE,
                EquipmentSlot.LEGS, AtmosItems.HARDSUIT_LEGGINGS,
                EquipmentSlot.FEET, AtmosItems.HARDSUIT_BOOTS);
        suit.forEach((slot, item) -> {
            ItemStack piece = new ItemStack(item.get());
            ILifeSupport r = support(h, piece);
            close(h, r.minPressure(), 0, 0, slot + ": low pressure");
            h.assertTrue(r.maxPressure() == Double.POSITIVE_INFINITY, slot + ": high pressure " + r.maxPressure());
            close(h, r.minTemperature(), 0, 0, slot + ": cold");
            h.assertTrue(r.maxTemperature() == Double.POSITIVE_INFINITY, slot + ": heat " + r.maxTemperature());
            h.assertValueEqual(r.sealedBreathing(), slot == EquipmentSlot.HEAD, slot + ": the seal");
            h.assertFalse(r.gasFiltering(), slot + " filters");
            h.assertValueEqual(item.get().getEquipmentSlot(), slot, slot + ": its slot");
            h.assertFalse(piece.isDamageableItem(), slot + " wears out");
        });

        Site s = new Site(h, 2701);
        FakePlayer p = player(s, "zas_hardsuit");
        suit.forEach((slot, item) -> p.setItemSlot(slot, new ItemStack(item.get())));
        FakePlayer bare = player(s, "zas_hardsuit_bare");
        GasMixture vacuum = new GasMixture(Map.of(), T, StandardAir.CELL_VOLUME);
        close(h, EnvironmentHazards.exposure(p, vacuum).decompression(), 0, 0, "decompression in vacuum");
        h.assertTrue(EnvironmentHazards.exposure(bare, vacuum).decompression() > 0, "the vacuum harms no one");
        close(h, EnvironmentHazards.exposure(p, nitrogen(2_000, T)).barotrauma(), 0, 0, "barotrauma at 2,000 kPa");
        h.assertTrue(EnvironmentHazards.exposure(bare, nitrogen(2_000, T)).barotrauma() > 0, "2,000 kPa harms no one");
        close(h, EnvironmentHazards.exposure(p, nitrogen(ATM, 1_500)).burns(), 0, 0, "burns at 1,500 K");
        h.assertTrue(EnvironmentHazards.exposure(bare, nitrogen(ATM, 1_500)).burns() > 0, "1,500 K harms no one");
        close(h, EnvironmentHazards.exposure(p, nitrogen(ATM, 50)).frostbite(), 0, 0, "frostbite at 50 K");
        h.assertTrue(EnvironmentHazards.exposure(bare, nitrogen(ATM, 50)).frostbite() > 0, "50 K harms no one");
        h.succeed();
    }

    // ---- CONT-089 -------------------------------------------------------------------------------

    /**
     * Each hardsuit piece worn cuts movement speed by 10 % (CONT-089): one to four pieces leave 90,
     * 80, 70 and 60 %, a softsuit piece nothing, and taking them off restores it. The share is
     * {@code [gear] hardsuit_slowdown}, read when the speed is next set, and the server sets it when
     * the equipment changes. A mob in a hardsuit is slowed too.
     */
    @GameTest(template = "empty")
    public static void cont_089_each_hardsuit_piece_cuts_speed_by_10_percent(GameTestHelper h) {
        Site s = new Site(h, 2702);
        FakePlayer p = player(s, "zas_hardsuit_speed");
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        double base = speed.getValue();
        close(h, base, speed.getBaseValue(), 0, "the speed before");
        List<Map.Entry<EquipmentSlot, java.util.function.Supplier<SuitItem>>> pieces = List.of(
                Map.entry(EquipmentSlot.HEAD, AtmosItems.HARDSUIT_HELMET),
                Map.entry(EquipmentSlot.CHEST, AtmosItems.HARDSUIT_CHESTPLATE),
                Map.entry(EquipmentSlot.LEGS, AtmosItems.HARDSUIT_LEGGINGS),
                Map.entry(EquipmentSlot.FEET, AtmosItems.HARDSUIT_BOOTS));
        double before = AtmosServerConfig.HARDSUIT_SLOWDOWN.get();
        try {
            close(h, before, 0.1, 0, "the default slowdown");
            for (int n = 1; n <= 4; n++) {
                var piece = pieces.get(n - 1);
                ItemStack stack = new ItemStack(piece.getValue().get());
                p.setItemSlot(piece.getKey(), stack);
                // The game posts this when it sees the change, on the server.
                NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(p, piece.getKey(), ItemStack.EMPTY, stack));
                close(h, speed.getValue(), base * (1 - 0.1 * n), 1e-12, n + " pieces");
            }

            // The setting reaches what is worn at the next update.
            AtmosServerConfig.HARDSUIT_SLOWDOWN.set(0.05);
            Encumbrance.update(p);
            close(h, speed.getValue(), base * 0.8, 1e-12, "4 pieces at 5 %");
            AtmosServerConfig.HARDSUIT_SLOWDOWN.set(0.0);
            Encumbrance.update(p);
            close(h, speed.getValue(), base, 1e-12, "4 pieces at 0");
            h.assertTrue(speed.getModifier(Encumbrance.MODIFIER) == null, "a slowdown of 0 left a modifier");
            AtmosServerConfig.HARDSUIT_SLOWDOWN.set(0.1);

            // A softsuit piece doesn't slow; taking the suit off restores the speed.
            p.setItemSlot(EquipmentSlot.FEET, stack(AtmosItems.SOFTSUIT_BOOTS));
            Encumbrance.update(p);
            close(h, speed.getValue(), base * 0.7, 1e-12, "3 hardsuit pieces and softsuit boots");
            for (var piece : pieces) {
                p.setItemSlot(piece.getKey(), ItemStack.EMPTY);
            }
            Encumbrance.update(p);
            close(h, speed.getValue(), base, 0, "the suit taken off");

            Zombie zombie = EntityType.ZOMBIE.create(s.level);
            h.assertTrue(zombie != null, "no zombie");
            AttributeInstance its = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            double zombieBase = its.getValue();
            pieces.forEach(piece -> zombie.setItemSlot(
                    piece.getKey(), new ItemStack(piece.getValue().get())));
            Encumbrance.update(zombie);
            close(h, its.getValue(), zombieBase * 0.6, 1e-12, "the hardsuited zombie's speed");
            zombie.discard();
        } finally {
            AtmosServerConfig.HARDSUIT_SLOWDOWN.set(before);
        }
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
