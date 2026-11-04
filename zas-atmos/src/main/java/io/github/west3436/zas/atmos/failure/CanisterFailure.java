package io.github.west3436.zas.atmos.failure;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Kind;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Verdict;
import io.github.west3436.zas.atmos.gas.GasStore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * How a canister fails (CONT-085, CONT-101, CONT-102): by its pressure or temperature, at its atmos
 * tick, or when an explosion reaches it.
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
     * (CONT-100), so the check sees the pressure a burn raised in the same tick. It goes by the hotter
     * of its gas and the air around it.
     *
     * @param level the level
     * @param pos the canister
     * @param gas its network's gas
     * @return whether the canister failed, and is gone
     */
    public static boolean check(ServerLevel level, BlockPos pos, GasStore gas) {
        double kelvin = Math.max(gas.isEmpty() ? 0 : gas.temperature(), airKelvin(level, pos));
        Verdict verdict = ContainerFailure.canister(gas.pressure(), kelvin, ContainerBlast.powerCap());
        if (!verdict.destroys()) {
            return false;
        }
        Vec3 at = Vec3.atCenterOf(pos);
        level.removeBlock(pos, false);
        if (verdict.kind() == Kind.EXPLODE) {
            ContainerBlast.explode(level, at, verdict.power(), null);
        } else {
            ContainerBlast.burst(level, at);
        }
        return true;
    }

    /** The air around a canister, K, or 0 while its cell is paused or unresolved and its reading isn't real. */
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
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (level.getBlockEntity(pos) instanceof CanisterBlockEntity) {
                level.removeBlock(pos, false);
                ContainerBlast.burst(level, Vec3.atCenterOf(pos));
            }
        }
    }
}
