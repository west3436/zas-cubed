package io.github.west3436.zas.airflow;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.config.AirflowConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * An entity's airflow class, which sets the least pressure difference that moves it (FLOW-003,
 * FLOW-011), from the entity type tags {@code zas:airflow/immune}, {@code heavy}, {@code light} and
 * {@code mob}, looked at in that order. A living entity in none of them is a mob; anything else in
 * none of them, such as a painting or a display entity, is never moved.
 */
public enum AirflowClass {
    LIGHT,
    MOB,
    HEAVY,
    /** Never moved. */
    IMMUNE;

    public static final TagKey<EntityType<?>> LIGHT_TAG = tag("light");
    public static final TagKey<EntityType<?>> MOB_TAG = tag("mob");
    public static final TagKey<EntityType<?>> HEAVY_TAG = tag("heavy");
    public static final TagKey<EntityType<?>> IMMUNE_TAG = tag("immune");

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(
                Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "airflow/" + name));
    }

    /** An entity's class. */
    public static AirflowClass of(Entity entity) {
        EntityType<?> type = entity.getType();
        if (type.is(IMMUNE_TAG)) {
            return IMMUNE;
        }
        if (type.is(HEAVY_TAG)) {
            return HEAVY;
        }
        if (type.is(LIGHT_TAG)) {
            return LIGHT;
        }
        if (type.is(MOB_TAG) || entity instanceof LivingEntity) {
            return MOB;
        }
        return IMMUNE;
    }

    /** The least pressure difference that moves this class, kPa; infinity for {@link #IMMUNE}. */
    public double threshold() {
        return switch (this) {
            case LIGHT -> AirflowConfig.lightKpa();
            case MOB -> AirflowConfig.mobKpa();
            case HEAVY -> AirflowConfig.heavyKpa();
            case IMMUNE -> Double.POSITIVE_INFINITY;
        };
    }
}
