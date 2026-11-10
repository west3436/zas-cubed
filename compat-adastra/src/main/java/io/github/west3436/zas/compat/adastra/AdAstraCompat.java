package io.github.west3436.zas.compat.adastra;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * The adapter's entry class. {@code zas} calls {@link #init} when Ad Astra is installed in a
 * supported version (SPACE-003), and names this class nowhere else.
 */
public final class AdAstraCompat {
    /** Ad Astra's mod id. */
    public static final String AD_ASTRA = "ad_astra";

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
        modBus.addListener(FMLCommonSetupEvent.class, setup -> setup.enqueueWork(Answers::register));
    }
}
