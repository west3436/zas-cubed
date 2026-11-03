package io.github.west3436.zas.reaction;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.data.DatapackErrors;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionSet;
import io.github.west3436.zas.gas.ServerGasRegistry;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

/**
 * The running server's reactions (FIRE-001), compiled against its gases.
 *
 * <p>Unlike gases, reactions take effect on {@code /reload}: they are rules, not state, and the
 * next atmos batch carries the new set, so one tick always sees one set. A definition naming a gas
 * the server doesn't define is reported and skipped when the set is compiled: when the server
 * starts, and on each reload while it runs.
 */
public final class ServerReactions {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile List<ReactionDefinition> loaded = List.of();
    private static volatile ReactionSet current = ReactionSet.EMPTY;

    private ServerReactions() {}

    /**
     * Registers the loader and the server lifecycle hooks, after the gases' ({@link
     * io.github.west3436.zas.gas.GasRegistryEvents}), so the gases are known when reactions compile.
     */
    public static void register(IEventBus gameBus) {
        gameBus.addListener((AddReloadListenerEvent e) -> e.addListener(new ReactionLoader()));
        gameBus.addListener((ServerAboutToStartEvent e) -> compile());
        gameBus.addListener((ServerStoppedEvent e) -> current = ReactionSet.EMPTY);
    }

    /** The running server's reactions; {@link ReactionSet#EMPTY} when no server runs. Any thread. */
    public static ReactionSet current() {
        return current;
    }

    static void loaded(List<ReactionDefinition> definitions) {
        loaded = List.copyOf(definitions);
        if (ServerGasRegistry.running()) {
            compile();
        }
    }

    private static void compile() {
        ReactionSet set = ReactionSet.compile(
                ServerGasRegistry.current(),
                loaded,
                (def, why) -> DatapackErrors.skip(
                        ReactionLoader.KIND, ReactionCodecs.DIRECTORY, ResourceLocation.parse(def.id()), why));
        LOGGER.info("Compiled {} reactions", set.definitions().size());
        current = set;
    }
}
