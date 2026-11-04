package io.github.west3436.zas.atmos.gametest;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * The vanilla explosions a GameTest set off, with the power each was made with. Tests in other areas
 * can be exploding at the same time, so each asks only about the explosions near its own.
 */
final class Blasts {
    private static final List<Explosion> SEEN = new ArrayList<>();
    private static boolean listening;

    private Blasts() {}

    /** Starts recording, if nothing has yet. Call it before the first explosion a test looks for. */
    static void listen() {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((ExplosionEvent.Start e) -> SEEN.add(e.getExplosion()));
        }
    }

    /**
     * Takes the explosions recorded around a point: they aren't reported again.
     *
     * @param near the point
     * @return the explosions within 64 blocks of it, oldest first
     */
    static List<Explosion> take(Vec3 near) {
        List<Explosion> out = new ArrayList<>();
        for (Iterator<Explosion> it = SEEN.iterator(); it.hasNext(); ) {
            Explosion e = it.next();
            if (e.center().distanceTo(near) <= 64) {
                out.add(e);
                it.remove();
            }
        }
        return out;
    }
}
