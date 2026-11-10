package io.github.west3436.zas.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.Protection;
import io.github.west3436.zas.config.HazardConfig;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.EnvironmentHazards;
import io.github.west3436.zas.life.HazardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * {@code /zas life} and {@code /zas hazards}: what breathing and the hazards make of an entity where
 * it is, and the hazard settings in a dimension (LIFE-010, LIFE-011, LIFE-014, LIFE-031, LIFE-036),
 * documented on the wiki's Commands page. They only read.
 *
 * <p>{@code /zas life} reports the air the rules apply. In another mod's breathable place outside
 * zones that is the place's air, not the open air an atmosphere query finds there (SPACE-017), and
 * the exposure line says {@code local}.
 */
final class LifeCommand {
    /** What the exposure line names instead of the atmosphere's state where another mod's air applies. */
    private static final String LOCAL = "commands.zas.life.local";

    private LifeCommand() {}

    /** The {@code life} node under {@code /zas}. */
    static LiteralArgumentBuilder<CommandSourceStack> lifeNode() {
        return Commands.literal("life")
                .executes(ctx -> life(ctx.getSource(), List.of(ctx.getSource().getEntityOrException())))
                .then(Commands.argument("targets", EntityArgument.entities())
                        .executes(ctx -> life(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"))));
    }

    /** The {@code hazards} node under {@code /zas}. */
    static LiteralArgumentBuilder<CommandSourceStack> hazardsNode() {
        return Commands.literal("hazards").executes(ctx -> hazards(ctx.getSource()));
    }

    private static int life(CommandSourceStack source, Collection<? extends Entity> targets)
            throws CommandSyntaxException {
        int count = 0;
        for (Entity e : targets) {
            if (e instanceof LivingEntity living) {
                lines(living).forEach(line -> source.sendSuccess(() -> line, false));
                count++;
            }
        }
        if (count == 0) {
            source.sendFailure(Component.translatable("commands.zas.life.none"));
        }
        return count;
    }

    /** What the checks make of a living entity where it is now. */
    static List<Component> lines(LivingEntity entity) {
        List<Component> lines = new ArrayList<>();
        Breathability verdict = Breathers.verdict(entity);
        lines.add(Component.translatable(
                "commands.zas.life.header",
                entity.getDisplayName(),
                entity.level().dimension().location().toString(),
                lower(verdict.reason().name()),
                lower(verdict.source().name())));
        lines.add(Component.translatable(
                "commands.zas.life.hazards", names(verdict.hazards()), protections(verdict.protection())));
        if (EnvironmentHazards.affects(entity)) {
            AtmosphereSample sample = Atmospheres.of(entity);
            // The air the rules take it to be in, which outside zones may be another mod's (SPACE-017).
            GasMixture local = BreathingRules.localAir(entity, sample);
            EnvironmentHazards.Exposure x =
                    EnvironmentHazards.exposure(entity, local != null ? local : sample.mixture());
            lines.add(Component.translatable(
                    "commands.zas.life.exposure",
                    ZasCommands.num(x.pressure()),
                    ZasCommands.num(x.effectivePressure()),
                    ZasCommands.num(x.temperature()),
                    local != null
                            ? Component.translatable(LOCAL)
                            : lower(sample.state().name())));
            List<String> rates = new ArrayList<>();
            for (HazardType t : HazardType.values()) {
                if (x.rate(t) > 0) {
                    rates.add(t.id() + " " + ZasCommands.num(x.rate(t)));
                }
            }
            lines.add(Component.translatable(
                    "commands.zas.life.damage", rates.isEmpty() ? "-" : String.join(", ", rates)));
        }
        lines.add(Component.translatable(
                "commands.zas.life.status",
                ZasCommands.num(entity.getHealth()),
                ZasCommands.num(entity.getMaxHealth()),
                entity.getAirSupply(),
                entity.getMaxAirSupply()));
        return lines;
    }

    private static int hazards(CommandSourceStack source) {
        ResourceKey<Level> dimension = source.getLevel().dimension();
        source.sendSuccess(
                () -> Component.translatable(
                        "commands.zas.hazards.header",
                        dimension.location().toString(),
                        ZasCommands.num(HazardConfig.comfortMin()),
                        ZasCommands.num(HazardConfig.comfortMax())),
                false);
        for (HazardType t : HazardType.values()) {
            boolean overridden = HazardConfig.override(dimension.location(), t) != null;
            Component line = Component.translatable(
                    overridden ? "commands.zas.hazards.overridden" : "commands.zas.hazards.line",
                    t.id(),
                    Component.translatable(
                            HazardConfig.enabled(dimension, t)
                                    ? "commands.zas.hazards.on"
                                    : "commands.zas.hazards.off"),
                    ZasCommands.num(HazardConfig.scale(dimension, t)));
            source.sendSuccess(() -> line, false);
        }
        return HazardType.values().length;
    }

    private static String names(Set<Hazard> hazards) {
        return hazards.isEmpty()
                ? "-"
                : String.join(", ", hazards.stream().map(h -> lower(h.name())).toList());
    }

    private static String protections(Set<Protection> protection) {
        return protection.isEmpty()
                ? "-"
                : String.join(
                        ", ", protection.stream().map(p -> lower(p.name())).toList());
    }

    private static String lower(String name) {
        return name.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
