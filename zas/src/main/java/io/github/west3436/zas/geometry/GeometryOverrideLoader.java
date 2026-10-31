package io.github.west3436.zas.geometry;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads {@link GeometryOverride}s from datapacks on every data load and hands them to {@link
 * Geometries}. A file that fails to parse is reported, naming the file and field, and skipped
 * (OPS-008).
 */
final class GeometryOverrideLoader extends SimpleJsonResourceReloadListener {
    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "geometry override";

    GeometryOverrideLoader() {
        super(new Gson(), GeometryOverride.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<GeometryOverride> overrides = new ArrayList<>();
        DatapackErrors.beginLoad(KIND);
        // Sorted by file id, so later files win ties deterministically.
        for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
            var result = GeometryOverride.CODEC.parse(JsonOps.INSTANCE, e.getValue());
            result.error()
                    .ifPresent(err -> DatapackErrors.skip(KIND, GeometryOverride.DIRECTORY, e.getKey(), err.message()));
            result.result().ifPresent(overrides::add);
        }
        Geometries.setOverrides(overrides);
    }
}
