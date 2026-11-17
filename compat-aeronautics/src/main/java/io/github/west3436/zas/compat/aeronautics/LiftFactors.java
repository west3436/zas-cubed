package io.github.west3436.zas.compat.aeronautics;

import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.gas.ServerGasRegistry;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Each dimension's lift factor, ρ / ρ₀, on the server and on clients (COMPAT-080 to COMPAT-083), and
 * what the hook into Sable multiplies its air pressure by ({@link #of}).
 *
 * <p><b>Server.</b> At the end of every server tick, after ZAS³'s atmos scheduler has taken this
 * tick's provider and seed answers, {@link #serverTicked} looks at each dimension's ambient as
 * ZAS³ has it now, in the preset's variant. Where the ambient, the setting or the server's gases
 * changed, it works the factor out again. A changed factor applies to the server's Sable from that
 * moment, and is sent to every client in the same tick (COMPAT-081). The factor is:
 *
 * <ul>
 *   <li>1 with {@code [lift] follow_ambient} off (COMPAT-083);
 *   <li>1 where Sable's own {@code dimension_physics} seeded the ambient ({@link SableSeed}), since
 *       Sable's pressure already holds it (COMPAT-082);
 *   <li>otherwise the ambient's density over standard air's at 293.15 K ({@link LiftFactor}).
 * </ul>
 *
 * <p>It is the dimension's ambient wherever the craft is, inside a sealed room too: COMPAT-080 is
 * about the open air, and Sable asks for its pressure many times a tick, so the hook reads one
 * number per dimension rather than the atmosphere at a point.
 *
 * <p><b>Client.</b> A client holds the table the server last sent ({@link LiftFactorsPayload}),
 * which replaces the one before; it is cleared when the client leaves a server. A client's own
 * levels read that table, so in single player the client and the integrated server each read their
 * own.
 */
public final class LiftFactors {
    /** The server's factors, by dimension. Replaced whole, so the hook reads it without a lock. */
    private static volatile Map<ResourceKey<Level>, Double> server = Map.of();

    /** The client's factors, by dimension id, as the server last sent them. */
    private static volatile Map<ResourceLocation, Double> client = Map.of();

    /** What each dimension's factor was last worked out from. Server thread. */
    private static final Map<ResourceKey<Level>, Inputs> INPUTS = new HashMap<>();

    /** The table last sent to clients, and when. */
    @Nullable
    private static volatile Sent lastSent;

    private LiftFactors() {}

    /** What one dimension's factor is worked out from. */
    private record Inputs(Ambient ambient, boolean sableSeeded, boolean followAmbient, GasRegistry gases) {
        boolean same(Ambient a, boolean seeded, boolean follow, GasRegistry g) {
            return sableSeeded == seeded && followAmbient == follow && gases == g && ambient.equals(a);
        }
    }

    /**
     * A table sent to clients.
     *
     * @param gameTime the server's game time when it was sent
     * @param payload what was sent
     */
    public record Sent(long gameTime, LiftFactorsPayload payload) {}

    /**
     * What Sable's air pressure in a level is multiplied by: the server's factor for a server level,
     * the one the server last sent for a client level. Any thread.
     *
     * @param level the level Sable asks about
     * @return the factor, 1 where none is known
     */
    public static double of(Level level) {
        Double factor = level.isClientSide() ? client.get(level.dimension().location()) : server.get(level.dimension());
        return factor == null ? LiftFactor.NONE : factor;
    }

    /**
     * The server's factor for a dimension. Any thread.
     *
     * @param dimension the dimension
     * @return the factor, 1 where none is known
     */
    public static double server(ResourceKey<Level> dimension) {
        return server.getOrDefault(dimension, LiftFactor.NONE);
    }

    /**
     * The factor a client holds for a dimension. Any thread.
     *
     * @param dimension the dimension's id
     * @return the factor, 1 where none is known
     */
    public static double client(ResourceLocation dimension) {
        return client.getOrDefault(dimension, LiftFactor.NONE);
    }

    /**
     * The table last sent to clients.
     *
     * @return it and when, or null if none has been sent since the server started
     */
    @Nullable
    public static Sent lastSent() {
        return lastSent;
    }

    /**
     * Works out what changed this tick and sends it. Server thread, at the end of each server tick.
     *
     * @param server the server
     */
    static void serverTicked(MinecraftServer server) {
        boolean follow = AeroSettings.followAmbient();
        GasRegistry gases = ServerGasRegistry.current();
        Map<ResourceKey<Level>, Double> next = null;
        for (ServerLevel level : server.getAllLevels()) {
            ResourceKey<Level> dimension = level.dimension();
            Ambient ambient = Ambients.ambient(dimension);
            boolean seeded =
                    Ambients.seedOf(dimension).filter(SableSeed.ID::equals).isPresent();
            Inputs last = INPUTS.get(dimension);
            if (last != null && last.same(ambient, seeded, follow, gases)) {
                continue;
            }
            INPUTS.put(dimension, new Inputs(ambient, seeded, follow, gases));
            double factor = !follow || seeded ? LiftFactor.NONE : factor(ambient, gases);
            Double before = LiftFactors.server.get(dimension);
            if (before == null || before != factor) {
                if (next == null) {
                    next = new HashMap<>(LiftFactors.server);
                }
                next.put(dimension, factor);
            }
        }
        if (next != null) {
            LiftFactors.server = Map.copyOf(next);
            LiftFactorsPayload payload = payload();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                send(player, payload);
            }
            lastSent = new Sent(server.overworld().getGameTime(), payload);
        }
    }

    /** An ambient's factor against standard air, with the server's gases' molar masses. */
    static double factor(Ambient ambient, GasRegistry gases) {
        return LiftFactor.factor(
                ambient.partialPressures(),
                ambient.temperature(),
                Ambient.STANDARD_AIR.partialPressures(),
                Ambient.STANDARD_AIR.temperature(),
                gas -> gases.find(gas).map(GasDefinition::molarMass).orElse(Double.NaN));
    }

    /** The server's whole table, as clients are sent it. */
    static LiftFactorsPayload payload() {
        Map<ResourceLocation, Double> byId = new HashMap<>();
        server.forEach((dimension, factor) -> byId.put(dimension.location(), factor));
        return new LiftFactorsPayload(byId);
    }

    /** A player has joined: it is sent the whole table. Server thread. */
    static void joined(ServerPlayer player) {
        send(player, payload());
    }

    /** Sends a table to a client that knows the payload; one whose game lacks the adapter is skipped. */
    private static void send(ServerPlayer player, LiftFactorsPayload payload) {
        if (player.connection.hasChannel(LiftFactorsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * A client was sent the server's table: it replaces the one the client held.
     *
     * @param payload the table
     * @param context the payload's context, unused
     */
    static void received(LiftFactorsPayload payload, @Nullable IPayloadContext context) {
        client = payload.factors();
    }

    /**
     * Takes a table as a client would on receiving it: for GameTests, which run with no client.
     *
     * @param payload the table
     */
    public static void receiveForTest(LiftFactorsPayload payload) {
        received(payload, null);
    }

    /** The client has left a server: what it was sent no longer applies. */
    static void clientLeft() {
        client = Map.of();
    }

    /** The server has stopped: nothing is kept for the next one. */
    static void serverStopped() {
        server = Map.of();
        INPUTS.clear();
        lastSent = null;
    }
}
