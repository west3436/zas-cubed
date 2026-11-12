package io.github.west3436.zas.compat.accessories;

import io.github.west3436.zas.api.life.ExtraSlots;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;

/**
 * Where the Accessories adapter starts (COMPAT-041, COMPAT-042). {@code zas} calls {@link #init} by
 * name, and only when Accessories is installed in a version the adapter supports; otherwise this
 * class is never loaded.
 *
 * <p>The adapter is one registration: {@link AccessoriesSlots} as a provider of extra equipment
 * slots ({@link ExtraSlots}), so that internals find a tank in an Accessories slot and a mask in a
 * slot of its head group seals and filters. A mod with slots of its own does the same from its own
 * constructor.
 *
 * <p>With Curios installed too, each mod keeps its own slots and each adapter reads its own, so no
 * item is given twice; this one's id sorts first, so Accessories' slots are looked in first. With
 * Accessories' compatibility layer for Curios there is only this one's to read, and the Curios
 * adapter registers nothing.
 *
 * <p>This class names nothing of Accessories', so asking it {@link #isActive} never loads a class
 * of Accessories'.
 */
public final class AccessoriesCompat {
    /** The provider's id among the registered ones. */
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "accessories");

    private static volatile boolean active;

    private AccessoriesCompat() {}

    /**
     * Starts the adapter. It only registers; nothing here calls into Accessories, which may not be
     * constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        ExtraSlots.register(ID, AccessoriesSlots::items);
        active = true;
    }

    /** Whether the adapter started. Any thread. */
    public static boolean isActive() {
        return active;
    }
}
