package io.github.west3436.zas.compat.mi;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * Where the Modern Industrialization adapter starts (COMPAT-091). {@code zas}'s adapter loader
 * calls {@link #init} by name, and only with Modern Industrialization installed in a version its
 * row supports (COMPAT-001, COMPAT-002); otherwise this class is never loaded, and nothing of the
 * adapter registers.
 *
 * <p>The adapter is data, in two built-in data packs. Modern Industrialization tags each of its gas
 * fluids with the {@code c:} tag of its name, and the content mod's fluid bridge maps the tags of
 * ZAS³'s own gases, so its oxygen, nitrogen, hydrogen, methane and steam need nothing more
 * (COMPAT-090). The packs add its other gases (COMPAT-092): its own pack has argon, helium and
 * helium-3, which NuclearCraft has too, and maps {@code #c:ethylene} to ethene; the pack of gases
 * several mods share has chlorine, deuterium and tritium. None of it needs the content mod, though
 * the fluid tags only matter with it: its pipes are what moves gas through another mod's tank.
 *
 * <p>This class names nothing of Modern Industrialization's.
 */
public final class ModernIndustrializationCompat {
    /** Modern Industrialization's mod id. */
    public static final String MOD_ID = "modern_industrialization";

    /**
     * The adapter's built-in data pack, a folder in the jar: the gases Modern Industrialization has
     * that ZAS³ and the shared pack don't, and the fluid tags they map from. It is offered only by
     * {@link #init}, so none of it loads without Modern Industrialization.
     */
    public static final String DATA_PACK = "datapacks/modern_industrialization";

    /**
     * The gases several mods have, in a pack of zas's that the Mekanism and NuclearCraft adapters
     * offer too: of Modern Industrialization's, chlorine, deuterium and tritium. Offered by more
     * than one adapter, it is still one pack.
     */
    public static final String SHARED_GASES = "datapacks/shared_gases";

    private static volatile boolean active;

    private ModernIndustrializationCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into Modern Industrialization.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        String modId = container.getModId();
        modBus.addListener((AddPackFindersEvent event) -> {
            addDataPack(
                    event,
                    ResourceLocation.fromNamespaceAndPath(modId, DATA_PACK),
                    "pack.zas.modern_industrialization");
            addDataPack(event, ResourceLocation.fromNamespaceAndPath(modId, SHARED_GASES), "pack.zas.shared_gases");
        });
        active = true;
    }

    /**
     * Whether the adapter has started.
     *
     * @return true once {@link #init} has run
     */
    public static boolean isActive() {
        return active;
    }

    /**
     * The id the game gives the adapter's data pack.
     *
     * @param modId the id of the mod the adapter ships in
     * @return the pack id, as the pack repository lists it
     */
    public static String dataPackId(String modId) {
        return "mod/" + ResourceLocation.fromNamespaceAndPath(modId, DATA_PACK);
    }

    /**
     * The id the game gives the pack of shared gases.
     *
     * @param modId the id of the mod the adapter ships in
     * @return the pack id, as the pack repository lists it
     */
    public static String sharedGasesPackId(String modId) {
        return "mod/" + ResourceLocation.fromNamespaceAndPath(modId, SHARED_GASES);
    }

    /**
     * A pack is always on and starts at the bottom, so any other data pack overrides it. It is a
     * built-in pack rather than the mod's own data because the mod's data loads whether or not
     * Modern Industrialization is installed.
     */
    private static void addDataPack(AddPackFindersEvent event, ResourceLocation pack, String title) {
        event.addPackFinders(
                pack,
                PackType.SERVER_DATA,
                Component.translatable(title),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM);
    }
}
