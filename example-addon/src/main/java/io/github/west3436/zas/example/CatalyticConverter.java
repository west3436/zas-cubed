package io.github.west3436.zas.example;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.reaction.ReactionChange;
import io.github.west3436.zas.api.reaction.ReactionContext;
import io.github.west3436.zas.api.reaction.ReactionType;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * A reaction type in code (API-020): CO + ½ O₂ → CO₂, converting a share of the CO each second. {@code
 * data/zas_example/zas/reaction/catalytic_converter.json} names it, gives it that share as a parameter
 * and says when it runs; ZAS³ checks those conditions before it calls this.
 *
 * <p>It runs on the atmos worker, or on whatever thread runs a standalone mixture, so it's a pure
 * function of what it's given. Without ZAS³ the type is kept and never run.
 *
 * @param efficiency the share of the CO it converts each second, 0 to 1
 */
public record CatalyticConverter(double efficiency) implements ReactionType {
    /** The type's id, which a definition names in {@code "type"}. */
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ExampleAddonMod.MOD_ID, "catalytic_converter");

    /** What's registered. Each definition configures its own copy with {@link #withParameters}. */
    public static final CatalyticConverter TYPE = new CatalyticConverter(1);

    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");

    /** The heat of CO + ½ O₂ → CO₂, in J per mol of CO: what the shipped CO combustion releases. */
    private static final double HEAT_PER_MOLE = 283_000;

    private static final Codec<CatalyticConverter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    Codec.doubleRange(0, 1).fieldOf("efficiency").forGetter(CatalyticConverter::efficiency))
            .apply(instance, CatalyticConverter::new));

    @Override
    public ReactionType withParameters(Dynamic<?> parameters) {
        return CODEC.parse(parameters).getOrThrow(IllegalArgumentException::new);
    }

    @Override
    public ReactionChange react(ReactionContext context) {
        double share = 1 - Math.pow(1 - efficiency, context.seconds());
        double moles = Math.min(share * context.available(CO), 2 * context.available(StandardAir.OXYGEN));
        if (!(moles > 0)) {
            return null;
        }
        return new ReactionChange(
                Map.of(CO, moles, StandardAir.OXYGEN, moles / 2), Map.of(CO2, moles), moles * HEAT_PER_MOLE);
    }
}
