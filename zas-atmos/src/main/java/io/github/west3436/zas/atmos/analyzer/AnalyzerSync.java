package io.github.west3436.zas.atmos.analyzer;

import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * What an analyzer's window shows (CONT-034), sent by the server to the player with it open, and only
 * to that player (DATA-005). It holds no zone geometry: a zone's id and its mixture, as {@code /zas
 * at} gives them. The menu sends it at most every {@link AnalyzerMenu#SYNC_TICKS} game ticks, and only
 * when something it shows has changed at the resolution the window shows it ({@link #showsSameAs}).
 *
 * @param containerId the menu it is for
 * @param kind what the window is reading now: the target, or {@link Kind#TANK} for the other hand's
 *     tank
 * @param target the target's name, as its tab and its row show it
 * @param tank the name of the tank in the other hand, if it holds one, for its tab
 * @param state the air's state where the reading is of air, else null
 * @param zone the zone's id where the air is a zone's, else -1
 * @param local whether the air read is another mod's for the place, outside zones (SPACE-017), rather
 *     than the atmosphere's, whose state is then {@link AtmosphereState#AMBIENT}
 * @param gas the gas read
 */
public record AnalyzerSync(
        int containerId,
        Kind kind,
        Component target,
        Optional<Component> tank,
        @Nullable AtmosphereState state,
        long zone,
        boolean local,
        GasMixture gas)
        implements CustomPacketPayload {

    /** What an analyzer reads. */
    public enum Kind {
        /** The air at the player's eyes. */
        HERE,
        /** The air at a point in front of a block's face. */
        POINT,
        /** A pipe network. */
        NETWORK,
        /** A canister's or a large gas tank's own gas. */
        CANISTER,
        /** The tank in the hand without the analyzer. */
        TANK,
        /** Another mod's store of gas: a block read through the gas handler it provides (API-021). */
        STORE;

        /** Whether it reads air, which has a state and maybe a zone. */
        public boolean isAir() {
            return this == HERE || this == POINT;
        }
    }

    public static final Type<AnalyzerSync> TYPE = new Type<>(ZasAtmosMod.id("analyzer_sync"));

    private static final int MOST_GASES = 256;

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, Double>> MOLES =
            ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.DOUBLE, MOST_GASES);

    public static final StreamCodec<RegistryFriendlyByteBuf, AnalyzerSync> CODEC =
            StreamCodec.of((buf, s) -> s.write(buf), AnalyzerSync::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(kind.ordinal());
        ComponentSerialization.STREAM_CODEC.encode(buf, target);
        ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, tank);
        buf.writeVarInt(state == null ? -1 : state.ordinal());
        buf.writeLong(zone);
        buf.writeBoolean(local);
        MOLES.encode(buf, new HashMap<>(gas.moles()));
        buf.writeDouble(gas.temperature());
        buf.writeDouble(gas.volume());
    }

    private static AnalyzerSync read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        Kind kind = byOrdinal(Kind.values(), buf.readVarInt());
        Component target = ComponentSerialization.STREAM_CODEC.decode(buf);
        Optional<Component> tank = ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf);
        int state = buf.readVarInt();
        long zone = buf.readLong();
        boolean local = buf.readBoolean();
        Map<ResourceLocation, Double> moles = MOLES.decode(buf);
        double temperature = buf.readDouble();
        double volume = buf.readDouble();
        return new AnalyzerSync(
                containerId,
                kind == null ? Kind.HERE : kind,
                target,
                tank,
                byOrdinal(AtmosphereState.values(), state),
                zone,
                local,
                new GasMixture(moles, temperature, volume));
    }

    private static <E> @Nullable E byOrdinal(E[] values, int ordinal) {
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Whether the window is showing the tank's tab. */
    public boolean showsTank() {
        return kind == Kind.TANK;
    }

    /**
     * Whether this shows the same as another, at the resolution the window shows it in any of the
     * player's units: what it reads, the zone, whose air it is, pressure to 4 significant figures,
     * temperature to 0.05 K (°F's 0.1° is 0.056 K) and each gas's share to 3 significant figures. The
     * server doesn't know the player's units, so this is as fine as the finest of them.
     */
    public boolean showsSameAs(AnalyzerSync other) {
        return kind == other.kind
                && target.equals(other.target)
                && tank.equals(other.tank)
                && state == other.state
                && zone == other.zone
                && local == other.local
                && gas.isEmpty() == other.gas.isEmpty()
                && significant(gas.pressure(), 4) == significant(other.gas.pressure(), 4)
                && Math.round(gas.temperature() * 20) == Math.round(other.gas.temperature() * 20)
                && shares(gas).equals(shares(other.gas));
    }

    private static Map<ResourceLocation, Double> shares(GasMixture gas) {
        Map<ResourceLocation, Double> out = new TreeMap<>();
        gas.composition().forEach((id, share) -> out.put(id, significant(share, 3)));
        return out;
    }

    private static double significant(double value, int digits) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return new BigDecimal(value).round(new MathContext(digits)).doubleValue();
    }

    /** On the client: hands the reading to the open menu it is for. */
    public static void handle(AnalyzerSync sync, IPayloadContext context) {
        if (context.player().containerMenu instanceof AnalyzerMenu menu && menu.containerId == sync.containerId()) {
            menu.receive(sync);
        }
    }
}
