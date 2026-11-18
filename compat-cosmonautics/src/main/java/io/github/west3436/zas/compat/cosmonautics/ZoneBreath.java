package io.github.west3436.zas.compat.cosmonautics;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.life.BreathState;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.ClientBreath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Breathing where Cosmonautics finds no air (SPACE-012).
 *
 * <p>Cosmonautics decides whether there is air by altitude: in deep space, on its Moon, and high
 * above a planet, its listener to {@link LivingBreatheEvent}, at {@code HIGHEST} priority, sets
 * {@code canBreathe} to false for anything that could breathe there. It doesn't know ZAS³'s rooms,
 * so a sealed, pressurized room on its Moon would drown a player.
 *
 * <p>This listens at {@code HIGH} priority, after that and before ZAS³'s own listener at {@code
 * NORMAL}. Where Cosmonautics' answer is "can't breathe", vanilla's would be "can" (the eyes are in
 * air, or in a fluid the entity can breathe in), and ZAS³ knows the air the entity breathes, it
 * sets {@code canBreathe} back to true and leaves the rest to ZAS³'s listener (SPACE-002), whose
 * verdict then decides as it does everywhere else. ZAS³ knows the air:
 *
 * <ul>
 *   <li>where its last breathing check found the entity breathing from its internals, in any
 *       dimension and at any altitude; or
 *   <li>where the eyes are in a zone and either that check found the entity breathing, or, for
 *       something no check has reached yet, the zone's O₂ is at or above the breathing minimum
 *       (LIFE-003).
 * </ul>
 *
 * <p>Elsewhere, in the open, Cosmonautics' answer stands: ZAS³'s ambient is one per dimension and
 * has no altitude, so it can't say where Cosmonautics' sky ends. On its Moon and in deep space the
 * Aeronautics adapter seeds a vacuum from the same mod's {@code dimension_physics} data, so there
 * the two agree anyway. Under water vanilla's answer stands as well: this never lets an entity
 * breathe where vanilla would drown it.
 *
 * <p><b>Client.</b> Cosmonautics' listener also runs on the client, where it would drain the local
 * player's air bar between the server's updates. So the server sends each player whether this
 * gives it a breath ({@link ZoneBreathPayload}), when that changes, and on the client this does the
 * same for the local player with that answer.
 */
final class ZoneBreath {
    /** What each player was last sent. Server thread. */
    private static final Map<UUID, Boolean> SENT = new HashMap<>();

    /** The server's last answer for the local player. Client thread. */
    private static boolean localGrants;

    private ZoneBreath() {}

    static void onBreathe(LivingBreatheEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.level().isClientSide()) {
            if (!e.canBreathe() && localGrants && ClientBreath.isLocal(entity) && vanillaBreathes(entity)) {
                e.setCanBreathe(true);
            }
            return;
        }
        if (!(entity.level() instanceof ServerLevel)) {
            return;
        }
        boolean player = entity instanceof ServerPlayer && !(entity instanceof FakePlayer);
        if (e.canBreathe() && !player) {
            return;
        }
        boolean grants = vanillaBreathes(entity) && zasKnowsTheAir(entity);
        if (grants && !e.canBreathe()) {
            e.setCanBreathe(true);
        }
        if (player) {
            sync((ServerPlayer) entity, grants);
        }
    }

    /** Whether vanilla's rule alone lets the entity breathe where it is. */
    static boolean vanillaBreathes(LivingEntity entity) {
        return Breathing.vanilla(entity).canBreathe();
    }

    /**
     * Whether ZAS³ knows the air an entity breathes, and finds it breathing: from its internals, or
     * in a zone. Server thread.
     */
    static boolean zasKnowsTheAir(LivingEntity entity) {
        BreathState state = entity.getExistingDataOrNull(Breathers.STATE);
        Breathability verdict = state == null ? null : state.verdict();
        if (verdict != null) {
            if (!verdict.canBreathe()) {
                return false;
            }
            if (verdict.source() == BreathSource.INTERNALS) {
                return true;
            }
        }
        AtmosphereSample sample = Atmospheres.of(entity);
        if (sample.state() != AtmosphereState.ZONED || sample.zone().isEmpty()) {
            return false;
        }
        if (verdict != null) {
            return true;
        }
        return sample.partialPressures().getOrDefault(StandardAir.OXYGEN, 0.0) >= Breathing.oxygenMinimum();
    }

    /** Sends a player its answer if it changed since the last one it was sent. */
    private static void sync(ServerPlayer player, boolean grants) {
        Boolean last = SENT.put(player.getUUID(), grants);
        if ((last == null || last != grants) && player.connection.hasChannel(ZoneBreathPayload.TYPE)) {
            player.connection.send(new ZoneBreathPayload(grants));
        }
    }

    /** Forgets a player that left, so it is sent its answer afresh when it comes back. */
    static void left(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }

    /** The server's answer for the local player. */
    static void received(ZoneBreathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> localGrants = payload.grants());
    }
}
