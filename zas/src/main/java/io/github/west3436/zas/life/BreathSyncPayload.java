package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The local player's breathing state, from the server to that player only (LIFE-023, DATA-005):
 * the verdict of its last breath check without the breath gas, and how fast its air drains. The
 * client's breathing hook drains the air bar with it as the server does, so the bar doesn't jitter.
 *
 * @param verdict the server's verdict, or null before its first check
 * @param drain the fraction of the vanilla underwater rate at which the air drains, 0 to 1
 *     (LIFE-003); 0 unless the verdict is {@link BreathReason#LOW_OXYGEN}
 */
public record BreathSyncPayload(@Nullable Breathability verdict, double drain) implements CustomPacketPayload {
    public static final Type<BreathSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "breath_sync"));

    /** No verdict yet: the client leaves its air bar to vanilla. */
    public static final BreathSyncPayload NONE = new BreathSyncPayload(null, 0);

    public static final StreamCodec<ByteBuf, BreathSyncPayload> STREAM_CODEC =
            StreamCodec.of(BreathSyncPayload::write, BreathSyncPayload::read);

    /** The state to sync for a verdict and its drain: the verdict without its breath gas. */
    public static BreathSyncPayload of(@Nullable Breathability verdict, double drain) {
        if (verdict == null) {
            return NONE;
        }
        Breathability.Builder b = Breathability.builder(verdict.reason(), verdict.source());
        verdict.hazards().forEach(b::hazard);
        verdict.protection().forEach(b::protection);
        return new BreathSyncPayload(b.build(), verdict.reason() == BreathReason.LOW_OXYGEN ? drain : 0);
    }

    /** Whether the server finds the player short of O2, so its air drains (LIFE-003). */
    public boolean hypoxic() {
        return verdict != null && verdict.reason() == BreathReason.LOW_OXYGEN;
    }

    private static void write(ByteBuf buf, BreathSyncPayload p) {
        ByteBufCodecs.BOOL.encode(buf, p.verdict != null);
        if (p.verdict == null) {
            return;
        }
        ByteBufCodecs.VAR_INT.encode(buf, p.verdict.reason().ordinal());
        ByteBufCodecs.VAR_INT.encode(buf, p.verdict.source().ordinal());
        int hazards = 0;
        for (Hazard h : p.verdict.hazards()) {
            hazards |= 1 << h.ordinal();
        }
        int protection = 0;
        for (Protection pr : p.verdict.protection()) {
            protection |= 1 << pr.ordinal();
        }
        ByteBufCodecs.VAR_INT.encode(buf, hazards);
        ByteBufCodecs.VAR_INT.encode(buf, protection);
        ByteBufCodecs.DOUBLE.encode(buf, p.drain);
    }

    private static BreathSyncPayload read(ByteBuf buf) {
        if (!ByteBufCodecs.BOOL.decode(buf)) {
            return NONE;
        }
        BreathReason reason = BreathReason.values()[ByteBufCodecs.VAR_INT.decode(buf)];
        BreathSource source = BreathSource.values()[ByteBufCodecs.VAR_INT.decode(buf)];
        int hazards = ByteBufCodecs.VAR_INT.decode(buf);
        int protection = ByteBufCodecs.VAR_INT.decode(buf);
        Breathability.Builder b = Breathability.builder(reason, source);
        for (Hazard h : Hazard.values()) {
            if ((hazards & (1 << h.ordinal())) != 0) {
                b.hazard(h);
            }
        }
        for (Protection pr : Protection.values()) {
            if ((protection & (1 << pr.ordinal())) != 0) {
                b.protection(pr);
            }
        }
        double drain = ByteBufCodecs.DOUBLE.decode(buf);
        return new BreathSyncPayload(b.build(), drain);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
