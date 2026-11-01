package io.github.west3436.zas.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.PresetOption;
import io.github.west3436.zas.config.PresetOptions;
import io.github.west3436.zas.config.ZasServerConfig;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * {@code /zas preset}: the atmosphere preset and the options it sets, each with its value and
 * whether that comes from the preset or an override (OPS-007, OPS-014, OPS-015), documented in
 * {@code docs/commands.md}.
 *
 * <p>Switching the preset or changing an override writes the server config file, so the change
 * lasts, and applies from the next read of the option: for the ambient, the next atmos tick.
 * Overrides are kept when the preset changes.
 */
final class PresetCommand {
    private static final DynamicCommandExceptionType UNKNOWN_OPTION =
            new DynamicCommandExceptionType(key -> Component.translatable("commands.zas.error.unknown_option", key));
    private static final Dynamic3CommandExceptionType INVALID_VALUE = new Dynamic3CommandExceptionType(
            (key, value, valid) -> Component.translatable("commands.zas.error.invalid_value", key, value, valid));
    private static final SimpleCommandExceptionType NO_CONFIG =
            new SimpleCommandExceptionType(Component.translatable("commands.zas.error.no_config"));

    private static final SuggestionProvider<CommandSourceStack> OPTIONS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    PresetOptions.all().stream().map(PresetOption::key).toList(), builder);

    /** The option's own values: its type's, and its two defaults. */
    private static final SuggestionProvider<CommandSourceStack> VALUES = (ctx, builder) -> {
        Optional<PresetOption<?>> option = PresetOptions.byKey(StringArgumentType.getString(ctx, "option"));
        return SharedSuggestionProvider.suggest(
                option.map(PresetCommand::suggestions).orElse(List.of()), builder);
    };

    private PresetCommand() {}

    /** The {@code preset} node under {@code /zas}. */
    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("preset")
                .executes(ctx -> report(ctx.getSource()))
                .then(Commands.literal("normal").executes(ctx -> switchTo(ctx.getSource(), AtmospherePreset.NORMAL)))
                .then(Commands.literal("hardcore")
                        .executes(ctx -> switchTo(ctx.getSource(), AtmospherePreset.HARDCORE)))
                .then(Commands.literal("override")
                        .then(Commands.argument("option", StringArgumentType.word())
                                .suggests(OPTIONS)
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests(VALUES)
                                        .executes(PresetCommand::override))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("option", StringArgumentType.word())
                                .suggests(OPTIONS)
                                .executes(PresetCommand::clear)));
    }

    // ---- Subcommands ----------------------------------------------------------------------

    /** {@code /zas preset}: the preset, the config file, and every option with its source. */
    static int report(CommandSourceStack source) throws CommandSyntaxException {
        requireConfig();
        for (Component line : reportLines(source)) {
            source.sendSuccess(() -> line, false);
        }
        return PresetOptions.overridden();
    }

    private static int switchTo(CommandSourceStack source, AtmospherePreset preset) throws CommandSyntaxException {
        requireConfig();
        if (ZasServerConfig.preset() == preset) {
            source.sendSuccess(() -> Component.translatable("commands.zas.preset.unchanged", name(preset)), false);
            return report(source);
        }
        ZasServerConfig.setPreset(preset);
        ZasServerConfig.save();
        int kept = PresetOptions.overridden();
        source.sendSuccess(() -> Component.translatable("commands.zas.preset.switched", name(preset), kept), true);
        return report(source);
    }

    private static int override(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireConfig();
        return override(ctx.getSource(), option(ctx), StringArgumentType.getString(ctx, "value"));
    }

    private static <T> int override(CommandSourceStack source, PresetOption<T> option, String text)
            throws CommandSyntaxException {
        T value = option.type()
                .parse(text)
                .orElseThrow(() ->
                        INVALID_VALUE.create(option.key(), text, option.type().describe()));
        option.setOverride(value);
        ZasServerConfig.save();
        source.sendSuccess(
                () -> Component.translatable("commands.zas.preset.overridden", option.key(), option.format(value)),
                true);
        source.sendSuccess(() -> line(option), false);
        source.sendSuccess(() -> fileLine(source), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        requireConfig();
        CommandSourceStack source = ctx.getSource();
        PresetOption<?> option = option(ctx);
        if (option.source() != PresetOption.Source.OVERRIDE) {
            source.sendSuccess(() -> Component.translatable("commands.zas.preset.not_overridden", option.key()), false);
            source.sendSuccess(() -> line(option), false);
            return 0;
        }
        option.clearOverride();
        ZasServerConfig.save();
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.zas.preset.cleared", option.key(), name(ZasServerConfig.preset())),
                true);
        source.sendSuccess(() -> line(option), false);
        source.sendSuccess(() -> fileLine(source), false);
        return 1;
    }

    // ---- Output ------------------------------------------------------------------------

    /** The report's lines: the preset, the file, each option, and the triggers on under both presets. */
    static List<Component> reportLines(CommandSourceStack source) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable(
                "commands.zas.preset.header",
                name(ZasServerConfig.preset()),
                PresetOptions.overridden(),
                PresetOptions.all().size()));
        out.add(fileLine(source));
        for (PresetOption<?> option : PresetOptions.all()) {
            out.add(line(option));
        }
        out.add(Component.translatable("commands.zas.preset.fixed_triggers"));
        return out;
    }

    /**
     * One option: its key, its effective value, its source, its two defaults, and, while its feature
     * doesn't exist, the requirement it waits for.
     */
    static <T> Component line(PresetOption<T> option) {
        Component main = Component.translatable(
                "commands.zas.preset.option",
                option.key(),
                option.format(option.get()),
                option.source().id(),
                option.format(option.defaultFor(AtmospherePreset.NORMAL)),
                option.format(option.defaultFor(AtmospherePreset.HARDCORE)));
        return option.waitsFor()
                .<Component>map(req -> Component.translatable("commands.zas.preset.option_waiting", main, req))
                .orElse(main);
    }

    private static Component fileLine(CommandSourceStack source) {
        return ZasServerConfig.file()
                .<Component>map(path -> Component.translatable("commands.zas.preset.file", shown(source, path)))
                .orElseGet(() -> Component.translatable("commands.zas.preset.no_file"));
    }

    /** A path relative to the server's directory when it is inside it, with forward slashes. */
    private static String shown(CommandSourceStack source, Path path) {
        Path shown = path.toAbsolutePath().normalize();
        Path root = source.getServer().getServerDirectory().toAbsolutePath().normalize();
        if (shown.startsWith(root)) {
            shown = root.relativize(shown);
        }
        return shown.toString().replace('\\', '/');
    }

    private static Component name(AtmospherePreset preset) {
        return Component.translatable("zas.preset." + preset.id());
    }

    // ---- Helpers -----------------------------------------------------------------------

    private static PresetOption<?> option(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String key = StringArgumentType.getString(ctx, "option");
        return PresetOptions.byKey(key).orElseThrow(() -> UNKNOWN_OPTION.create(key));
    }

    private static void requireConfig() throws CommandSyntaxException {
        if (!ZasServerConfig.SPEC.isLoaded()) {
            throw NO_CONFIG.create();
        }
    }

    private static <T> List<String> suggestions(PresetOption<T> option) {
        Set<String> values = new LinkedHashSet<>(option.type().suggestions());
        values.add(option.format(option.defaultFor(AtmospherePreset.NORMAL)));
        values.add(option.format(option.defaultFor(AtmospherePreset.HARDCORE)));
        return List.copyOf(values);
    }
}
