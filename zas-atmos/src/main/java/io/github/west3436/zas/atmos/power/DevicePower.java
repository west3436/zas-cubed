package io.github.west3436.zas.atmos.power;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import net.minecraft.nbt.CompoundTag;

/**
 * A powered device's side of the power contract (CONT-060, CONT-065, CONT-069, architecture
 * §11.3).
 *
 * <p><b>The device</b> declares what it draws now, in FE/t ({@link #draw()}; 0 while it is off),
 * and its {@link #priority()}. It runs an atmos tick only while {@link #isPowered()}, and does
 * nothing otherwise (CONT-060).
 *
 * <p><b>A provider</b> (the area air controller, CONT-068) powers it for one atmos tick at a time:
 * it takes {@link #drawPerAtmosTick()} FE from its own store and calls {@link #supply(Object)}.
 * Each supply powers the device for {@link #LEASE_TICKS} game ticks from the call, which covers
 * one atmos tick with room to spare, so the device and its provider needn't step in any order.
 * Nothing needs to switch a device off: when no provider supplies it any more, because the
 * provider ran short, was removed or no longer covers it, its lease runs out and it stops.
 *
 * <p><b>Without a provider</b> a device has no power, even beside an FE source (CONT-069). There
 * is no FE capability on devices.
 *
 * <p><b>For tests,</b> {@link #setTestPowered(boolean)} powers a device with no provider. It is
 * saved with the device as {@code TestPowered}, so a test world can set it with {@code /data merge
 * block}; it is not a gameplay feature, and nothing in survival sets it.
 *
 * <p>Server thread only.
 */
public final class DevicePower {
    /** How long one {@link #supply} powers a device: two atmos ticks, in game ticks. */
    public static final int LEASE_TICKS = 2 * AtmosTicks.PERIOD;

    private static final String TEST_POWERED = "TestPowered";

    private final PowerPriority priority;
    private final IntSupplier draw;
    private final LongSupplier clock;
    private long poweredUntil = Long.MIN_VALUE;
    private Object source;
    private boolean testPowered;

    /**
     * @param priority who the device is powered before or after when power runs short
     * @param draw what the device draws now, in FE/t; 0 while it is off
     * @param clock the current game tick, as {@link AtmosTicks#now}
     */
    public DevicePower(PowerPriority priority, IntSupplier draw, LongSupplier clock) {
        this.priority = Objects.requireNonNull(priority, "priority");
        this.draw = Objects.requireNonNull(draw, "draw");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The device's priority group (CONT-064). */
    public PowerPriority priority() {
        return priority;
    }

    /** What the device draws now, in FE/t (CONT-065); 0 while it is off. Never negative. */
    public int draw() {
        return Math.max(0, draw.getAsInt());
    }

    /** What one atmos tick of power costs a provider now: {@link #draw()} × {@link AtmosTicks#PERIOD} FE. */
    public long drawPerAtmosTick() {
        return (long) draw() * AtmosTicks.PERIOD;
    }

    /**
     * Powers the device for the next {@link #LEASE_TICKS} game ticks. The provider has already
     * taken {@link #drawPerAtmosTick()} FE from its store.
     *
     * @param provider who powers it, for {@link #source()}
     */
    public void supply(Object provider) {
        source = Objects.requireNonNull(provider, "provider");
        poweredUntil = clock.getAsLong() + LEASE_TICKS;
    }

    /** Whether the device has power now: a supply's lease hasn't run out, or it is test-powered. */
    public boolean isPowered() {
        return testPowered || clock.getAsLong() < poweredUntil;
    }

    /**
     * Whether a provider has supplied the device within the last atmos tick, so that a provider
     * covering it a second time, through its zone and a link, needn't pay twice.
     */
    public boolean suppliedThisAtmosTick() {
        return poweredUntil != Long.MIN_VALUE && clock.getAsLong() < poweredUntil - LEASE_TICKS + AtmosTicks.PERIOD;
    }

    /** Who supplied the device last, while its lease lasts; null otherwise, and when test-powered alone. */
    public Object source() {
        return clock.getAsLong() < poweredUntil ? source : null;
    }

    /** Powers the device with no provider, for GameTests and test worlds only. */
    public void setTestPowered(boolean on) {
        testPowered = on;
    }

    /** Whether the device is test-powered. */
    public boolean testPowered() {
        return testPowered;
    }

    /** Saves what outlasts a reload: only the test flag. A lease never does. */
    public void save(CompoundTag tag) {
        if (testPowered) {
            tag.putBoolean(TEST_POWERED, true);
        }
    }

    /** Loads what {@link #save} wrote. */
    public void load(CompoundTag tag) {
        testPowered = tag.getBoolean(TEST_POWERED);
    }
}
