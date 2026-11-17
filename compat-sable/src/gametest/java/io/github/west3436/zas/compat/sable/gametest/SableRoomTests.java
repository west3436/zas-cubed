package io.github.west3436.zas.compat.sable.gametest;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.persistence.AtmosPersistence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A room on a sub-level keeps its gas through what Sable does to the sub-level (COMPAT-100): when it
 * is assembled, when it unloads and loads again, across a restart, and when it is taken apart. Each
 * builds a sealed stone box with a room at 150 kPa of a mix that isn't the ambient's, and turns it
 * into a sub-level as {@code /sable assemble area} does.
 */
@GameTestHolder(SableGateTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class SableRoomTests {
    private SableRoomTests() {}

    /** The box's room, filled, then assembled: the room on board, with every gas it held. */
    private static ServerSubLevel assembled(Deck d, ZoneInfo[] onBoard) {
        d.box();
        ZoneInfo before = d.fill(d.abs(2, 2, 2));
        ServerSubLevel sub = d.assemble();
        d.ticks(3);
        ZoneInfo after = d.zone(Deck.onBoard(sub, 2, 2, 2));
        d.same(before, after, "on board");
        d.h.assertTrue(d.zoneAt(d.abs(2, 2, 2)).isEmpty(), "the room is still where the box was");
        onBoard[0] = after;
        return sub;
    }

    /** Whether every plot chunk tag of a saved sub-level keeps a record of its air. */
    private static void recorded(GameTestHelper h, CompoundTag full, String what) {
        CompoundTag chunks = full.getCompound("plot").getCompound("chunks");
        h.assertFalse(chunks.isEmpty(), what + ": no plot chunks");
        for (String key : chunks.getAllKeys()) {
            h.assertTrue(
                    chunks.getCompound(key).contains(AtmosPersistence.TAG),
                    what + ": plot chunk " + key + " keeps no air");
        }
    }

    /**
     * The owner's first case: a box holding a room is assembled into a sub-level, and the room is on
     * board with every gas it held, and gone from where the box was.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void compat_100_an_assembled_room_keeps_its_gas(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 0);
        ZoneInfo[] room = new ZoneInfo[1];
        ServerSubLevel sub = assembled(d, room);
        h.assertFalse(sub.isRemoved(), "the sub-level is gone");
        h.succeed();
    }

    /**
     * The owner's second case: a sub-level holding a room unloads, as Sable unloads one that moves
     * into a chunk nobody is near, and loads again by itself; the room is back with every gas it
     * held. While it is unloaded, the record of its air is in what Sable holds for it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void compat_100_a_room_survives_unload_and_reload(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 1);
        ZoneInfo[] room = new ZoneInfo[1];
        ServerSubLevel sub = assembled(d, room);
        BlockPos cell = Deck.onBoard(sub, 2, 2, 2);
        List<ChunkPos> plot = Deck.chunks(sub);
        UUID id = sub.getUniqueId();
        ServerSubLevelContainer container = d.container();

        container.getHoldingChunkMap().moveToUnloaded(sub, new ChunkPos(d.abs(2, 2, 2)));
        h.assertTrue(sub.isRemoved(), "the sub-level didn't unload");
        HoldingSubLevel held = container.getHoldingChunkMap().getHoldingSubLevel(id);
        h.assertTrue(held != null, "Sable holds nothing for the sub-level");
        recorded(h, held.data().fullTag(), "while unloaded");
        for (ChunkPos c : plot) {
            h.assertFalse(d.dimension().isChunkLoaded(c.toLong()), "ZAS still has plot chunk " + c);
        }
        d.tick();
        h.assertTrue(d.zoneAt(cell).isEmpty(), "the room is still there while unloaded");

        h.succeedWhen(() -> {
            SubLevel back = container.getSubLevel(id);
            if (back == null) {
                throw new GameTestAssertException("Sable hasn't loaded the sub-level again");
            }
            d.tick();
            d.same(room[0], d.zone(Deck.onBoard((ServerSubLevel) back, 2, 2, 2)), "after the reload");
        });
    }

    /**
     * The owner's third case: across a restart. What Sable writes for the sub-level when the level
     * saves keeps the room's air, and the sub-level loaded from it after ZAS³ restarts has the room
     * with every gas it held. In the Nether, in a batch of its own, since the restart stops the
     * dimension's atmosphere.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "sable_restart")
    public static void compat_100_a_room_survives_a_restart(GameTestHelper h) {
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        if (nether == null) {
            throw new GameTestAssertException("no Nether");
        }
        Deck d = new Deck(h, nether, 2);
        ZoneInfo[] room = new ZoneInfo[1];
        ServerSubLevel sub = assembled(d, room);

        // What Sable writes when the level saves, through a file's bytes.
        CompoundTag written =
                roundTrip(SubLevelSerializer.toData(sub, List.of()).fullTag());
        recorded(h, written, "as saved");

        Deck.service().stopForTest(nether);
        d.container().removeSubLevel(sub, SubLevelRemovalReason.UNLOADED);
        Deck.service().startForTest(nether, Map.of());
        ServerSubLevel back = SubLevelSerializer.fullyLoad(nether, SubLevelSerializer.fromData(written));
        h.assertTrue(back != null, "Sable didn't load the saved sub-level");
        d.ticks(3);
        d.same(room[0], d.zone(Deck.onBoard(back, 2, 2, 2)), "after the restart");
        h.succeed();
    }

    /**
     * A sub-level taken apart, as Create Aeronautics takes one apart: its blocks, but not its air,
     * put back into the world a quarter turn round. The room goes with them, with every gas it held,
     * even though Sable removes the empty sub-level before ZAS³ has moved the room; and ZAS³ then
     * forgets the plot's chunks.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void compat_100_a_taken_apart_sub_level_puts_its_room_back(GameTestHelper h) {
        Deck d = new Deck(h, h.getLevel(), 3);
        ZoneInfo[] room = new ZoneInfo[1];
        ServerSubLevel sub = assembled(d, room);
        List<ChunkPos> plot = Deck.chunks(sub);
        BlockPos plotAnchor = Deck.onBoard(sub, 0, 0, 0);
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(plotAnchor, Deck.onBoard(sub, 4, 4, 4))) {
            if (!d.level.getBlockState(p).isAir()) {
                blocks.add(p.immutable());
            }
        }
        SubLevelAssemblyHelper.AssemblyTransform back = new SubLevelAssemblyHelper.AssemblyTransform(
                plotAnchor, d.abs(0, 0, 4), Rotation.CLOCKWISE_90.ordinal(), Rotation.CLOCKWISE_90, d.level);
        BlockPos landed = back.apply(Deck.onBoard(sub, 2, 2, 2));
        SubLevelAssemblyHelper.moveBlocks(d.level, back, blocks);
        // Sable removes a sub-level left without blocks; here before the next atmos tick.
        d.container().processSubLevelRemovals();
        h.assertTrue(sub.isRemoved(), "Sable didn't remove the empty sub-level");

        d.ticks(3);
        d.same(room[0], d.zone(landed), "back in the world");
        for (ChunkPos c : plot) {
            if (d.container().getChunk(c) == null) {
                h.assertFalse(d.dimension().isChunkLoaded(c.toLong()), "ZAS still has plot chunk " + c);
            }
        }
        h.succeed();
    }

    private static CompoundTag roundTrip(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(tag, new DataOutputStream(bytes));
            return NbtIo.read(
                    new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.unlimitedHeap());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
