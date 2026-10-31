package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.AnyThread;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * Who or what made a mutation, for the conservation ledger and logs (API-012,
 * CORE-073). Use your mod id as the namespace, for example
 * {@code mymod:scrubber}.
 *
 * @param id the source's id
 */
@AnyThread
public record SourceId(ResourceLocation id) {
    /**
     * Checks the id.
     *
     * @throws NullPointerException if the id is null
     */
    public SourceId {
        Objects.requireNonNull(id, "id");
    }

    /**
     * A source id from a namespace and path.
     *
     * <p>Thread: any.
     *
     * @param namespace usually your mod id
     * @param path what in your mod made the change
     * @return the source id
     */
    @AnyThread
    public static SourceId of(String namespace, String path) {
        return new SourceId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
