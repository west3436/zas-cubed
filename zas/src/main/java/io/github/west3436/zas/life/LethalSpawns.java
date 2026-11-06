package io.github.west3436.zas.life;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.config.HazardConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * Keeps breathing mobs from spawning naturally where the atmosphere would kill them, such as a
 * vacuum (LIFE-034): they would die at once, which wastes work and litters drops.
 *
 * <p>It listens to NeoForge's {@link MobSpawnEvent.PositionCheck} for natural spawns and for those of
 * chunk generation, which runs on a worker thread. So it reads only what any thread may: the
 * atmosphere at the mob's eyes, which is the ambient where a chunk is still generating, and the
 * config and hazard data. It never asks for a mob's life support, which is third-party code (D-30).
 * A mob has no gear yet at this point in any case. Spawners, spawn eggs, commands and the other
 * spawn types are left alone, as is a check another listener has already decided, so a mod can let
 * its own mob spawn with {@link MobSpawnEvent.PositionCheck.Result#SUCCEED}.
 */
public final class LethalSpawns {
    private LethalSpawns() {}

    /** Registers the listener. Called once, from {@link Breathers#register}. */
    static void register(IEventBus gameBus) {
        gameBus.addListener(LethalSpawns::onPositionCheck);
    }

    private static void onPositionCheck(MobSpawnEvent.PositionCheck e) {
        MobSpawnType type = e.getSpawnType();
        if ((type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)
                || e.getResult() != MobSpawnEvent.PositionCheck.Result.DEFAULT) {
            return;
        }
        Mob mob = e.getEntity();
        if (BreathingRules.breathes(mob) && lethal(mob)) {
            e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    /**
     * Whether the atmosphere at a mob's eyes would kill it if it stayed: there is too little O2 to
     * breathe (LIFE-003), a gas in it deals damage (LIFE-004), or its pressure or temperature does
     * (LIFE-010, LIFE-011, LIFE-014). A hazard turned off in the mob's dimension doesn't count
     * (LIFE-031, LIFE-036), and the mob counts as bare. Any thread.
     */
    private static boolean lethal(Mob mob) {
        ResourceKey<Level> dimension = mob.level().dimension();
        GasMixture air = Atmospheres.of(mob).mixture();
        if (HazardConfig.enabled(dimension, HazardType.HYPOXIA)
                && air.partialPressure(StandardAir.OXYGEN) < BreathingConfig.oxygenMinimum()) {
            return true;
        }
        if (HazardConfig.enabled(dimension, HazardType.TOXIC_GAS) && GasHazards.damaging(air)) {
            return true;
        }
        return !EnvironmentHazards.immune(mob)
                && !EnvironmentHazards.exposure(dimension, air, Shielding.NONE)
                        .hazards()
                        .isEmpty();
    }
}
