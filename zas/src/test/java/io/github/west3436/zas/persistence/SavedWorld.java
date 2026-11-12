package io.github.west3436.zas.persistence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * A world saved by a release of the mods, kept under {@code fixtures/saves/<release>} in these test
 * resources for the tests of forward migration (DATA-012): the station's chunks in {@code region}
 * and {@code entities}, the mods' own files in {@code data}, and {@code expected.json}, what the
 * release's console said the station held just before it stopped.
 *
 * <p><b>Making one for the next release.</b> Check out the release's tag and start its dedicated
 * server on a new flat world: {@code ./gradlew :zas-atmos:createServerLaunchScript} writes a java
 * command that has a console. Build a station by console commands, with every block entity the
 * release has and a chest of items with every data component. Once nothing changes any more, {@code
 * tick freeze}, read each block with {@code data get block} and each zone with {@code zas zone at}
 * into {@code expected.json}, and {@code stop}. Keep only the station's chunks of the region files,
 * their stored bytes unchanged, and add the release to the tests that read this one.
 *
 * <p>Nothing here needs the game to be running, so the content mod's unit tests read it too.
 */
public final class SavedWorld {
    private static final int SECTOR = 4096;
    private static final int ZLIB = 2;

    private final String folder;

    private SavedWorld(String release) {
        this.folder = "/fixtures/saves/" + release + "/";
    }

    /** The world a release saved, such as {@code 0.1.0}. */
    public static SavedWorld of(String release) {
        return new SavedWorld(release);
    }

    /** A chunk as the release wrote it to its region file, or null if the fixture doesn't keep it. */
    public CompoundTag chunk(int x, int z) {
        return stored("region", x, z);
    }

    /** A chunk's entities as the release wrote them, or null if the chunk had none. */
    public CompoundTag entities(int x, int z) {
        return stored("entities", x, z);
    }

    /** What a file of the level's {@code data} folder holds, such as {@code zas_atmos}. */
    public CompoundTag data(String name) {
        try {
            return NbtIo.readCompressed(
                            new ByteArrayInputStream(bytes("data/" + name + ".dat")), NbtAccounter.unlimitedHeap())
                    .getCompound("data");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** What the release's console said the world held. */
    public JsonObject expected() {
        return JsonParser.parseString(new String(bytes("expected.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    /** One chunk of a region file: its sector offset from the file's header, then its packed tag. */
    private CompoundTag stored(String kind, int x, int z) {
        byte[] file = bytes(kind + "/r." + (x >> 5) + "." + (z >> 5) + ".mca");
        ByteBuffer region = ByteBuffer.wrap(file);
        int location = region.getInt(4 * ((x & 31) + (z & 31) * 32));
        if (location == 0) {
            return null;
        }
        int at = (location >>> 8) * SECTOR;
        int length = region.getInt(at);
        if (file[at + 4] != ZLIB) {
            throw new IllegalStateException("chunk " + x + ", " + z + " isn't stored with zlib");
        }
        try (DataInputStream in =
                new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(file, at + 5, length - 1)))) {
            return NbtIo.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private byte[] bytes(String path) {
        try (InputStream in = SavedWorld.class.getResourceAsStream(folder + path)) {
            if (in == null) {
                throw new IllegalStateException("no " + folder + path + " in the test resources");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
