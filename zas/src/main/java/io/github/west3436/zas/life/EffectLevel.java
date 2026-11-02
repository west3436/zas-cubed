package io.github.west3436.zas.life;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Holder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

/**
 * The effect levels a hazard profile can apply (LIFE-040). Minecraft has no paralysis or sleep, so
 * each level is a set of vanilla status effects; a higher level includes the lower one's effects
 * at least as strongly.
 */
public enum EffectLevel implements StringRepresentable {
    /** Nausea and Weakness. */
    IMPAIRED(List.of(new Effect(MobEffects.CONFUSION, 0), new Effect(MobEffects.WEAKNESS, 0))),

    /** Slowness IV, Mining Fatigue III and Weakness II. */
    INCAPACITATED(List.of(
            new Effect(MobEffects.MOVEMENT_SLOWDOWN, 3),
            new Effect(MobEffects.DIG_SLOWDOWN, 2),
            new Effect(MobEffects.WEAKNESS, 1))),

    /** Incapacitated, plus Blindness. */
    UNCONSCIOUS(List.of(
            new Effect(MobEffects.MOVEMENT_SLOWDOWN, 3),
            new Effect(MobEffects.DIG_SLOWDOWN, 2),
            new Effect(MobEffects.WEAKNESS, 1),
            new Effect(MobEffects.BLINDNESS, 0)));

    /** Every level by its name in data, such as {@code "incapacitated"}. */
    public static final Codec<EffectLevel> CODEC = StringRepresentable.fromEnum(EffectLevel::values);

    /**
     * One status effect and its amplifier (0 for level I).
     *
     * @param effect the effect
     * @param amplifier the amplifier, 0 for level I
     */
    public record Effect(Holder<MobEffect> effect, int amplifier) {}

    private final List<Effect> effects;

    EffectLevel(List<Effect> effects) {
        this.effects = effects;
    }

    /** The effects this level applies. */
    public List<Effect> effects() {
        return effects;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
