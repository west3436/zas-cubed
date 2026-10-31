package io.github.west3436.zas.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * A datapack geometry override (GEO-005): the blocks it applies to, which of their faces are
 * gas-tight, and their gas volume. An overridden block is a single cell.
 *
 * <p>Files live at {@code data/<namespace>/zas/geometry_overrides/<path>.json}:
 *
 * <pre>{@code
 * {
 *   "blocks": ["minecraft:honey_block", "#minecraft:leaves"],
 *   "sealed_faces": ["down", "up", "north", "south", "west", "east"],
 *   "volume": 0
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code blocks}: block ids, or block tags prefixed with {@code #}. Unknown ids and tags
 *       are skipped, so a pack can name blocks from mods that aren't installed.
 *   <li>{@code sealed_faces}: the gas-tight faces; the others are fully open. Optional, default
 *       none.
 *   <li>{@code volume}: gas volume in litres, 0 to 1,000.
 * </ul>
 *
 * <p>When several overrides match a block, one naming the block directly beats one matching it
 * through a tag; between equals, the file whose id sorts last wins.
 *
 * @param targets the blocks and tags it applies to
 * @param sealedFaces the gas-tight faces
 * @param volume gas volume in litres
 */
public record GeometryOverride(List<Target> targets, Set<Face> sealedFaces, double volume) {
    /** The datapack directory, under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/geometry_overrides";

    private static final Codec<Face> FACE_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                for (Face f : Face.values()) {
                    if (f.name().toLowerCase(Locale.ROOT).equals(s)) {
                        return DataResult.success(f);
                    }
                }
                return DataResult.error(
                        () -> "Unknown face '" + s + "'; expected down, up, north, south, west or east");
            },
            f -> f.name().toLowerCase(Locale.ROOT));

    public static final Codec<GeometryOverride> CODEC = RecordCodecBuilder.create(i -> i.group(
                    Target.CODEC.listOf().fieldOf("blocks").forGetter(GeometryOverride::targets),
                    FACE_CODEC
                            .listOf()
                            .optionalFieldOf("sealed_faces", List.of())
                            .<Set<Face>>xmap(
                                    l -> l.isEmpty() ? EnumSet.noneOf(Face.class) : EnumSet.copyOf(l), List::copyOf)
                            .forGetter(GeometryOverride::sealedFaces),
                    Codec.doubleRange(0, Geometry.FULL_VOLUME).fieldOf("volume").forGetter(GeometryOverride::volume))
            .apply(i, GeometryOverride::new));

    public GeometryOverride {
        targets = List.copyOf(targets);
        sealedFaces = Set.copyOf(sealedFaces);
    }

    /** The geometry this override declares. */
    public Geometry geometry() {
        return Geometry.declared(sealedFaces, volume, 0);
    }

    /**
     * A block id, or a block tag when {@code tag} is set.
     *
     * @param id the block or tag id
     * @param tag whether {@code id} names a tag
     */
    public record Target(ResourceLocation id, boolean tag) {
        public static final Codec<Target> CODEC = Codec.STRING.comapFlatMap(Target::parse, Target::toString);

        static DataResult<Target> parse(String s) {
            boolean tag = s.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? s.substring(1) : s);
            return id == null
                    ? DataResult.error(() -> "Invalid block or tag id '" + s + "'")
                    : DataResult.success(new Target(id, tag));
        }

        @Override
        public String toString() {
            return (tag ? "#" : "") + id;
        }
    }
}
