package io.github.west3436.zas.s10;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The in-game S-10 bench (00-overview; NFR-001, NFR-002, NFR-004, NFR-005, DATA-006, the bench
 * halves of DATA-014 and DATA-015, spike #80). It only runs on the dedicated server that {@code
 * ./gradlew :s10-bench:s10Bench} starts, and stops that server when it has written its report.
 * Development only: no jar of it is ever built. The archived benchmarks note says how to read it:
 * https://github.com/west3436/zas-cubed/blob/f6931d5b91f839b3835fc6c81b1e69c9e09070e3/docs/research/benchmarks.md#the-in-game-s-10-bench
 */
@Mod(S10Mod.MOD_ID)
public final class S10Mod {
    public static final String MOD_ID = "zas_s10";
    static final Logger LOGGER = LogUtils.getLogger();

    private static Bench bench;

    public S10Mod(IEventBus modBus) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            return;
        }
        Machine.pin();
        GcPauses.install();
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener((ServerStartedEvent e) -> {
            int wrapped = BusTiming.install(game);
            Clock.start(Thread.currentThread());
            bench = new Bench(e.getServer(), Bench.Settings.fromSystem());
            bench.notes.put("Listeners timed", wrapped + " of the mods' game-bus listeners");
            LOGGER.info("[S-10] timing {} listeners; {}", wrapped, Machine.describe());
        });
        game.addListener(EventPriority.HIGHEST, (ServerTickEvent.Pre e) -> {
            if (bench != null) {
                bench.preTick();
            }
        });
        game.addListener(EventPriority.LOWEST, (ServerTickEvent.Post e) -> {
            if (bench != null) {
                bench.postTick();
            }
        });
        game.addListener((ChunkEvent.Load e) -> {
            if (bench != null) {
                bench.chunkLoaded(e.getLevel(), e.getChunk().getPos());
            }
        });
    }
}
