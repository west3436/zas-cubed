package io.github.west3436.zas.atmos.particle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** The payload on the wire (CLIENT-018, CLIENT-019). */
class GasJetTest {
    /** A jet's payload, as this test measures it. */
    static final int PAYLOAD_BYTES = 41;

    @Test
    void client_018_a_jet_round_trips_in_41_bytes() {
        for (GasJetKind kind : GasJetKind.values()) {
            GasJet jet = new GasJet(kind, 1.5, -60.25, 3.0e6, 0.6f, 0, -0.8f, 12.5f);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            GasJet.CODEC.encode(buf, jet);
            assertEquals(PAYLOAD_BYTES, buf.readableBytes(), "its size");
            assertEquals(jet, GasJet.CODEC.decode(buf));
            assertEquals(0, buf.readableBytes(), "bytes left over");
        }
    }
}
