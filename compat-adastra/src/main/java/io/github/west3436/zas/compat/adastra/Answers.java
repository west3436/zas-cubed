package io.github.west3436.zas.compat.adastra;

import com.mojang.logging.LogUtils;
import earth.terrarium.adastra.api.events.AdAstraEvents;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathing;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * The adapter's answers to Ad Astra's events: its oxygen check is ZAS³'s breathing verdict
 * (SPACE-010), it deals no oxygen or temperature harm (SPACE-013), and its environment tick leaves
 * alone what a zone touches (SPACE-014). Each answer reads the last
 * applied atmosphere and the entity itself, never the world around it (SPACE-018).
 */
final class Answers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private Answers() {}

    /** Registers every answer with Ad Astra. Main thread, once. */
    static void register() {
        AdAstraEvents.EntityOxygenEvent.register(Answers::hasOxygen);
        // ZAS³'s hazards apply instead of Ad Astra's own (SPACE-013). Its heat and cold only run
        // from the temperature tick, but another mod may call that tick itself, so they are
        // refused by name as well.
        AdAstraEvents.OxygenTickEvent.register((level, entity) -> false);
        AdAstraEvents.TemperatureTickEvent.register((level, entity) -> false);
        AdAstraEvents.HotTemperatureTickEvent.register((level, entity) -> false);
        AdAstraEvents.ColdTemperatureTickEvent.register((level, entity) -> false);
        AdAstraEvents.EnvironmentTickEvent.register(Answers::environmentTick);
        Areas.register();
    }

    /**
     * Whether Ad Astra's environment tick may act on a block (SPACE-014): not on one a zone
     * touches, where ZAS³ knows the real air. Elsewhere Ad Astra carries on as it would alone:
     * freezing or boiling off water, killing plants, putting out fires.
     *
     * <p>Ad Astra asks once per randomly ticked block, so this is its hot path (SPACE-018): one
     * lookup in the last applied atmos tick, which builds nothing.
     */
    static boolean environmentTick(ServerLevel level, BlockPos pos, BlockState state, short temperature) {
        return !Atmospheres.zoneTouches(level, pos);
    }

    /**
     * Whether an entity has oxygen, for {@code OxygenApi.hasOxygen(Entity)} (SPACE-010): ZAS³'s
     * verdict on whether it can breathe.
     *
     * <p>Ad Astra asks this on the server alone. Should another mod ask on a client, the local
     * player gets the verdict the server last sent, and anything else Ad Astra's own answer, since
     * a client knows no more. A call from a thread that may not read the entity gets Ad Astra's
     * answer too.
     *
     * @param entity the entity asked about
     * @param adAstras what Ad Astra made of it by itself
     */
    static boolean hasOxygen(Entity entity, boolean adAstras) {
        try {
            if (entity.level() instanceof ServerLevel level) {
                if (!level.getServer().isSameThread()) {
                    return adAstras;
                }
                if (entity instanceof LivingEntity living) {
                    return Breathing.of(living).canBreathe();
                }
                // Something that isn't alive has no verdict: it has oxygen where a breather would.
                return breathed(level, entity).partialPressure(StandardAir.OXYGEN) >= Breathing.oxygenMinimum();
            }
            if (entity instanceof Player player && player.isLocalPlayer()) {
                return Breathing.of(player).canBreathe();
            }
            return adAstras;
        } catch (RuntimeException e) {
            // Ad Astra asks from inside the server tick, where a throw would stop the server.
            if (ThirdPartyFaults.shouldLog("Ad Astra adapter oxygen check")) {
                LOGGER.error("ZAS³ couldn't answer Ad Astra's oxygen check, so Ad Astra's own answer stands", e);
            }
            return adAstras;
        }
    }

    /** The air a breather would have at an entity's eyes: the atmosphere's, or a distributor's (SPACE-017). */
    private static GasMixture breathed(ServerLevel level, Entity entity) {
        AtmosphereSample sample = Atmospheres.of(entity);
        if (sample.state() == AtmosphereState.AMBIENT) {
            GasMixture distributed = Areas.air(level, entity.getEyePosition());
            if (distributed != null) {
                return distributed;
            }
        }
        return sample.mixture();
    }
}
