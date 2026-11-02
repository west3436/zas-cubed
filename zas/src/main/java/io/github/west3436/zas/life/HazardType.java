package io.github.west3436.zas.life;

import io.github.west3436.zas.api.life.Hazard;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.Nullable;

/**
 * The hazards ZAS³ deals, one per atmospheric damage type (LIFE-030). Each can be turned off and
 * scaled in the server config's {@code [hazards]} section, and per dimension (LIFE-031, LIFE-036;
 * {@link io.github.west3436.zas.config.HazardConfig}).
 */
public enum HazardType {
    /** An empty air meter in a breath with too little O2 (LIFE-003, LIFE-008). */
    HYPOXIA(LifeDamage.HYPOXIA, null),
    /** A gas's hazard profile (LIFE-004). */
    TOXIC_GAS(LifeDamage.TOXIC_GAS, Hazard.TOXIC_GAS),
    /** Low pressure (LIFE-010). */
    DECOMPRESSION(LifeDamage.DECOMPRESSION, Hazard.LOW_PRESSURE),
    /** High pressure (LIFE-014). */
    BAROTRAUMA(LifeDamage.BAROTRAUMA, Hazard.HIGH_PRESSURE),
    /** Cold (LIFE-011). */
    FROSTBITE(LifeDamage.FROSTBITE, Hazard.COLD),
    /** Heat (LIFE-011). */
    BURNS(LifeDamage.BURNS, Hazard.HEAT),
    /** Airflow throwing an entity against a block (FLOW). */
    AIRFLOW_IMPACT(LifeDamage.AIRFLOW_IMPACT, null);

    private final ResourceKey<DamageType> damageType;
    private final @Nullable Hazard hazard;

    HazardType(ResourceKey<DamageType> damageType, @Nullable Hazard hazard) {
        this.damageType = damageType;
        this.hazard = hazard;
    }

    /** Its name in the config, as its damage type's: {@code decompression}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The damage type it deals. */
    public ResourceKey<DamageType> damageType() {
        return damageType;
    }

    /**
     * The API's name for it in a verdict's hazards (API-014), if a verdict lists it. Hypoxia is the
     * verdict's {@code canBreathe}, and airflow is FLOW's, not a property of where the entity is.
     */
    public Optional<Hazard> hazard() {
        return Optional.ofNullable(hazard);
    }

    /** The hazard with a config name, if there is one. */
    public static Optional<HazardType> byId(String id) {
        for (HazardType t : values()) {
            if (t.id().equals(id)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
