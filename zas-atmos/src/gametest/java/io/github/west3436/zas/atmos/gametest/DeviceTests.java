package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.power.DevicePower;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The device base and the power contract, with the GameTest-only {@link TestDevice}: devices step
 * once per atmos tick on the server thread, spread across game ticks, only while loaded (CONT-006);
 * and a device has power only while a provider supplies it (CONT-069). These run in real game
 * ticks, since the schedule is driven by the server tick.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeviceTests {
    private DeviceTests() {}

    private static TestDevice.Entity place(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, TestDevice.block());
        return h.getBlockEntity(pos);
    }

    /**
     * CONT-006: each device steps every 20 game ticks, on the server thread, in its own phase. In a batch
     * of its own, because a test that steps a level's devices itself ({@link Area#step}) steps these too.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "zas_atmos_device_schedule")
    public static void cont_006_devices_step_once_per_atmos_tick(GameTestHelper h) {
        List<BlockPos> at = List.of(new BlockPos(0, 1, 0), new BlockPos(0, 2, 0), new BlockPos(0, 3, 0));
        List<TestDevice.Entity> devices = at.stream().map(p -> place(h, p)).toList();
        h.runAfterDelay(110, () -> {
            Thread server = h.getLevel().getServer().getRunningThread();
            Set<Long> phases = new TreeSet<>();
            for (TestDevice.Entity d : devices) {
                h.assertTrue(d.steps.size() >= 5 && d.steps.size() <= 6, "steps in 110 ticks: " + d.steps);
                for (int i = 1; i < d.steps.size(); i++) {
                    h.assertValueEqual(
                            d.steps.get(i) - d.steps.get(i - 1), (long) AtmosTicks.PERIOD, "ticks between steps");
                }
                for (Thread t : d.threads) {
                    h.assertTrue(t == server, "stepped on " + t.getName() + ", not the server thread");
                }
                phases.add(d.steps.get(0) % AtmosTicks.PERIOD);
                h.assertTrue(DeviceTicker.loaded(h.getLevel()).contains(d), "not in the level's schedule");
            }
            h.assertValueEqual(phases.size(), devices.size(), "devices placed together share a phase");
            h.setBlock(at.get(2), Blocks.AIR);
        });
        h.runAfterDelay(111, () -> {
            TestDevice.Entity removed = devices.get(2);
            int before = removed.steps.size();
            h.assertFalse(DeviceTicker.isScheduled(removed), "a removed device is still scheduled");
            h.runAfterDelay(40, () -> {
                h.assertValueEqual(removed.steps.size(), before, "a removed device's steps");
                h.succeed();
            });
        });
    }

    /** CONT-069: with no provider a device has no power; a supply powers it for its lease only. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cont_069_device_without_a_provider_has_no_power(GameTestHelper h) {
        TestDevice.Entity d = place(h, new BlockPos(0, 1, 0));
        DevicePower power = d.power();
        h.assertFalse(power.isPowered(), "powered with no provider");
        h.assertValueEqual(power.drawPerAtmosTick(), 10L * AtmosTicks.PERIOD, "FE per atmos tick");
        Object provider = "test provider";
        power.supply(provider);
        h.assertTrue(power.isPowered(), "unpowered after a supply");
        h.assertTrue(power.source() == provider, "the source is the provider");
        h.assertTrue(power.suppliedThisAtmosTick(), "a supply this atmos tick isn't seen");
        h.runAfterDelay(DevicePower.LEASE_TICKS - 1, () -> {
            h.assertTrue(power.isPowered(), "the lease ran out early");
            h.assertFalse(power.suppliedThisAtmosTick(), "a supply two atmos ticks ago counts as this one's");
        });
        h.runAfterDelay(DevicePower.LEASE_TICKS + 1, () -> {
            h.assertFalse(power.isPowered(), "powered after the lease ran out");
            h.assertTrue(power.source() == null, "a source after the lease ran out");
            h.assertFalse(d.poweredSteps.isEmpty(), "never stepped while powered");

            power.setTestPowered(true);
            h.assertTrue(power.isPowered(), "test power doesn't power it");
            CompoundTag saved = d.saveWithoutMetadata(h.getLevel().registryAccess());
            h.assertTrue(saved.getBoolean("TestPowered"), "test power isn't saved");
            power.setTestPowered(false);
            power.load(saved);
            h.assertTrue(power.testPowered(), "test power isn't loaded");
            h.succeed();
        });
    }
}
