package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.systems.OxygenApi;
import earth.terrarium.adastra.api.systems.TemperatureApi;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * With the adapter on, Ad Astra deals no oxygen or temperature harm, and ZAS³'s hazards apply
 * instead (SPACE-013, D-51).
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class HarmTests {
    private static final ResourceLocation AD_ASTRA_OXYGEN = ResourceLocation.fromNamespaceAndPath("ad_astra", "oxygen");

    private HarmTests() {}

    /** Gives a mob the health to outlast a test's harm, so that every tick of it is a live one. */
    private static <T extends LivingEntity> T sturdy(T entity) {
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        entity.setHealth(1000);
        return entity;
    }

    /**
     * A pig in the open on the Moon, in vacuum at −173 °C, through 60 seconds of its own ticks, each
     * the whole tick that Ad Astra hooks: everything that hurts it is one of ZAS³'s damage types.
     * Ad Astra's oxygen tick would have hurt it, as calling that tick directly shows on a second
     * pig, so it is the adapter's refusal that keeps it out.
     */
    @GameTest(template = "empty")
    public static void space_013_only_zas_damage_types_apply_on_the_moon(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 20)) {
            Pig pig = sturdy(lab.mob(EntityType.PIG, lab.open(2)));
            BlockPos at = pig.blockPosition();
            h.assertFalse(OxygenApi.API.hasOxygen(pig), "the pig has oxygen");
            h.assertTrue(TemperatureApi.API.isCold(lab.level, at), "Ad Astra doesn't find the Moon cold");

            Lab.live(pig, 1200);

            List<ResourceLocation> dealt = Lab.dealt(pig);
            h.assertFalse(dealt.isEmpty(), "nothing hurt a pig in vacuum");
            for (ResourceLocation type : dealt) {
                h.assertValueEqual(type.getNamespace(), "zas", "the namespace of " + type);
            }
            h.assertTrue(pig.isAlive(), "the pig died, so not every tick was a live one");

            Pig second = sturdy(lab.mob(EntityType.PIG, lab.open(6)));
            OxygenApi.API.entityTick(lab.level, second);
            h.assertValueEqual(Lab.dealt(second), List.of(AD_ASTRA_OXYGEN), "what Ad Astra's own oxygen tick deals");
        }
        h.succeed();
    }

    /**
     * Ad Astra's temperature tick called directly, as another mod might, still freezes and burns
     * nothing: its cold and heat are refused by name. On the Moon it finds the pig cold; at Venus's
     * 464 °C, set at the pig's position through Ad Astra's API, it finds it hot.
     */
    @GameTest(template = "empty")
    public static void space_013_ad_astras_temperature_tick_deals_nothing_when_called_directly(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 21)) {
            Pig pig = sturdy(lab.mob(EntityType.PIG, lab.open(2)));
            BlockPos at = pig.blockPosition();
            h.assertTrue(TemperatureApi.API.isCold(lab.level, at), "Ad Astra doesn't find the Moon cold");
            TemperatureApi.API.entityTick(lab.level, pig);
            h.assertValueEqual(pig.getTicksFrozen(), 0, "ticks frozen");

            TemperatureApi.API.setTemperature(lab.level, at, (short) 464);
            try {
                h.assertTrue(TemperatureApi.API.isHot(lab.level, at), "Ad Astra doesn't find 464 °C hot");
                TemperatureApi.API.entityTick(lab.level, pig);
                h.assertTrue(pig.getRemainingFireTicks() <= 0, "the pig is alight");
            } finally {
                TemperatureApi.API.removeTemperature(lab.level, at);
            }
            h.assertValueEqual(Lab.dealt(pig), List.of(), "damage dealt");
        }
        h.succeed();
    }

    /**
     * A whole space suit holding oxygen, worn through a minute of ticks. In a room of ZAS³ air on the
     * Moon, where Ad Astra by itself finds no oxygen and would drain the suit, it keeps every mB:
     * the wearer breathes the room. In the open, where the verdict is that the wearer can't breathe,
     * Ad Astra's drain is the one thing of its own still running, 1 mB every 12 ticks: its only
     * public switch is the oxygen check, which has to stay the verdict (SPACE-010).
     */
    @GameTest(template = "empty")
    public static void space_013_a_suit_keeps_its_oxygen_where_the_wearer_can_breathe(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 22)) {
            lab.room(0, Lab.AIR);
            FakePlayer inRoom = suited(lab, lab.floor(0));
            FakePlayer inTheOpen = suited(lab, lab.open(10));
            h.assertFalse(
                    OxygenApi.API.hasOxygen(lab.level, BlockPos.containing(inRoom.getEyePosition())),
                    "Ad Astra by itself found oxygen in a room on the Moon");

            Lab.wear(inRoom, 1200);
            Lab.wear(inTheOpen, 1200);

            h.assertValueEqual(Lab.oxygenIn(inRoom.getItemBySlot(EquipmentSlot.CHEST)), 500, "mB left in the room");
            h.assertValueEqual(Lab.oxygenIn(inTheOpen.getItemBySlot(EquipmentSlot.CHEST)), 400, "mB left in the open");
        }
        h.succeed();
    }

    /** A player in a whole space suit holding 500 mB of oxygen. */
    private static FakePlayer suited(Lab lab, Vec3 at) {
        FakePlayer player = lab.player(at);
        Lab.dress(
                player,
                Lab.adAstra("space_helmet"),
                Lab.suitHolding("space_suit", 500),
                Lab.adAstra("space_pants"),
                Lab.adAstra("space_boots"));
        return player;
    }
}
