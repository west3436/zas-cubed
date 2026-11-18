package io.github.west3436.zas.compat.cosmonautics.gametest;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Cosmonautics' dimensions on the GameTest server. A GameTest server's world has only vanilla's
 * dimensions, and Cosmonautics reads its deep space level on every server tick, so as the server
 * starts, before its first tick, this adds deep space and the Moon: each its own dimension type,
 * over an empty world, as a mod that makes dimensions at run time would.
 */
@EventBusSubscriber(modid = "zas")
public final class Dimensions {
    private static final List<String> COSMONAUTICS = List.of("deep_space", "moon");

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

    private Dimensions() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void serverStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (String path : COSMONAUTICS) {
            ResourceKey<Level> key = ResourceKey.create(
                    Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("rocketnautics", path));
            if (server.getLevel(key) == null) {
                create(server, key);
            }
        }
    }

    /** Adds a level to a running server. */
    private static void create(MinecraftServer server, ResourceKey<Level> key) {
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
    }

    private static <T> T field(MinecraftServer server, String name, Class<T> type) {
        try {
            Field f = MinecraftServer.class.getDeclaredField(name);
            f.setAccessible(true);
            return type.cast(f.get(server));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("can't read the server's " + name, e);
        }
    }
}
