package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The atmospheric damage types breathing deals (LIFE-030): data in {@code data/zas/damage_type/},
 * each with its own death message. Both bypass armor (LIFE-035), skip the damage cooldown, so a
 * hazard isn't swallowed by another's invulnerability ticks, and don't knock back.
 *
 * <p>The other types of LIFE-030 (decompression, barotrauma, frostbite, burns and airflow impact)
 * come with their hazards (#35).
 */
public final class LifeDamage {
    /** Hypoxia: an empty air meter in a breath with too little O2 (LIFE-008). */
    public static final ResourceKey<DamageType> HYPOXIA = key("hypoxia");

    /** Toxic gas: a hazard profile's damage (LIFE-004, LIFE-041). */
    public static final ResourceKey<DamageType> TOXIC_GAS = key("toxic_gas");

    private LifeDamage() {}

    /** A damage source of a type, from the entity's level's registry. */
    public static DamageSource source(LivingEntity entity, ResourceKey<DamageType> type) {
        return entity.damageSources().source(type);
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, path));
    }
}
