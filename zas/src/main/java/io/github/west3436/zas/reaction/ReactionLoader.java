package io.github.west3436.zas.reaction;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Loads reaction definitions from {@code data/<namespace>/zas/reaction/*.json} (FIRE-001). An
 * invalid definition is reported, naming the file and field, and skipped (OPS-008); so is one that
 * names a gas the server doesn't define, when the reactions are compiled ({@link ServerReactions}).
 */
final class ReactionLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "reaction definition";

    ReactionLoader() {
        super(new Gson(), ReactionCodecs.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        DatapackErrors.beginLoad(KIND);
        List<ReactionDefinition> definitions = new ArrayList<>();
        // Sorted, so errors are logged in the same order every time.
        new TreeMap<>(files).forEach((id, json) -> parse(id, json).ifPresent(definitions::add));
        LOGGER.info("Loaded {} reaction definitions", definitions.size());
        ServerReactions.loaded(definitions);
    }

    /** One file's definition, or empty if it is invalid, which is reported. */
    static Optional<ReactionDefinition> parse(ResourceLocation id, JsonElement json) {
        Optional<ReactionDefinition> out = Optional.empty();
        try {
            var result = ReactionCodecs.Body.CODEC.parse(JsonOps.INSTANCE, json);
            if (result.error().isPresent()) {
                DatapackErrors.skip(
                        KIND, ReactionCodecs.DIRECTORY, id, result.error().get().message());
                return Optional.empty();
            }
            ReactionCodecs.Body body = result.getOrThrow();
            out = Optional.of(body.toDefinition(id, Optional.empty()));
            for (String name : body.unknownSources()) {
                DatapackErrors.warn(
                        KIND,
                        ReactionCodecs.DIRECTORY,
                        id,
                        "sources: '" + name + "' names no field of a reaction definition");
            }
        } catch (IllegalArgumentException e) {
            // A record's own checks, which run as the codec builds it.
            DatapackErrors.skip(KIND, ReactionCodecs.DIRECTORY, id, e.getMessage());
            return Optional.empty();
        }
        return out;
    }
}
