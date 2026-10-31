package io.github.west3436.s0consumer;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ZasApi;
import net.minecraft.core.BlockPos;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod("s0consumer")
public final class ConsumerMod {
    private static final Logger LOG = LogUtils.getLogger();

    public ConsumerMod() {
        LOG.info("S0 consumer: constructed, api present at construction={}", ZasApi.isPresent());
        NeoForge.EVENT_BUS.addListener(ConsumerMod::onStarted);
    }

    private static void onStarted(ServerStartedEvent e) {
        var level = e.getServer().overworld();
        LOG.info("S0 consumer: api version={} present={} impl={} pressure={} {}", ZasApi.VERSION, ZasApi.isPresent(),
                ZasApi.get().implementationName(), ZasApi.get().pressureKPa(level, BlockPos.ZERO), ZasApi.origin());
        if (Boolean.getBoolean("s0.halt")) {
            e.getServer().halt(false);
        }
    }
}
