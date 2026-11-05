package io.github.west3436.zas.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.api.capability.SealGeometry;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.Test;

/** Seal providers in the API (API-023, GEO-014): the geometry they answer with, and the provider default. */
class SealProviderTest {
    @Test
    void api_023_the_constants_mean_what_they_say() {
        assertEquals(EnumSet.allOf(Direction.class), SealGeometry.SEALED.sealedFaces());
        assertEquals(0, SealGeometry.SEALED.volume());
        assertTrue(SealGeometry.OPEN.sealedFaces().isEmpty());
        assertTrue(SealGeometry.OPEN.boundaryFaces().isEmpty());
        assertEquals(1000, SealGeometry.OPEN.volume());
    }

    /** The volume of a block position is 0 to 1,000 L, as in a datapack override (GEO-005). */
    @Test
    void api_023_volume_is_checked() {
        assertThrows(IllegalArgumentException.class, () -> new SealGeometry(Set.of(), Set.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> new SealGeometry(Set.of(), Set.of(), 1000.5));
        assertThrows(IllegalArgumentException.class, () -> new SealGeometry(Set.of(), Set.of(), Double.NaN));
        assertEquals(1000, new SealGeometry(Set.of(), Set.of(), 1000).volume());
        assertEquals(0, new SealGeometry(Set.of(), Set.of(), 0).volume());
    }

    /** The geometry keeps its own copy of the faces. */
    @Test
    void api_023_faces_are_copied() {
        Set<Direction> faces = new HashSet<>(Set.of(Direction.UP));
        SealGeometry seal = new SealGeometry(faces, faces, 500);
        faces.add(Direction.DOWN);
        assertEquals(Set.of(Direction.UP), seal.sealedFaces());
        assertEquals(Set.of(Direction.UP), seal.boundaryFaces());
    }

    /**
     * The provider method has a default that does nothing, so a provider built against an API from
     * before it still works when a newer API is loaded, and the fallback does nothing (D-17).
     */
    @Test
    void geo_014_mark_dirty_has_a_default() throws NoSuchMethodException {
        assertTrue(ZasApiProvider.class
                .getMethod("markDirty", ServerLevel.class, BlockPos.class)
                .isDefault());
        FallbackProvider.INSTANCE.markDirty(null, BlockPos.ZERO);
    }
}
