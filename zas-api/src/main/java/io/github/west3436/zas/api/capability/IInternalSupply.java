package io.github.west3436.zas.api.capability;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * An internal supply of breathable gas, such as a gas tank, that internals breathe from (API-022,
 * LIFE-021): {@link ILifeSupport#internalSupply()}.
 *
 * <p>While a player's internals are on and its breathing is sealed, ZAS³ breathes from the first
 * supply it finds (LIFE-028). The supply delivers its gas at its distribution pressure (LIFE-044)
 * while it holds at least that pressure; otherwise the player breathes the atmosphere (LIFE-046).
 * Each second ZAS³ draws P_dist × 0.0625 L / (R · T) moles from it (LIFE-043), and the gas goes
 * into the atmosphere around the player once breathed (LIFE-045).
 *
 * <p><b>Gear with a switch of its own.</b> The internals switch is the wearer's, and nothing but
 * the wearer flips it (LIFE-029). A breathing set that another mod switches on and off itself
 * answers true from {@link #alwaysOn()} instead, and offers its supply only while it runs: ZAS³
 * then breathes from it whenever the wearer's breathing is sealed, whatever the wearer's switch
 * says.
 *
 * <p><b>Where it comes from.</b> An item or entity with life support of its own gives its supply
 * through {@link ILifeSupport#internalSupply()}. An item that only holds gas, and gets its
 * protection from data or has none, gives it through {@link #ITEM} instead. A supply is treated
 * the same whichever way it is found.
 *
 * <p><b>Threads.</b> ZAS³ calls it on the server thread, on the item as it is then.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it. A supply whose {@link #contents()} or {@link
 * #distributionPressure()} throws delivers nothing for that call, so the player breathes the
 * atmosphere. A {@link #draw} that throws gives no gas, so nothing is exhaled. ZAS³ asks again the
 * next time, and logs the fault at most once a minute for each item or entity type.
 */
public interface IInternalSupply {
    /**
     * The capability on an item that is an internal supply without being life support in code: its
     * protection, if it has any, comes from the {@code zas/protection} datapack directory (LIFE-026),
     * which an {@link ILifeSupport#ITEM} capability on the same item would replace as a whole. Such
     * as another mod's suit that holds its own oxygen, with its ratings shipped as data (SPACE-023).
     *
     * <p>ZAS³ looks for it wherever internals look for a supply (LIFE-028): in the armor slots, the
     * slots other mods add ({@link io.github.west3436.zas.api.life.ExtraSlots}), the offhand and the
     * hotbar. On an item that has both, the supply of its {@link ILifeSupport} is
     * used and this one isn't asked. Without ZAS³ the capability still exists, but nothing reads
     * it.
     */
    ItemCapability<IInternalSupply, Void> ITEM = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("zas", "internal_supply"), IInternalSupply.class);

    /**
     * The gas it holds now, with its temperature and its volume.
     *
     * @return the contents
     */
    GasMixture contents();

    /**
     * The pressure it delivers its gas at, in kPa (LIFE-044): 0 delivers nothing.
     *
     * @return the distribution pressure in kPa, at least 0
     */
    double distributionPressure();

    /**
     * Takes gas out to be breathed: up to {@code moles} in all, in proportion to what it holds.
     * It must change what {@link #contents()} answers next.
     *
     * @param moles the moles wanted, at least 0
     * @return the gas taken, at the supply's temperature; less than asked if it holds less
     */
    GasParcel draw(double moles);

    /**
     * Whether it delivers without the wearer's internals being switched on (LIFE-029), for gear
     * that has a switch of its own. False by default: the wearer's switch decides.
     *
     * <p>While the wearer's breathing is sealed, ZAS³ breathes from the first supply it finds if the
     * wearer's switch is on and that supply delivers (LIFE-028), as ever. Failing that, it breathes
     * from the first always-on supply that delivers, looking in the same order. So an always-on
     * supply never keeps the wearer from choosing a tank, and never needs the switch.
     *
     * @return true if it needs no internals switch
     */
    default boolean alwaysOn() {
        return false;
    }
}
