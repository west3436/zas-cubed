package io.github.west3436.zas.api.capability;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

/**
 * A block that says for itself which of its faces are gas-tight, which keep zones apart, and how
 * much gas it holds (API-023), such as an airlock that opens and closes.
 *
 * <p>Register a provider for {@link #BLOCK} on the block in {@code RegisterCapabilitiesEvent}, at
 * any priority but the lowest, which is when ZAS³ notes the blocks that have one. While the provider
 * gives an answer ZAS³ ignores the block's collision shape. A datapack geometry override of the block
 * wins over the provider (GEO-005): the pack has the last word, and the provider isn't called.
 *
 * <p><b>Changes.</b> ZAS³ keeps the answer until the block changes, or you call {@link
 * io.github.west3436.zas.api.mutation.BlockGeometry#markDirty} (API-028). So call it whenever the
 * answer would change for a reason ZAS³ can't see: a change of the block entity's data, for
 * example, rather than of the block state.
 *
 * <p><b>Threads.</b> ZAS³ calls it on the server thread, when it copies the block's section for an
 * atmos tick (D-30). It should be cheap, since a section's copy waits for it. Without ZAS³ the
 * capability still exists, but nothing reads it.
 *
 * <p><b>If it throws</b> (NFR-013). ZAS³ catches it and uses the block's collision shape for that
 * atmos tick. It asks again the next atmos tick, without waiting for {@code markDirty}, and logs the
 * fault at most once a minute.
 */
public interface ISealProvider {
    /** The capability on blocks. */
    BlockCapability<ISealProvider, Void> BLOCK = BlockCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("zas", "seal_provider"), ISealProvider.class);

    /**
     * The block's geometry now.
     *
     * @return the geometry, or empty to use the one from the block's collision shape
     */
    Optional<SealGeometry> geometry();
}
