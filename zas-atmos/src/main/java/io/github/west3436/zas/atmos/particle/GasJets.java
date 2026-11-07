package io.github.west3436.zas.atmos.particle;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Tells nearby clients about {@link GasJet}s (CLIENT-018, CLIENT-019). Server thread.
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

    /** How far from a pipe's middle its open end's mouth is, in blocks: just past its 4 px core. */
    public static final double PIPE_MOUTH = 0.2;

    /**
     * The most flowing jets a player is sent in a second: about 1.2 KiB/s of 60-byte packets at most,
     * however large the station. That counts towards DATA-006's 2 KiB/s, which all atmos sync shares.
     */
    static final int FLOWING_PER_SECOND = 20;

    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static final SendLimiter FLOWING = new SendLimiter(FLOWING_PER_SECOND, AtmosTicks.PERIOD);

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

    /**
     * A tank leaks: a level jet from it, since a tank has no way up, the way its leak chose as it began.
     *
     * @param level the level
     * @param at where the tank is
     * @param moles the moles it let out this atmos tick; none sends nothing
     * @param angle the jet's direction around the vertical, in radians
     */
    public static void leak(ServerLevel level, Vec3 at, double moles, float angle) {
        if (moles > 0) {
            send(
                    level,
                    new GasJet(
                            GasJetKind.LEAK,
                            at.x,
                            at.y,
                            at.z,
                            (float) Math.cos(angle),
                            0,
                            (float) Math.sin(angle),
                            (float) moles));
        }
    }

    /**
     * A tank or canister ruptures, or a pipe bursts: gas in every direction.
     *
     * @param level the level
     * @param at where it was
     * @param moles the moles it let out; none sends nothing, as an empty canister has nothing to show
     */
    public static void burst(ServerLevel level, Vec3 at, double moles) {
        if (moles > 0) {
            send(level, new GasJet(GasJetKind.BURST, at.x, at.y, at.z, 0, 0, 0, (float) moles));
        }
    }

    /**
     * An open pipe end or a passive vent moves gas: a small jet from its mouth, unless it is only a
     * trickle (CLIENT-019).
     *
     * @param level the level
     * @param pos the block
     * @param outward the way the end looks out; null for a pipe with no connection, open every way
     * @param molesPerSecond its net flow: positive out of the pipe, negative into it
     * @param mouth how far from the block's middle the jet starts, in blocks
     */
    public static void openEnd(
            ServerLevel level, BlockPos pos, @Nullable Direction outward, double molesPerSecond, double mouth) {
        if (!JetPlan.showsFlow(molesPerSecond)) {
            return;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        float amount = (float) molesPerSecond;
        if (outward == null) {
            send(level, new GasJet(GasJetKind.OPEN_END, at.x, at.y, at.z, 0, 0, 0, amount));
        } else {
            Vec3 from = at.add(Vec3.atLowerCornerOf(outward.getNormal()).scale(mouth));
            send(
                    level,
                    new GasJet(
                            GasJetKind.OPEN_END,
                            from.x,
                            from.y,
                            from.z,
                            outward.getStepX(),
                            outward.getStepY(),
                            outward.getStepZ(),
                            amount));
        }
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
                    && player.connection.hasChannel(GasJet.TYPE)
                    && (!jet.kind().flowing()
                            || FLOWING.allow(player.getUUID(), level.getServer().getTickCount()))) {
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
