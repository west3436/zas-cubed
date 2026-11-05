package io.github.west3436.zas.airflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.west3436.zas.engine.flow.FlowField;
import io.github.west3436.zas.engine.flow.FlowSettings;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The payload's format and its size (DATA-015), which DATA-015's note quotes. */
class AirflowEffectsPayloadTest {
    private static ByteBuf encode(List<VisibleFlow> flows) {
        ByteBuf buf = Unpooled.buffer();
        AirflowEffectsPayload.STREAM_CODEC.encode(buf, new AirflowEffectsPayload(flows));
        return buf;
    }

    private static int bytes(List<VisibleFlow> flows) {
        return encode(flows).readableBytes();
    }

    private static List<VisibleOpening> grid(int x, int y, int z, int w, int h) {
        List<VisibleOpening> out = new ArrayList<>();
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                out.add(new VisibleOpening(x, y + j, z + i, 4));
            }
        }
        return out;
    }

    @Test
    void data_015_payload_round_trips() {
        List<VisibleFlow> flows = List.of(
                new VisibleFlow(101, 38, List.of(new VisibleOpening(-29_999_000, 300, 29_999_000, 4))),
                new VisibleFlow(
                        1500,
                        0,
                        List.of(
                                new VisibleOpening(-29_999_033, 267, 29_999_033, 0),
                                new VisibleOpening(-29_998_967, 333, 29_998_967, VisibleOpening.NO_FACE))));
        ByteBuf buf = encode(flows);
        assertEquals(flows, AirflowEffectsPayload.STREAM_CODEC.decode(buf).flows());
        assertEquals(0, buf.readableBytes());

        ByteBuf empty = encode(List.of());
        assertEquals(List.of(), AirflowEffectsPayload.STREAM_CODEC.decode(empty).flows());
        assertEquals(0, empty.readableBytes());
    }

    @Test
    void data_015_measured_bytes() {
        // Nothing: the end of airflow.
        assertEquals(1, bytes(List.of()));
        // One hole in a wall: count, origin, kPa, flow, opening count, one opening.
        assertEquals(1 + 8 + 1 + 1 + 1 + 4, bytes(List.of(new VisibleFlow(101, 38, grid(100, 64, 100, 1, 1)))));
        // A 3 by 3 hole.
        assertEquals(1 + 8 + 1 + 1 + 1 + 9 * 4, bytes(List.of(new VisibleFlow(101, 38, grid(100, 64, 100, 3, 3)))));
        // A difference over 127 kPa takes one byte more.
        assertEquals(1 + 8 + 2 + 1 + 1 + 4, bytes(List.of(new VisibleFlow(500, 38, grid(100, 64, 100, 1, 1)))));
        // The flow's step takes a byte up to well over any real flow.
        assertTrue(VisibleFlow.step(30_000) <= 127);
        // The largest list: 32 edges of 8 openings, 256 in all, within 33 blocks of the first.
        List<VisibleFlow> largest = new ArrayList<>();
        for (int i = 0; i < VisibleFlows.MAX_FLOWS; i++) {
            largest.add(new VisibleFlow(500, 38, grid(100 + i, 64, 100, 2, 4)));
        }
        int openings = largest.stream().mapToInt(f -> f.openings().size()).sum();
        assertEquals(VisibleFlows.MAX_OPENINGS, openings);
        int size = bytes(largest);
        assertEquals(1 + 8 + 32 * (2 + 1 + 1) + 256 * 4, size);
        assertTrue(size < 1200, "the largest list is " + size + " bytes");

        // For comparison, what the push's payload (FLOW-017) takes for one hole, every atmos tick.
        ByteBuf push = Unpooled.buffer();
        AirflowSyncPayload.STREAM_CODEC.encode(
                push,
                new AirflowSyncPayload(
                        ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"),
                        FlowSettings.DEFAULT,
                        65,
                        0,
                        60,
                        List.of(new FlowField.Opening(0, 64, 0, -1, 64, 0, 101, 0, 0))));
        assertEquals(89, push.readableBytes());
    }
}
