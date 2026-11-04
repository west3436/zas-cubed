package io.github.west3436.zas.fire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.geometry.GeometryOverride;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.slf4j.Logger;

/**
 * Which block states can catch fire from the gas beside them, and when (FIRE-033, FIRE-034):
 * vanilla's flammable blocks, with their ignite odds, and the ignition temperatures of {@code
 * data/<namespace>/zas/block_ignition/} ({@link BlockIgnition}).
 *
 * <p>A block named directly beats one matched through a tag; otherwise the file whose id sorts last
 * wins, as for heat data. An invalid file is logged, naming the file and field, and skipped
 * (OPS-008).
 *
 * <p>Data loads when the server starts and reloads with {@code /reload}. The geometry table asks
 * {@link #flammableStates} when it is rebuilt, once tags are bound, so the worker has the answers per
 * geometry. Changing them changes the palette, which replaces each dimension's engine (§4.5).
 */
public final class BlockIgnitions {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "block ignition";

    /** The loaded files in id order. */
    private static volatile List<BlockIgnition> loaded = List.of();

    private BlockIgnitions() {}

    /**
     * A flammable block state.
     *
     * @param stateId its {@link Block#getId} id
     * @param odds its vanilla ignite odds, above 0
     * @param temperature its ignition temperature in K, or NaN for the configured one
     */
    public record Flammable(int stateId, int odds, double temperature) {}

    /** Registers the reload listener. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
    }

    /**
     * Every flammable state, by ascending id: those {@code isFlammable} with ignite odds above 0,
     * which leaves out a waterlogged fence. Both are asked with no level, as the geometry's shapes are
     * (GEO-041), so a mod's block whose answer depends on where it is counts as it does at the origin;
     * one that throws counts as not flammable, and is logged once. Read on the thread that bound the
     * tags.
     */
    public static List<Flammable> flammableStates() {
        Map<Block, Double> temperatures = temperatures();
        List<Flammable> out = new ArrayList<>();
        boolean logged = false;
        for (BlockState state : Block.BLOCK_STATE_REGISTRY) {
            try {
                if (state.isFlammable(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP)) {
                    int odds = state.getFireSpreadSpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
                    if (odds > 0) {
                        out.add(new Flammable(
                                Block.getId(state), odds, temperatures.getOrDefault(state.getBlock(), Double.NaN)));
                    }
                }
            } catch (RuntimeException e) {
                if (!logged) {
                    LOGGER.warn("Couldn't ask whether {} is flammable; treating it as not", state, e);
                    logged = true;
                }
            }
        }
        out.sort((a, b) -> Integer.compare(a.stateId(), b.stateId()));
        return out;
    }

    /** Each block with an ignition temperature of its own. */
    private static Map<Block, Double> temperatures() {
        Map<Block, Double> viaTag = new HashMap<>();
        Map<Block, Double> direct = new HashMap<>();
        for (BlockIgnition file : loaded) {
            for (GeometryOverride.Target target : file.targets()) {
                if (target.tag()) {
                    TagKey<Block> key = TagKey.create(Registries.BLOCK, target.id());
                    for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(key)) {
                        viaTag.put(holder.value(), file.temperature());
                    }
                } else {
                    BuiltInRegistries.BLOCK
                            .getOptional(target.id())
                            .ifPresent(block -> direct.put(block, file.temperature()));
                }
            }
        }
        viaTag.putAll(direct);
        return viaTag;
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), BlockIgnition.DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller p) {
            List<BlockIgnition> parsedFiles = new ArrayList<>();
            DatapackErrors.beginLoad(KIND);
            new TreeMap<>(files).forEach((file, json) -> {
                DataResult<BlockIgnition> parsed = BlockIgnition.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.result().isEmpty()) {
                    DatapackErrors.skip(
                            KIND,
                            BlockIgnition.DIRECTORY,
                            file,
                            parsed.error().map(DataResult.Error::message).orElse("invalid"));
                    return;
                }
                parsedFiles.add(parsed.result().get());
            });
            LOGGER.info("Loaded {} block ignition files", parsedFiles.size());
            loaded = List.copyOf(parsedFiles);
        }
    }
}
