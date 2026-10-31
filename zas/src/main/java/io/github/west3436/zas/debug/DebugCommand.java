package io.github.west3436.zas.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /zas debug}: switches the operator debug overlay's layers for the player running it
 * (CLIENT-005, CLIENT-011 to CLIENT-013). Needs permission level {@link
 * DebugOverlay#PERMISSION_LEVEL}.
 *
 * <ul>
 *   <li>{@code /zas debug}: lists the layers that are on
 *   <li>{@code /zas debug <zones|edges|cells|hotspots>}: switches one layer on or off
 *   <li>{@code /zas debug all}: switches every layer on
 *   <li>{@code /zas debug off}: switches the overlay off
 * </ul>
 *
 * <p>Registered under the shared {@code /zas} root; Brigadier merges it with the other {@code
 * /zas} subcommands.
 */
public final class DebugCommand {
    private DebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug")
                .requires(s -> s.hasPermission(DebugOverlay.PERMISSION_LEVEL))
                .executes(c -> status(c.getSource()));
        for (DebugLayer layer : DebugLayer.values()) {
            debug.then(Commands.literal(layer.id()).executes(c -> toggle(c.getSource(), layer)));
        }
        debug.then(Commands.literal("all").executes(c -> set(c.getSource(), DebugLayer.ALL)));
        debug.then(Commands.literal("off").executes(c -> set(c.getSource(), 0)));
        dispatcher.register(Commands.literal("zas")
                .requires(s -> s.hasPermission(DebugOverlay.PERMISSION_LEVEL))
                .then(debug));
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        report(source, DebugOverlay.layers(source.getPlayerOrException()));
        return 1;
    }

    private static int toggle(CommandSourceStack source, DebugLayer layer) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        return set(source, DebugOverlay.layers(player) ^ layer.bit());
    }

    private static int set(CommandSourceStack source, int layers) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        DebugOverlay.setLayers(player, layers);
        report(source, layers);
        return 1;
    }

    private static void report(CommandSourceStack source, int layers) {
        if (layers == 0) {
            source.sendSuccess(() -> Component.translatable("commands.zas.debug.off"), false);
            return;
        }
        List<String> on = new ArrayList<>();
        for (DebugLayer layer : DebugLayer.values()) {
            if (layer.in(layers)) {
                on.add(layer.id());
            }
        }
        String list = String.join(", ", on);
        source.sendSuccess(() -> Component.translatable("commands.zas.debug.on", list), false);
    }
}
