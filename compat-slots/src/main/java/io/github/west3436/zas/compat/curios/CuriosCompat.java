package io.github.west3436.zas.compat.curios;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.life.ExtraSlots;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * Where the Curios adapter starts (COMPAT-040, COMPAT-042). {@code zas} calls {@link #init} by
 * name, and only when Curios is installed in a version the adapter supports; otherwise this class
 * is never loaded.
 *
 * <p>The adapter is one registration: {@link CuriosSlots} as a provider of extra equipment slots
 * ({@link ExtraSlots}), so that internals find a tank in a Curios slot and a mask in the Curios
 * head slot seals and filters. A mod with slots of its own does the same from its own constructor.
 *
 * <p><b>Accessories' compatibility layer.</b> Accessories has an addon that answers to the mod id
 * {@code curios} and serves Curios' API from Accessories' own slots. With it installed there is no
 * Curios inventory apart from Accessories', and the Accessories adapter reads those slots, so this
 * one registers nothing: each worn item is given once.
 *
 * <p>This class names nothing of Curios', so asking it {@link #isActive} never loads a class of
 * Curios'.
 */
public final class CuriosCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The provider's id among the registered ones. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "curios");

    /** The mod id of Accessories' compatibility layer for Curios. */
    public static final String LAYER = "cclayer";

    private static volatile boolean active;

    private CuriosCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into Curios, which may not be
     * constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        if (ModList.get().isLoaded(LAYER)) {
            LOGGER.info("Curios here is Accessories' compatibility layer, so its slots are read as Accessories' slots");
            return;
        }
        ExtraSlots.register(ID, CuriosSlots::items);
        active = true;
    }

    /** Whether the adapter reads Curios' slots: it started, and Curios isn't Accessories' layer. Any thread. */
    public static boolean isActive() {
        return active;
    }
}
