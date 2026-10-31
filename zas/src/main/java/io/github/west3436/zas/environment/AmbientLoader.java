package io.github.west3436.zas.environment;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Loads ambient definitions from {@code data/<namespace>/zas/ambient/*.json} (ENV-001). An
 * invalid file is logged, naming the file and field, and skipped (OPS-008).
 */
final class AmbientLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    AmbientLoader() {
        super(new Gson(), AmbientFormat.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, DimensionAmbient> definitions = new LinkedHashMap<>();
        // Sorted, so errors are logged in the same order every time.
        new TreeMap<>(files).forEach((dimension, json) -> {
            try {
                definitions.put(
                        dimension,
                        AmbientFormat.parse(
                                json, warning -> LOGGER.warn("Ambient definition {}: {}", dimension, warning)));
            } catch (AmbientFormat.InvalidAmbientException e) {
                LOGGER.error("Skipping ambient definition {}: {}", dimension, e.getMessage());
            }
        });
        LOGGER.info("Loaded {} ambient definitions", definitions.size());
        Ambients.loaded(definitions);
    }
}
