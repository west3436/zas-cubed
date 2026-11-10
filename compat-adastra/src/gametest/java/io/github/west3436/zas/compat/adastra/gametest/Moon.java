package io.github.west3436.zas.compat.adastra.gametest;

import earth.terrarium.adastra.api.planets.Planet;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Ad Astra's Moon, for the tests. A GameTest server's world has only vanilla's dimensions, so the
 * first test to ask gets {@code ad_astra:moon} made: Ad Astra's own dimension type and planet file,
 * over an empty world.
 */
final class Moon {
    /** The Moon's temperature in Ad Astra's planet file, −173 °C, in kelvin. */
    static final double TEMPERATURE = 100.15;

    private static final ChunkProgressListener NO_PROGRESS = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos center) {}

        @Override
        public void onStatusChange(ChunkPos chunk, ChunkStatus status) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}
    };

    private Moon() {}

    /** The Moon's level, made on first use. Server thread. */
    static ServerLevel level(MinecraftServer server) {
        return planet(server, Planet.MOON);
    }

    /** The level of one of Ad Astra's planets, made on first use in the same way. Server thread. */
    static ServerLevel planet(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel level = server.getLevel(key);
        return level != null ? level : create(server, key);
    }

    /** Adds a level to a running server, as a mod that makes dimensions at run time does. */
    private static ServerLevel create(MinecraftServer server, ResourceKey<Level> key) {
        RegistryAccess registries = server.registryAccess();
        Holder<DimensionType> type = registries
                .registryOrThrow(Registries.DIMENSION_TYPE)
                .getHolderOrThrow(ResourceKey.create(Registries.DIMENSION_TYPE, key.location()));
        Holder<Biome> biome = registries.registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        LevelStem stem = new LevelStem(
                type, new FlatLevelSource(new FlatLevelGeneratorSettings(Optional.empty(), biome, List.of())));
        WorldData world = server.getWorldData();
        ServerLevel level = new ServerLevel(
                server,
                field(server, "executor", Executor.class),
                field(server, "storageSource", LevelStorageSource.LevelStorageAccess.class),
                new DerivedLevelData(world, world.overworldData()),
                key,
                stem,
                NO_PROGRESS,
                false,
                BiomeManager.obfuscateSeed(world.worldGenOptions().seed()),
                List.of(),
                false,
                null);
        server.forgeGetWorldMap().put(key, level);
        server.markWorldsDirty();
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        return level;
    }

    private static <T> T field(MinecraftServer server, String name, Class<T> type) {
        try {
            Field f = MinecraftServer.class.getDeclaredField(name);
            f.setAccessible(true);
            return type.cast(f.get(server));
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("can't read the server's " + name + ": " + e);
        }
    }
}
