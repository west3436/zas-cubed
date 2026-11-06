package io.github.west3436.zas.api.event;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.life.Hazard;
import java.util.Objects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * ZAS³ is about to deal a hazard to an entity (API-033). Change {@linkplain #setDamage the damage} to
 * adjust it, or {@linkplain #setCanceled cancel} the event to spare the entity this time, for example
 * for a suit of your own.
 *
 * <p><b>When it fires.</b> Once for each hazard, each time that hazard would harm the entity: at each
 * breath check for {@link Hazard#TOXIC_GAS} and the pressure and temperature hazards, which is every
 * 2 s by default, once a second on an empty air meter for {@link Hazard#HYPOXIA}, and at each blow for
 * {@link Hazard#AIRFLOW_IMPACT}. It doesn't fire where a hazard would do nothing, as where it's turned
 * off or the entity's protection covers it, and it covers only ZAS³'s own hazards, not what a {@link
 * io.github.west3436.zas.api.life.HazardHandler} does.
 *
 * <p><b>The damage</b> is in HP, for all the time since the last check, after protection and the
 * hazard's scale. A {@link Hazard#TOXIC_GAS} tick may have 0 damage, when its gas only applies status
 * effects so far.
 *
 * <p><b>Cancelling</b> deals no damage, and for {@link Hazard#TOXIC_GAS} applies no status effects, but
 * changes nothing else: exposures go on counting, the verdict from {@link
 * io.github.west3436.zas.api.life.Breathing#of} still lists the hazard, and the air meter of a hypoxic
 * entity stays empty. Setting the damage to 0 instead keeps a toxic gas's effects.
 *
 * <p>Fired on {@code NeoForge.EVENT_BUS}, on the server thread, just before the damage is dealt
 * (API-032). A listener that throws is skipped for that event, and what the listeners before it did
 * stands (NFR-013). Without ZAS³ it never fires.
 */
@ServerThread
public final class HazardTickEvent extends Event implements ICancellableEvent {
    private final LivingEntity entity;
    private final Hazard hazard;
    private final double originalDamage;
    private double damage;

    /**
     * Creates the event. Only ZAS³ fires it.
     *
     * <p>Thread: server thread.
     *
     * @param entity the entity about to be harmed
     * @param hazard the hazard
     * @param damage the damage it would deal, in HP, at least 0
     * @throws IllegalArgumentException if the damage isn't finite and at least 0
     */
    public HazardTickEvent(LivingEntity entity, Hazard hazard, double damage) {
        this.entity = Objects.requireNonNull(entity, "entity");
        this.hazard = Objects.requireNonNull(hazard, "hazard");
        this.originalDamage = checked(damage);
        this.damage = damage;
    }

    /**
     * The entity about to be harmed.
     *
     * <p>Thread: server thread.
     *
     * @return the entity
     */
    public LivingEntity entity() {
        return entity;
    }

    /**
     * The hazard.
     *
     * <p>Thread: server thread.
     *
     * @return the hazard
     */
    public Hazard hazard() {
        return hazard;
    }

    /**
     * The damage ZAS³ worked out, whatever listeners have changed it to.
     *
     * <p>Thread: server thread.
     *
     * @return the damage in HP, at least 0
     */
    public double originalDamage() {
        return originalDamage;
    }

    /**
     * The damage it will deal, as far as listeners have left it.
     *
     * <p>Thread: server thread.
     *
     * @return the damage in HP, at least 0
     */
    public double damage() {
        return damage;
    }

    /**
     * Changes the damage it will deal.
     *
     * <p>Thread: server thread.
     *
     * @param damage the damage in HP; 0 deals none
     * @throws IllegalArgumentException if the damage isn't finite and at least 0
     */
    public void setDamage(double damage) {
        this.damage = checked(damage);
    }

    private static double checked(double damage) {
        if (!Double.isFinite(damage) || damage < 0) {
            throw new IllegalArgumentException("damage must be finite and >= 0: " + damage);
        }
        return damage;
    }
}
