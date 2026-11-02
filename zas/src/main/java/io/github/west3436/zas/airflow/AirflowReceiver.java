package io.github.west3436.zas.airflow;

import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The airflow a client last received for its own player (FLOW-017), which pushes that player
 * every game tick with {@link Airflow#apply}, as the server pushes everything else. It lapses
 * when the server stops sending, after the payload's {@code validTicks}.
 *
 * <p>Holds no client-only classes, so the payload registration that names its handlers loads on
 * a dedicated server too. GameTests make their own to push a player through the client's path.
 */
public final class AirflowReceiver {
    /** The client's own. */
    public static final AirflowReceiver CLIENT = new AirflowReceiver();

    private FlowField field = FlowField.EMPTY;
    private FlowSettings settings = FlowSettings.DEFAULT;
    private double threshold = Double.POSITIVE_INFINITY;
    private double resistance;
    private ResourceLocation dimension;
    private int ticksLeft;

    /** Takes a payload in place of the last; one with no openings ends airflow. Client thread. */
    public void accept(AirflowSyncPayload p) {
        field = FlowField.ofOpenings(p.openings());
        settings = p.settings();
        threshold = p.threshold();
        resistance = p.resistance();
        dimension = p.dimension();
        ticksLeft = field.isEmpty() ? 0 : p.validTicks();
    }

    /** Drops what it holds, as when leaving a server. */
    public void clear() {
        field = FlowField.EMPTY;
        ticksLeft = 0;
        dimension = null;
    }

    /** Whether it holds airflow that hasn't lapsed. */
    public boolean active() {
        return ticksLeft > 0 && !field.isEmpty();
    }

    /**
     * One game tick for the player: pushes it if it is in the dimension the openings are from.
     *
     * @return the change of velocity, or null for none
     */
    public Vec3 tick(Player player) {
        if (!active()) {
            return null;
        }
        ticksLeft--;
        if (!player.level().dimension().location().equals(dimension)) {
            return null;
        }
        return Airflow.apply(player, field, FlowField.ANY_ZONE, threshold, settings, resistance);
    }

    static void handleSync(AirflowSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CLIENT.accept(payload));
    }

    static void handleImpulse(AirflowImpulsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> Airflow.applyImpulse(context.player(), payload));
    }
}
