package io.github.west3436.zas.compat.nuclearcraft;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Where the NuclearCraft: Neoteric adapter starts (COMPAT-150). {@code zas}'s adapter loader calls
 * {@link #init} by name, and only with NuclearCraft installed in a version its row supports
 * (COMPAT-001, COMPAT-002); otherwise this class is never loaded, and nothing of the adapter
 * registers.
 *
 * <p>Most of the adapter is data, in two built-in data packs: its own, with NuclearCraft's gases,
 * the fluid tags they map from and the HEV suit's protection (COMPAT-151, COMPAT-152, COMPAT-154);
 * and the gases it shares with the Mekanism adapter, which either adapter offers, so each is
 * defined once (COMPAT-151). The one piece of code is the fission reactor's heat (COMPAT-153).
 * None of it needs the content mod, though the fluid tags only matter with it: its pipes are what
 * moves gas through another mod's fluid tank.
 *
 * <p>This class names nothing of NuclearCraft's.
 */
public final class NuclearCraftCompat {
    /** NuclearCraft's mod id. */
    public static final String MOD_ID = "nuclearcraft";

    /**
     * The adapter's built-in data pack, a folder in the jar: NuclearCraft's gases and their hazards,
     * the fluid tags they map from, and the HEV suit's protection. It is offered only by {@link
     * #init}, so none of it loads without NuclearCraft.
     */
    public static final String DATA_PACK = "datapacks/nuclearcraft";

    /**
     * The gases NuclearCraft shares with Mekanism, in a pack of zas's that the Mekanism adapter
     * offers too: chlorine, sulfur dioxide, ethene, deuterium and tritium, their hazards, and the
     * fluid tags they map from. Offered twice, it is still one pack.
     */
    public static final String SHARED_GASES = "datapacks/shared_gases";

    private static volatile boolean active;

    private NuclearCraftCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into NuclearCraft, which may not be
     * constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        NuclearCraftSettings.register(container);

        // Gases, fluid tags and the HEV suit (COMPAT-151, COMPAT-152, COMPAT-154): data.
        String modId = container.getModId();
        modBus.addListener((AddPackFindersEvent event) -> {
            addDataPack(event, ResourceLocation.fromNamespaceAndPath(modId, DATA_PACK), "pack.zas.nuclearcraft");
            addDataPack(event, ResourceLocation.fromNamespaceAndPath(modId, SHARED_GASES), "pack.zas.shared_gases");
        });

        // A fission reactor's heat (COMPAT-153): controllers are learned as they load or are placed,
        // the heat their sinks remove is counted each tick, and sent to the room once an atmos tick.
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> ReactorHeat.chunkLoaded(event));
        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent event) -> ReactorHeat.blockChanged(event));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> ReactorHeat.levelTicked(event));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> ReactorHeat.forget());

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
     * NuclearCraft is installed.
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
