package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.particle.GasJet;
import io.github.west3436.zas.atmos.particle.GasJets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * The gas jets the server sent during GameTests. Tests in other areas can be sending at the same
 * time, so each asks only about the jets near its own.
 */
final class JetLog {
    private static final List<GasJet> SEEN = new ArrayList<>();
    private static boolean listening;

    private JetLog() {}

    /** Starts recording, if nothing has yet. Call it before the first jet a test looks for. */
    static void listen() {
        if (!listening) {
            listening = true;
            GasJets.hookForTest(SEEN::add);
        }
    }

    /**
     * Takes the jets recorded around a point: they aren't reported again.
     *
     * @param near the point
     * @return the jets within 64 blocks of it, oldest first
     */
    static List<GasJet> take(Vec3 near) {
        List<GasJet> out = new ArrayList<>();
        for (Iterator<GasJet> it = SEEN.iterator(); it.hasNext(); ) {
            GasJet jet = it.next();
            if (near.distanceTo(new Vec3(jet.x(), jet.y(), jet.z())) <= 64) {
                out.add(jet);
                it.remove();
            }
        }
        return out;
    }
}
