package io.github.west3436.zas.compat.kubejs;

import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.ZoneSplitEvent;

/** A zone was cut in two (COMPAT-050): {@code zone} keeps its id, and {@code remnant} is the new zone. */
public final class ZoneSplitKubeEvent extends ZoneKubeEvent {
    private final ZoneSplitEvent event;

    ZoneSplitKubeEvent(ZoneSplitEvent event) {
        super(event);
        this.event = event;
    }

    /** The part cut off, a new zone. */
    public ZoneId getRemnant() {
        return event.remnant();
    }
}
