package io.github.west3436.zas.atmos.compat.cc;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * Real CC: Tweaked computers for the tests: a computer block, placed and switched on as a saved one
 * is, running a Lua program the test wrote as its {@code startup.lua}. Nothing of CC: Tweaked's is
 * used but its blocks, by their ids, its documented save folder and its API's way to number a
 * computer, so the same test runs with every build.
 *
 * <p>A computer runs on a thread of its own, in real time, while the test server runs its ticks as
 * fast as it can. So a program writes what it found to files in its own folder, each followed by a
 * second file that says the first is whole, and the test waits for those, a short sleep at a time.
 */
final class Computers {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How long a test with a computer may take, in game ticks: each waiting tick sleeps a millisecond. */
    static final int TIMEOUT_TICKS = 60_000;

    /**
     * What every program starts with: {@code put(name, text)} writes a file for the test, {@code
     * json(value)} is a value as JSON, and {@code call(fn, ...)} is a call's result or its error.
     */
    private static final String PRELUDE = """
            local function put(name, text)
              local f = fs.open(name, "w")
              f.write(text)
              f.close()
              fs.open(name .. ".ok", "w").close()
            end
            local function json(value)
              return textutils.serialiseJSON(value)
            end
            local function call(fn, ...)
              local r = table.pack(pcall(fn, ...))
              if r[1] then
                return { ok = true, value = r[2] }
              end
              return { ok = false, error = tostring(r[2]) }
            end
            local ok, err = pcall(function()
            """;

    /** What every program ends with: an error that stopped it is written for the test too. */
    private static final String END = """

            end)
            if not ok then
              put("error", tostring(err))
            end
            """;

    private Computers() {}

    /** A started computer. */
    static final class Computer {
        final int id;
        final BlockPos pos;
        private final Path folder;
        private final Set<String> logged = new HashSet<>();

        private Computer(int id, BlockPos pos, Path folder) {
            this.id = id;
            this.pos = pos;
            this.folder = folder;
        }

        /** Whether the program has written a file whole. */
        boolean wrote(String name) {
            return Files.exists(folder.resolve(name + ".ok"));
        }

        /**
         * A file the program wrote. Until it has, this waits a millisecond of real time and fails, so
         * a test asks again at its next tick.
         */
        String read(String name) {
            if (wrote("error")) {
                throw new IllegalStateException("computer " + id + "'s program stopped: " + text("error"));
            }
            if (!wrote(name)) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw new GameTestAssertException("computer " + id + " hasn't written " + name);
            }
            String text = text(name);
            if (logged.add(name)) {
                LOGGER.info("Computer {} wrote {}: {}", id, name, text);
            }
            return text;
        }

        /** A file the program wrote as JSON. */
        JsonElement json(String name) {
            return JsonParser.parseString(read(name));
        }

        private String text(String name) {
            try {
                return Files.readString(folder.resolve(name), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new GameTestAssertException("couldn't read computer " + id + "'s " + name + ": " + e);
            }
        }
    }

    /**
     * Places a computer and switches it on, running a program.
     *
     * @param program Lua, after the prelude: it may use {@code put}, {@code json} and {@code call}
     */
    static Computer start(Spot s, int x, int y, int z, String program) {
        MinecraftServer server = s.level.getServer();
        int id = ComputerCraftAPI.createUniqueNumberedSaveDir(server, "computer");
        Path folder = server.getWorldPath(LevelResource.ROOT)
                .resolve("computercraft")
                .resolve("computer")
                .resolve(Integer.toString(id));
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("startup.lua"), PRELUDE + program + END, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GameTestAssertException("couldn't write computer " + id + "'s program: " + e);
        }
        BlockPos pos = s.abs(x, y, z);
        s.level.setBlockAndUpdate(pos, block("computer_normal").defaultBlockState());
        BlockEntity computer = s.level.getBlockEntity(pos);
        if (computer == null) {
            throw new GameTestAssertException("the computer has no block entity");
        }
        // As a computer that was on when its chunk was saved: it starts at its next tick.
        CompoundTag saved = new CompoundTag();
        saved.putInt("ComputerId", id);
        saved.putBoolean("On", true);
        computer.loadWithComponents(saved, s.level.registryAccess());
        return new Computer(id, pos, folder);
    }

    /**
     * Places a wired modem block. It joins the peripherals around it to its network once {@link
     * #switchOn} has clicked it.
     */
    static void modem(Spot s, int x, int y, int z) {
        s.set(x, y, z, block("wired_modem_full").defaultBlockState());
    }

    /** Clicks a wired modem block as a player does, which joins the peripherals around it to its network. */
    static void switchOn(Spot s, int x, int y, int z) {
        BlockPos pos = s.abs(x, y, z);
        Player player = s.h.makeMockPlayer(GameType.CREATIVE);
        s.level
                .getBlockState(pos)
                .useWithoutItem(s.level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static Block block(String path) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("computercraft", path));
        if (block == Blocks.AIR) {
            throw new GameTestAssertException("CC: Tweaked has no block " + path);
        }
        return block;
    }
}
