package io.github.west3436.zas.fire;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.geometry.GeometryOverride;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The fuel profiles (FIRE-025) from {@code data/<namespace>/zas/fuel_profile/}: which power each
 * fuel-burning block burns at.
 *
 * <p>For a block, the profiles naming it, directly or through a tag, compete: one whose {@code
 * burning_on} matches the block a fire burns on beats one without {@code burning_on} (FIRE-022), a
 * profile naming the block directly beats one matching it through a tag, and between equals the
 * file whose id sorts last wins, as for heat data. A profile whose {@code burning_on} doesn't match
 * doesn't apply.
 *
 * <p>Data loads when the server starts and reloads with {@code /reload}; an invalid file is logged,
 * naming the file and field, and skipped (OPS-008). Tags are read at lookup, so they apply once
 * bound. Server thread.
 */
public final class FuelProfiles {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "fuel profile";

    /** The loaded profiles in file id order. */
    private static volatile List<FuelProfile> loaded = List.of();

    private FuelProfiles() {}

    /** Registers the reload listener. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
    }

    /**
     * The power a burning block burns at, in W, or 0 if no profile names it.
     *
     * @param state the burning block
     * @param burningOn for a fire, the block it burns on; null for none
     */
    public static double watts(BlockState state, @Nullable BlockState burningOn) {
        FuelProfile best = null;
        int bestRank = -1;
        for (FuelProfile p : loaded) {
            int block = match(p.targets(), state);
            if (block < 0) {
                continue;
            }
            int rank = block;
            if (!p.burningOn().isEmpty()) {
                int on = burningOn == null ? -1 : match(p.burningOn(), burningOn);
                if (on < 0) {
                    continue;
                }
                rank += 2;
            }
            // Later files win ties.
            if (rank >= bestRank) {
                best = p;
                bestRank = rank;
            }
        }
        return best == null ? 0 : best.watts();
    }

    /** 1 if a target names the block, 0 if one matches it through a tag, -1 if none matches. */
    private static int match(List<GeometryOverride.Target> targets, BlockState state) {
        int found = -1;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (GeometryOverride.Target t : targets) {
            if (!t.tag() && t.id().equals(id)) {
                return 1;
            }
            if (t.tag() && state.is(TagKey.create(Registries.BLOCK, t.id()))) {
                found = 0;
            }
        }
        return found;
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), FuelProfile.DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller p) {
            List<FuelProfile> profiles = new ArrayList<>();
            DatapackErrors.beginLoad(KIND);
            new TreeMap<>(files).forEach((file, json) -> {
                DataResult<FuelProfile> parsed = FuelProfile.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.result().isEmpty()) {
                    DatapackErrors.skip(
                            KIND,
                            FuelProfile.DIRECTORY,
                            file,
                            parsed.error().map(DataResult.Error::message).orElse("invalid"));
                    return;
                }
                profiles.add(parsed.result().get());
            });
            LOGGER.info("Loaded {} fuel profiles", profiles.size());
            loaded = List.copyOf(profiles);
        }
    }
}
