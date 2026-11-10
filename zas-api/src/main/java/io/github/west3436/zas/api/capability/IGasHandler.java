package io.github.west3436.zas.api.capability;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;
import org.jetbrains.annotations.Nullable;

/**
 * A block's store of gas, which others can fill and drain (API-021): a tank, or a machine's buffer.
 * Register a provider for {@link #BLOCK} in {@code RegisterCapabilitiesEvent}. The context is the face
 * being asked about, or null for no face in particular, so a block can offer it on some faces only.
 *
 * <p><b>Pipes.</b> The ZAS³ content mod's pipes and devices connect to any block that provides it
 * (API-025). A pipe grows an arm toward it, and a device's pipe face connects as it would to a pipe.
 * The handler doesn't join the pipe network. Each atmos tick the node on that face asks for {@link
 * #contents()}, then moves each gas toward an equal concentration on both sides, in one atmos tick
 * and as far as the handler lets it: what it refuses stays in the network. ZAS³ looks the capability
 * up anew each atmos tick, so it follows one that comes or goes within an atmos tick. Other mods may
 * cache it, so call {@code level.invalidateCapabilities(pos)} when it does.
 *
 * <p><b>Pipes provide it too.</b> The content mod's pipes, caps, valves and devices' pipe faces are gas
 * handlers themselves, so another mod's pipe or machine can join a pipe network: what it finds on a
 * face is the whole network behind it. A fill stops at the highest pressure a ZAS³ pump reaches, and a
 * shut valve holds nothing. ZAS³'s own parts never use it on each other.
 *
 * <p><b>Conservation.</b> ZAS³ takes what it offers out of the network before it calls {@link #fill}
 * and takes back whatever the handler doesn't say it took, and it adds all that {@link #drain} returns
 * to the network. So gas is conserved as long as the handler's returns are true: it gains what it
 * says it took, and loses what it says it gave.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³'s content mod catches it, skips the handler for that atmos
 * tick and logs the fault at most once a minute. If a fill throws, all it was offered goes back to the
 * network, so a handler shouldn't throw after taking gas.
 *
 * <p>Without ZAS³ or its content mod the capability still exists, but nothing here calls it.
 *
 * <p><b>Threads.</b> ZAS³ makes every call on the server thread (API-026).
 */
public interface IGasHandler {
    /** The capability on blocks. */
    BlockCapability<IGasHandler, @Nullable Direction> BLOCK =
            BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("zas", "gas_handler"), IGasHandler.class);

    /** Whether a call changes anything. */
    enum Action {
        /** Do it. */
        EXECUTE,
        /** Change nothing, and answer with what executing it now would do. */
        SIMULATE
    }

    /**
     * The gas it holds now. The volume is what ZAS³ works out a concentration from, so for a tank it
     * is the tank's size.
     *
     * <p>Thread: server.
     *
     * @return its moles, temperature and volume; an empty mixture if it holds none
     */
    @ServerThread
    GasMixture contents();

    /**
     * Fills it with gas.
     *
     * @param gas the gas offered
     * @param action whether to do it or only to say what it would do
     * @return what it took, no more of any gas than was offered; {@link GasParcel#EMPTY} if it took
     *     none. Its temperature isn't used
     */
    @ServerThread
    GasParcel fill(GasParcel gas, Action action);

    /**
     * Drains gas from it.
     *
     * @param moles how many moles of each gas are wanted
     * @param action whether to do it or only to say what it would do
     * @return what it gave, at its temperature; {@link GasParcel#EMPTY} if it gave none
     */
    @ServerThread
    GasParcel drain(Map<ResourceLocation, Double> moles, Action action);
}
