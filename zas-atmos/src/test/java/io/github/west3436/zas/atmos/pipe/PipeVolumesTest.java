package io.github.west3436.zas.atmos.pipe;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.west3436.zas.atmos.vent.VentFlow;
import org.junit.jupiter.api.Test;

/** The volumes of network parts (CONT-007). */
class PipeVolumesTest {
    @Test
    void cont_007_each_part_has_its_volume() {
        assertEquals(70, PipeVolumes.PIPE);
        assertEquals(200, PipeVolumes.PORT);
        assertEquals(400, PipeVolumes.VENT);
        assertEquals(1_000, PipeVolumes.CANISTER);
        // The vent's rate is half its own buffer's moles (CONT-078).
        assertEquals(PipeVolumes.VENT, VentFlow.BUFFER);
    }
}
