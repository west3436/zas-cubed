package io.github.west3436.zas;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.AtmosphereService;
import io.github.west3436.zas.api.ZasApi;
import io.github.west3436.zas.engine.Engine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(ZasMod.MOD_ID)
public final class ZasMod {
    public static final String MOD_ID = "zas";
    private static final Logger LOG = LogUtils.getLogger();

    public ZasMod() {
        ZasApi.install(new AtmosphereService() {
            @Override
            public double pressureKPa(Level level, BlockPos pos) {
                return Engine.pressureKPa(41.6, 293.15, 1000.0);
            }

            @Override
            public String implementationName() {
                return "zas";
            }
        });
        LOG.info("S0 zas: constructed, engine={} engineModule={}", Engine.hello(), Engine.class.getModule().getName());
        NeoForge.EVENT_BUS.addListener(ZasMod::onStarted);
    }

    private static void onStarted(ServerStartedEvent e) {
        LOG.info("S0 zas: api version={} present={} impl={} {}", ZasApi.VERSION, ZasApi.isPresent(),
                ZasApi.get().implementationName(), ZasApi.origin());
        if (Boolean.getBoolean("s0.halt")) {
            e.getServer().halt(false);
        }
    }
}
