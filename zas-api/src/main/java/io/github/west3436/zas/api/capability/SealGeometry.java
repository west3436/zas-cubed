package io.github.west3436.zas.api.capability;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.Direction;

/**
 * The gas geometry a block supplies through {@link ISealProvider} (API-023): a single cell, as with
 * a datapack geometry override. The faces not sealed are fully open.
 *
 * @param sealedFaces the gas-tight faces; with all six there is no cell, so the block holds no gas
 * @param boundaryFaces the faces that keep zones apart where gas crosses them, as an open door does
 *     (GEO-007)
 * @param volume the block's gas volume in litres, 0 to 1,000
 */
public record SealGeometry(Set<Direction> sealedFaces, Set<Direction> boundaryFaces, double volume) {
    /** The most gas a block position holds, in litres. */
    public static final double FULL_VOLUME = 1000;

    /** Gas-tight on every face, holding no gas. */
    public static final SealGeometry SEALED = new SealGeometry(EnumSet.allOf(Direction.class), Set.of(), 0);

    /** Open on every face and holding as much gas as air does. */
    public static final SealGeometry OPEN = new SealGeometry(Set.of(), Set.of(), FULL_VOLUME);

    /**
     * @throws IllegalArgumentException if {@code volume} isn't from 0 to 1,000
     */
    public SealGeometry {
        sealedFaces = Set.copyOf(Objects.requireNonNull(sealedFaces, "sealedFaces"));
        boundaryFaces = Set.copyOf(Objects.requireNonNull(boundaryFaces, "boundaryFaces"));
        if (!(volume >= 0 && volume <= FULL_VOLUME)) {
            throw new IllegalArgumentException("volume must be 0 to 1000 L, got " + volume);
        }
    }
}
