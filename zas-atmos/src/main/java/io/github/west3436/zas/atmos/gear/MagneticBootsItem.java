package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

/**
 * Magnetic boots (CONT-033): while on, they hold their wearer against airflow, an airflow
 * resistance of 1 (FLOW-012). They protect the feet as the hardsuit's boots do, so they can take
 * their place in a hardsuit without leaving a gap, and they don't slow their wearer.
 *
 * <p><b>On and off.</b> Whether they're on is the boots' own, {@link AtmosComponents#MAGNETIZED}, so
 * it's saved with them and goes wherever they go: through death, kept or dropped, and onto whoever
 * wears them next. New boots are off. A player switches the boots they wear with a keybind, which
 * sends {@link TogglePayload}; the server switches the sender's and answers in the action bar. The
 * item and the worn boots show which they are: the item through its {@code zas_atmos:on} model
 * property, the worn boots through {@link #getArmorTexture}.
 */
public class MagneticBootsItem extends SuitItem {
    /** The worn boots while on: the off layer with its light green. */
    public static final ResourceLocation ON_TEXTURE =
            ZasAtmosMod.id("textures/models/armor/magnetic_boots_on_layer_1.png");

    public MagneticBootsItem(Properties properties) {
        super(GearMaterials.MAGNETIC_BOOTS, Type.BOOTS, properties, Rating.hardsuit(false), false);
    }

    /** Whether a stack of boots is on. */
    public static boolean on(ItemStack boots) {
        return boots.getOrDefault(AtmosComponents.MAGNETIZED.get(), false);
    }

    /** Switches a stack of boots on or off. */
    public static void set(ItemStack boots, boolean on) {
        if (on) {
            boots.set(AtmosComponents.MAGNETIZED.get(), true);
        } else {
            boots.remove(AtmosComponents.MAGNETIZED.get());
        }
    }

    @Override
    protected double airflowResistance(ItemStack stack) {
        return on(stack) ? 1 : 0;
    }

    @Override
    public @Nullable ResourceLocation getArmorTexture(
            ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return on(stack) ? ON_TEXTURE : null;
    }

    // ---- The keybind --------------------------------------------------------------------------

    /**
     * Switches the boots a player wears and tells it, in its action bar, whether they're on now or
     * it wears none.
     *
     * @return whether its boots are on now; false if it wears none
     */
    public static boolean toggle(ServerPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        if (!(boots.getItem() instanceof MagneticBootsItem)) {
            player.displayClientMessage(Component.translatable("message.zas_atmos.magnetic_boots.none"), true);
            return false;
        }
        boolean now = !on(boots);
        set(boots, now);
        player.displayClientMessage(
                Component.translatable(
                        now ? "message.zas_atmos.magnetic_boots.on" : "message.zas_atmos.magnetic_boots.off"),
                true);
        return now;
    }

    /** Handles the keybind's toggle on the server: only for the sender, and not for one dead or spectating (NFR-011). */
    public static void handleToggle(TogglePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            toggle(player);
        }
    }

    /** The keybind's request to switch the sender's magnetic boots; it carries nothing. */
    public record TogglePayload() implements CustomPacketPayload {
        public static final TogglePayload INSTANCE = new TogglePayload();

        public static final Type<TogglePayload> TYPE = new Type<>(ZasAtmosMod.id("toggle_magnetic_boots"));

        public static final StreamCodec<io.netty.buffer.ByteBuf, TogglePayload> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
