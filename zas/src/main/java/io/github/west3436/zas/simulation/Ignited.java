package io.github.west3436.zas.simulation;

import io.github.west3436.zas.engine.scheduler.OpOutcome;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * What an ignition did (FIRE-014), as {@link DimensionAtmos#ignite} completes with it.
 *
 * @param status what happened
 * @param hotspot the hotspot's position for {@link OpOutcome.IgnitionStatus#IGNITED} and {@link
 *     OpOutcome.IgnitionStatus#BURNING}; otherwise null
 * @param zoneId the zone it found: the burning zone, the zone whose gas won't burn or has no room,
 *     or the paused zone; 0 if none
 */
public record Ignited(
        OpOutcome.IgnitionStatus status, @Nullable BlockPos hotspot, long zoneId) {
    public Ignited {
        Objects.requireNonNull(status, "status");
    }

    /** No zone there: open air, solid, or a dimension without a simulation. */
    public static final Ignited OPEN = new Ignited(OpOutcome.IgnitionStatus.OPEN, null, 0);
}
