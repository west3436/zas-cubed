package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.atmos.portable.PortableScrubberMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A portable scrubber's window (CONT-018): a portable's, with no rows of its own. */
final class PortableScrubberScreen extends PortableGasScreen<PortableScrubberMenu> {
    PortableScrubberScreen(PortableScrubberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 0);
    }
}
