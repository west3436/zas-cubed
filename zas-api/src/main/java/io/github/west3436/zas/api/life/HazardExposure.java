package io.github.west3436.zas.api.life;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a {@link HazardHandler} is told at a check: an entity, one gas it's exposed to, and how much of
 * that gas reaches it (LIFE-032).
 *
 * <p>The gas may be in its breath, around it, or both, and they can differ. A filter or a tank leaves
 * less or more in the breath than is around it, and an entity that doesn't breathe has no breath. With
 * its eyes in a fluid it has no atmosphere around it.
 */
@ServerThread
public final class HazardExposure {
    private final LivingEntity entity;
    private final ResourceLocation gas;
    private final Breathability verdict;
    private final Optional<GasMixture> around;
    private final double seconds;

    /**
     * Creates an exposure. Only ZAS³ makes them, for its calls to handlers.
     *
     * <p>Thread: server thread.
     *
     * @param entity the entity
     * @param gas the gas the handler is called for
     * @param verdict the entity's verdict at this check
     * @param around the atmosphere at its eyes, or empty with its eyes in a fluid
     * @param seconds the seconds this check charges for
     */
    public HazardExposure(
            LivingEntity entity,
            ResourceLocation gas,
            Breathability verdict,
            Optional<GasMixture> around,
            double seconds) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.gas = Objects.requireNonNull(gas, "gas");
        this.verdict = Objects.requireNonNull(verdict, "verdict");
        this.around = Objects.requireNonNull(around, "around");
        this.seconds = seconds;
    }

    /**
     * The entity.
     *
     * <p>Thread: server thread.
     *
     * @return the entity, alive
     */
    public LivingEntity entity() {
        return entity;
    }

    /**
     * The gas the handler is called for, which for a handler registered for a tag is one of the tag's.
     *
     * <p>Thread: server thread.
     *
     * @return the gas id
     */
    public ResourceLocation gas() {
        return gas;
    }

    /**
     * The entity's verdict at this check, which has its breath and its protection.
     *
     * <p>Thread: server thread.
     *
     * @return the verdict
     */
    public Breathability verdict() {
        return verdict;
    }

    /**
     * The atmosphere at the entity's eyes.
     *
     * <p>Thread: server thread.
     *
     * @return the mixture, or empty with its eyes in a fluid
     */
    public Optional<GasMixture> around() {
        return around;
    }

    /**
     * The partial pressure of the gas at the entity's eyes.
     *
     * <p>Thread: server thread.
     *
     * @return the pressure in kPa; 0 with none there, or with its eyes in a fluid
     */
    public double surroundingPressure() {
        return around.map(m -> m.partialPressure(gas)).orElse(0.0);
    }

    /**
     * The partial pressure of the gas in what the entity breathes, after any filter, from its tank if
     * it breathes from one.
     *
     * <p>Thread: server thread.
     *
     * @return the pressure in kPa; 0 if it breathes none of it, or doesn't breathe
     */
    public double breathedPressure() {
        return verdict.breath().map(m -> m.partialPressure(gas)).orElse(0.0);
    }

    /**
     * The seconds since the entity's last check, which this one charges for. Scale what the handler does
     * by it.
     *
     * <p>Thread: server thread.
     *
     * @return the seconds, above 0
     */
    public double seconds() {
        return seconds;
    }
}
