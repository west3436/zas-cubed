package io.github.west3436.zas.atmos.device;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/** The client's table of how hard nearby devices are working (DATA-018). */
class ClientDeviceActivityTest {
    private static final BlockPos A = new BlockPos(1, 2, 3);
    private static final BlockPos B = new BlockPos(-4, 70, 900);

    private static DeviceActivityPayload told(BlockPos pos, int level) {
        return new DeviceActivityPayload(List.of(new DeviceActivityPayload.Change(pos.asLong(), level)));
    }

    @Test
    void data_018_a_device_nobody_mentioned_is_idle() {
        assertEquals(DeviceActivity.IDLE, new ClientDeviceActivity().level(A));
    }

    @Test
    void data_018_the_table_holds_what_it_is_told_until_told_otherwise() {
        ClientDeviceActivity table = new ClientDeviceActivity();
        table.accept(new DeviceActivityPayload(List.of(
                new DeviceActivityPayload.Change(A.asLong(), DeviceActivity.HARD),
                new DeviceActivityPayload.Change(B.asLong(), DeviceActivity.TRICKLE))));
        assertEquals(DeviceActivity.HARD, table.level(A));
        assertEquals(DeviceActivity.TRICKLE, table.level(B));
        assertEquals(2, table.size());
        table.accept(told(A, DeviceActivity.WORKING));
        assertEquals(DeviceActivity.WORKING, table.level(A));
        assertEquals(DeviceActivity.TRICKLE, table.level(B), "one not mentioned stays");
    }

    @Test
    void data_018_idle_forgets_a_device() {
        ClientDeviceActivity table = new ClientDeviceActivity();
        table.accept(told(A, DeviceActivity.HARD));
        table.accept(told(A, DeviceActivity.IDLE));
        assertEquals(DeviceActivity.IDLE, table.level(A));
        assertEquals(0, table.size());
    }

    @Test
    void data_018_for_each_visits_every_device_listed() {
        ClientDeviceActivity table = new ClientDeviceActivity();
        table.accept(told(A, DeviceActivity.HARD));
        table.accept(told(B, DeviceActivity.TRICKLE));
        Map<Long, Integer> seen = new HashMap<>();
        table.forEach(seen::put);
        assertEquals(Map.of(A.asLong(), DeviceActivity.HARD, B.asLong(), DeviceActivity.TRICKLE), seen);
    }

    /** A new level on the client: the server has forgotten what it told it, so the table must too. */
    @Test
    void data_018_a_new_level_empties_the_table_and_the_same_one_doesnt() {
        ClientDeviceActivity table = new ClientDeviceActivity();
        Object first = new Object();
        table.enter(first);
        table.accept(told(A, DeviceActivity.HARD));
        table.enter(first);
        assertEquals(DeviceActivity.HARD, table.level(A), "the same level");
        table.enter(new Object());
        assertEquals(DeviceActivity.IDLE, table.level(A), "a new level");
        table.accept(told(A, DeviceActivity.HARD));
        table.enter(null);
        assertEquals(0, table.size(), "no level");
    }

    @Test
    void data_018_clear_forgets_everything() {
        ClientDeviceActivity table = new ClientDeviceActivity();
        Object level = new Object();
        table.enter(level);
        table.accept(told(A, DeviceActivity.HARD));
        table.clear();
        assertEquals(0, table.size());
        table.accept(told(A, DeviceActivity.HARD));
        table.enter(level);
        assertEquals(0, table.size(), "clear forgot the level too, so entering it counts as new");
    }
}
