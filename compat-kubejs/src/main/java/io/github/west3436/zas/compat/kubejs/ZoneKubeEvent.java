package io.github.west3436.zas.compat.kubejs;

import dev.latvian.mods.kubejs.level.KubeLevelEvent;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.ZoneLifecycleEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * What a script hears of a change in a zone's life (COMPAT-050): the data of the API's {@link
 * ZoneLifecycleEvent}, as the properties {@code level}, {@code server}, {@code zone} and {@code
 * pos}. It is the whole event for a zone that formed, was breached, was sealed, dissolved, paused
 * or resumed; a merge and a split add one property each.
 */
public class ZoneKubeEvent implements KubeLevelEvent {
    private final ZoneLifecycleEvent event;

    ZoneKubeEvent(ZoneLifecycleEvent event) {
        this.event = event;
    }

    /** The level the zone is in. */
    @Override
    public ServerLevel getLevel() {
        return event.level();
    }

    /** The zone's id, to compare with another or to give back to {@code Atmospheres.zone}. */
    public ZoneId getZone() {
        return event.zone();
    }

    /** A block in the zone, or where it was if it is gone. */
    public BlockPos getPos() {
        return event.pos();
    }
}
