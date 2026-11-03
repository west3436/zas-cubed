package io.github.west3436.zas.api.fire;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * STUB of Track 2's fire API (FIRE-019), with its agreed signatures and its answers without ZAS³.
 * Track 2's class replaces this file when its PR lands; nothing here is meant to be merged.
 */
public final class Fires {
    private Fires() {}

    /** Puts out the hotspots and fire blocks in an inclusive box; how many. Stub: 0. */
    @ServerThread
    public static int extinguish(ServerLevel level, BlockPos from, BlockPos to) {
        return 0;
    }

    /** The hotspots in a zone. Stub: none. */
    @AnyThread
    public static List<BlockPos> hotspots(Level level, ZoneId zone) {
        return List.of();
    }

    /** Whether a zone has a hotspot. Stub: false. */
    @AnyThread
    public static boolean isBurning(Level level, ZoneId zone) {
        return false;
    }

    /** Whether a position is a hotspot. Stub: false. */
    @AnyThread
    public static boolean isHotspot(Level level, BlockPos pos) {
        return false;
    }
}
