package io.github.west3436.zas.atmos.fluid;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;
import org.slf4j.Logger;

/**
 * Loads the fluid tags' gases from {@code data/<namespace>/zas/fluid_gas/*.json} (COMPAT-140), when
 * the server starts and on {@code /reload}. An invalid file is logged, naming the file and the
 * field at fault, and skipped, as ZAS³'s own loaders do (OPS-008); a file whose NeoForge conditions
 * fail is left out without a word, which is how a datapack removes a shipped mapping.
 */
public final class FluidGasLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    public FluidGasLoader() {
        super(new Gson(), FluidGasFormat.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        ConditionalOps<JsonElement> conditions = makeConditionalOps();
        List<FluidGasMapping> mappings = new ArrayList<>();
        // Sorted, so problems are logged in the same order every time.
        new TreeMap<>(files).forEach((tag, json) -> {
            String file = FluidGasFormat.file(tag);
            try {
                if (json.isJsonObject() && !ICondition.conditionsMatched(conditions, json)) {
                    return;
                }
                mappings.add(FluidGasFormat.parse(
                        tag,
                        json,
                        warning -> LOGGER.warn("Ignoring part of {} {}: {}", FluidGasFormat.KIND, file, warning)));
            } catch (FluidGasFormat.InvalidMappingException e) {
                LOGGER.error("Skipping {} {}: {}", FluidGasFormat.KIND, file, e.getMessage());
            } catch (JsonParseException e) {
                LOGGER.error("Skipping {} {}: neoforge:conditions: {}", FluidGasFormat.KIND, file, e.getMessage());
            }
        });
        LOGGER.info("Loaded {} fluid gas mappings", mappings.size());
        FluidGases.loaded(mappings);
    }
}
