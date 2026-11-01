package io.github.west3436.zas.gas;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Loads gas definitions from {@code data/<namespace>/zas/gas/*.json} (CORE-010) and gas tags from
 * {@code data/<namespace>/tags/zas/gas/*.json} (CORE-012). An invalid definition is reported, naming
 * the file and field, and skipped (OPS-008).
 *
 * <p>Gases aren't a Minecraft registry (their set is fixed per server, {@link ServerGasRegistry}),
 * so Minecraft doesn't load their tags. This loader reads them with Minecraft's own {@link
 * TagLoader}, so they have its format and behaviour: files of the same tag in several packs add
 * up, {@code "replace": true} drops what lower packs added, {@code #tag} entries include other gas
 * tags, an entry with {@code "required": false} may name a gas that doesn't exist, and NeoForge's
 * {@code "remove"} list takes gases out. A tag that names a required gas or tag that doesn't exist
 * fails as a whole, as in Minecraft; it is logged by the tag loader and reported here too, naming
 * the entries at fault.
 */
final class GasDefinitionLoader extends SimplePreparableReloadListener<GasDefinitionLoader.Files> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "gas definition";
    /** The kind of a gas tag, in {@link DatapackErrors} messages. */
    static final String TAG_KIND = "gas tag";

    /** What the preparation read: the definition files, and the tag files by tag id. */
    record Files(
            Map<ResourceLocation, JsonElement> gases, Map<ResourceLocation, List<TagLoader.EntryWithSource>> tags) {}

    @Override
    protected Files prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> gases = new HashMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(resources, GasDefinitionCodecs.DIRECTORY, GSON, gases);
        // Reading needs no lookup; the gases are resolved in apply, once they are known.
        var tags = new TagLoader<String>(id -> Optional.empty(), GasDefinitionCodecs.TAG_DIRECTORY).load(resources);
        return new Files(gases, tags);
    }

    @Override
    protected void apply(Files files, ResourceManager resources, ProfilerFiller profiler) {
        List<GasDefinition> definitions = new ArrayList<>();
        DatapackErrors.beginLoad(KIND);
        // Sorted, so errors are logged in the same order every time.
        new TreeMap<>(files.gases())
                .forEach((id, json) -> GasDefinitionCodecs.Body.CODEC
                        .parse(JsonOps.INSTANCE, json)
                        .ifError(error -> DatapackErrors.skip(KIND, GasDefinitionCodecs.DIRECTORY, id, error.message()))
                        .ifSuccess(body -> {
                            try {
                                definitions.add(body.toDefinition(id));
                                for (String name : body.unknownSources()) {
                                    DatapackErrors.warn(
                                            KIND,
                                            GasDefinitionCodecs.DIRECTORY,
                                            id,
                                            "sources: '" + name + "' names no field of a gas definition");
                                }
                            } catch (IllegalArgumentException e) {
                                DatapackErrors.skip(KIND, GasDefinitionCodecs.DIRECTORY, id, e.getMessage());
                            }
                        }));
        Map<String, List<String>> tags = tags(files.tags(), definitions);
        GasRegistry registry = GasRegistry.of(definitions, tags);
        LOGGER.info("Loaded {} gas definitions and {} gas tags", registry.size(), tags.size());
        ServerGasRegistry.loaded(registry);
    }

    /** The tags resolved against the loaded gases, by tag id; failed tags are reported and left out. */
    private static Map<String, List<String>> tags(
            Map<ResourceLocation, List<TagLoader.EntryWithSource>> read, List<GasDefinition> definitions) {
        DatapackErrors.beginLoad(TAG_KIND);
        Set<String> ids = definitions.stream().map(GasDefinition::id).collect(Collectors.toSet());
        Map<ResourceLocation, Collection<String>> built = new TagLoader<String>(
                        id -> ids.contains(id.toString()) ? Optional.of(id.toString()) : Optional.empty(),
                        GasDefinitionCodecs.TAG_DIRECTORY)
                .build(read);
        Map<String, List<String>> out = new TreeMap<>();
        for (ResourceLocation tag : new TreeMap<>(read).keySet()) {
            Collection<String> gases = built.get(tag);
            if (gases == null) {
                DatapackErrors.skip(
                        TAG_KIND,
                        GasDefinitionCodecs.TAG_DIRECTORY,
                        tag,
                        "values: no such gas or gas tag: " + String.join(", ", missing(read.get(tag), ids, built))
                                + "; an entry that may be missing needs \"required\": false");
            } else {
                out.put(tag.toString(), List.copyOf(gases));
            }
        }
        return out;
    }

    /** The entries of a failed tag that name a gas or tag that doesn't exist, as the file wrote them. */
    private static List<String> missing(
            List<TagLoader.EntryWithSource> entries, Set<String> ids, Map<ResourceLocation, Collection<String>> built) {
        TagEntry.Lookup<String> lookup = new TagEntry.Lookup<>() {
            @Override
            public String element(ResourceLocation id) {
                return ids.contains(id.toString()) ? id.toString() : null;
            }

            @Override
            public Collection<String> tag(ResourceLocation id) {
                return built.get(id);
            }
        };
        List<String> missing = new ArrayList<>();
        for (TagLoader.EntryWithSource entry : entries) {
            if (!entry.entry().build(lookup, gas -> {})) {
                missing.add(entry.entry().toString());
            }
        }
        return missing;
    }
}
