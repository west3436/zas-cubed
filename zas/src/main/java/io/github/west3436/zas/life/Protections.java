package io.github.west3436.zas.life;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.data.DatapackErrors;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Where an item's or entity's life support comes from (LIFE-026): its {@link ILifeSupport}
 * capability (API-022), or else, for an item, the protection data that names it ({@link
 * ProtectionData}, from {@code data/<namespace>/zas/protection/}).
 *
 * <p>An item's own capability wins over data. Among data, a file naming the item wins over one
 * naming a tag it is in; between files of the same kind, the first in file id order (by path, then
 * namespace) wins, and the later
 * file's claim on that item is logged. A file may name items that aren't registered, such as
 * another mod's, and they are kept in case they are.
 *
 * <p>A capability comes back behind NFR-013's guard ({@link GuardedLifeSupport}). One whose lookup
 * throws counts as absent, so data naming the item applies for that call.
 *
 * <p>Data loads when the server starts and reloads with {@code /reload}; an invalid file is
 * logged, naming the file and field, and skipped (OPS-008). Lookups are for the server thread.
 */
public final class Protections {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The directory under {@code data/<namespace>/}. */
    public static final String DIRECTORY = "zas/protection";

    /** The kind of entry, in {@link DatapackErrors} messages. */
    static final String KIND = "protection";

    private record Loaded(Map<ResourceLocation, ProtectionData> byItem, List<TagProtection> byTag) {}

    private record TagProtection(TagKey<Item> tag, ProtectionData data) {}

    private static volatile Loaded loaded = new Loaded(Map.of(), List.of());

    private Protections() {}

    /** Registers the reload listener. Called once, from the mod constructor. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new Loader()));
    }

    /** An item's life support, or null if it gives none. */
    public static @Nullable ILifeSupport of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        ILifeSupport own = GuardedLifeSupport.of(stack);
        if (own != null) {
            return own;
        }
        Loaded l = loaded;
        ProtectionData named = l.byItem.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (named != null) {
            return named;
        }
        for (TagProtection t : l.byTag) {
            if (stack.is(t.tag)) {
                return t.data;
            }
        }
        return null;
    }

    /** An entity's own life support, or null if it gives itself none. */
    public static @Nullable ILifeSupport of(Entity entity) {
        return GuardedLifeSupport.of(entity);
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        Loader() {
            super(new Gson(), DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller p) {
            Map<ResourceLocation, ProtectionData> byItem = new HashMap<>();
            Map<ResourceLocation, ProtectionData> byTagId = new HashMap<>();
            List<TagProtection> byTag = new ArrayList<>();
            DatapackErrors.beginLoad(KIND);
            // Sorted, so the first file by id wins every time.
            new TreeMap<>(files).forEach((file, json) -> {
                DataResult<ProtectionData> parsed = ProtectionData.CODEC.parse(JsonOps.INSTANCE, json);
                if (parsed.result().isEmpty()) {
                    DatapackErrors.skip(
                            KIND,
                            DIRECTORY,
                            file,
                            parsed.error().map(DataResult.Error::message).orElse("invalid"));
                    return;
                }
                ProtectionData data = parsed.result().get();
                for (ProtectionData.Entry entry : data.items()) {
                    Map<ResourceLocation, ProtectionData> claimed = entry.tag() ? byTagId : byItem;
                    if (claimed.putIfAbsent(entry.id(), data) != null) {
                        DatapackErrors.warn(
                                KIND,
                                DIRECTORY,
                                file,
                                "items: " + entry + " is already given protection by another file");
                    } else if (entry.tag()) {
                        byTag.add(new TagProtection(TagKey.create(Registries.ITEM, entry.id()), data));
                    }
                }
            });
            LOGGER.info("Loaded protection for {} items and {} item tags", byItem.size(), byTag.size());
            loaded = new Loaded(Map.copyOf(byItem), List.copyOf(byTag));
        }
    }
}
