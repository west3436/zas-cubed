package io.github.west3436.zas.api.gas;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * A gas tag: a named group of gases, such as {@link GasTags#TOXIC} (CORE-012). Datapacks define
 * tags, and add gases to existing ones, in Minecraft's tag format, at {@code
 * data/<namespace>/tags/zas/gas/<path>.json}. {@link GasTags} has the tags ZAS³ ships and reads
 * their members.
 *
 * <p>Immutable; any thread.
 *
 * @param id the tag's id, {@code <namespace>:<path>}
 */
public record GasTag(ResourceLocation id) {
    /**
     * Checks the id.
     *
     * @throws NullPointerException if the id is null
     */
    public GasTag {
        Objects.requireNonNull(id, "id");
    }

    /**
     * The tag with a namespace and path.
     *
     * @param namespace the namespace, such as {@code zas}
     * @param path the path, such as {@code toxic}
     * @return the tag
     * @throws net.minecraft.ResourceLocationException if they don't form a valid id
     */
    public static GasTag of(String namespace, String path) {
        return new GasTag(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** The tag as datapacks write a reference to it: {@code #<namespace>:<path>}. */
    @Override
    public String toString() {
        return "#" + id;
    }
}
