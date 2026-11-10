package io.github.west3436.zas.compat.mekanism;

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
 * Loads the chemical map from {@code data/<namespace>/zas/mekanism_chemical/*.json} (MEK-010), when
 * the server starts and on {@code /reload}. An invalid file is logged, naming the file and the
 * field at fault, and skipped, as ZAS³'s own loaders do (OPS-008); a file whose NeoForge conditions
 * fail is left out without a word, which is how a datapack removes a shipped mapping.
 */
final class ChemicalMapLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();

    ChemicalMapLoader() {
        super(new Gson(), ChemicalMapFormat.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        ConditionalOps<JsonElement> conditions = makeConditionalOps();
        List<ChemicalMapping> mappings = new ArrayList<>();
        // Sorted, so problems are logged in the same order every time.
        new TreeMap<>(files).forEach((chemical, json) -> {
            String file = ChemicalMapFormat.file(chemical);
            try {
                if (json.isJsonObject() && !ICondition.conditionsMatched(conditions, json)) {
                    return;
                }
                mappings.add(ChemicalMapFormat.parse(
                        chemical,
                        json,
                        warning -> LOGGER.warn("Ignoring part of {} {}: {}", ChemicalMapFormat.KIND, file, warning)));
            } catch (ChemicalMapFormat.InvalidMappingException e) {
                LOGGER.error("Skipping {} {}: {}", ChemicalMapFormat.KIND, file, e.getMessage());
            } catch (JsonParseException e) {
                LOGGER.error("Skipping {} {}: neoforge:conditions: {}", ChemicalMapFormat.KIND, file, e.getMessage());
            }
        });
        ChemicalMap map = ChemicalMap.of(mappings);
        map.ties()
                .forEach((gas, chemicals) -> LOGGER.warn(
                        "Several chemicals are the gas {} at the same min_temperature: {}. The first of the tied "
                                + "ones is used; mark the others \"one_way\": true",
                        gas,
                        chemicals));
        LOGGER.info("Loaded {} chemical mappings", map.mappings().size());
        ChemicalGases.loaded(map);
    }
}
