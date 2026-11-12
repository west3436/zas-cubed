package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.level.KubeLevelEvent;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.FireIgnitedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A zone caught fire (COMPAT-050): the data of the API's {@link FireIgnitedEvent}, as the
 * properties {@code level}, {@code server}, {@code zone} and {@code hotspot}.
 */
public final class FireIgnitedKubeEvent implements KubeLevelEvent {
    private final FireIgnitedEvent event;

    FireIgnitedKubeEvent(FireIgnitedEvent event) {
        this.event = event;
    }

    /** The level the fire burns in. */
    @Override
    public ServerLevel getLevel() {
        return event.level();
    }

    /** The zone that caught fire. */
    public ZoneId getZone() {
        return event.zone();
    }

    /** One of the zone's hotspots. */
    public BlockPos getHotspot() {
        return event.hotspot();
    }
}
