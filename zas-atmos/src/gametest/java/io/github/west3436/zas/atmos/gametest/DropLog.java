package io.github.west3436.zas.atmos.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * The items dropped while a GameTest watches. A level only finds entities in a chunk that is ticking
 * them, so a test far from the players sees each dropped item as it joins the level. A test watches
 * and takes within one call, so nothing else drops an item in the meantime.
 */
final class DropLog {
    private static final List<ItemEntity> SEEN = new ArrayList<>();
    private static boolean listening;
    private static boolean watching;

    private DropLog() {}

    /** Starts collecting dropped items afresh. */
    static void watch() {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
                if (watching && e.getEntity() instanceof ItemEntity item) {
                    SEEN.add(item);
                }
            });
        }
        SEEN.clear();
        watching = true;
    }

    /**
     * Stops collecting, and removes from the level the items dropped since {@link #watch}.
     *
     * @return their stacks, in the order they dropped
     */
    static List<ItemStack> take() {
        watching = false;
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemEntity item : SEEN) {
            stacks.add(item.getItem().copy());
            item.discard();
        }
        SEEN.clear();
        return stacks;
    }
}
