package io.github.west3436.zas.gas;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/** Loads gas definitions from {@code data/<namespace>/zas/gas/*.json} (CORE-010). */
final class GasDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    GasDefinitionLoader() {
        super(new Gson(), GasDefinitionCodecs.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        List<GasDefinition> definitions = new ArrayList<>();
        // Sorted, so errors are logged in the same order every time.
        new TreeMap<>(files)
                .forEach((id, json) -> GasDefinitionCodecs.Body.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .ifError(error -> LOGGER.error("Skipping gas definition {}: {}", id, error.message()))
                        .ifSuccess(body -> {
                            try {
                                definitions.add(body.toDefinition(id));
                            } catch (IllegalArgumentException e) {
                                LOGGER.error("Skipping gas definition {}: {}", id, e.getMessage());
                            }
                        }));
        GasRegistry registry = GasRegistry.of(definitions);
        LOGGER.info("Loaded {} gas definitions", registry.size());
        ServerGasRegistry.loaded(registry);
    }
}
