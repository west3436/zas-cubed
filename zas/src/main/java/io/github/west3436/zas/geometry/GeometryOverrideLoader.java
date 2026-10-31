package io.github.west3436.zas.geometry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Loads {@link GeometryOverride}s from datapacks on every data load and hands them to {@link
 * Geometries}. A file that fails to parse is logged and skipped.
 */
final class GeometryOverrideLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    GeometryOverrideLoader() {
        super(new Gson(), GeometryOverride.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<GeometryOverride> overrides = new ArrayList<>();
        // Sorted by file id, so later files win ties deterministically.
        for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
            var result = GeometryOverride.CODEC.parse(JsonOps.INSTANCE, e.getValue());
            result.error()
                    .ifPresent(err -> LOGGER.error("Invalid geometry override {}: {}", e.getKey(), err.message()));
            result.result().ifPresent(overrides::add);
        }
        Geometries.setOverrides(overrides);
    }
}
