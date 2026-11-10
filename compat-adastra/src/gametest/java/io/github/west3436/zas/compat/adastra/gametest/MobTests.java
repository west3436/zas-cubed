package io.github.west3436.zas.compat.adastra.gametest;

import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Protection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ad Astra's space-native mobs (SPACE-019): its two entity tags are in {@code
 * zas:atmosphere_immune}, so the mobs it puts on its planets live there.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class MobTests {
    private static final TagKey<EntityType<?>> IMMUNE = tag("zas", "atmosphere_immune");

    private static final List<TagKey<EntityType<?>>> AD_ASTRAS =
            List.of(tag("ad_astra", "lives_without_oxygen"), tag("ad_astra", "can_survive_in_space"));

    private MobTests() {}

    private static TagKey<EntityType<?>> tag(String namespace, String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /**
     * Every entity type in either of Ad Astra's tags is in {@code zas:atmosphere_immune}, with the
     * four ZAS³ puts there itself. Each of Ad Astra's own mobs among them, in the open on the Moon,
     * has no hazard and takes nothing through a minute of ZAS³'s checks, where a pig takes harm.
     */
    @GameTest(template = "empty")
    public static void space_019_ad_astras_space_mobs_take_no_harm_in_vacuum(GameTestHelper h) {
        List<EntityType<?>> own = new ArrayList<>();
        for (TagKey<EntityType<?>> tag : AD_ASTRAS) {
            int size = 0;
            for (Holder<EntityType<?>> holder : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag)) {
                size++;
                EntityType<?> type = holder.value();
                h.assertTrue(type.is(IMMUNE), type + " of #" + tag.location() + " isn't in #" + IMMUNE.location());
                if (BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("ad_astra")) {
                    own.add(type);
                }
            }
            h.assertTrue(size > 0, "#" + tag.location() + " is empty");
        }
        h.assertTrue(EntityType.IRON_GOLEM.is(IMMUNE), "ZAS³'s own entries are gone from #" + IMMUNE.location());
        h.assertTrue(EntityType.ZOMBIE.is(IMMUNE), "a zombie, which Ad Astra lists, isn't immune");
        h.assertFalse(EntityType.PIG.is(IMMUNE), "a pig is immune");
        h.assertTrue(own.size() >= 10, "Ad Astra's own mobs in its tags: " + own);

        try (Lab lab = Lab.onTheMoon(h, 80)) {
            Pig pig = lab.mob(EntityType.PIG, lab.open(10));
            Lab.check(pig, 5);
            h.assertFalse(Lab.dealt(pig).isEmpty(), "nothing hurt a pig in vacuum");

            for (EntityType<?> type : own) {
                @SuppressWarnings("unchecked")
                Mob mob = lab.mob((EntityType<? extends Mob>) type, lab.open(10));
                Breathability verdict = Breathing.of(mob);
                h.assertValueEqual(verdict.reason(), BreathReason.DOES_NOT_BREATHE, "the verdict for " + type);
                h.assertTrue(verdict.isProtected(), "hazards for " + type + ": " + verdict.hazards());
                h.assertTrue(
                        verdict.protection().contains(Protection.IMMUNE),
                        "the protection of " + type + ": " + verdict.protection());
                Lab.check(mob, 31);
                h.assertValueEqual(Lab.dealt(mob), List.of(), "damage dealt to " + type);
                h.assertValueEqual(mob.getHealth(), mob.getMaxHealth(), "the health of " + type);
            }
        }
        h.succeed();
    }
}
