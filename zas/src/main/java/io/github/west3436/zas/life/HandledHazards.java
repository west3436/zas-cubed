package io.github.west3436.zas.life;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.life.HazardHandlers;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/**
 * Runs the hazards other mods add (LIFE-032) at a breath check. The one place a {@link
 * io.github.west3436.zas.api.life.HazardHandler} is called, with NFR-013's guard around it: one that
 * throws is skipped for the rest of the check and logged at most once a minute under its id, and the
 * others still run. Server thread.
 */
final class HandledHazards {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HandledHazards() {}

    /**
     * Calls each handler for each of its gases in the entity's breath or the atmosphere at its eyes.
     * Reads the gases from the verdict's breath and the assessment's sample, as either can be absent
     * without the other: an entity that doesn't breathe has no breath, and with its eyes in a fluid
     * there's no sample, though it may breathe from a tank.
     *
     * @param seconds the seconds this check charges for; a check that charges none calls nothing
     */
    static void apply(LivingEntity entity, BreathingRules.Assessment assessment, double seconds) {
        List<HazardHandlers.Registration> handlers = HazardHandlers.registered();
        if (handlers.isEmpty() || !(seconds > 0)) {
            return;
        }
        Optional<GasMixture> around = Optional.ofNullable(assessment.sample()).map(AtmosphereSample::mixture);
        Optional<GasMixture> breath = assessment.verdict().breath();
        Set<ResourceLocation> gases = new TreeSet<>();
        around.ifPresent(m -> gases.addAll(m.moles().keySet()));
        breath.ifPresent(m -> gases.addAll(m.moles().keySet()));
        for (HazardHandlers.Registration handler : handlers) {
            for (ResourceLocation gas : gases) {
                if (!entity.isAlive()) {
                    return;
                }
                if (handler.appliesTo(gas)) {
                    HazardExposure exposure = new HazardExposure(entity, gas, assessment.verdict(), around, seconds);
                    if (!call(handler, exposure)) {
                        break;
                    }
                }
            }
        }
    }

    /** Runs a handler; false if it threw. */
    private static boolean call(HazardHandlers.Registration handler, HazardExposure exposure) {
        try {
            handler.handler().apply(exposure);
            return true;
        } catch (Exception | LinkageError e) {
            String source = "Hazard handler " + handler.id();
            if (ThirdPartyFaults.shouldLog(source)) {
                LOGGER.error("{} threw; skipped for this check (logged at most once a minute)", source, e);
            }
            return false;
        }
    }
}
