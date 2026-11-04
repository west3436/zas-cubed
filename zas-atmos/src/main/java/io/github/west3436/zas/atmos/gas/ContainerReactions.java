package io.github.west3436.zas.atmos.gas;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Reactions in the gas the content mod holds: a pipe network's, a canister's, a tank's (CONT-100,
 * D-57). Whatever holds the gas calls this once per atmos tick. The gas reacts through the API
 * (API-019), with no ignition source, so it burns only above a fuel's autoignition temperature:
 * 375 L of it per second, or all of it if it is smaller (FIRE-031).
 *
 * <p>Each call copies the gas into the framework's mixture and back, and writes nothing if nothing
 * reacted. Server thread only, as {@link GasStore} is.
 */
public final class ContainerReactions {
    private ContainerReactions() {}

    /**
     * One atmos tick of reactions on a store. The pressure follows from the new moles and
     * temperature ({@link GasStore#pressure()}).
     *
     * @param gas the gas, changed in place
     * @return whether anything reacted
     */
    public static boolean react(GasStore gas) {
        if (gas.isEmpty()) {
            return false;
        }
        GasMixture before = gas.mixture();
        GasMixture after = Reactions.applyStandalone(before, AtmosTicks.SECONDS, false);
        if (after == before) {
            return false;
        }
        TreeMap<ResourceLocation, Double> moles = new TreeMap<>(after.moles());
        moles.values().removeIf(n -> n <= GasStore.EPSILON);
        gas.set(new GasParcel(moles, after.temperature()));
        return true;
    }

    /**
     * One atmos tick of reactions on an item that carries gas as {@link AtmosComponents#GAS}: a
     * tank, or a mined canister. The stack is written only if something reacted, so a quiet item
     * costs its holder no inventory sync.
     *
     * @param stack the item, changed in place
     * @param volume its volume, L
     * @return whether anything reacted
     */
    public static boolean react(ItemStack stack, double volume) {
        GasParcel held = stack.get(AtmosComponents.GAS.get());
        if (held == null || held.isEmpty()) {
            return false;
        }
        GasStore gas = new GasStore(volume);
        gas.set(held);
        if (!react(gas)) {
            return false;
        }
        stack.set(AtmosComponents.GAS.get(), new GasParcel(gas.moles(), gas.temperature()));
        return true;
    }
}
