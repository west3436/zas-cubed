package io.github.west3436.zas.compat.sable;

import io.github.west3436.zas.compat.sable.mixin.SableMixinPlugin;
import io.github.west3436.zas.provider.Frames;
import io.github.west3436.zas.provider.PointResolver;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Where the Sable adapter starts (COMPAT-100 to COMPAT-102). {@code zas}'s adapter loader calls
 * {@link #init} by name, and only with Sable installed in a version its row supports (COMPAT-001,
 * COMPAT-002); otherwise this class is never loaded, and nothing of the adapter registers. The two
 * hooks into Sable are mixins, which their plugin applies under the same rule ({@link
 * SableMixinPlugin}).
 *
 * <p>Nothing here needs the content mod, so all of it works with the content off (CONT-155). This
 * class names nothing of Sable's, so asking whether the adapter is on never loads Sable.
 */
public final class SableCompat {
    private static volatile boolean active;

    private SableCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into Sable, which may not be
     * constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        // The air on board (COMPAT-101).
        PointResolver.addProjection(Aboard::project);
        // Where the world shows each plot: airflow's sounds, particles and pushes on board, and the
        // coordinates players read (COMPAT-101, API-068).
        Frames.add(new SableFrames());
        // The plots' air through unloads and removals (COMPAT-100).
        NeoForge.EVENT_BUS.addListener((LevelEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                PlotAir.watch(level);
            }
        });
        active = true;
    }

    /**
     * Whether the adapter has started.
     *
     * @return true once {@link #init} has run
     */
    public static boolean isActive() {
        return active;
    }
}
