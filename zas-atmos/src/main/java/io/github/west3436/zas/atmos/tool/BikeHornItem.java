package io.github.west3436.zas.atmos.tool;

import io.github.west3436.zas.atmos.registry.AtmosSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The bike horn (CONT-154), an easter egg with vg's sprite and honk. Using it honks and puts it on a
 * {@link #COOLDOWN} cooldown, and every hit it lands on a mob honks too. The server plays the honk
 * for everyone in range, its user included. It has no recipe: it turns up in dungeon and stronghold
 * chests, through the global loot modifiers in {@code data/zas_atmos/loot_modifiers}.
 */
public class BikeHornItem extends Item {
    /** Ticks after a use before the horn can be used again: 2 s. */
    public static final int COOLDOWN = 40;

    public BikeHornItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** The game checks the cooldown before calling this; the check here covers anything that doesn't. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            honk(level, player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Called on the server for each hit that lands. It neither waits for nor starts the cooldown. */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            honk(attacker.level(), attacker);
        }
        return true;
    }

    /**
     * Plays the honk where {@code at} is, for every player in range, {@code at} included.
     *
     * @param level the level, on the server
     * @param at who honked
     */
    public static void honk(Level level, Entity at) {
        level.playSound(
                null, at.getX(), at.getY(), at.getZ(), AtmosSounds.BIKE_HORN.get(), SoundSource.PLAYERS, 1f, 1f);
    }
}
