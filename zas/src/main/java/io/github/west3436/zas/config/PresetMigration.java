package io.github.west3436.zas.config;

import com.mojang.logging.LogUtils;
import io.github.west3436.ss13.api.preset.PresetOption;
import io.github.west3436.ss13.api.preset.Presets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Carries a 1.2.0 world's atmosphere preset and overrides over to SS13 Framework's switch (OPS-007).
 *
 * <p>ZAS³ 1.2.0 kept them in {@code zas-server.toml}, under {@code preset} and {@code
 * [preset_options]}; now the framework keeps them in {@code ss13_framework-presets.toml}, for every
 * mod. As the server starts, once both files are loaded, a {@code zas-server.toml} that says
 * anything but {@code NORMAL} and {@code "preset"} there is carried over:
 *
 * <ul>
 *   <li>from the world's own {@code serverconfig/zas-server.toml}, or on a dedicated server, which
 *       has one world: the preset and each override go to the framework, which saves them in its
 *       file, and the entries in {@code zas-server.toml} are set back to {@code NORMAL} and {@code
 *       "preset"} and saved. That marks the carry-over as done.
 *   <li>from the shared {@code config/zas-server.toml} in singleplayer, which other worlds read too:
 *       only into a world that has no {@code serverconfig/ss13_framework-presets.toml} yet, which the
 *       carry-over then writes, so it happens once per world. The shared file is left as it is.
 * </ul>
 *
 * <p>A value the option's type doesn't accept is left out with a warning, and the option follows
 * the preset.
 */
public final class PresetMigration {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LevelResource SERVERCONFIG = new LevelResource("serverconfig");

    /** What to do with the old entries. */
    public enum Plan {
        /** They say Normal and nothing overridden: nothing to carry over. */
        NOTHING,
        /** They are the world's own, or the dedicated server's: carry them over, then reset them. */
        CARRY_AND_RESET,
        /** They are the shared file's, and the world has no preset file: carry them over only. */
        CARRY,
        /** They are the shared file's, and the world already has its own preset file. */
        KEEP_THE_WORLDS
    }

    /**
     * What a carry-over did.
     *
     * @param plan what it decided
     * @param preset the preset the old entries gave
     * @param overrides the options overridden, by key, as the framework stores them
     * @param refused the entries left out because the option's type doesn't accept them, by key
     */
    public record Result(
            Plan plan, AtmospherePreset preset, Map<String, Object> overrides, Map<String, Object> refused) {}

    private PresetMigration() {}

    /** Carries the old entries over as each server starts, once both configs are loaded. */
    public static void register(IEventBus gameBus) {
        gameBus.addListener(ServerStartingEvent.class, e -> migrate(e.getServer()));
    }

    /**
     * Decides what to do with the old entries.
     *
     * @param movedValues whether the old entries say anything but Normal and {@code "preset"}
     * @param dedicated whether the server is dedicated
     * @param loaded the file {@code zas-server.toml} was read from, or null for one not read from a
     *     file
     * @param worldConfig the world's {@code serverconfig/zas-server.toml}
     * @param worldHasPresetFile whether the world has its own {@code
     *     serverconfig/ss13_framework-presets.toml}
     * @return the plan
     */
    public static Plan plan(
            boolean movedValues,
            boolean dedicated,
            @Nullable Path loaded,
            Path worldConfig,
            boolean worldHasPresetFile) {
        if (!movedValues) {
            return Plan.NOTHING;
        }
        if (dedicated || (loaded != null && same(loaded, worldConfig))) {
            return Plan.CARRY_AND_RESET;
        }
        return worldHasPresetFile ? Plan.KEEP_THE_WORLDS : Plan.CARRY;
    }

    /**
     * Carries the running world's old entries over, as the server starts. Server thread.
     *
     * @param server the server
     * @return what it did; empty if either config isn't loaded
     */
    public static Optional<Result> migrate(MinecraftServer server) {
        Optional<ModConfig> config = ZasServerConfig.modConfig();
        if (config.isEmpty() || !ZasServerConfig.SPEC.isLoaded() || !Presets.isLoaded()) {
            return Optional.empty();
        }
        Path serverconfig = server.getWorldPath(SERVERCONFIG);
        return Optional.of(migrate(
                server.isDedicatedServer(),
                path(config.get()),
                serverconfig.resolve(config.get().getFileName()),
                serverconfig.resolve(Presets.FILE_NAME),
                ZasServerConfig.SPEC::save));
    }

    /**
     * Carries the old entries in the loaded {@code zas-server.toml} over to the framework, as {@link
     * #plan} decides. Server thread; both configs loaded.
     *
     * @param dedicated whether the server is dedicated
     * @param loaded the file {@code zas-server.toml} was read from, or null
     * @param worldConfig the world's {@code serverconfig/zas-server.toml}
     * @param worldPresetFile the world's {@code serverconfig/ss13_framework-presets.toml}
     * @param save writes {@code zas-server.toml} once its entries are reset
     * @return what it did
     */
    public static Result migrate(
            boolean dedicated, @Nullable Path loaded, Path worldConfig, Path worldPresetFile, Runnable save) {
        AtmospherePreset preset = ZasServerConfig.MOVED_PRESET.get();
        Map<String, Object> overrides = new LinkedHashMap<>();
        Map<String, Object> refused = new LinkedHashMap<>();
        List<Runnable> apply = new ArrayList<>();
        for (PresetOption<?> option : PresetOptions.all()) {
            String key = option.id().getPath();
            Object raw = ZasServerConfig.MOVED_PRESET_OPTIONS.get(key).get();
            if (!PresetOption.followsPreset(raw)) {
                if (!stage(option, raw, apply)) {
                    refused.put(key, raw);
                } else {
                    overrides.put(key, raw);
                }
            }
        }
        boolean moved = preset != AtmospherePreset.NORMAL || !overrides.isEmpty() || !refused.isEmpty();
        Plan plan = plan(moved, dedicated, loaded, worldConfig, Files.exists(worldPresetFile));
        Result result = new Result(plan, preset, Map.copyOf(overrides), Map.copyOf(refused));
        switch (plan) {
            case NOTHING -> {}
            case KEEP_THE_WORLDS ->
                LOGGER.debug(
                        "{} still has a preset in the form ZAS 1.2.0 kept it; this world has its own {}, so it"
                                + " keeps that",
                        loaded,
                        worldPresetFile);
            case CARRY, CARRY_AND_RESET -> {
                AtmospherePreset.switchTo(preset);
                apply.forEach(Runnable::run);
                refused.forEach((key, raw) -> LOGGER.warn(
                        "{}: [preset_options] {} = {} isn't a value of that option, so it follows the preset",
                        loaded,
                        key,
                        raw));
                if (plan == Plan.CARRY_AND_RESET) {
                    ZasServerConfig.MOVED_PRESET.set(AtmospherePreset.NORMAL);
                    ZasServerConfig.MOVED_PRESET_OPTIONS.values().forEach(e -> e.set(PresetOption.FOLLOW_PRESET));
                    save.run();
                }
                LOGGER.info(
                        "Carried the atmosphere preset {} and {} override(s) over from {} to SS13 Framework's {}{}",
                        preset,
                        overrides.size(),
                        loaded,
                        Presets.FILE_NAME,
                        plan == Plan.CARRY_AND_RESET
                                ? "; its old entries now say NORMAL and \"preset\""
                                : "; the shared file, which other worlds read, is unchanged");
            }
        }
        return result;
    }

    /** Readies an override to apply, if the option's type accepts the old value. */
    private static <T> boolean stage(PresetOption<T> option, Object raw, List<Runnable> apply) {
        Optional<T> value = option.type().parse(raw);
        value.ifPresent(v -> apply.add(() -> option.setOverride(v)));
        return value.isPresent();
    }

    private static @Nullable Path path(ModConfig config) {
        try {
            return config.getFullPath();
        } catch (IllegalStateException notFromAFile) {
            return null;
        }
    }

    private static boolean same(Path a, Path b) {
        return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
    }
}
