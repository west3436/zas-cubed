package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.systems.OxygenApi;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathing;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ad Astra's entity oxygen check is ZAS³'s breathing verdict (SPACE-010, S-06): in a sealed zone,
 * in vacuum and on the Overworld, whatever Ad Astra itself makes of the place.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class OxygenTests {
    private OxygenTests() {}

    /** What Ad Astra makes of the block an entity's eyes are in, before any listener. */
    private static boolean adAstrasOwn(Entity entity) {
        return OxygenApi.API.hasOxygen(entity.level(), BlockPos.containing(entity.getEyePosition()));
    }

    /** Checks the verdict is the one expected and that Ad Astra's check gives the same. */
    private static void agree(GameTestHelper h, LivingEntity entity, BreathReason expected, String what) {
        h.assertValueEqual(Breathing.of(entity).reason(), expected, "ZAS³'s verdict for " + what);
        h.assertValueEqual(
                OxygenApi.API.hasOxygen(entity), expected.canBreathe(), "Ad Astra's oxygen check for " + what);
    }

    /**
     * On the Moon, in a sealed room of ZAS³ air with no oxygen distributor: Ad Astra by itself finds
     * no oxygen there, and its entity check says there is, as the verdict does (S-06). A room too
     * thin in O₂ has none.
     */
    @GameTest(template = "empty")
    public static void space_010_has_oxygen_matches_the_verdict_in_a_sealed_zone_on_the_moon(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 10)) {
            lab.room(0, Lab.AIR);
            lab.room(8, Map.of(Lab.N2, 90.0, Lab.O2, 10.0));
            Pig pig = lab.mob(EntityType.PIG, lab.floor(0));
            FakePlayer player = lab.player(lab.floor(0));
            h.assertFalse(adAstrasOwn(pig), "Ad Astra by itself found oxygen in a room on the Moon");
            agree(h, pig, BreathReason.BREATHABLE, "a pig in the room");
            agree(h, player, BreathReason.BREATHABLE, "a player in the room");

            agree(h, lab.mob(EntityType.PIG, lab.floor(8)), BreathReason.LOW_OXYGEN, "a pig in thin air");
            agree(h, lab.player(lab.floor(8)), BreathReason.LOW_OXYGEN, "a player in thin air");
        }
        h.succeed();
    }

    /**
     * On the Moon in the open, a vacuum: no oxygen for a breather. Something that doesn't breathe
     * has the verdict "doesn't breathe", so Ad Astra's check passes it. Something that isn't alive
     * has no verdict, and has oxygen where a breather would: not here, and in the room beside it.
     */
    @GameTest(template = "empty")
    public static void space_010_has_oxygen_matches_the_verdict_in_vacuum(GameTestHelper h) {
        try (Lab lab = Lab.onTheMoon(h, 11)) {
            lab.room(0, Lab.AIR);
            Vec3 open = lab.open(10);
            agree(h, lab.mob(EntityType.PIG, open), BreathReason.LOW_OXYGEN, "a pig in vacuum");
            agree(h, lab.player(open), BreathReason.LOW_OXYGEN, "a player in vacuum");

            ArmorStand stand = lab.add(new ArmorStand(lab.level, open.x, open.y, open.z));
            h.assertFalse(adAstrasOwn(stand), "Ad Astra by itself found oxygen in the open on the Moon");
            agree(h, stand, BreathReason.DOES_NOT_BREATHE, "an armor stand in vacuum");

            ItemStack stone = new ItemStack(Items.STONE);
            ItemEntity outside = lab.add(new ItemEntity(lab.level, open.x, open.y, open.z, stone.copy()));
            Vec3 inside = lab.floor(0);
            ItemEntity inRoom = lab.add(new ItemEntity(lab.level, inside.x, inside.y, inside.z, stone.copy()));
            h.assertFalse(OxygenApi.API.hasOxygen(outside), "an item in vacuum has oxygen");
            h.assertTrue(OxygenApi.API.hasOxygen(inRoom), "an item in the room has no oxygen");
        }
        h.succeed();
    }

    /**
     * On the Overworld, which Ad Astra counts as having oxygen everywhere: in the open the check
     * passes, and in a sealed room emptied to a vacuum it fails, as the verdict does.
     */
    @GameTest(template = "empty")
    public static void space_010_has_oxygen_matches_the_verdict_on_the_overworld(GameTestHelper h) {
        try (Lab lab = Lab.onTheOverworld(h, 12)) {
            lab.room(0, Map.of());
            Vec3 open = lab.open(10);
            agree(h, lab.mob(EntityType.PIG, open), BreathReason.BREATHABLE, "a pig in the open");
            agree(h, lab.player(open), BreathReason.BREATHABLE, "a player in the open");

            Pig pig = lab.mob(EntityType.PIG, lab.floor(0));
            h.assertTrue(adAstrasOwn(pig), "Ad Astra by itself found no oxygen on the Overworld");
            agree(h, pig, BreathReason.LOW_OXYGEN, "a pig in a vacuum room");
            agree(h, lab.player(lab.floor(0)), BreathReason.LOW_OXYGEN, "a player in a vacuum room");
        }
        h.succeed();
    }
}
