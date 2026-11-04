package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Two gas tanks joined by a valve (CONT-103): what a tank transfer valve is, whether it is an item
 * ({@link TransferValveItem}) or primed ({@link PrimedTransferValve}).
 *
 * <p>Opening the valve moves all of the first tank's gas into the second, mixing temperatures.
 * Closing it shares the gas back by volume. Without both tanks there is nothing to join, so the
 * valve moves no gas. The merged gas stays in the second tank's volume, so the pressure there is
 * what makes a TTV a bomb (S-13).
 *
 * <p>Server thread only.
 */
public final class TransferValve {
    /** What one atmos tick of the tanks' gas came to. */
    public enum Step {
        /** Nothing happened to the tanks. */
        QUIET,
        /** A tank's gas changed, so a holder that keeps a copy writes it back. */
        CHANGED,
        /** A tank's failure destroyed the TTV. */
        DESTROYED
    }

    /** The shortest timer, s (CONT-104): the valve opens as soon as the timer starts. */
    public static final int MIN_TIMER = 0;

    /** The longest timer, s (CONT-104). */
    public static final int MAX_TIMER = 600;

    /** A new TTV's timer, s (CONT-104). */
    public static final int DEFAULT_TIMER = 10;

    /** The pressure above which a tank explodes, atm (CONT-038). */
    private static final double EXPLODES_ATM = 50;

    /** The explosion's power per atm above that (CONT-038). */
    private static final double ATM_PER_POWER = 10;

    /** The greatest power of a container's explosion (CONT-109). */
    private static final double MAX_POWER = 16;

    private ItemStack first;
    private ItemStack second;
    private boolean open;

    /**
     * @param first the first tank, or an empty stack; the valve keeps this stack and changes it
     * @param second the second tank, or an empty stack; the same
     * @param open whether the valve is open: its gas is already in the second tank
     */
    public TransferValve(ItemStack first, ItemStack second, boolean open) {
        this.first = first;
        this.second = second;
        this.open = open;
    }

    /** A timer setting clamped to its range (CONT-104). */
    public static int clampTimer(int seconds) {
        return Math.max(MIN_TIMER, Math.min(MAX_TIMER, seconds));
    }

    /** The first tank, or an empty stack. */
    public ItemStack first() {
        return first;
    }

    /** The second tank, or an empty stack. */
    public ItemStack second() {
        return second;
    }

    /** Whether the valve is open. */
    public boolean isOpen() {
        return open;
    }

    /** Whether it holds both tanks, so the valve can join them. */
    public boolean joined() {
        return !first.isEmpty() && !second.isEmpty();
    }

    /** Opens the valve, moving all of the first tank's gas into the second if it holds both. */
    public void open() {
        if (open) {
            return;
        }
        open = true;
        if (joined()) {
            GasParcel gas = GasTankItem.gas(first);
            GasTankItem.setGas(first, GasParcel.EMPTY);
            GasTankItem.add(second, gas);
            settleKinds();
        }
    }

    /** Closes the valve, sharing the gas between the tanks by volume if it holds both. */
    public void close() {
        if (!open) {
            return;
        }
        open = false;
        if (joined()) {
            double firstVolume = ((GasTankItem) first.getItem()).volume();
            double secondVolume = ((GasTankItem) second.getItem()).volume();
            GasStore all = new GasStore(firstVolume + secondVolume);
            all.add(GasTankItem.gas(first));
            all.add(GasTankItem.gas(second));
            GasTankItem.setGas(first, all.takeFraction(firstVolume / all.volume()));
            GasTankItem.setGas(second, all.drain());
            settleKinds();
        }
    }

    /** A tank takes the kind of what it now holds (CONT-039), so a tank's name never lies. */
    private void settleKinds() {
        first = GasTankItem.ofItsKind(first);
        second = GasTankItem.ofItsKind(second);
    }

    /**
     * Fits a tank in the first free place. Tanks only go on while the valve is closed.
     *
     * @param tank the tank, whose stack the valve keeps
     * @return whether it went on
     */
    public boolean attach(ItemStack tank) {
        if (open || !(tank.getItem() instanceof GasTankItem)) {
            return false;
        }
        if (first.isEmpty()) {
            first = tank;
            return true;
        }
        if (second.isEmpty()) {
            second = tank;
            return true;
        }
        return false;
    }

    /** Takes the first tank off, closing an open valve first. */
    public ItemStack detachFirst() {
        close();
        ItemStack tank = first;
        first = ItemStack.EMPTY;
        return tank;
    }

    /** Takes the second tank off, closing an open valve first. */
    public ItemStack detachSecond() {
        close();
        ItemStack tank = second;
        second = ItemStack.EMPTY;
        return tank;
    }

    /**
     * One atmos tick of the tanks' gas: each tank reacts what it holds (CONT-100), and a tank past
     * its limit fails (CONT-038).
     *
     * @param level the level it is in
     * @param holder who or what holds it, or the primed TTV itself
     * @return what it came to
     */
    public Step step(ServerLevel level, Entity holder) {
        boolean changed = atmosTick(first, level, holder);
        changed |= atmosTick(second, level, holder);
        Vec3 at = holder.getBoundingBox().getCenter();
        if (explodes(level, at, first) || explodes(level, at, second)) {
            return Step.DESTROYED;
        }
        return changed ? Step.CHANGED : Step.QUIET;
    }

    private static boolean atmosTick(ItemStack tank, ServerLevel level, Entity holder) {
        return tank.getItem() instanceof GasTankItem && GasTankItem.atmosTick(tank, level, holder);
    }

    private static boolean explodes(ServerLevel level, Vec3 at, ItemStack tank) {
        if (!(tank.getItem() instanceof GasTankItem)) {
            return false;
        }
        double atm = GasTankItem.contents(tank).pressure() / 101.325;
        if (atm <= EXPLODES_ATM) {
            return false;
        }
        float power = (float) Math.min(MAX_POWER, (atm - EXPLODES_ATM) / ATM_PER_POWER);
        level.explode(null, at.x, at.y, at.z, power, Level.ExplosionInteraction.NONE);
        return true;
    }
}
