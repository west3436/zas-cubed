package io.github.west3436.zas.compat.kubejs;

import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.event.ZoneMergedEvent;

/** Two zones became one (COMPAT-050): {@code zone} took in {@code absorbed}, whose id is gone. */
public final class ZoneMergedKubeEvent extends ZoneKubeEvent {
    private final ZoneMergedEvent event;

    ZoneMergedKubeEvent(ZoneMergedEvent event) {
        super(event);
        this.event = event;
    }

    /** The zone that was taken in. */
    public ZoneId getAbsorbed() {
        return event.absorbed();
    }
}
