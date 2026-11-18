package io.github.west3436.zas.atmos.ttv;

import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A remote signaler (CONT-185): a handheld radio with a frequency and a code. Using it opens its
 * window, where both are set and Send sends a signal; sneak-using it sends at once. A primed TTV fitted
 * with a signaler set the same, in the same dimension and within {@code [ttv_triggers] signaler_range}
 * blocks, opens its valve when it hears one (CONT-186). A TTV in an unloaded chunk hears nothing.
 *
 * <p>Fitted to a TTV in its timer's place (CONT-184), the signaler is what the TTV listens with, and
 * keeps its own frequency and code. A signaler can send once every {@value #COOLDOWN_TICKS} game ticks.
 */
public class RemoteSignalerItem extends Item {
    /** Game ticks between two signals from one player's signalers. */
    public static final int COOLDOWN_TICKS = 10;

    public RemoteSignalerItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** A signaler's frequency and code. */
    public static Signal signal(ItemStack signaler) {
        return signaler.getOrDefault(AtmosComponents.SIGNAL.get(), Signal.DEFAULT);
    }

    /** Sets a signaler's frequency and code. */
    public static void setSignal(ItemStack signaler, Signal signal) {
        signaler.set(AtmosComponents.SIGNAL.get(), signal);
    }

    /**
     * Sends a signal from a point (CONT-186): each primed TTV in the level fitted with a signaler set
     * the same, within range, opens its valve.
     *
     * @param level the level, whose loaded entities alone hear it
     * @param from where it is sent from
     * @param signal its frequency and code
     * @return how many TTVs opened
     */
    public static int send(ServerLevel level, Vec3 from, Signal signal) {
        int range = AtmosServerConfig.get(AtmosServerConfig.SIGNALER_RANGE);
        List<? extends PrimedTransferValve> heard;
        if (range <= 0) {
            heard = level.getEntities(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), ttv -> ttv.hears(signal));
        } else {
            heard = level.getEntities(
                    AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(),
                    new AABB(from, from).inflate(range),
                    ttv -> ttv.hears(signal) && ttv.distanceToSqr(from) <= (double) range * range);
        }
        heard.forEach(PrimedTransferValve::signalled);
        return heard.size();
    }

    /**
     * Sends a player's signaler's signal from where they stand, with its beep, unless it is cooling
     * down. Server side.
     *
     * @return how many TTVs opened, or -1 if it didn't send
     */
    public static int send(ServerPlayer player, ItemStack signaler) {
        if (!(signaler.getItem() instanceof RemoteSignalerItem item)
                || player.getCooldowns().isOnCooldown(item)
                || player.isSpectator()) {
            return -1;
        }
        player.getCooldowns().addCooldown(item, COOLDOWN_TICKS);
        ServerLevel level = player.serverLevel();
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.NOTE_BLOCK_BIT,
                SoundSource.PLAYERS,
                0.6f,
                1.8f);
        return send(level, player.position(), signal(signaler));
    }

    /** Opens its window; sneak-used, it sends at once. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer server) {
            if (player.isSecondaryUseActive()) {
                send(server, stack);
            } else {
                RemoteSignalerMenu.open(server, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Signal signal = signal(stack);
        tooltip.add(
                Component.translatable("item.zas_atmos.remote_signaler.signal", signal.frequencyText(), signal.code())
                        .withStyle(ChatFormatting.GRAY));
    }
}
