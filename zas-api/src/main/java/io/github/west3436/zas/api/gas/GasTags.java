package io.github.west3436.zas.api.gas;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ZasApi;
import java.util.Objects;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The gas tags ZAS³ ships, and their members (CORE-012). Hazard, fire and device logic read these
 * instead of naming gases, so gases that datapacks and addons define, and add to the tags, are
 * handled too.
 *
 * <p>The members come from datapacks, in Minecraft's tag format: {@code
 * data/<namespace>/tags/zas/gas/<path>.json} defines {@code #<namespace>:<path>}, and a pack adds
 * a gas to a shipped tag with its own {@code data/zas/tags/zas/gas/toxic.json}. Like the gas
 * definitions, the tags are fixed when the server starts; a {@code /reload} that changes them takes
 * effect at the next start. Clients receive the server's tags with its gas definitions.
 *
 * <p>Queries answer for the server running in this JVM, or else for the server the client is
 * connected to. Without ZAS³ the only gases are those of {@link StandardAir}, and the only tag
 * with a member is {@link #OXIDIZER}, which holds oxygen.
 *
 * <p>Thread: any. Results are immutable.
 */
public final class GasTags {
    /**
     * Gases that support combustion as an oxidizer: O₂ and N₂O. For fire logic and for warnings
     * about a fuel mixed with an oxidizer.
     */
    public static final GasTag OXIDIZER = GasTag.of("zas", "oxidizer");

    /**
     * Gases that burn in air: H₂, CH₄ and CO, ZAS³'s fuels (FIRE-003). For fire logic, the air
     * alarm and devices that warn of flammable mixtures.
     */
    public static final GasTag FLAMMABLE = GasTag.of("zas", "flammable");

    /**
     * Gases that harm whoever breathes them, beyond displacing oxygen: CO and N₂O. A gas mask
     * filters them out of the breath (CONT-088), and a scrubber removes them by default, with CO₂
     * (CONT-015). CO₂ is harmful only at high partial pressures, which its hazard profile covers,
     * so it isn't in this tag.
     */
    public static final GasTag TOXIC = GasTag.of("zas", "toxic");

    private GasTags() {}

    /**
     * The gases in a tag.
     *
     * <p>Thread: any.
     *
     * @param tag the tag
     * @return the ids of its gases; empty if the tag doesn't exist or has none
     */
    @AnyThread
    public static Set<ResourceLocation> gases(GasTag tag) {
        return ZasApi.provider().gasesInTag(Objects.requireNonNull(tag, "tag"));
    }

    /**
     * Whether a gas is in a tag.
     *
     * <p>Thread: any.
     *
     * @param tag the tag
     * @param gas the gas id
     * @return true if the tag holds the gas
     */
    @AnyThread
    public static boolean contains(GasTag tag, ResourceLocation gas) {
        return gases(tag).contains(Objects.requireNonNull(gas, "gas"));
    }
}
