package io.github.west3436.zas.compat.adastra;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * The adapter's entry class. {@code zas} calls {@link #init} when Ad Astra is installed in a
 * supported version (SPACE-003), and names this class nowhere else.
 */
public final class AdAstraCompat {
    /** Ad Astra's mod id. */
    public static final String AD_ASTRA = "ad_astra";

    /** The adapter's built-in data pack, a folder in the jar. */
    public static final String DATA_PACK = "datapacks/ad_astra";

    private AdAstraCompat() {}

    /**
     * Starts the adapter. It runs while {@code zas} is being constructed, when Ad Astra may not
     * have been yet, so it only registers; Ad Astra is first called at common setup.
     *
     * @param modBus {@code zas}'s mod bus
     * @param container {@code zas}'s container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        // Ad Astra keeps its listeners in plain lists, so they are added on the main thread.
        modBus.addListener(
                FMLCommonSetupEvent.class,
                setup -> setup.enqueueWork(() -> {
                    Answers.register();
                    Planets.register();
                }));
        AdAstraConfig.register(container);
        Torches.register(NeoForge.EVENT_BUS);
        modBus.addListener(RegisterCapabilitiesEvent.class, SuitOxygen::register);
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, stopped -> Planets.serverStopped());
        ResourceLocation pack = ResourceLocation.fromNamespaceAndPath(container.getModId(), DATA_PACK);
        modBus.addListener(AddPackFindersEvent.class, event -> addDataPack(event, pack));
    }

    /**
     * Offers the adapter's data pack: the suits' protection (SPACE-022) and the mobs that live in
     * space (SPACE-019). It is always on and starts at the bottom, so any other data pack overrides
     * it. It is a built-in pack rather than the mod's own data because the mod's data loads whether
     * or not the adapter is on.
     */
    private static void addDataPack(AddPackFindersEvent event, ResourceLocation pack) {
        event.addPackFinders(
                pack,
                PackType.SERVER_DATA,
                Component.translatable("pack.zas.ad_astra"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.BOTTOM);
    }
}
