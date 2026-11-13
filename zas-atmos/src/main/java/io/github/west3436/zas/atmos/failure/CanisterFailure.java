package io.github.west3436.zas.atmos.failure;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Kind;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Verdict;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeNodeEntity;
import io.github.west3436.zas.atmos.tank.GasVessels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * How a canister fails (CONT-085, CONT-101, CONT-102): by its pressure or temperature, at its atmos
 * tick, or when an explosion reaches it. A large gas tank is rated the same and fails the same way.
 *
 * <p>Either way the block goes and leaves nothing behind. As it is removed, its share of its
 * network's gas goes into its cell, and the rest stays with the network's other members (CONT-009).
 */
public final class CanisterFailure {
    private CanisterFailure() {}

    /** Listens for explosions, on the game's event bus. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(CanisterFailure::onDetonate);
    }

    /**
     * Applies the failure rule to a canister. Its atmos tick calls this after its network has reacted
     * (CONT-100), so the check sees the pressure a burn raised in the same tick. Its temperature limit
     * goes by the air around it. Hot gas inside it is left to the pressure limits (CONT-085, D-57).
     *
     * @param level the level
     * @param pos the canister, or a large tank's lower half
     * @param gas its network's gas
     * @return whether the canister failed, and is gone
     */
    public static boolean check(ServerLevel level, BlockPos pos, GasStore gas) {
        Verdict verdict = ContainerFailure.canister(gas.pressure(), airKelvin(level, pos), ContainerBlast.powerCap());
        if (!verdict.destroys()) {
            return false;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        double moles = shareOf(level, pos);
        level.removeBlock(pos, false);
        if (verdict.kind() == Kind.EXPLODE) {
            ContainerBlast.explode(level, at, verdict.power(), null);
        } else {
            ContainerBlast.burst(level, at, moles);
        }
        return true;
    }

    /** The moles a canister lets out when it goes, read before it does: its share of its network's gas. */
    private static double shareOf(ServerLevel level, BlockPos pos) {
        PipeNodeEntity vessel = GasVessels.at(level, pos);
        return vessel == null ? 0 : vessel.share().totalMoles();
    }

    /**
     * The air around a canister, K: its zone's, or the ambient's in open air. A paused or unresolved
     * cell reads 0, because its sample is a last-known mixture or a stand-in, so it can't rupture a
     * canister by heat until it resolves.
     */
    private static double airKelvin(ServerLevel level, BlockPos pos) {
        AtmosphereSample cell = Atmospheres.at(level, pos);
        return cell.state() == AtmosphereState.ZONED || cell.state() == AtmosphereState.AMBIENT
                ? cell.mixture().temperature()
                : 0;
    }

    /**
     * A canister an explosion reaches ruptures, before the game makes any drops, so that its gas goes
     * into its cell and not into an item. That holds for an explosion that keeps blocks too, as a
     * container's does under Normal (CONT-107). A wind burst only pushes things and triggers doors, so
     * it leaves a canister alone.
     */
    private static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || event.getExplosion().getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) {
            return;
        }
        for (BlockPos reached : event.getAffectedBlocks()) {
            PipeNodeEntity vessel = GasVessels.at(level, reached);
            if (vessel != null && !vessel.isRemoved()) {
                // A tank goes by its lower half, which holds its gas, whichever half the blast reached.
                BlockPos pos = vessel.getBlockPos();
                double moles = vessel.share().totalMoles();
                level.removeBlock(pos, false);
                ContainerBlast.burst(level, Vec3.atCenterOf(pos), moles);
            }
        }
    }
}
