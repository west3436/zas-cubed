package io.github.west3436.zas.s10;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.zip.Deflater;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.neoforged.neoforge.network.connection.ConnectionType;

/**
 * A clientbound play packet's size on the wire, as the server's pipeline would write it: the
 * packet's id and body through the play protocol's own codec, then compressed as the connection
 * compresses (zlib at the default level for packets of at least the threshold, a 0 length before
 * smaller ones), then framed with its length. It leaves out what's under Minecraft's frames: TCP
 * and IP headers. Encryption, which is AES/CFB8, adds no bytes.
 */
final class WireSize {
    private final StreamCodec<ByteBuf, Packet<? super ClientGamePacketListener>> codec;
    private final int threshold;
    private final Deflater deflater = new Deflater();
    private final byte[] out = new byte[1 << 16];

    WireSize(RegistryAccess registries, int compressionThreshold) {
        this.codec = GameProtocols.CLIENTBOUND_TEMPLATE
                .bind(RegistryFriendlyByteBuf.decorator(registries, ConnectionType.NEOFORGE))
                .codec();
        this.threshold = compressionThreshold;
    }

    /**
     * The bytes a packet takes on the wire.
     *
     * @throws RuntimeException if the codec can't encode it
     */
    @SuppressWarnings("unchecked")
    int of(Packet<?> packet) {
        ByteBuf buf = Unpooled.buffer();
        try {
            codec.encode(buf, (Packet<? super ClientGamePacketListener>) packet);
            int n = buf.readableBytes();
            int body;
            if (threshold < 0) {
                body = n;
            } else if (n < threshold) {
                body = 1 + n;
            } else {
                byte[] raw = new byte[n];
                buf.getBytes(buf.readerIndex(), raw);
                body = varIntSize(n) + deflated(raw);
            }
            return varIntSize(body) + body;
        } finally {
            buf.release();
        }
    }

    private int deflated(byte[] raw) {
        deflater.reset();
        deflater.setInput(raw);
        deflater.finish();
        int total = 0;
        while (!deflater.finished()) {
            total += deflater.deflate(out);
        }
        return total;
    }

    static int varIntSize(int v) {
        int n = 1;
        while ((v & ~0x7F) != 0) {
            v >>>= 7;
            n++;
        }
        return n;
    }
}
