package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.level.KubeLevelEvent;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.FireExtinguishedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A zone stopped burning (COMPAT-050): the data of the API's {@link FireExtinguishedEvent}, as the
 * properties {@code level}, {@code server}, {@code zone} and {@code lastHotspot}.
 */
public final class FireExtinguishedKubeEvent implements KubeLevelEvent {
    private final FireExtinguishedEvent event;

    FireExtinguishedKubeEvent(FireExtinguishedEvent event) {
        this.event = event;
    }

    /** The level the fire burned in. */
    @Override
    public ServerLevel getLevel() {
        return event.level();
    }

    /** The zone that stopped burning. */
    public ZoneId getZone() {
        return event.zone();
    }

    /** One of the hotspots the zone had before it went out. */
    public BlockPos getLastHotspot() {
        return event.lastHotspot();
    }
}
