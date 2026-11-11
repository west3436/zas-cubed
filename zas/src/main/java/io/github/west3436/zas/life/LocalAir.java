package io.github.west3436.zas.life;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The air of another mod's breathable place, outside zones (SPACE-017): the first answer of the
 * registered {@link io.github.west3436.zas.api.life.LocalAtmosphereProvider}s. The breathing
 * rules ask ({@link BreathingRules}), and through them what reports on an entity, such as the HUD
 * and {@code /zas life}, and what reads the air at a point to show it ({@link
 * io.github.west3436.zas.api.life.LocalAtmospheres#at}); atmosphere queries never do.
 */
final class LocalAir {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LocalAir() {}

    /**
     * The air living things are in at a point outside zones, or null if no provider says. Server
     * thread. With no provider registered it is one read.
     */
    static @Nullable GasMixture at(ServerLevel level, Vec3 point) {
        for (LocalAtmospheres.Registration provider : LocalAtmospheres.registered()) {
            GasMixture air = ask(provider, level, point);
            if (air != null) {
                return air;
            }
        }
        return null;
    }

    /** Asks one provider; null if it threw (NFR-013). */
    private static @Nullable GasMixture ask(LocalAtmospheres.Registration provider, ServerLevel level, Vec3 point) {
        try {
            return provider.provider().at(level, point);
        } catch (Exception | LinkageError e) {
            String source = "Local atmosphere provider " + provider.id();
            if (ThirdPartyFaults.shouldLog(source)) {
                LOGGER.error("{} threw; skipped for this check (logged at most once a minute)", source, e);
            }
            return null;
        }
    }
}
