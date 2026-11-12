package io.github.west3436.zas.api.capability;

import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * The life support an item gives when worn in an armor slot, or an entity gives itself (API-022,
 * LIFE-026). ZAS³ reads it when it judges what the environment does to an entity:
 *
 * <ul>
 *   <li>pressure protection: the range of pressures it is rated for (LIFE-020);
 *   <li>thermal protection: the range of temperatures it is rated for (LIFE-025);
 *   <li>sealed breathing, which internals need, from the head slot (LIFE-021);
 *   <li>gas filtering, which keeps the {@code #zas:toxic} gases out of the breath, from the head
 *       slot (CONT-088);
 *   <li>airflow resistance (FLOW-012);
 *   <li>an internal supply of breathable gas for internals ({@link IInternalSupply}), from a gas
 *       tank anywhere internals look for one, or a suit's tank slot (LIFE-028).
 * </ul>
 *
 * <p>Each part has a default that gives nothing, so an implementation overrides only what it
 * provides, and later versions can add parts. Without ZAS³ the capabilities still exist, but
 * nothing reads them.
 *
 * <p><b>Worn items.</b> Register a provider for {@link #ITEM} on the item. It counts in the head,
 * chest, legs and feet slots, each covering its share of the body (LIFE-024: by default 0.3, 0.4,
 * 0.2 and 0.1). An item without the capability can get the same parts from data instead (the
 * {@code zas/protection} datapack directory). <b>Entities.</b> Register a provider for {@link
 * #ENTITY} on the entity type. What an entity gives itself covers its whole body.
 *
 * <p><b>Extra slots.</b> In an equipment slot another mod adds ({@link
 * io.github.west3436.zas.api.life.ExtraSlots}), an item covers none of the body. Its internal
 * supply still counts there, and in a head slot so do its seal and its filter.
 *
 * <p><b>Coverage.</b> A piece protects against a pressure P if P is within its pressure range on
 * that side of 1 atm. The worn pieces that do give the coverage c, and (1 − c⁵) of the difference
 * |P − 1 atm| still reaches the wearer (LIFE-020). Likewise c of pieces whose thermal range holds
 * the temperature scales temperature damage by (1 − c) (LIFE-025).
 *
 * <p><b>Threads.</b> ZAS³ calls it on the server thread, on the item or entity as it is then. It
 * should be cheap: ZAS³ asks every check, every 2 s by default.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it, and a part that throws gives nothing for that
 * call, as its default does, so a suit that throws leaves its wearer exposed. Its other parts still
 * count, and a capability provider that throws counts as no capability. ZAS³ asks again the next
 * time, and logs the fault at most once a minute for each item or entity type.
 */
public interface ILifeSupport {
    /** The capability on worn items. */
    ItemCapability<ILifeSupport, Void> ITEM =
            ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath("zas", "life_support"), ILifeSupport.class);

    /** The capability on entities, for life support of their own. */
    EntityCapability<ILifeSupport, Void> ENTITY = EntityCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("zas", "life_support"), ILifeSupport.class);

    /**
     * The lowest pressure it protects against, in kPa (LIFE-020): 0 protects down to vacuum. The
     * default, 1 atm ({@link StandardAir#PRESSURE}), gives no low-pressure protection.
     *
     * @return the pressure in kPa, from 0 to 1 atm
     */
    default double minPressure() {
        return StandardAir.PRESSURE;
    }

    /**
     * The highest pressure it protects against, in kPa (LIFE-020). The default, 1 atm ({@link
     * StandardAir#PRESSURE}), gives no high-pressure protection.
     *
     * @return the pressure in kPa, at least 1 atm
     */
    default double maxPressure() {
        return StandardAir.PRESSURE;
    }

    /**
     * The lowest temperature it protects against, in K (LIFE-025). The default, {@link
     * StandardAir#TEMPERATURE}, gives no cold protection.
     *
     * @return the temperature in K, from 0 to {@link StandardAir#TEMPERATURE}
     */
    default double minTemperature() {
        return StandardAir.TEMPERATURE;
    }

    /**
     * The highest temperature it protects against, in K (LIFE-025). The default, {@link
     * StandardAir#TEMPERATURE}, gives no heat protection.
     *
     * @return the temperature in K, at least {@link StandardAir#TEMPERATURE}
     */
    default double maxTemperature() {
        return StandardAir.TEMPERATURE;
    }

    /**
     * Whether it seals the wearer's breathing, so internals can work (LIFE-021). It counts only in
     * the head slot, in a head slot another mod adds, or on the entity itself. False by default.
     *
     * @return true if it seals the breathing
     */
    default boolean sealedBreathing() {
        return false;
    }

    /**
     * Whether it filters the {@link io.github.west3436.zas.api.gas.GasTags#TOXIC} gases out of the
     * wearer's breath, whether that comes from the atmosphere or its internals, as a gas mask does
     * (CONT-088). It supplies no O₂, holds nothing and never wears out: the gases stay where they
     * were. It counts only in the head slot, in a head slot another mod adds, or on the entity
     * itself. False by default.
     *
     * @return true if it filters
     */
    default boolean gasFiltering() {
        return false;
    }

    /**
     * How much it resists being pushed by airflow, from 0 (none, the default) to 1 (not pushed at
     * all) (FLOW-012). The wearer's resistance is the highest of its pieces' and its own.
     *
     * @return the resistance, 0 to 1
     */
    default double airflowResistance() {
        return 0;
    }

    /**
     * The internal supply of breathable gas it holds, for internals, or empty for none (the
     * default). A gas tank gives its own gas, and counts in the slots other mods add, the offhand
     * and the hotbar too; a suit gives the tank in its tank slot (LIFE-028). Gear with a switch of
     * its own gives its supply only while it runs, and makes it {@linkplain
     * IInternalSupply#alwaysOn() always on}.
     *
     * @return the supply, or empty
     */
    default Optional<IInternalSupply> internalSupply() {
        return Optional.empty();
    }
}
