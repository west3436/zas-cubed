package io.github.west3436.zas.compat.viewer;

import io.github.west3436.zas.api.client.DisplayUnits;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.reaction.ClientReactions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What this client was sent, as the viewers list it: the gases ({@link ClientGasRegistry}) and the
 * reactions ({@link ClientReactions}). The server sends both before the recipes, on join and after
 * {@code /reload}, which is when a viewer builds its lists.
 */
public final class ViewerData {
    private ViewerData() {}

    /** Every gas the server defines. */
    public static List<GasEntry> gases() {
        return ViewerEntries.gases(ClientGasRegistry.current());
    }

    /** A gas by its id, if the server defines it. */
    public static Optional<GasEntry> gas(String id) {
        return ViewerEntries.find(ClientGasRegistry.current(), id);
    }

    /**
     * What a reaction's values are written under: the fire settings and the player's units. A page
     * made under one of these reads the same until this changes.
     */
    public static List<Object> writtenUnder() {
        return List.of(FireConfig.settings(), DisplayUnits.temperatureSymbol(), DisplayUnits.pressureSymbol());
    }

    /** Every reaction the server runs, each followed by its incomplete form if it has one. */
    public static List<Reaction> reactions() {
        return reactions(ClientGasRegistry.current(), ClientReactions.current(), FireConfig::settings);
    }

    /**
     * The reactions of a given set.
     *
     * @param fire the fire oxygen threshold, asked each time a page is shown
     */
    public static List<Reaction> reactions(
            GasRegistry registry, List<ReactionDefinition> definitions, Supplier<FireSettings> fire) {
        List<Reaction> out = new ArrayList<>();
        for (ReactionDefinition def : definitions) {
            out.add(new Reaction(registry, def, false, fire));
            if (def.incomplete().isPresent()) {
                out.add(new Reaction(registry, def, true, fire));
            }
        }
        return List.copyOf(out);
    }

    /**
     * One reaction's page, as a viewer holds it from one reload to the next. Its gases never change,
     * but its values are written in the player's units and under the server's fire settings, and
     * both can change while the viewer holds it, so {@link #now} reads them each time.
     */
    public static final class Reaction {
        private final GasRegistry registry;
        private final ReactionDefinition definition;
        private final boolean incomplete;
        private final Supplier<FireSettings> fire;
        private ReactionEntry entry;
        private List<Object> madeUnder = List.of();

        private Reaction(
                GasRegistry registry, ReactionDefinition definition, boolean incomplete, Supplier<FireSettings> fire) {
            this.registry = registry;
            this.definition = definition;
            this.incomplete = incomplete;
            this.fire = fire;
        }

        /** The page as it reads now. */
        public synchronized ReactionEntry now() {
            FireSettings settings = fire.get();
            List<Object> under = List.of(settings, DisplayUnits.temperatureSymbol(), DisplayUnits.pressureSymbol());
            if (entry == null || !under.equals(madeUnder)) {
                entry = ViewerEntries.reaction(registry, definition, settings, incomplete);
                madeUnder = under;
            }
            return entry;
        }

        /** The namespace of the reaction's id. */
        public String namespace() {
            return now().namespace();
        }

        /** The path of this page's own id: the reaction's, with a suffix for the incomplete form. */
        public String path() {
            return now().path();
        }
    }
}
