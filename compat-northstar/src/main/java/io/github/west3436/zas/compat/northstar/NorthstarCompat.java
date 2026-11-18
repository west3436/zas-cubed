package io.github.west3436.zas.compat.northstar;

import com.lightning.northstar.accessor.NorthstarLevel;
import com.lightning.northstar.world.oxygen.NorthstarOxygen;
import io.github.west3436.zas.api.environment.AmbientSeeds;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneCreatedEvent;
import io.github.west3436.zas.api.event.ZoneDissolvedEvent;
import io.github.west3436.zas.api.event.ZoneLifecycleEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZonePausedEvent;
import io.github.west3436.zas.api.event.ZoneRelocatedEvent;
import io.github.west3436.zas.api.event.ZoneResumedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Where the Create: Northstar – Redux adapter starts (SPACE-011). {@code zas}'s adapter loader calls
 * {@link #init} by name, and only with Northstar installed in a version its row supports
 * (COMPAT-001, COMPAT-002); otherwise this class is never loaded. Its two mixins check the same
 * range for themselves.
 *
 * <p>In each server level, ZAS³'s zones are one of Northstar's oxygen sealers ({@link ZoneSealer})
 * and one of its temperature regulators ({@link ZoneClimate}), and Northstar's blocks are told when
 * a zone changes ({@link ZoneUpdates}). Northstar's planets seed ZAS³'s ambients ({@link
 * NorthstarPlanets}), its own sealers' areas are breathable under the Normal preset ({@link
 * SealerAreas}), and its suits' oxygen feeds ZAS³'s internals ({@link SuitOxygen}). What
 * breathing, cold and heat do to entities is ZAS³'s alone (D-51): the mixins stop Northstar's own
 * checks of them.
 *
 * <p>Nothing here needs the content mod, so all of it works with the content off (CONT-155).
 */
public final class NorthstarCompat {
    /** The adapter's data pack, in the zas jar. */
    static final String DATA_PACK = "datapacks/northstar";

    /** The sealer and regulator of each server level. Server thread. */
    private static final Map<ResourceKey<Level>, Providers> PROVIDERS = new HashMap<>();

    private record Providers(ZoneSealer sealer, ZoneClimate climate) {}

    private static volatile boolean active;

    private NorthstarCompat() {}

    /**
     * Starts the adapter.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        AmbientSeeds.register(NorthstarPlanets.ID, NorthstarPlanets::seed);
        SealerAreas.register();
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                loaded(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, e -> {
            if (e.getLevel() instanceof ServerLevel level) {
                unloaded(level);
            }
        });
        NeoForge.EVENT_BUS.addListener(
                ServerTickEvent.Post.class,
                e -> PROVIDERS.values().forEach(p -> {
                    p.sealer.tick();
                    ZoneUpdates.tick(p.sealer.level());
                }));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            PROVIDERS.clear();
            NorthstarPlanets.serverStopped();
            ZoneUpdates.serverStopped();
        });
        // The event's parent is abstract, which NeoForge's bus won't listen to.
        Consumer<ZoneLifecycleEvent> changed = ZoneUpdates::changed;
        for (Class<? extends ZoneLifecycleEvent> type : List.of(
                ZoneCreatedEvent.class,
                ZoneMergedEvent.class,
                ZoneSplitEvent.class,
                ZoneBreachedEvent.class,
                ZoneSealedEvent.class,
                ZoneDissolvedEvent.class,
                ZonePausedEvent.class,
                ZoneResumedEvent.class,
                ZoneRelocatedEvent.class)) {
            listen(type, changed);
        }
        modBus.addListener(RegisterCapabilitiesEvent.class, SuitOxygen::register);
        ResourceLocation pack = ResourceLocation.fromNamespaceAndPath(container.getModId(), DATA_PACK);
        modBus.addListener(AddPackFindersEvent.class, event -> addDataPack(event, pack));
        active = true;
    }

    private static <T extends ZoneLifecycleEvent> void listen(Class<T> type, Consumer<ZoneLifecycleEvent> listener) {
        NeoForge.EVENT_BUS.addListener(type, listener::accept);
    }

    private static void loaded(ServerLevel level) {
        Providers providers = new Providers(new ZoneSealer(level), new ZoneClimate(level));
        Providers before = PROVIDERS.put(level.dimension(), providers);
        if (before != null) {
            remove(before);
        }
        NorthstarOxygen.getDimension(level).registerSealer(providers.sealer);
        ((NorthstarLevel) level).northstar$temperature().registerSealer(providers.climate);
    }

    private static void unloaded(ServerLevel level) {
        Providers providers = PROVIDERS.get(level.dimension());
        if (providers != null && providers.sealer.level() == level) {
            PROVIDERS.remove(level.dimension());
            providers.sealer.flush();
            remove(providers);
        }
        ZoneUpdates.unloaded(level);
    }

    private static void remove(Providers providers) {
        NorthstarOxygen.getDimension(providers.sealer.level()).unregisterSealer(providers.sealer);
        ((NorthstarLevel) providers.climate.level()).northstar$temperature().unregisterSealer(providers.climate);
    }

    /** The oxygen sealer of a level, or null if it has none. For tests. */
    public static @Nullable NorthstarOxygen.Provider sealerOf(ServerLevel level) {
        Providers providers = PROVIDERS.get(level.dimension());
        return providers == null ? null : providers.sealer;
    }

    /** Looks at a level's zones for oxygen now, rather than at the next second. For tests. */
    public static void checkZones(ServerLevel level) {
        ZoneUpdates.check(level);
    }

    /** Takes what Northstar drained in a level now, rather than at the next second. For tests. */
    public static void flushDrains(ServerLevel level) {
        Providers providers = PROVIDERS.get(level.dimension());
        if (providers != null) {
            providers.sealer.flush();
        }
    }

    /**
     * Offers the adapter's data pack: the suits' protection and the mobs that live in space. It is
     * always on and starts at the bottom, so any other data pack overrides it. It is a built-in pack
     * rather than the mod's own data because the mod's data loads whether or not the adapter is on.
     */
    private static void addDataPack(AddPackFindersEvent event, ResourceLocation pack) {
        event.addPackFinders(
                pack,
                PackType.SERVER_DATA,
                Component.translatable("pack.zas.northstar"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM);
    }

    /**
     * Whether the adapter has started.
     *
     * @return true once {@link #init} has run
     */
    public static boolean isActive() {
        return active;
    }
}
