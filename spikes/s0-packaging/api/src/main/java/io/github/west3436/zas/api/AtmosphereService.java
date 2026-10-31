package io.github.west3436.zas.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** S0: a tiny service interface that uses Minecraft types. */
public interface AtmosphereService {
    double pressureKPa(Level level, BlockPos pos);

    String implementationName();
}
