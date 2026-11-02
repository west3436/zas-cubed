package io.github.west3436.zas.atmos.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.atmos.device.AtmosTicks;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** The power contract (CONT-060, CONT-065, CONT-069). */
class DevicePowerTest {
    @Test
    void cont_069_a_device_has_no_power_until_supplied() {
        long[] clock = {1000};
        int[] draw = {10};
        DevicePower p = new DevicePower(PowerPriority.LIFE_SUPPORT, () -> draw[0], () -> clock[0]);
        assertFalse(p.isPowered());
        assertNull(p.source());
        assertFalse(p.suppliedThisAtmosTick());

        Object provider = new Object();
        p.supply(provider);
        assertTrue(p.isPowered());
        assertSame(provider, p.source());
        assertTrue(p.suppliedThisAtmosTick());

        clock[0] += AtmosTicks.PERIOD;
        assertTrue(p.isPowered(), "one atmos tick later");
        assertFalse(p.suppliedThisAtmosTick());
        clock[0] += DevicePower.LEASE_TICKS - AtmosTicks.PERIOD - 1;
        assertTrue(p.isPowered(), "the lease's last tick");
        clock[0]++;
        assertFalse(p.isPowered(), "after the lease");
        assertNull(p.source());
    }

    @Test
    void cont_065_draw_is_per_game_tick_and_never_negative() {
        int[] draw = {10};
        DevicePower p = new DevicePower(PowerPriority.LIFE_SUPPORT, () -> draw[0], () -> 0L);
        assertEquals(10, p.draw());
        assertEquals(10L * AtmosTicks.PERIOD, p.drawPerAtmosTick());
        draw[0] = -5;
        assertEquals(0, p.draw());
        assertEquals(PowerPriority.LIFE_SUPPORT, p.priority());
    }

    @Test
    void cont_069_test_power_needs_no_provider_and_is_saved() {
        DevicePower p = new DevicePower(PowerPriority.AUXILIARY, () -> 1, () -> 0L);
        p.setTestPowered(true);
        assertTrue(p.isPowered());
        assertNull(p.source());
        CompoundTag tag = new CompoundTag();
        p.save(tag);
        DevicePower q = new DevicePower(PowerPriority.AUXILIARY, () -> 1, () -> 0L);
        q.load(tag);
        assertTrue(q.isPowered());
        DevicePower r = new DevicePower(PowerPriority.AUXILIARY, () -> 1, () -> 0L);
        r.load(new CompoundTag());
        assertFalse(r.isPowered());
    }

    @Test
    void cont_064_priorities_sort_life_support_first() {
        assertTrue(PowerPriority.LIFE_SUPPORT.compareTo(PowerPriority.GAS_HANDLING) < 0);
        assertTrue(PowerPriority.GAS_HANDLING.compareTo(PowerPriority.AUXILIARY) < 0);
    }
}
