package io.github.west3436.zas.atmos.failure;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.failure.ContainerFailure.Verdict;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.particle.GasJets;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Makes a tank fail at a position, by its pressure (CONT-038): whoever holds a tank calls {@link
 * #check} once per atmos tick. A tank in an inventory, a suit's slot or on the ground does this
 * through {@link GasTankItem#atmosTick}.
 */
public final class TankFailure {
    /** Who a failing tank's gas is released by, for the conservation ledger. */
    public static final SourceId RELEASE = SourceId.of(ZasAtmosMod.MOD_ID, "tank_failure");

    private TankFailure() {}

    /**
     * Where a tank an entity carries fails: the middle of its body. Not its eyes: a vanilla
     * explosion exactly at an entity's eye height has no direction to push it in, and skips it.
     *
     * @param holder the entity carrying the tank
     * @return the point
     */
    public static Vec3 pointOf(Entity holder) {
        return holder.getBoundingBox().getCenter();
    }

    /**
     * Applies the failure rule to a tank, at its pressure now.
     *
     * <ul>
     *   <li>Holding, nothing changes.
     *   <li>Leaking, some of its gas goes into the cell at {@code at} ({@link
     *       ContainerFailure#leakMoles}), and the stack stays. It hisses as the leak starts, and not
     *       again until it has held at a check and then leaked again (CONT-127).
     *   <li>Rupturing, the stack is used up ({@link ItemStack#isEmpty()} is then true), and all its
     *       gas goes into the cell.
     *   <li>Exploding, the same, then a vanilla explosion at {@code at} with the capped power, which
     *       breaks blocks only if the preset says so (CONT-107).
     * </ul>
     *
     * <p>The stack changes in place, so a holder that keeps a copy, as a suit does, writes it back
     * if the verdict isn't {@code NONE} or the tank was {@linkplain #leaking leaking} before the check.
     * It is used up before the explosion, so a holder killed by the blast doesn't drop it.
     *
     * @param level the level
     * @param at where the tank is
     * @param tank the tank's stack; anything else does nothing
     * @param source the entity an explosion comes from, or null; see {@link ContainerBlast#explode}.
     *     Something carrying the tank isn't its source. A primed tank transfer valve is
     * @return what the tank did
     */
    public static Verdict check(ServerLevel level, Vec3 at, ItemStack tank, @Nullable Entity source) {
        if (!(tank.getItem() instanceof GasTankItem)) {
            return Verdict.NONE;
        }
        GasMixture gas = GasTankItem.contents(tank);
        Verdict verdict = ContainerFailure.tank(gas.pressure(), ContainerBlast.powerCap());
        switch (verdict.kind()) {
            case NONE -> tank.remove(AtmosComponents.LEAKING.get());
            case LEAK -> {
                GasParcel lost = GasTankItem.take(tank, ContainerFailure.leakMoles(gas.totalMoles(), gas.pressure()));
                GasMutations.add(level, at, lost, RELEASE);
                if (tank.set(AtmosComponents.LEAKING.get(), Unit.INSTANCE) == null) {
                    ContainerBlast.hiss(level, at);
                }
                GasJets.leak(level, at, lost.totalMoles());
            }
            case RUPTURE -> ContainerBlast.burst(level, at, release(level, at, tank));
            case EXPLODE -> {
                release(level, at, tank);
                ContainerBlast.explode(level, at, verdict.power(), source);
            }
        }
        return verdict;
    }

    /**
     * Whether a tank was leaking at its last check. The check that finds it holding again clears this,
     * changing the stack under a {@code NONE} verdict, so a holder of a copy asks before it checks.
     *
     * @param tank the tank's stack
     * @return whether it is in the middle of a leak
     */
    public static boolean leaking(ItemStack tank) {
        return tank.has(AtmosComponents.LEAKING.get());
    }

    /**
     * Uses the stack up and puts all its gas in the cell at a point.
     *
     * @return the moles it let out
     */
    private static double release(ServerLevel level, Vec3 at, ItemStack tank) {
        GasParcel all = GasTankItem.gas(tank);
        tank.setCount(0);
        GasMutations.add(level, at, all, RELEASE);
        return all.totalMoles();
    }
}
