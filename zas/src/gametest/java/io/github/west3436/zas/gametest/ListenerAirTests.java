package io.github.west3436.zas.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ProtectionConfig;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.hud.AtmosHud;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.hud.ListenerAir;
import io.github.west3436.zas.hud.ListenerAirPayload;
import io.github.west3436.zas.sound.ThinAir;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The pressure a player is told is around it, for how much of this mod's sounds it hears (CLIENT-016,
 * DATA-019). As in {@link HudTests}, rooms are built in their own {@link Rooms} area, areas 330 and
 * up, and {@link ListenerAir#syncAt} records what would be sent.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ListenerAirTests {
    private ListenerAirTests() {}

    /** A sealed room from x0 to x0 + 4 of air at a total pressure in kPa. */
    private static void room(Rooms r, int x0, double kPa) {
        r.clear(x0, x0 + 5);
        r.shell(x0, x0 + 4);
        r.settle(GasMutations.remove(
                r.level,
                r.point(x0 + 2, 2, 2),
                Map.of(StandardAir.NITROGEN, 1e6, StandardAir.OXYGEN, 1e6),
                Rooms.source("listener_air")));
        if (kPa > 0) {
            Map<ResourceLocation, Double> moles = new HashMap<>();
            double n = kPa * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
            moles.put(StandardAir.OXYGEN, n * 0.21);
            moles.put(StandardAir.NITROGEN, n * 0.79);
            r.settle(GasMutations.add(
                    r.level,
                    r.point(x0 + 2, 2, 2),
                    new GasParcel(moles, StandardAir.TEMPERATURE),
                    Rooms.source("listener_air")));
        }
    }

    private static FakePlayer player(Rooms r, int x0) {
        FakePlayer p = FakePlayerFactory.get(r.level, new GameProfile(UUID.randomUUID(), "zas_listener"));
        p.moveTo(Vec3.atBottomCenterOf(r.abs(x0 + 2, 1, 2)));
        return p;
    }

    /** The reading a player would be sent now, as a first sync. */
    private static int synced(GameTestHelper h, FakePlayer p, String what) {
        List<ListenerAirPayload> sent = new ArrayList<>();
        ListenerAir.syncAt(p, p.level().getGameTime(), sent::add);
        h.assertValueEqual(sent.size(), 1, what + ": readings sent");
        return sent.get(0).kPa();
    }

    /** The reading follows the room's real pressure, and the curve then gives the factor. */
    @GameTest(batch = "hud", template = "empty")
    public static void client_016_a_room_gives_the_factor_for_its_pressure(GameTestHelper h) {
        Rooms r = new Rooms(h, 330);
        room(r, 0, 0);
        room(r, 6, 20.5);
        room(r, 12, 101.325);
        int vacuum = synced(h, player(r, 0), "vacuum");
        h.assertValueEqual(vacuum, 0, "a vacuum's reading");
        h.assertValueEqual(ThinAir.factor(vacuum), 0.0, "a vacuum's factor");
        h.assertValueEqual(synced(h, player(r, 6), "20.5 kPa"), 20, "the thin room's reading");
        int normal = synced(h, player(r, 12), "1 atm");
        h.assertValueEqual(normal, ThinAir.FULL_KPA, "normal air's reading");
        h.assertValueEqual(ThinAir.factor(normal), 1.0, "normal air's factor");
        h.succeed();
    }

    /**
     * A creative player and a spectator hear what the air gives them, though the HUD shows them no
     * alert at all. This is one reason its state can't serve here.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void data_019_creative_and_spectator_in_a_vacuum_read_as_a_vacuum(GameTestHelper h) {
        Rooms r = new Rooms(h, 331);
        room(r, 0, 0);
        FakePlayer creative = player(r, 0);
        creative.setGameMode(GameType.CREATIVE);
        List<AtmosHudPayload> hud = new ArrayList<>();
        AtmosHud.syncAt(creative, creative.level().getGameTime(), hud::add);
        h.assertValueEqual(hud, List.of(AtmosHudPayload.NONE), "the creative player's HUD");
        h.assertValueEqual(synced(h, creative, "creative"), 0, "the creative player's reading");
        FakePlayer spectator = player(r, 0);
        spectator.setGameMode(GameType.SPECTATOR);
        h.assertValueEqual(synced(h, spectator, "spectator"), 0, "the spectator's reading");
        h.succeed();
    }

    /**
     * A sealed suit in a vacuum reads as a vacuum, though its pressure reaches the player as normal
     * and the HUD has no pressure alert (LIFE-020). This is the other reason its state can't serve:
     * it is the main case of a listener in a vacuum.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void data_019_a_sealed_suit_in_a_vacuum_reads_as_a_vacuum(GameTestHelper h) {
        Rooms r = new Rooms(h, 332);
        room(r, 0, 0);
        FakePlayer suited = player(r, 0);
        for (EquipmentSlot slot : ProtectionConfig.SLOTS) {
            suited.setItemSlot(slot, new ItemStack(TestLifeSupport.item()));
        }
        List<AtmosHudPayload> hud = new ArrayList<>();
        AtmosHud.syncAt(suited, suited.level().getGameTime(), hud::add);
        h.assertValueEqual(hud.size(), 1, "the suited player's HUD states");
        h.assertValueEqual(hud.get(0).pressure(), HudLevels.NONE, "the suited player's pressure alert");
        h.assertValueEqual(synced(h, suited, "suited"), 0, "the suited player's reading");
        h.succeed();
    }

    /**
     * The open air reads as its dimension's ambient: normal air in the Overworld, the Nether and any
     * breathable dimension, and a vacuum in an airless one. An unresolved point says nothing.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void client_016_open_air_reads_as_its_dimensions_ambient(GameTestHelper h) {
        GasMixture overworld = Ambients.cell(Ambients.ambient(Level.OVERWORLD, AtmospherePreset.NORMAL));
        h.assertValueEqual(
                ListenerAir.reading(AtmosphereSample.ambient(overworld)),
                ThinAir.FULL_KPA,
                "the Overworld's open air is normal air");
        ResourceKey<Level> airless = ResourceKey.create(
                Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));
        GasMixture vacuum = Ambients.cell(Ambients.ambient(airless, AtmospherePreset.NORMAL));
        h.assertValueEqual(
                ListenerAir.reading(AtmosphereSample.ambient(vacuum)),
                0,
                "an airless dimension's open air is a vacuum");
        h.assertValueEqual(
                ListenerAir.reading(new AtmosphereSample(AtmosphereState.UNRESOLVED, Optional.empty(), overworld)),
                -1,
                "an unresolved point says nothing");
        h.succeed();
    }

    /**
     * Sent at once, then not while the reading is the same; a change waits until 5 ticks after the last
     * send, so at most 4 a second. A move between pressures above the cap sends nothing.
     */
    @GameTest(batch = "hud", template = "empty")
    public static void data_019_sent_on_change_at_most_4_a_second(GameTestHelper h) {
        Rooms r = new Rooms(h, 333);
        room(r, 0, 0);
        room(r, 6, 101.325);
        room(r, 12, 300);
        FakePlayer p = player(r, 0);
        List<ListenerAirPayload> sent = new ArrayList<>();
        long t = p.level().getGameTime();
        ListenerAir.syncAt(p, t, sent::add);
        h.assertValueEqual(sent.size(), 1, "the first reading");
        ListenerAir.syncAt(p, t + 5, sent::add);
        ListenerAir.syncAt(p, t + 10, sent::add);
        h.assertValueEqual(sent.size(), 1, "readings sent while nothing changed");
        p.moveTo(Vec3.atBottomCenterOf(r.abs(8, 1, 2)));
        ListenerAir.syncAt(p, t + 11, sent::add);
        h.assertValueEqual(sent.size(), 2, "a change 11 ticks after the last send");
        h.assertValueEqual(sent.get(1).kPa(), ThinAir.FULL_KPA, "the reading in normal air");
        p.moveTo(Vec3.atBottomCenterOf(r.abs(14, 1, 2)));
        ListenerAir.syncAt(p, t + 16, sent::add);
        ListenerAir.syncAt(p, t + 21, sent::add);
        h.assertValueEqual(sent.size(), 2, "normal air and high pressure read the same");
        p.moveTo(Vec3.atBottomCenterOf(r.abs(2, 1, 2)));
        ListenerAir.syncAt(p, t + 22, sent::add);
        ListenerAir.syncAt(p, t + 25, sent::add);
        h.assertValueEqual(sent.size(), 3, "a change waits for nothing if 5 ticks have passed");
        p.moveTo(Vec3.atBottomCenterOf(r.abs(8, 1, 2)));
        ListenerAir.syncAt(p, t + 26, sent::add);
        h.assertValueEqual(sent.size(), 3, "a change 4 ticks after the last send");
        ListenerAir.syncAt(p, t + 27, sent::add);
        h.assertValueEqual(sent.size(), 4, "the change 5 ticks after the last send");
        h.succeed();
    }

    /** The payload survives its codec, in one byte, which with its channel's name makes 19 on the wire. */
    @GameTest(batch = "hud", template = "empty")
    public static void data_019_payload_round_trips_in_one_byte(GameTestHelper h) {
        FriendlyByteBuf name = new FriendlyByteBuf(Unpooled.buffer());
        ResourceLocation.STREAM_CODEC.encode(name, ListenerAirPayload.TYPE.id());
        h.assertValueEqual(1 + name.readableBytes() + 1, 19, "a packet's bytes: its id, the channel and the payload");
        for (int kPa : new int[] {0, 1, 25, ThinAir.FULL_KPA}) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            ListenerAirPayload.STREAM_CODEC.encode(buf, new ListenerAirPayload(kPa));
            h.assertValueEqual(buf.readableBytes(), 1, "bytes");
            h.assertValueEqual(ListenerAirPayload.STREAM_CODEC.decode(buf).kPa(), kPa, "round trip");
        }
        h.succeed();
    }
}
