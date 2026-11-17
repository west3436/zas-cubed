package io.github.west3436.zas.compat.pneumaticcraft;

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
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Where the PneumaticCraft: Repressurized adapter starts (COMPAT-010 to COMPAT-013). {@code zas}'s
 * adapter loader calls {@link #init} by name, and only with PneumaticCraft installed in a version
 * its row supports (COMPAT-001, COMPAT-002); otherwise this class is never loaded, and nothing of
 * the adapter registers.
 *
 * <p>Everything the adapter adds hangs off this one call, each part in a block of its own. None of
 * it needs the content mod, so all of it works with the content off (CONT-155).
 *
 * <p>This class names nothing of PneumaticCraft's.
 */
public final class PneumaticCraftCompat {
    /** PneumaticCraft's mod id. */
    public static final String MOD_ID = "pneumaticcraft";

    /**
     * The adapter's built-in data pack, a folder in the jar: Pneumatic Armor's protection
     * (COMPAT-010). It is offered only by {@link #init}, so none of it loads without PneumaticCraft.
     */
    public static final String DATA_PACK = "datapacks/pneumaticcraft";

    private static volatile boolean active;

    private PneumaticCraftCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into PneumaticCraft, which may not be
     * constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        PncSettings.register(container);

        // Pneumatic Armor as a suit (COMPAT-010): its protection is data, in the adapter's pack.
        ResourceLocation pack = ResourceLocation.fromNamespaceAndPath(container.getModId(), DATA_PACK);
        modBus.addListener((AddPackFindersEvent event) -> addDataPack(event, pack));

        // Its air as a tank for internals (COMPAT-011).
        modBus.addListener((RegisterCapabilitiesEvent event) -> ArmorAir.register(event));

        // Leaks (COMPAT-012) and heat (COMPAT-013): the blocks with air or heat are learned as they
        // load or are placed; leaks are counted each tick and sent, and heat exchanged, once an atmos
        // tick.
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load event) -> PncBlocks.chunkLoaded(event));
        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent event) -> PncBlocks.blockChanged(event));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Pre event) -> PncLeaks.beforeTick(event));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> PncLeaks.afterTick(event));
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> PncHeat.levelTicked(event));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            PncBlocks.forget();
            PncLeaks.forget();
            PncHeat.forget();
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
     * The pack is always on and starts at the bottom, so any other data pack overrides it. It is a
     * built-in pack rather than the mod's own data because the mod's data loads whether or not
     * PneumaticCraft is installed.
     */
    private static void addDataPack(AddPackFindersEvent event, ResourceLocation pack) {
        event.addPackFinders(
                pack,
                PackType.SERVER_DATA,
                Component.translatable("pack.zas.pneumaticcraft"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM);
    }
}
