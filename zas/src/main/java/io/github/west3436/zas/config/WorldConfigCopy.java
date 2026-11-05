package io.github.west3436.zas.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.core.io.WritingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.electronwill.nightconfig.toml.TomlWriter;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A singleplayer world's own copy of the server config (OPS-007, #139).
 *
 * <p>NeoForge reads a world's {@code serverconfig/zas-server.toml} if it has one, and the shared
 * {@code config/zas-server.toml} otherwise, so the preset is per world only for a world with a copy.
 * The first change {@code /zas preset} saves in a world without one writes one: the whole loaded
 * config, with the change, to the world's {@code serverconfig/zas-server.toml}. The shared file is
 * left alone, so other worlds without a copy keep their values. The change already applies in
 * memory, and NeoForge reads the copy from the world's next load; until then this class sends the
 * session's later saves to the copy and names it as the config's file.
 *
 * <p>NeoForge keeps watching the shared file for the rest of the session. If that file changes,
 * NeoForge reloads the config from it; a listener at the highest priority puts the copy's values
 * back, so the world keeps its own. It doesn't watch the copy, so {@link #pickUpEdits} looks at it
 * as each atmos tick closes, which keeps a hand edit of the copy live as well (OPS-016).
 *
 * <p>A dedicated server is unchanged: it has one world, and saves go to the file it read, whichever
 * that is.
 */
public final class WorldConfigCopy {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LevelResource SERVERCONFIG = new LevelResource("serverconfig");

    /** Where a save goes. */
    public enum Target {
        /** The file the config was read from, as NeoForge saves it. */
        IN_PLACE,
        /** A new copy in the world's {@code serverconfig/}. */
        NEW_COPY,
        /** The copy made earlier this session. */
        COPY
    }

    /**
     * What a save did.
     *
     * @param file the file written, or null for a config not read from a file
     * @param newCopy whether it made the world's copy
     */
    public record Saved(@Nullable Path file, boolean newCopy) {}

    /** The copy this session made, which the loaded config doesn't know about; null if none. */
    private static volatile @Nullable Path active;

    /** When the copy last changed, as of this class's last read or write of it. Server thread. */
    private static @Nullable FileTime activeModified;

    private WorldConfigCopy() {}

    static void register(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener(EventPriority.HIGHEST, ModConfigEvent.Reloading.class, WorldConfigCopy::onReloading);
        modBus.addListener(ModConfigEvent.Unloading.class, e -> {
            if (e.getConfig().getSpec() == ZasServerConfig.SPEC) {
                active = null;
            }
        });
        gameBus.addListener(ServerStoppedEvent.class, e -> active = null);
    }

    /**
     * Where a save goes: in place on a dedicated server, for a config not read from a file, and for
     * a world that has its own copy; else to the copy made this session, or a new one.
     *
     * @param dedicated whether the server is dedicated
     * @param loaded the file the config was read from, or null
     * @param worldCopy the world's {@code serverconfig/zas-server.toml}
     * @param active the copy made this session, or null
     */
    public static Target target(boolean dedicated, @Nullable Path loaded, Path worldCopy, @Nullable Path active) {
        if (dedicated || loaded == null) {
            return Target.IN_PLACE;
        }
        if (active != null) {
            return Target.COPY;
        }
        return same(loaded, worldCopy) ? Target.IN_PLACE : Target.NEW_COPY;
    }

    /** The copy made this session, if any. */
    public static Optional<Path> active() {
        return Optional.ofNullable(active);
    }

    /**
     * Saves the loaded server config for {@code server}: see {@link #target}. Server thread.
     *
     * @throws UncheckedIOException if the copy can't be written
     */
    static Saved save(MinecraftServer server, ModConfig config) {
        Path loaded = path(config);
        Path worldCopy = server.getWorldPath(SERVERCONFIG).resolve(config.getFileName());
        Path copy = active;
        switch (target(server.isDedicatedServer(), loaded, worldCopy, copy)) {
            case NEW_COPY -> {
                write(worldCopy, config);
                active = worldCopy;
                LOGGER.info("Wrote this world's own server config, {}; {} is unchanged", worldCopy, loaded);
                return new Saved(worldCopy, true);
            }
            case COPY -> {
                write(copy, config);
                return new Saved(copy, false);
            }
            default -> {
                ZasServerConfig.SPEC.save();
                return new Saved(loaded, false);
            }
        }
    }

    /**
     * Puts the copy's values back into a config reloaded from another file (see the class doc).
     * Public for GameTests, which can't make NeoForge reload.
     */
    public static void reapply(ModConfig config) {
        Path copy = active;
        if (copy == null || config.getSpec() != ZasServerConfig.SPEC || same(path(config), copy)) {
            return;
        }
        if (read(copy, config)) {
            LOGGER.info("The server config was reloaded from {}; kept this world's own, {}", path(config), copy);
        }
    }

    /**
     * Applies a hand edit of the copy made this session, which NeoForge doesn't watch (OPS-016).
     * Called as an atmos tick closes, so the edit applies from that tick. It costs one look at the
     * file's time, and nothing without a copy. A copy that can't be read, as can happen half way
     * through an editor's save, leaves the values as they are, and is read again when it next
     * changes. Server thread.
     */
    public static void pickUpEdits() {
        Path copy = active;
        if (copy == null) {
            return;
        }
        FileTime modified = modified(copy);
        if (modified == null || modified.equals(activeModified)) {
            return;
        }
        activeModified = modified;
        ZasServerConfig.modConfig().ifPresent(config -> {
            if (read(copy, config)) {
                LOGGER.info("Applied the edit of this world's own server config, {}", copy);
            }
        });
    }

    /**
     * Puts a copy's values into the loaded config. As in NeoForge's own load, a value the config
     * wouldn't accept, or a missing one, takes its default, with a warning for the operator.
     *
     * @return whether it could read the copy; if not, the config is as it was
     */
    private static boolean read(Path copy, ModConfig config) {
        try (CommentedFileConfig file = CommentedFileConfig.builder(copy, TomlFormat.instance())
                .onFileNotFound(FileNotFoundAction.THROW_ERROR)
                .sync()
                .build()) {
            file.load();
            ZasServerConfig.SPEC.correct(
                    file,
                    (action, key, was, now) ->
                            LOGGER.warn("{}: {} can't be {}, so it is {}", copy, String.join(".", key), was, now));
            CommentedConfig values = (CommentedConfig) config.getLoadedConfig().config();
            values.putAll(file);
        } catch (ParsingException | WritingException e) {
            LOGGER.warn("Couldn't read {}, so the server config stays as it was: {}", copy, e.toString());
            return false;
        }
        ZasServerConfig.SPEC.afterReload();
        return true;
    }

    private static @Nullable FileTime modified(Path file) {
        try {
            return Files.getLastModifiedTime(file);
        } catch (IOException gone) {
            return null;
        }
    }

    /** For GameTests: forgets the copy made this session, after the test has deleted it. */
    public static void forgetForTest() {
        active = null;
    }

    private static void onReloading(ModConfigEvent.Reloading e) {
        reapply(e.getConfig());
    }

    private static void write(Path file, ModConfig config) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        new TomlWriter().write(config.getLoadedConfig().config(), file, WritingMode.REPLACE_ATOMIC);
        activeModified = modified(file);
    }

    static @Nullable Path path(ModConfig config) {
        try {
            return config.getFullPath();
        } catch (IllegalStateException notFromAFile) {
            return null;
        }
    }

    private static boolean same(@Nullable Path a, Path b) {
        return a != null
                && a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
