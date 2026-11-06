package io.github.west3436.zas.example;

import io.github.west3436.zas.api.event.HazardTickEvent;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.life.HazardHandlers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hazards of the addon's own, from a gas it defines (LIFE-032), and changes to ZAS³'s hazards (API-033).
 * Without ZAS³ the handlers are kept and never called, and the event never fires.
 */
public final class ExampleHazards {
    /** The addon's gas, which {@code data/zas_example/zas/gas/radon.json} defines. */
    public static final ResourceLocation RADON = id("radon");

    /** A gas tag of the addon's, which {@code data/zas_example/tags/zas/gas/radioactive.json} defines. */
    public static final GasTag RADIOACTIVE = GasTag.of(ExampleAddonMod.MOD_ID, "radioactive");

    /** The radon handler's id. */
    public static final ResourceLocation DOSE = id("radon_dose");

    /** The radioactive gases handler's id. */
    public static final ResourceLocation SICKNESS = id("radiation_sickness");

    private static final double HP_PER_KPA_SECOND = 0.01;
    private static final int SICKNESS_TICKS = 100;

    private ExampleHazards() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleAddonMod.MOD_ID, path);
    }

    static void register() {
        HazardHandlers.register(DOSE, RADON, ExampleHazards::dose);
        HazardHandlers.register(SICKNESS, RADIOACTIVE, ExampleHazards::sickness);
    }

    /**
     * Radon around an entity irradiates it, whatever it breathes. ZAS³ deals nothing for a handler, so
     * this deals its own damage. It scales by the check's seconds, so that the check interval changes
     * how smooth this is and not how fast.
     */
    private static void dose(HazardExposure exposure) {
        LivingEntity entity = exposure.entity();
        double damage = HP_PER_KPA_SECOND * exposure.surroundingPressure() * exposure.seconds();
        if (damage > 0) {
            entity.hurt(entity.damageSources().magic(), (float) damage);
        }
    }

    /** Breathing a radioactive gas sickens: a filter that keeps it out of the breath, or a tank, spares the wearer. */
    private static void sickness(HazardExposure exposure) {
        if (exposure.breathedPressure() > 0) {
            exposure.entity().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SICKNESS_TICKS));
        }
    }

    /**
     * Just before ZAS³ harms an entity. Set the damage to reduce it, or cancel to spare the entity this
     * time. Later minor versions may add hazards, so a {@code switch} over them has a default.
     */
    static void onHazardTick(HazardTickEvent event) {
        LivingEntity entity = event.entity();
        switch (event.hazard()) {
            case HEAT -> {
                if (entity.hasEffect(MobEffects.FIRE_RESISTANCE)) {
                    event.setDamage(event.damage() * 0.5);
                }
            }
            case HYPOXIA -> {
                if (entity.hasEffect(MobEffects.CONDUIT_POWER)) {
                    event.setCanceled(true);
                }
            }
            case AIRFLOW_IMPACT -> {
                if (entity.hasEffect(MobEffects.SLOW_FALLING)) {
                    event.setDamage(0);
                }
            }
            default -> {}
        }
    }
}
