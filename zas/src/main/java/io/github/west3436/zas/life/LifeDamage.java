package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The atmospheric damage types (LIFE-030): data in {@code data/zas/damage_type/}, each with its own
 * death message. All but airflow impact bypass armor (LIFE-035), skip the damage cooldown, so a
 * hazard isn't swallowed by another's invulnerability ticks, and don't knock back. Airflow impact
 * is a physical blow, so armor and the cooldown apply to it as to any hit.
 */
public final class LifeDamage {
    /** Hypoxia: an empty air meter in a breath with too little O2 (LIFE-008). */
    public static final ResourceKey<DamageType> HYPOXIA = key("hypoxia");

    /** Toxic gas: a hazard profile's damage (LIFE-004, LIFE-041). */
    public static final ResourceKey<DamageType> TOXIC_GAS = key("toxic_gas");

    /** Decompression: an effective pressure below the low-pressure threshold (LIFE-010). */
    public static final ResourceKey<DamageType> DECOMPRESSION = key("decompression");

    /** Barotrauma: an effective pressure at or above the high-pressure threshold (LIFE-014). */
    public static final ResourceKey<DamageType> BAROTRAUMA = key("barotrauma");

    /** Frostbite: an exposure temperature below the comfort band (LIFE-011). */
    public static final ResourceKey<DamageType> FROSTBITE = key("frostbite");

    /** Burns: an exposure temperature above the comfort band (LIFE-011). */
    public static final ResourceKey<DamageType> BURNS = key("burns");

    /** Airflow impact: an entity thrown against a block by airflow (FLOW). Armor applies to it. */
    public static final ResourceKey<DamageType> AIRFLOW_IMPACT = key("airflow_impact");

    private LifeDamage() {}

    /** A damage source of a type, from the entity's level's registry. */
    public static DamageSource source(LivingEntity entity, ResourceKey<DamageType> type) {
        return entity.damageSources().source(type);
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, path));
    }
}
