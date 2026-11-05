package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * A player at a point that keeps every packet the server sends it, so a test can check what a client
 * would be told. {@link #during} puts listeners in the level's player list only while a call runs,
 * within one game tick, so no other test or system sees them.
 */
final class ListeningPlayer {
    private final List<Packet<?>> sent = new ArrayList<>();
    private final FakePlayer player;

    private ListeningPlayer(ServerLevel level, Vec3 at) {
        player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "zas_packets"));
        player.moveTo(at);
        new Recording(level, player, sent);
    }

    /** A listener at a point, not yet in the level. */
    static ListeningPlayer at(ServerLevel level, Vec3 at) {
        return new ListeningPlayer(level, at);
    }

    /** Runs a call with these listeners in the level's player list. */
    static void during(ServerLevel level, List<ListeningPlayer> listeners, Runnable call) {
        List<ServerPlayer> players = new ArrayList<>();
        listeners.forEach(l -> players.add(l.player));
        level.players().addAll(players);
        try {
            call.run();
        } finally {
            level.players().removeAll(players);
        }
    }

    /** The sounds it was sent. */
    List<ClientboundSoundPacket> sounds() {
        return sent.stream()
                .filter(ClientboundSoundPacket.class::isInstance)
                .map(ClientboundSoundPacket.class::cast)
                .toList();
    }

    /** The explosions it was sent. A client plays each one's own sound. */
    List<ClientboundExplodePacket> explosions() {
        return sent.stream()
                .filter(ClientboundExplodePacket.class::isInstance)
                .map(ClientboundExplodePacket.class::cast)
                .toList();
    }

    /** A connection that keeps what a server sends, and hands what it's given to the game's own handlers. */
    static final class Recording extends ServerGamePacketListenerImpl {
        private final List<Packet<?>> sent;

        Recording(ServerLevel level, ServerPlayer player, List<Packet<?>> sent) {
            super(
                    level.getServer(),
                    new Connection(PacketFlow.SERVERBOUND),
                    player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.sent = sent;
        }

        @Override
        public void send(Packet<?> packet, @Nullable PacketSendListener listener) {
            sent.add(packet);
        }
    }
}
