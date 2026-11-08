package io.github.west3436.zas.s10;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.EventHooks;

/**
 * S-10's 8 players: real {@link ServerPlayer}s on the server's player list and in the station's
 * level, so chunks load and track around them and every sync treats them as clients with the mods.
 * Each has a {@link Counting} connection instead of a network one. Nothing hurts them.
 *
 * <p>A player's ticks are what its connection's tick would run ({@code doTick}), without the
 * checks that answer a client's packets, since none come. The server sends it chunks as it would a
 * client's, and each batch is acknowledged at the next tick, as a client that keeps up does.
 */
final class BenchPlayers {
    /** The bytes counted as atmos sync: the mods' payloads and sounds. */
    static final String ATMOS = "atmos ";

    final List<ServerPlayer> players = new ArrayList<>();
    private final MinecraftServer server;
    private final WireSize wire;
    private final int delimiterBytes;
    /** Packets that failed to encode, by type, so the report can say. */
    final Map<String, Integer> encodeFailures = new java.util.TreeMap<>();

    BenchPlayers(MinecraftServer server) {
        this.server = server;
        this.wire = new WireSize(server.registryAccess(), server.getCompressionThreshold());
        // A delimiter has a one-byte id and no body, so it is 3 bytes framed with compression on and 2 without.
        // The protocol's codec only encodes its own instance, which it doesn't share.
        this.delimiterBytes = server.getCompressionThreshold() >= 0 ? 3 : 2;
    }

    /** A player at a point of a level, joined as a client joins. */
    ServerPlayer join(ServerLevel level, int number, double x, double y, double z) {
        String name = "s10_p" + number;
        GameProfile profile =
                new GameProfile(UUID.nameUUIDFromBytes(("zas_s10:" + name).getBytes(StandardCharsets.UTF_8)), name);
        ServerPlayer player = new ServerPlayer(server, level, profile, ClientInformation.createDefault()) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return true;
            }
        };
        new Counting(server, player);
        player.setNoGravity(true);
        player.moveTo(x, y, z, 0f, 0f);
        player.setGameMode(GameType.SURVIVAL);
        PlayerList list = server.getPlayerList();
        BenchPlayers.<List<ServerPlayer>>field(list, "players").add(player);
        BenchPlayers.<Map<UUID, ServerPlayer>>field(list, "playersByUUID").put(player.getUUID(), player);
        level.addNewPlayer(player);
        player.initInventoryMenu();
        EventHooks.firePlayerLoggedIn(player);
        players.add(player);
        return player;
    }

    /** Moves a player within its level, and its chunk tickets and tracking with it. */
    static void teleport(ServerPlayer player, double x, double y, double z) {
        player.teleportTo(player.serverLevel(), x, y, z, 0f, 0f);
        // The server moves these when a client's movement packet arrives, which a teleport waits for.
        player.serverLevel().getChunkSource().move(player);
    }

    /** One game tick of each player, as its connection's tick would run it. Server thread. */
    void tick() {
        for (ServerPlayer p : players) {
            Counting c = (Counting) p.connection;
            for (; c.batchesToAcknowledge > 0; c.batchesToAcknowledge--) {
                c.chunkSender.onChunkBatchReceivedByClient(64f);
            }
            p.doTick();
        }
    }

    /**
     * Counts what was sent to each player this tick into {@code window}, or forgets it if null.
     * Outside the timed parts of the tick: encoding is the network thread's work, not the server's.
     */
    void count(@Nullable Window window) {
        for (ServerPlayer p : players) {
            Counting c = (Counting) p.connection;
            if (window != null) {
                String who = p.getGameProfile().getName();
                for (Packet<?> packet : c.sent) {
                    if (packet instanceof ClientboundBundlePacket bundle) {
                        for (Packet<?> sub : bundle.subPackets()) {
                            count(window, who, sub);
                        }
                        window.sent(who, "vanilla minecraft:bundle_delimiter", 2 * delimiterBytes);
                    } else {
                        count(window, who, packet);
                    }
                }
            }
            c.sent.clear();
        }
    }

    private void count(Window window, String who, Packet<?> packet) {
        String what = classify(packet);
        int size;
        try {
            size = wire.of(packet);
        } catch (RuntimeException e) {
            encodeFailures.merge(what, 1, Integer::sum);
            return;
        }
        window.sent(who, what, size);
    }

    /** "atmos <id>" for the mods' payloads and sounds, "vanilla <packet type>" for the rest. */
    static String classify(Packet<?> packet) {
        if (packet instanceof ClientboundCustomPayloadPacket custom) {
            ResourceLocation id = custom.payload().type().id();
            if (isMods(id)) {
                return ATMOS + id;
            }
            return "vanilla payload " + id;
        }
        Holder<SoundEvent> sound = null;
        if (packet instanceof ClientboundSoundPacket s) {
            sound = s.getSound();
        } else if (packet instanceof ClientboundSoundEntityPacket s) {
            sound = s.getSound();
        }
        if (sound != null) {
            ResourceLocation id = sound.value().getLocation();
            if (isMods(id)) {
                return ATMOS + "sound " + id;
            }
        }
        return "vanilla " + packet.type().id();
    }

    private static boolean isMods(ResourceLocation id) {
        return id.getNamespace().equals("zas") || id.getNamespace().equals("zas_atmos");
    }

    /** Takes the players off the server's lists before it stops, so it doesn't wait on their connections. */
    void leave() {
        for (ServerPlayer p : players) {
            try {
                server.getPlayerList().remove(p);
            } catch (RuntimeException e) {
                S10Mod.LOGGER.warn(
                        "[S-10] removing {} failed", p.getGameProfile().getName(), e);
            }
        }
        players.clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(PlayerList list, String name) {
        try {
            Field field = PlayerList.class.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(list);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("can't reach the player list's " + name, e);
        }
    }

    /**
     * A connection that keeps what the server sends until the bench counts it. A client with the
     * mods has every channel, so this says it has any. Handing a packet to Netty is a queue offer;
     * this is a list add, about as cheap.
     */
    static final class Counting extends ServerGamePacketListenerImpl {
        final List<Packet<?>> sent = new ArrayList<>();
        int batchesToAcknowledge;

        Counting(MinecraftServer server, ServerPlayer player) {
            super(
                    server,
                    new Connection(PacketFlow.SERVERBOUND),
                    player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            sent.add(packet);
            if (packet instanceof ClientboundChunkBatchFinishedPacket) {
                batchesToAcknowledge++;
            }
        }

        @Override
        public boolean hasChannel(ResourceLocation payloadId) {
            return true;
        }

        /** Nothing to flush; the connection's own would queue an action for a channel that never opens. */
        @Override
        public void resumeFlushing() {}
    }
}
