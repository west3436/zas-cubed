package io.github.west3436.zas.api.reaction;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.api.gas.GasMixture;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * Gas reactions outside the world (API-019) and reaction types implemented in code (API-020).
 *
 * <p>Reactions are datapack definitions (FIRE-001, {@code docs/datapacks.md}). Zones run them on
 * their own; this class lets a mod run them on gas it holds itself, such as a tank's or a pipe's,
 * and lets it add reaction types that a datapack definition can name.
 *
 * <p>Without ZAS³ installed there are no reactions: {@link #applyStandalone} returns its mixture
 * unchanged, and registered types are kept but never run (D-17).
 */
public final class Reactions {
    /** The id of the built-in type, a reaction given wholly by its stoichiometry. */
    public static final ResourceLocation STANDARD = ResourceLocation.fromNamespaceAndPath("zas", "standard");

    /**
     * A fire's parcel, in litres of gas per second (FIRE-011, FIRE-031): a container's mixture that
     * ignites burns this much of itself per second, or all of it if it is smaller.
     */
    public static final double PARCEL_LITRES_PER_SECOND = 375;

    /** Registered types, by id. Lookups only; never iterated. */
    private static final Map<ResourceLocation, ReactionType> TYPES = new ConcurrentHashMap<>();

    private Reactions() {}

    /**
     * Applies the server's reactions to a mixture held outside the world, such as a tank's, over a
     * time step (API-019, FIRE-031). It touches no world state.
     *
     * <p>It does what a zone's atmos tick does to its gas, in the same order:
     *
     * <ol>
     *   <li><b>Fire.</b> If the mixture ignites, because {@code ignition} says an ignition source
     *       is present or because it is at or above a fuel's autoignition temperature (FIRE-010),
     *       and it is within that fuel's flammability limits at its temperature (FIRE-032) and passes
     *       the fire oxygen threshold (FIRE-030), it burns as one hotspot: {@link
     *       #PARCEL_LITRES_PER_SECOND} times {@code seconds} litres of it, or all of it if it is
     *       smaller. A 70 L tank therefore burns completely in one second.
     *   <li><b>Other reactions,</b> those that need no ignition, run over the whole mixture for
     *       {@code seconds}, such as N₂O decomposing above 850 K (FIRE-004).
     * </ol>
     *
     * <p>Heat released goes into the mixture's temperature through its gases' heat capacities
     * ({@link io.github.west3436.zas.api.gas.GasProperties}). Gases the server doesn't define take
     * no part, except that they absorb their share of the heat. The volume doesn't change, so the
     * pressure follows from the new moles and temperature.
     *
     * <p>Thread: any. The reactions are those the server last loaded; on a client with no
     * integrated server there are none.
     *
     * @param mixture the gas, with its volume and temperature
     * @param seconds the time step, finite and not negative; one atmos tick is {@link
     *     io.github.west3436.zas.api.atmosphere.Atmospheres#periodTicks()} / 20 seconds
     * @param ignition whether an ignition source acts on the gas, such as a spark in a tank
     * @return the mixture after the step; the same mixture if nothing reacted
     * @throws IllegalArgumentException if {@code seconds} is negative or not finite
     */
    @AnyThread
    public static GasMixture applyStandalone(GasMixture mixture, double seconds, boolean ignition) {
        Objects.requireNonNull(mixture, "mixture");
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("seconds must be finite and >= 0: " + seconds);
        }
        return ZasApi.provider().applyStandalone(mixture, seconds, ignition);
    }

    /**
     * Registers a reaction type implemented in code (API-020), so datapack reaction definitions can
     * name it in their {@code "type"} field. Register during mod loading, such as in common setup,
     * before datapacks load; a definition naming a type that isn't registered is skipped with an
     * error.
     *
     * <p>Thread: any.
     *
     * @param id the type's id, in the registering mod's namespace
     * @param type the type
     * @throws IllegalArgumentException if a type with that id is already registered, or the id is
     *     {@link #STANDARD}
     */
    @AnyThread
    public static void registerType(ResourceLocation id, ReactionType type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        if (id.equals(STANDARD)) {
            throw new IllegalArgumentException(STANDARD + " is the built-in type");
        }
        if (TYPES.putIfAbsent(id, type) != null) {
            throw new IllegalArgumentException("reaction type " + id + " is already registered");
        }
    }

    /**
     * A registered reaction type (API-020).
     *
     * <p>Thread: any.
     *
     * @param id the type's id
     * @return the type, or empty if none is registered with that id
     */
    @AnyThread
    public static Optional<ReactionType> type(ResourceLocation id) {
        return Optional.ofNullable(TYPES.get(Objects.requireNonNull(id, "id")));
    }
}
