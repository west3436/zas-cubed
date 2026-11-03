package io.github.west3436.zas.api.fire;

import io.github.west3436.zas.api.AnyThread;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * The result of an ignition, once the atmos tick that applied it has been applied (API-056,
 * API-016). It is immutable.
 *
 * @param outcome what the ignition found
 * @param hotspot the fire's position, present exactly when the outcome is {@link
 *     IgnitionOutcome#IGNITED} or {@link IgnitionOutcome#BURNING}
 * @param zone the zone it found: the one now burning, the one whose gas won't burn or has no room
 *     for a fire, or the paused one; empty for {@link IgnitionOutcome#OPEN} and {@link
 *     IgnitionOutcome#ABSENT}, and for {@link IgnitionOutcome#PAUSED} in an unresolved region
 */
@AnyThread
public record Ignition(IgnitionOutcome outcome, Optional<BlockPos> hotspot, Optional<ZoneId> zone) {
    /** The result of every ignition when ZAS³ is absent: nothing burns. */
    public static final Ignition ABSENT = new Ignition(IgnitionOutcome.ABSENT, Optional.empty(), Optional.empty());

    /**
     * Checks the arguments, and makes the position immutable.
     *
     * @throws NullPointerException if an argument is null
     * @throws IllegalArgumentException if a hotspot is given for an outcome other than {@link
     *     IgnitionOutcome#IGNITED} or {@link IgnitionOutcome#BURNING}, or none for them
     */
    public Ignition {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(hotspot, "hotspot");
        Objects.requireNonNull(zone, "zone");
        boolean burns = outcome == IgnitionOutcome.IGNITED || outcome == IgnitionOutcome.BURNING;
        if (hotspot.isPresent() != burns) {
            throw new IllegalArgumentException(
                    "an ignition names a hotspot exactly when its outcome is IGNITED or BURNING: " + outcome);
        }
        hotspot = hotspot.map(BlockPos::immutable);
    }

    /**
     * Whether a fire burns at or next to the position now: {@link IgnitionOutcome#IGNITED} or
     * {@link IgnitionOutcome#BURNING}.
     *
     * <p>Thread: any.
     *
     * @return true if it burns
     */
    @AnyThread
    public boolean burning() {
        return hotspot.isPresent();
    }
}
