package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Respiration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Internals (LIFE-021, LIFE-028, LIFE-029, LIFE-043 to LIFE-046) with the GameTest tanks of {@link
 * TestLifeSupport}: the test tank item, sealed by the test suit piece, and a zombie's own tank. The
 * content mod's tanks, mask and suit have their own tests there. Each test builds in its own area
 * ({@link Rooms}, areas 270 and up) and runs within one game tick.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InternalsTests {
    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");

    /** 1 mol of O2 in the test tank's 10 L: about 244 kPa. */
    private static final Map<ResourceLocation, Double> FULL = Map.of(O2, 1.0);

    private InternalsTests() {}

    private static final class Lab implements AutoCloseable {
        final Rooms r;
        final GameTestHelper h;
        final List<Entity> entities = new ArrayList<>();

        Lab(GameTestHelper h, int index) {
            this.h = h;
            this.r = new Rooms(h, index);
            r.clear(0, 16);
        }

        /** A sealed 3 × 3 × 3 room from x0 to x0 + 4 holding these moles at 293.15 K. */
        ZoneInfo room(int x0, Map<ResourceLocation, Double> moles) {
            r.shell(x0, x0 + 4);
            r.settle(GasMutations.remove(
                    r.level, r.point(x0 + 2, 2, 2), Map.of(N2, 1e6, O2, 1e6), Rooms.source("internals")));
            r.settle(GasMutations.add(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    new GasParcel(moles, StandardAir.TEMPERATURE),
                    Rooms.source("internals")));
            return r.zone(x0 + 2, 2, 2);
        }

        /** A survival fake player standing at (x, 1, 2). */
        FakePlayer player(int x) {
            FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_internals"));
            p.moveTo(Vec3.atBottomCenterOf(r.abs(x, 1, 2)));
            return p;
        }

        Zombie zombie(int x) {
            Zombie z = EntityType.ZOMBIE.create(r.level);
            h.assertTrue(z != null, "couldn't create a zombie");
            z.moveTo(Vec3.atBottomCenterOf(r.abs(x, 1, 2)));
            z.setNoAi(true);
            r.level.addFreshEntity(z);
            entities.add(z);
            return z;
        }

        @Override
        public void close() {
            entities.forEach(Entity::discard);
        }
    }

    private static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }

    private static void seal(FakePlayer p) {
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
    }

    // ---- LIFE-029 -----------------------------------------------------------------------------

    /** The toggle's handler switches the sender's own internals, and refuses a spectator or a dead sender (NFR-011). */
    @GameTest(template = "empty")
    public static void nfr_011_only_a_living_player_toggles_their_own_internals(GameTestHelper h) {
        FakePlayer sender = TestPlayers.hurtable(h.getLevel(), "zas_toggle_sender");
        FakePlayer other = TestPlayers.hurtable(h.getLevel(), "zas_toggle_other");
        Internals.handleToggle(Internals.TogglePayload.INSTANCE, TestPlayers.sentBy(sender));
        h.assertTrue(Internals.on(sender), "the toggle didn't reach the sender");
        h.assertFalse(Internals.on(other), "the toggle reached another player");

        sender.setGameMode(GameType.SPECTATOR);
        Internals.handleToggle(Internals.TogglePayload.INSTANCE, TestPlayers.sentBy(sender));
        h.assertTrue(Internals.on(sender), "a spectator toggled");

        sender.setGameMode(GameType.SURVIVAL);
        sender.setHealth(0);
        Internals.handleToggle(Internals.TogglePayload.INSTANCE, TestPlayers.sentBy(sender));
        h.assertTrue(Internals.on(sender), "a dead player toggled");
        h.succeed();
    }

    /**
     * Internals are off until toggled, stay as toggled, and are saved with the entity (LIFE-029).
     * The keybind sends the toggle; Manual checks it.
     */
    @GameTest(template = "empty")
    public static void life_029_internals_toggle_and_persist(GameTestHelper h) {
        try (Lab lab = new Lab(h, 270)) {
            FakePlayer p = lab.player(2);
            h.assertFalse(Internals.on(p), "a new player's internals are on");
            h.assertTrue(Internals.toggle(p), "toggling didn't switch them on");
            h.assertTrue(Internals.on(p), "they didn't stay on");
            h.assertFalse(Internals.toggle(p), "toggling again didn't switch them off");
            h.assertFalse(Internals.on(p), "they didn't stay off");

            Zombie z = lab.zombie(2);
            Internals.set(z, true);
            CompoundTag saved = z.saveWithoutId(new CompoundTag());
            Zombie loaded = EntityType.ZOMBIE.create(lab.r.level);
            h.assertTrue(loaded != null, "couldn't create a zombie");
            loaded.load(saved);
            h.assertTrue(Internals.on(loaded), "internals switched on weren't saved");
            loaded.discard();
        }
        h.succeed();
    }

    // ---- LIFE-021 -----------------------------------------------------------------------------

    /**
     * Internals work only with sealed breathing (LIFE-021): with a full tank in the offhand and
     * internals on, a player with nothing on its head breathes the air around it; with the sealed
     * test piece on its head, it breathes the tank's O2 at the distribution pressure, and its verdict
     * says so.
     */
    @GameTest(template = "empty")
    public static void life_021_internals_need_a_seal(GameTestHelper h) {
        try (Lab lab = new Lab(h, 271)) {
            FakePlayer p = lab.player(2);
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(FULL, 21.3));
            Internals.set(p, true);
            Breathability open = BreathingRules.assess(p).verdict();
            h.assertValueEqual(open.source(), BreathSource.ATMOSPHERE, "the unsealed player's breath");
            h.assertTrue(Internals.active(p) == null, "internals work without a seal");

            seal(p);
            Internals.Active active = Internals.active(p);
            h.assertTrue(active != null, "internals don't work with a seal");
            BreathingRules.Assessment a = BreathingRules.assess(p);
            h.assertValueEqual(a.verdict().source(), BreathSource.INTERNALS, "the sealed player's breath");
            h.assertValueEqual(a.verdict().reason(), BreathReason.BREATHABLE, "the sealed player's verdict");
            h.assertTrue(a.verdict().protection().contains(Protection.SEALED_BREATHING), "no SEALED_BREATHING");
            GasMixture breath = a.verdict().breath().orElseThrow();
            close(h, breath.pressure(), 21.3, 1e-9, "the breath's pressure (LIFE-044)");
            close(h, breath.partialPressure(O2), 21.3, 1e-9, "the breath's O2");

            Internals.set(p, false);
            h.assertValueEqual(BreathingRules.assess(p).verdict().source(), BreathSource.ATMOSPHERE, "internals off");
        }
        h.succeed();
    }

    // ---- LIFE-028 -----------------------------------------------------------------------------

    /**
     * Internals draw from the first tank found (LIFE-028): an armor slot's (a suit's tank slot),
     * then the offhand, then the hotbar from left to right, then the entity's own; a mob's main hand
     * stands in for the hotbar. The first one found counts even if it can't deliver, and then the
     * entity breathes the air (LIFE-046).
     */
    @GameTest(template = "empty")
    public static void life_028_the_first_tank_found(GameTestHelper h) {
        try (Lab lab = new Lab(h, 272)) {
            FakePlayer p = lab.player(2);
            seal(p);
            p.setItemSlot(EquipmentSlot.CHEST, TestLifeSupport.tank(FULL, 30));
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(FULL, 40));
            p.getInventory().setItem(0, TestLifeSupport.tank(FULL, 50));
            p.getInventory().setItem(4, TestLifeSupport.tank(FULL, 60));
            p.getInventory().setItem(9, TestLifeSupport.tank(FULL, 70)); // not the hotbar
            double[] expected = {30, 40, 50, 60};
            Runnable[] remove = {
                () -> p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY),
                () -> p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY),
                () -> p.getInventory().setItem(0, ItemStack.EMPTY),
                () -> p.getInventory().setItem(4, ItemStack.EMPTY)
            };
            for (int i = 0; i < expected.length; i++) {
                Optional<IInternalSupply> found = Internals.find(p);
                h.assertTrue(found.isPresent(), "no tank found, step " + i);
                close(h, found.get().distributionPressure(), expected[i], 0, "the tank found, step " + i);
                remove[i].run();
            }
            h.assertTrue(Internals.find(p).isEmpty(), "a tank outside the hotbar was found");

            // An empty tank found first stops the search, and the player breathes the air.
            Internals.set(p, true);
            p.setItemSlot(EquipmentSlot.CHEST, TestLifeSupport.tank(Map.of(), 21.3));
            p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(FULL, 21.3));
            h.assertTrue(Internals.active(p) == null, "internals passed over the empty tank found first");
            h.assertValueEqual(
                    BreathingRules.assess(p).verdict().source(), BreathSource.ATMOSPHERE, "behind an empty tank");

            // A mob: its own tank last, after its main hand.
            Zombie z = lab.zombie(4);
            z.addTag(TestLifeSupport.ENTITY_TAG);
            z.addTag(TestLifeSupport.OWN_TANK_TAG);
            close(h, Internals.find(z).orElseThrow().distributionPressure(), 21.3, 0, "the zombie's own tank");
            z.setItemInHand(InteractionHand.MAIN_HAND, TestLifeSupport.tank(FULL, 45));
            close(h, Internals.find(z).orElseThrow().distributionPressure(), 45, 0, "the zombie's main hand");
        }
        h.succeed();
    }

    // ---- LIFE-043 -----------------------------------------------------------------------------

    /**
     * Internals draw P_dist × 0.0625 L / (R · T) each second (LIFE-043), whatever the cadence: one
     * hour's draw at once is 1,800 checks' worth, and checks 2 s apart draw 2 s each. At the O2
     * tank's default 21.3 kPa, its 17.5 mol last about 9 hours, as in vg.
     */
    @GameTest(template = "empty")
    public static void life_043_draw_rate(GameTestHelper h) {
        try (Lab lab = new Lab(h, 273)) {
            double rate = 21.3 * 0.0625 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            close(h, Internals.drawRate(21.3, StandardAir.TEMPERATURE), rate, 1e-15, "the rate");
            close(h, Internals.drawRate(0, StandardAir.TEMPERATURE), 0, 0, "the rate at 0 kPa");
            // 70 L of O2 down from 17.5 mol to the 21.3 kPa it must keep.
            double floor = 21.3 * 70 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            double hours = (17.5 - floor) / rate / 3600;
            h.assertTrue(hours > 8.5 && hours < 9.5, "an O2 tank lasts " + hours + " h");

            FakePlayer p = lab.player(2); // in the open air: the exhale goes nowhere
            seal(p);
            ItemStack once = TestLifeSupport.tank(Map.of(O2, 5.0), 21.3);
            GasParcel hour = Internals.breathe(lab.r.level, p, supply(once), 3600);
            close(h, 5 - TestLifeSupport.contents(once).totalMoles(), rate * 3600, 1e-12, "an hour's draw");
            close(h, hour.totalMoles(), rate * 3600, 1e-12, "an hour's exhale");

            // Through the checks: internals on, three checks 2 s apart draw 4 s.
            ItemStack tank = TestLifeSupport.tank(FULL, 21.3);
            p.setItemInHand(InteractionHand.OFF_HAND, tank);
            Internals.set(p, true);
            long t0 = lab.r.level.getGameTime();
            for (int k = 0; k < 3; k++) {
                Breathers.checkAt(p, t0 + 40L * k);
            }
            ItemStack held = p.getItemInHand(InteractionHand.OFF_HAND);
            close(h, 1 - TestLifeSupport.contents(held).totalMoles(), rate * 4, 1e-12, "three checks' draw");
            h.assertValueEqual(
                    Breathers.verdict(p).source(), BreathSource.INTERNALS, "the verdict while breathing a tank");
        }
        h.succeed();
    }

    private static IInternalSupply supply(ItemStack tank) {
        return tank.getCapability(io.github.west3436.zas.api.capability.ILifeSupport.ITEM)
                .internalSupply()
                .orElseThrow();
    }

    // ---- LIFE-045 -----------------------------------------------------------------------------

    /**
     * The gas internals draw is exhaled into the zone around the breather, after respiration
     * (LIFE-045): in a room of N2, a player breathing an O2 tank for 60 s puts the O2 drawn into the
     * zone, the 60 s of respiration's O2 turned into CO2, and the zone gains exactly what the tank
     * lost.
     */
    @GameTest(template = "empty")
    public static void life_045_exhaled_gas(GameTestHelper h) {
        try (Lab lab = new Lab(h, 274)) {
            ZoneInfo before = lab.room(0, Map.of(N2, 1000.0));
            FakePlayer p = lab.player(2);
            seal(p);
            ItemStack tank = TestLifeSupport.tank(FULL, 21.3);
            double drawn = Internals.drawRate(21.3, StandardAir.TEMPERATURE) * 60;
            double used = Math.min(drawn, BreathingConfig.respirationMolesPerSecond() * 60);
            GasParcel out = Internals.breathe(lab.r.level, p, supply(tank), 60);
            close(h, out.moles(O2), drawn - used, 1e-12, "the O2 exhaled");
            close(h, out.moles(Respiration.CARBON_DIOXIDE), used, 1e-12, "the CO2 exhaled");
            lab.r.ticks(2);
            ZoneInfo after = lab.r.zone(2, 2, 2);
            h.assertValueEqual(after.id(), before.id(), "the zone");
            close(h, after.mixture().moles(O2), drawn - used, 1e-12, "the zone's O2");
            close(h, after.mixture().moles(Respiration.CARBON_DIOXIDE), used, 1e-12, "the zone's CO2");
            close(
                    h,
                    after.mixture().totalMoles() - before.mixture().totalMoles(),
                    1 - TestLifeSupport.contents(tank).totalMoles(),
                    1e-9,
                    "the zone's gain against the tank's loss");
        }
        h.succeed();
    }

    // ---- LIFE-046 -----------------------------------------------------------------------------

    /**
     * When the tank can't deliver its distribution pressure, the wearer breathes the environment
     * (LIFE-046): a tank set above its own pressure, an emptied tank and one set to 0 kPa each leave
     * a sealed player in the open air breathing it, and in a CO room, breathing the CO. With a full
     * tank, the CO room's toxic gas doesn't reach it.
     */
    @GameTest(template = "empty")
    public static void life_046_empty_tank_fallback(GameTestHelper h) {
        try (Lab lab = new Lab(h, 275)) {
            FakePlayer p = lab.player(10); // in the open air
            seal(p);
            Internals.set(p, true);
            for (ItemStack tank : List.of(
                    TestLifeSupport.tank(FULL, 300), // the tank holds about 244 kPa
                    TestLifeSupport.tank(Map.of(O2, 0.05), 21.3),
                    TestLifeSupport.tank(Map.of(), 21.3),
                    TestLifeSupport.tank(FULL, 0))) {
                p.setItemInHand(InteractionHand.OFF_HAND, tank);
                Breathability v = BreathingRules.assess(p).verdict();
                h.assertValueEqual(v.source(), BreathSource.ATMOSPHERE, "the fallback source");
                h.assertValueEqual(v.reason(), BreathReason.BREATHABLE, "the fallback in air");
            }

            // A room of air with 1 kPa of CO, which is toxic.
            double air = StandardAir.PRESSURE * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            double co = 1.0 * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            lab.room(0, Map.of(N2, air * 0.79, O2, air * 0.21, CO, co));
            FakePlayer in = lab.player(2);
            in.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN)); // seals, and doesn't filter
            Internals.set(in, true);
            in.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(FULL, 21.3));
            Breathability safe = BreathingRules.assess(in).verdict();
            h.assertValueEqual(safe.source(), BreathSource.INTERNALS, "in the CO room with a full tank");
            h.assertFalse(safe.hazards().contains(Hazard.TOXIC_GAS), "the CO reached the tank's breath");
            in.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.tank(Map.of(), 21.3));
            Breathability dry = BreathingRules.assess(in).verdict();
            h.assertValueEqual(dry.source(), BreathSource.ATMOSPHERE, "in the CO room with an empty tank");
            h.assertTrue(dry.hazards().contains(Hazard.TOXIC_GAS), "the CO room isn't toxic");
        }
        h.succeed();
    }
}
