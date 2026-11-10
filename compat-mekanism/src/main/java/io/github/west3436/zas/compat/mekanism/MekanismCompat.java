package io.github.west3436.zas.compat.mekanism;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Where the Mekanism adapter starts (MEK-001). {@code zas} calls {@link #init} by name, and only
 * when Mekanism is installed in a version {@link MekanismVersions} supports; otherwise this class
 * is never loaded, and nothing of the adapter registers.
 *
 * <p>Everything the adapter adds hangs off this one call: its config file, its data pack, its
 * reload listener and, as they are built, each part's own registrations. A mod copying the adapter
 * calls {@code init} from its own constructor behind a check that Mekanism is loaded.
 *
 * <p>This class names nothing of Mekanism's, so asking it {@link #isActive} never loads a class of
 * Mekanism's.
 */
public final class MekanismCompat {
    /**
     * The adapter's built-in data pack, a folder in the jar: the chemical map's defaults, the gas
     * definitions of the chemicals that ZAS³ has no gas for with their hazard profiles, and the
     * breathing gear's data (the MekaSuit's protection and how Mekanism treats ZAS³'s damage
     * types). It is offered only by {@link #init}, so none of it loads without Mekanism.
     */
    public static final String DATA_PACK = "datapacks/mekanism";

    private static volatile boolean active;

    private MekanismCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into Mekanism, which may not be
     * constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        MekanismSettings.register(container);
        ResourceLocation pack = ResourceLocation.fromNamespaceAndPath(container.getModId(), DATA_PACK);
        modBus.addListener((AddPackFindersEvent event) -> addDataPack(event, pack));
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(new ChemicalMapLoader()));
        // The gases a server defines are fixed while it runs, and may differ in the next one.
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> ChemicalGases.forget());
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> ChemicalGases.forget());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ChemicalGases.forget());

        // Breathing gear (MEK-030 to MEK-034): what a player wearing Mekanism's gear gives itself.
        modBus.addListener((RegisterCapabilitiesEvent event) -> MekanismGear.register(event));

        // Venting (MEK-020, MEK-021): its hooks are mixins; only the rate cap's counts are kept here.
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> Venting.forget());

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
     * The pack is always on and starts at the bottom, so any other data pack overrides it. It is a
     * built-in pack rather than the mod's own data because the mod's data loads whether or not
     * Mekanism is installed.
     */
    private static void addDataPack(AddPackFindersEvent event, ResourceLocation pack) {
        event.addPackFinders(
                pack,
                PackType.SERVER_DATA,
                Component.translatable("pack.zas.mekanism"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM);
    }
}
