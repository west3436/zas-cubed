package io.github.west3436.zas.compat.northstar.gametest;

import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import com.lightning.northstar.world.sealer.ProgressiveBlockUpdater;
import com.lightning.northstar.world.temperature.NorthstarTemperature;
import io.github.west3436.zas.api.atmosphere.AtmospherePreset;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.Millibuckets;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.northstar.NorthstarCompat;
import io.github.west3436.zas.compat.northstar.NorthstarVersions;
import io.github.west3436.zas.compat.northstar.mixin.NorthstarMixinPlugin;
import io.github.west3436.zas.life.Protections;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Northstar adapter (SPACE-011, S-06): on Northstar's Moon a sealed, pressurized ZAS³ room is
 * oxygen to Northstar's own blocks and breathable to entities, what Northstar burns there comes out
 * of the room, its planets seed ZAS³'s ambients, its suits protect in vacuum with their own oxygen,
 * and only ZAS³'s harm applies.
 */
@GameTestHolder(NorthstarTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NorthstarTests {
    /** The namespace of the adapter's GameTests. */
    public static final String NAMESPACE = "zas_northstar";

    /** The adapter's data pack, under the id NeoForge gives a mod's built-in pack. */
    private static final String PACK = "mod/zas:datapacks/northstar";

    private NorthstarTests() {}

    /** What NeoForge's breathing hook would be told on the entity's tick, with every listener. */
    static boolean breathes(LivingEntity entity) {
        LivingBreatheEvent event =
                new LivingBreatheEvent(entity, Breathing.vanilla(entity).canBreathe(), 1, 4);
        NeoForge.EVENT_BUS.post(event);
        return event.canBreathe();
    }

    private static double kPa(AtmosphereSample sample, ResourceLocation gas) {
        return sample.partialPressures().getOrDefault(gas, 0.0);
    }

    private static void near(GameTestHelper h, double actual, double expected, double within, String what) {
        if (!(Math.abs(actual - expected) <= within)) {
            throw new GameTestAssertException(what + ": expected " + expected + " ± " + within + ", got " + actual);
        }
    }

    /** What is queued in a level's Northstar oxygen updater, left as it was. */
    private static Set<Long> queued(ServerLevel level) {
        try {
            Field f = NorthstarOxygen.class.getDeclaredField("updater");
            f.setAccessible(true);
            LongArrayFIFOQueue queue =
                    ((ProgressiveBlockUpdater) f.get(NorthstarOxygen.getDimension(level))).getQueue();
            LongOpenHashSet seen = new LongOpenHashSet();
            int n = queue.size();
            for (int i = 0; i < n; i++) {
                long pos = queue.dequeueLong();
                seen.add(pos);
                queue.enqueue(pos);
            }
            return seen;
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("can't read Northstar's oxygen updates: " + e);
        }
    }

    private static void clearQueue(ServerLevel level) {
        try {
            Field f = NorthstarOxygen.class.getDeclaredField("updater");
            f.setAccessible(true);
            ((ProgressiveBlockUpdater) f.get(NorthstarOxygen.getDimension(level)))
                    .getQueue()
                    .clear();
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("can't read Northstar's oxygen updates: " + e);
        }
    }

    /** A whole Northstar suit, its chest piece holding this many mB of oxygen. */
    private static void wear(FakePlayer player, String suit, int oxygen) {
        player.setItemSlot(EquipmentSlot.HEAD, Site.northstarItem(suit + "_helmet"));
        ItemStack chest = Site.northstarItem(suit + "_chestpiece");
        chest.set(oxygenComponent(), oxygen);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.LEGS, Site.northstarItem(suit + "_leggings"));
        player.setItemSlot(EquipmentSlot.FEET, Site.northstarItem(suit + "_boots"));
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Integer> oxygenComponent() {
        return (DataComponentType<Integer>)
                BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.fromNamespaceAndPath("northstar", "oxygen"));
    }

    /**
     * With Northstar installed, {@code zas} starts the adapter, its mixins apply, the row's range is
     * the one the mixin plugin reads, and the Northstar on the server is the one the build asked for.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_is_on_with_northstar(GameTestHelper h) {
        String installed = Adapters.installedVersion(NorthstarVersions.MOD_ID).orElse(null);
        String wanted = System.getProperty("zas.northstar.version");
        h.assertTrue(
                installed != null && wanted.startsWith(installed),
                "the Northstar installed is " + installed + ", not " + wanted);
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(NorthstarVersions.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Northstar");
        h.assertValueEqual(status.row().entryClass(), NorthstarCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.row().range(), NorthstarVersions.SUPPORTED, "the row's range");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(NorthstarCompat.isActive(), "the adapter says it never started");
        h.assertTrue(NorthstarMixinPlugin.applies(), "the adapter's mixins don't apply");
        h.succeed();
    }

    /**
     * Northstar's planets seed ZAS³'s ambients: the Moon is a vacuum at −183 °C, Mars 610 Pa of CO₂
     * at −100 °C. The Overworld keeps ZAS³'s own air.
     */
    @GameTest(template = "empty")
    public static void space_020_northstars_planets_seed_the_ambients(GameTestHelper h) {
        try (Site moon = Site.onTheMoon(h, 0)) {
            AtmosphereSample open = Atmospheres.at(moon.level, moon.open(0));
            near(h, open.temperature(), 90.15, 0.01, "the Moon's temperature, K");
        }
        try (Site mars = Site.on(h, Site.MARS, 0)) {
            AtmosphereSample open = Atmospheres.at(mars.level, mars.open(0));
            near(
                    h,
                    kPa(open, ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide")),
                    0.61,
                    0.001,
                    "Mars' CO₂, kPa");
            near(h, kPa(open, Site.O2), 0, 1e-9, "Mars' O₂, kPa");
            near(h, open.temperature(), 173.15, 0.01, "Mars' temperature, K");
        }
        try (Site earth = Site.onTheOverworld(h, 0)) {
            AtmosphereSample open = Atmospheres.at(earth.level, earth.open(0));
            near(h, kPa(open, Site.O2), Site.AIR.get(Site.O2), 0.5, "the Overworld's O₂, kPa");
        }
        h.succeed();
    }

    /**
     * On the Moon, where Northstar finds no oxygen in the open, a sealed room of air is oxygen to
     * Northstar: at a point inside, at the block in the middle, and at a wall block a furnace could
     * be. A vacuum room and one with too little oxygen to breathe are not, and neither is the open.
     * Northstar's blocks in the room see the room's temperature, not the Moon's.
     */
    @GameTest(template = "empty")
    public static void space_011_northstar_finds_oxygen_in_a_sealed_room(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 1)) {
            site.room(0, Site.AIR);
            site.room(10, Map.of());
            site.room(20, Map.of(Site.N2, 79.0, Site.O2, 10.0));
            ServerLevel level = site.level;
            h.assertFalse(NorthstarOxygen.getDimension(level).hasOxygen(), "Northstar finds oxygen on the whole Moon");
            h.assertTrue(NorthstarOxygen.hasOxygen(level, site.middle(0)), "no oxygen at a point in the room");
            h.assertTrue(NorthstarOxygen.hasOxygen(level, site.abs(2, 2, 2)), "no oxygen at the room's middle block");
            h.assertTrue(NorthstarOxygen.hasOxygen(level, site.abs(0, 2, 2)), "no oxygen at the room's wall");
            h.assertFalse(NorthstarOxygen.hasOxygen(level, site.middle(10)), "oxygen in a vacuum room");
            h.assertFalse(NorthstarOxygen.hasOxygen(level, site.abs(12, 2, 2)), "oxygen at a vacuum room's block");
            h.assertFalse(NorthstarOxygen.hasOxygen(level, site.middle(20)), "oxygen in a room at 10 kPa of O₂");
            h.assertFalse(NorthstarOxygen.hasOxygen(level, site.open(30)), "oxygen in the open");
            h.assertFalse(NorthstarOxygen.hasOxygen(level, site.abs(30, 0, 2)), "oxygen at a block in the open");

            // The room cools towards the Moon's cold, so it is a little below the 20 °C it was filled at.
            double room = Atmospheres.at(level, site.middle(0)).temperature() - 273.15;
            h.assertTrue(room > 0, "the room is at " + room + " °C");
            near(
                    h,
                    NorthstarTemperature.getTemperature(level, site.middle(0)),
                    room,
                    0.01,
                    "the room's temperature, °C");
            near(h, NorthstarTemperature.getTemperature(level, site.abs(2, 2, 2)), room, 0.01, "the room's block, °C");
            near(h, NorthstarTemperature.getTemperature(level, site.open(30)), -183, 0.5, "the open's temperature, °C");
        }
        h.succeed();
    }

    /**
     * What Northstar burns or breathes from a room comes out of the room's oxygen, at ZAS³'s mB per
     * mole: 3000 mB drained from a room of air leaves it too little to breathe, and Northstar then
     * finds no oxygen there.
     */
    @GameTest(template = "empty")
    public static void space_011_what_northstar_drains_comes_out_of_the_room(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 2)) {
            site.room(0, Site.AIR);
            ServerLevel level = site.level;
            BlockPos furnace = site.abs(0, 2, 2);
            NorthstarOxygen.Provider sealer =
                    NorthstarOxygen.getDimension(level).getSealer(furnace);
            h.assertTrue(sealer != null, "no sealer answers for the room's wall");
            h.assertTrue(sealer == NorthstarCompat.sealerOf(level), "the sealer answering isn't the adapter's");
            double before = kPa(Atmospheres.at(level, site.middle(0)), Site.O2);
            sealer.drainOxygen(3000);
            NorthstarCompat.flushDrains(level);
            site.ticks(3);
            double taken = 3000 * Millibuckets.molesPerMillibucket();
            double after = kPa(Atmospheres.at(level, site.middle(0)), Site.O2);
            near(h, before - after, before * taken / Site.moles(before), 0.3, "O₂ taken from the room, kPa");
            h.assertFalse(NorthstarOxygen.hasOxygen(level, site.middle(0)), "Northstar still finds oxygen in the room");
        }
        h.succeed();
    }

    /**
     * Northstar's blocks are told when a zone changes: the blocks of a new room are queued for
     * Northstar to look at again, and so are those of a room when it is breached, with its walls, and
     * of a vacuum room when it is filled with air.
     */
    @GameTest(template = "empty")
    public static void space_011_zone_changes_reach_northstars_blocks(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 3)) {
            ServerLevel level = site.level;
            clearQueue(level);
            site.room(0, Site.AIR);
            long middle = site.abs(2, 2, 2).asLong();
            h.assertTrue(queued(level).contains(middle), "the new room's blocks weren't queued");
            clearQueue(level);
            site.set(2, 4, 2, Blocks.AIR.defaultBlockState());
            site.ticks(3);
            h.assertTrue(queued(level).contains(middle), "the breached room's blocks weren't queued");
            h.assertTrue(
                    queued(level).contains(site.abs(0, 2, 2).asLong()),
                    "the breached room's wall, where a furnace could be, wasn't queued");

            // A vacuum room filled with air, which no lifecycle event marks, is seen within a second.
            site.room(10, Map.of());
            long other = site.abs(12, 2, 2).asLong();
            NorthstarCompat.checkZones(level);
            clearQueue(level);
            site.settle(GasMutations.add(
                    level,
                    site.middle(10),
                    new GasParcel(Map.of(Site.N2, Site.moles(79), Site.O2, Site.moles(21.3)), 293.15),
                    SourceId.of("zas", "northstar_gametest")));
            NorthstarCompat.checkZones(level);
            h.assertTrue(queued(level).contains(other), "the filled room's blocks weren't queued");
        }
        h.succeed();
    }

    /**
     * In a sealed room of air on the Moon a player and a pig breathe, and nothing harms them (S-06).
     */
    @GameTest(template = "empty")
    public static void s_06_a_sealed_room_on_the_moon_is_breathable(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 4)) {
            site.room(0, Site.AIR);
            FakePlayer player = site.player(site.floor(0));
            Mob pig = site.mob(EntityType.PIG, site.floor(0));
            for (LivingEntity entity : List.<LivingEntity>of(player, pig)) {
                Site.check(entity, 5);
                Breathability verdict = Breathing.of(entity);
                h.assertTrue(verdict.canBreathe(), entity.getName().getString() + " can't breathe in the room");
                h.assertValueEqual(verdict.source(), BreathSource.ATMOSPHERE, "what it breathes");
                h.assertTrue(breathes(entity), entity.getName().getString() + "'s breathing hook says no");
                h.assertValueEqual(Site.dealt(entity), List.of(), "damage dealt in the room");
            }
        }
        h.succeed();
    }

    /**
     * In the open on the Moon, Northstar's own checks of a bare player do nothing: no suffocation,
     * no freezing. ZAS³'s do: the player has the low pressure and the cold, and takes ZAS³'s damage
     * alone (S-06, D-51).
     */
    @GameTest(template = "empty")
    public static void s_06_only_zas_harm_applies(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 5)) {
            FakePlayer player = site.player(site.open(0));
            for (int i = 0; i < 40; i++) {
                player.invulnerableTime = 0;
                NorthstarOxygen.onBreathe(new LivingBreatheEvent(player, false, 1, 4));
                NorthstarTemperature.tickEntity(player);
            }
            h.assertValueEqual(Site.dealt(player), List.of(), "damage Northstar dealt");
            h.assertValueEqual(player.getTicksFrozen(), 0, "ticks frozen by Northstar");
            h.assertValueEqual(
                    Breathing.of(player).hazards(), Set.of(Hazard.LOW_PRESSURE, Hazard.COLD), "ZAS³'s hazards");
            Site.check(player, 5);
            List<ResourceLocation> dealt = Site.dealt(player);
            h.assertFalse(dealt.isEmpty(), "ZAS³ dealt no damage in vacuum");
            h.assertTrue(
                    dealt.stream().allMatch(type -> type.getNamespace().equals("zas")),
                    "damage that isn't ZAS³'s: " + dealt);
        }
        h.succeed();
    }

    /**
     * The adapter's data for Northstar's suits: each piece protects from vacuum and cold, the
     * martian steel one from any pressure and heat too, and the helmets seal the breathing.
     */
    @GameTest(template = "empty")
    public static void space_011_the_suits_ratings_are_the_adapters_data(GameTestHelper h) {
        h.assertTrue(
                h.getLevel().getServer().getPackRepository().getSelectedIds().contains(PACK),
                "the adapter's data pack isn't on");
        for (String suit : List.of("iron_space_suit", "martian_steel_space_suit")) {
            boolean heavy = suit.startsWith("martian");
            for (String piece : List.of("helmet", "chestpiece", "leggings", "boots")) {
                String path = suit + "_" + piece;
                ILifeSupport rated = Protections.of(Site.northstarItem(path));
                h.assertTrue(rated != null, "no protection data names " + path);
                h.assertValueEqual(rated.minPressure(), 0.0, path + ": lowest pressure, kPa");
                h.assertValueEqual(rated.minTemperature(), 0.0, path + ": lowest temperature, K");
                h.assertValueEqual(rated.maxTemperature(), heavy ? 1e9 : 573.15, path + ": highest temperature, K");
                if (heavy) {
                    h.assertValueEqual(rated.maxPressure(), 1e9, path + ": highest pressure, kPa");
                }
                h.assertValueEqual(rated.sealedBreathing(), piece.equals("helmet"), path + ": seals the breathing");
            }
        }
        h.succeed();
    }

    /**
     * In the open on the Moon a whole suit whose chest piece holds oxygen keeps its wearer alive:
     * ZAS³'s internals breathe the suit's oxygen, which goes down, and the wearer has no hazard and
     * takes no damage. With the chest piece empty, the wearer can't breathe.
     */
    @GameTest(template = "empty")
    public static void s_06_a_suit_protects_in_vacuum_with_its_oxygen(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 6)) {
            for (String suit : List.of("iron_space_suit", "martian_steel_space_suit")) {
                FakePlayer player = site.player(site.open(0));
                wear(player, suit, 1800);
                Site.check(player, 5);
                Breathability verdict = Breathing.of(player);
                h.assertTrue(verdict.canBreathe(), suit + ": its wearer can't breathe: " + verdict.reason());
                h.assertValueEqual(verdict.source(), BreathSource.INTERNALS, suit + ": what its wearer breathes");
                h.assertValueEqual(verdict.hazards(), Set.of(), suit + ": hazards in vacuum");
                h.assertValueEqual(Site.dealt(player), List.of(), suit + ": damage dealt in vacuum");
                int left = player.getItemBySlot(EquipmentSlot.CHEST).getOrDefault(oxygenComponent(), 0);
                h.assertTrue(left < 1800, suit + ": the suit's oxygen didn't go down");

                FakePlayer empty = site.player(site.open(0));
                wear(empty, suit, 0);
                Site.check(empty, 1);
                h.assertFalse(Breathing.of(empty).canBreathe(), suit + ": its wearer breathes with no oxygen");
            }
        }
        h.succeed();
    }

    /** Northstar's mobs live on its planets: a Moon snail in the open takes no harm. */
    @GameTest(template = "empty")
    public static void space_011_northstars_mobs_live_in_vacuum(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 7)) {
            EntityType<?> type =
                    BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("northstar", "moon_snail"));
            h.assertTrue(type != EntityType.PIG, "Northstar has no moon snail");
            @SuppressWarnings("unchecked")
            Mob snail = site.mob((EntityType<Mob>) type, site.open(0));
            Site.check(snail, 5);
            h.assertValueEqual(Site.dealt(snail), List.of(), "damage dealt to a Moon snail");
        }
        h.succeed();
    }

    /**
     * Outside zones, a Northstar oxygen sealer's area on the Moon is breathable under the Normal
     * preset, as Northstar has it, and not under Hardcore, where only a ZAS³ zone is. The sealer adds
     * no gas: the open air there is still a vacuum.
     */
    @GameTest(template = "empty")
    public static void s_06_a_northstar_sealers_area_follows_the_preset(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 8)) {
            BlockPos inside = site.abs(2, 10, 2);
            NorthstarOxygen.Provider sealer = new NorthstarOxygen.Provider() {
                @Override
                public boolean isSealed(Vec3 pos) {
                    return isSealed(BlockPos.containing(pos));
                }

                @Override
                public boolean isSealed(Vec3i pos) {
                    return Math.abs(pos.getX() - inside.getX()) <= 2
                            && Math.abs(pos.getY() - inside.getY()) <= 2
                            && Math.abs(pos.getZ() - inside.getZ()) <= 2;
                }

                @Override
                public void drainOxygen(float oxygen) {}
            };
            NorthstarOxygen.getDimension(site.level).registerSealer(sealer);
            try {
                FakePlayer player = site.player(Vec3.atBottomCenterOf(inside));
                Site.check(player, 1);
                boolean normal = Atmospheres.preset() != AtmospherePreset.HARDCORE;
                h.assertValueEqual(
                        Breathing.of(player).canBreathe(),
                        normal,
                        "breathing in a sealer's area under " + Atmospheres.preset());
                h.assertTrue(
                        Atmospheres.at(site.level, player.getEyePosition())
                                .mixture()
                                .isEmpty(),
                        "the sealer's area holds gas");
                FakePlayer outside = site.player(site.open(30));
                Site.check(outside, 1);
                h.assertFalse(Breathing.of(outside).canBreathe(), "breathing outside the sealer's area");
            } finally {
                NorthstarOxygen.getDimension(site.level).unregisterSealer(sealer);
            }
        }
        h.succeed();
    }
}
