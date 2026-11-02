package io.github.west3436.zas.api.mutation;

import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.ServerThreads;
import io.github.west3436.zas.api.ZasApi;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Zone pins (API-017, API-018, API-050). A pinned zone is never dissolved into the ambient
 * (CORE-052, CORE-076): a room holding only ambient air keeps its zone, and a breached room keeps
 * exchanging with the open air instead of being dropped (D-64).
 *
 * <p><b>Owners.</b> Every pin has an owner id, and a zone stays pinned while any owner pins it.
 * Pinning a zone again under the same owner changes nothing, and {@link #unpin} takes off one
 * owner's pins. So give each thing that holds a pin its own id, for example
 * {@code mymod:habitat/12_64_-3} for a block at 12 64 -3, and reuse it when that thing pins again.
 *
 * <p><b>Pins follow the zone.</b> A zone that merges with a pinned zone takes its pins, and every
 * part of a pinned zone that splits keeps them (D-64), so one owner may come to pin several zones.
 * {@link #unpin} reaches all of them. Pins are saved with their zone and restored with it.
 *
 * <p><b>Threads:</b> every method may be called only from the server thread, and throws {@link
 * IllegalStateException} otherwise (API-045). The returned futures complete on the server thread
 * once the atmos tick that applies the call has been applied, as mutations do (API-016).
 *
 * <p>Without ZAS³ installed, a pin completes at once with {@link PinResult#ABSENT} and an unpin
 * completes at once: no zones exist (D-17).
 */
public final class ZonePins {
    private ZonePins() {}

    /**
     * Pins the zone at a point under an owner (API-050). If the point's region is enclosed and has
     * no zone, a zone forms there holding its cells' ambient gas, with no gas added or removed. This
     * works under every atmosphere preset (D-68). In the open nothing forms; pin again once the
     * region is enclosed.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level
     * @param point the point, resolved to a cell as in {@link
     *     io.github.west3436.zas.api.atmosphere.Atmospheres} (API-015)
     * @param owner who holds the pin
     * @return a future completing on the server thread with the zone pinned, or why none was
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static CompletableFuture<PinResult> pin(ServerLevel level, Vec3 point, SourceId owner) {
        ServerThreads.require(level, "ZonePins.pin");
        return ZasApi.provider()
                .pin(level, Objects.requireNonNull(point, "point"), Objects.requireNonNull(owner, "owner"));
    }

    /**
     * Takes an owner's pins off every zone it pins (API-017). A zone with no pin left can dissolve
     * again once it matches the ambient. Zones stored in unloaded chunks lose the pin when they
     * load, unless the owner has pinned again by then.
     *
     * <p>Thread: server thread only.
     *
     * @param level the level whose zones lose the pins
     * @param owner whose pins to take off
     * @return a future completing on the server thread once the pins are off
     * @throws IllegalStateException if called off the server thread
     */
    @ServerThread
    public static CompletableFuture<Void> unpin(ServerLevel level, SourceId owner) {
        ServerThreads.require(level, "ZonePins.unpin");
        return ZasApi.provider().unpin(level, Objects.requireNonNull(owner, "owner"));
    }
}
