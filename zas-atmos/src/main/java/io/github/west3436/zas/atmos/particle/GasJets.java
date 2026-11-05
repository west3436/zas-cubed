package io.github.west3436.zas.atmos.particle;

import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Tells nearby clients about {@link GasJet}s (CLIENT-018). Server thread.
 *
 * <p>What the jets look like is up to each client: this only says what happened, where, and how much.
 */
public final class GasJets {
    /** How far in front of the eye an extinguisher's nozzle is, at most, in blocks. */
    static final double NOZZLE_AHEAD = 0.7;

    /** How far to the side of the eye the hand holding it is, in blocks. */
    static final double NOZZLE_SIDE = 0.3;

    /** How far below the eye it is, in blocks. */
    static final double NOZZLE_BELOW = 0.25;

    private static final Vec3 UP = new Vec3(0, 1, 0);

    /** For GameTests: sees every jet the server sends, whoever is near; null sees none. */
    private static volatile Consumer<GasJet> hook;

    private GasJets() {}

    /**
     * An extinguisher sprays: a cone from the hand holding it along its user's look (CONT-037).
     *
     * @param level the level
     * @param user who sprays
     * @param reach how far the spray goes from the eye, as it put out fires: to a block, if it met one
     */
    public static void spray(ServerLevel level, LivingEntity user, double reach) {
        Vec3 look = user.getLookAngle();
        // Short of what the spray met, so a user up against a wall doesn't spray from inside it.
        double ahead = Math.min(NOZZLE_AHEAD, reach / 2);
        Vec3 from = user.getEyePosition()
                .add(look.scale(ahead))
                .add(side(look, user).scale(NOZZLE_SIDE))
                .subtract(0, NOZZLE_BELOW, 0);
        send(
                level,
                new GasJet(
                        GasJetKind.SPRAY,
                        from.x,
                        from.y,
                        from.z,
                        (float) look.x,
                        (float) look.y,
                        (float) look.z,
                        (float) (reach - ahead)));
    }

    /** A unit vector to the side of the hand the user is using, level; zero looking straight up or down. */
    private static Vec3 side(Vec3 look, LivingEntity user) {
        Vec3 right = look.cross(UP);
        if (right.lengthSqr() < 1e-6) {
            return Vec3.ZERO;
        }
        boolean mainHand = user.getUsedItemHand() == InteractionHand.MAIN_HAND;
        boolean onRight = mainHand == (user.getMainArm() == HumanoidArm.RIGHT);
        return right.normalize().scale(onRight ? 1 : -1);
    }

    /** Sends a jet to the players it reaches. */
    private static void send(ServerLevel level, GasJet jet) {
        Consumer<GasJet> h = hook;
        if (h != null) {
            h.accept(jet);
        }
        for (ServerPlayer player : level.players()) {
            if (reaches(player, jet)
                    && !(player instanceof FakePlayer)
                    && player.connection != null
                    && player.connection.hasChannel(GasJet.TYPE)) {
                player.connection.send(jet);
            }
        }
    }

    /**
     * Whether a jet is sent to a player: it is within the jet's range.
     *
     * @param player the player
     * @param jet the jet
     * @return whether it is near enough
     */
    public static boolean reaches(Entity player, GasJet jet) {
        double range = jet.kind().range();
        return player.distanceToSqr(jet.x(), jet.y(), jet.z()) <= range * range;
    }

    /** For GameTests: {@code seen} sees every jet the server sends; null ends it. */
    public static void hookForTest(Consumer<GasJet> seen) {
        hook = seen;
    }
}
