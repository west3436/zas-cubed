package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.persistence.DimensionData;
import io.github.west3436.zas.provider.PointResolver;
import io.github.west3436.zas.simulation.AtmosService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Internals: breathing from a gas tank instead of the atmosphere (LIFE-021, LIFE-028, LIFE-029,
 * LIFE-043 to LIFE-046).
 *
 * <ul>
 *   <li><b>The switch</b> (LIFE-029). Each entity's internals are on or off, saved with it and kept
 *       through death, until toggled. A player toggles them with a keybind, which sends {@link
 *       TogglePayload}; the server toggles the sender's and tells it what they do now.
 *   <li><b>The seal</b> (LIFE-021). They work only while the head slot, or the entity itself,
 *       seals its breathing ({@link WornProtection#sealedBreathing}).
 *   <li><b>The tank</b> (LIFE-028). They draw from the first internal supply found ({@link
 *       IInternalSupply}): in an armor piece, the chestplate's first, such as a suit's tank slot;
 *       then the offhand; then, for a player, the hotbar from left to right, or else the main hand;
 *       then the entity's own life support.
 *   <li><b>The breath</b> (LIFE-044, LIFE-046). The tank delivers its gas at its distribution
 *       pressure while it holds at least that pressure: the breath is the tank's mixture at that
 *       pressure. Otherwise the entity breathes the atmosphere.
 *   <li><b>The draw</b> (LIFE-043). Each second a breath check charges, the tank gives P_dist ×
 *       0.0625 L / (R · T_tank) moles, vg's 0.5 L per 8 s breath.
 *   <li><b>Gear with a switch of its own</b> (API-022). A supply that says it is {@linkplain
 *       IInternalSupply#alwaysOn() always on} needs no switch of ours: while the breathing is
 *       sealed and no switched tank delivers, the first always-on supply that delivers is breathed
 *       from, found in the same order.
 *   <li><b>The exhale</b> (LIFE-045). The gas drawn goes into the atmosphere at the eyes, after
 *       respiration has turned the O₂ used (LIFE-042's rate) into CO₂, recorded under the LIFE
 *       ledger like respiration. Breathing from a tank, the entity draws no O₂ from its zone.
 * </ul>
 *
 * <p>Server thread, but for the payload's registration.
 */
public final class Internals {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ZasMod.MOD_ID);

    /**
     * What brings a saved switch forward, oldest first: entry {@code i} changes version {@code i + 1}'s
     * tag, in place, into version {@code i + 2}'s (DATA-012). The version written follows from the
     * list.
     */
    private static final List<Consumer<CompoundTag>> SWITCH_STEPS = List.of();

    /** The switch as it's saved. One from before versions were added is the boolean alone, and reads as version 1. */
    private static final IAttachmentSerializer<Tag, Boolean> SWITCH = new IAttachmentSerializer<>() {
        @Override
        public Boolean read(IAttachmentHolder holder, Tag saved, HolderLookup.Provider registries) {
            CompoundTag tag;
            if (saved instanceof CompoundTag versioned) {
                tag = versioned;
            } else {
                tag = new CompoundTag();
                tag.putInt("version", 1);
                tag.put("on", saved);
            }
            DimensionData.migrate("An entity's internals switch", tag, SWITCH_STEPS);
            return tag.getBoolean("on");
        }

        @Override
        public Tag write(Boolean on, HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("version", SWITCH_STEPS.size() + 1);
            tag.putBoolean("on", on);
            return tag;
        }
    };

    /** Whether an entity's internals are on (LIFE-029): saved, and kept through death. */
    public static final Supplier<AttachmentType<Boolean>> ON = ATTACHMENTS.register(
            "internals",
            () -> AttachmentType.builder(() -> false)
                    .serialize(SWITCH)
                    .copyOnDeath()
                    .build());

    /** The volume of one second's breath at the distribution pressure, in L: vg's 0.5 L per 8 s (LIFE-043). */
    public static final double LITRES_PER_SECOND = 0.5 / 8;

    /** The armor slots searched for a supply, the chestplate's first (LIFE-028). */
    private static final EquipmentSlot[] ARMOR = {
        EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private Internals() {}

    /** Registers the attachment and the toggle's payload. Called once, from the mod constructor. */
    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener((RegisterPayloadHandlersEvent e) ->
                e.registrar("1").playToServer(TogglePayload.TYPE, TogglePayload.STREAM_CODEC, Internals::handleToggle));
    }

    // ---- The switch ----------------------------------------------------------------------

    /** Whether an entity's internals are on. */
    public static boolean on(LivingEntity entity) {
        return entity.getExistingData(ON).orElse(false);
    }

    /** Switches an entity's internals on or off. */
    public static void set(LivingEntity entity, boolean on) {
        entity.setData(ON, on);
    }

    /**
     * Toggles a player's internals and tells it, in its action bar, what they do now.
     *
     * @return whether they are now on
     */
    public static boolean toggle(ServerPlayer player) {
        boolean now = !on(player);
        set(player, now);
        player.displayClientMessage(status(player), true);
        return now;
    }

    /**
     * What a player's internals do now, for its action bar. While a supply delivers, that is what
     * they do, even with the switch off and the supply {@linkplain IInternalSupply#alwaysOn() always
     * on}.
     */
    public static Component status(LivingEntity entity) {
        Active active = active(entity);
        if (active != null) {
            return Component.translatable(
                    "message.zas.internals.on",
                    String.format("%.1f", active.supply().distributionPressure()));
        }
        if (!on(entity)) {
            return Component.translatable("message.zas.internals.off");
        }
        if (!WornProtection.sealedBreathing(entity)) {
            return Component.translatable("message.zas.internals.no_seal");
        }
        if (find(entity).isEmpty()) {
            return Component.translatable("message.zas.internals.no_tank");
        }
        return Component.translatable("message.zas.internals.empty");
    }

    /** Handles the keybind's toggle on the server: only for the sender, and not for one dead or spectating (NFR-011). */
    public static void handleToggle(TogglePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isAlive() && !player.isSpectator()) {
            toggle(player);
        }
    }

    /** The keybind's request to toggle the sender's internals; it carries nothing. */
    public record TogglePayload() implements CustomPacketPayload {
        public static final TogglePayload INSTANCE = new TogglePayload();

        public static final Type<TogglePayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "toggle_internals"));

        public static final StreamCodec<io.netty.buffer.ByteBuf, TogglePayload> STREAM_CODEC =
                StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ---- The tank --------------------------------------------------------------------------

    /**
     * An entity breathing from its internals now: the supply, and the breath it delivers.
     *
     * @param supply the tank
     * @param breath the tank's mixture at its distribution pressure
     */
    public record Active(IInternalSupply supply, GasMixture breath) {}

    /**
     * What an entity's internals give it now, or null if it breathes the atmosphere. Its breathing
     * is sealed, and either its internals are on and the first supply found delivers its
     * distribution pressure, or a supply that is {@linkplain IInternalSupply#alwaysOn() always on}
     * does.
     */
    public static @Nullable Active active(LivingEntity entity) {
        if (!WornProtection.sealedBreathing(entity)) {
            return null;
        }
        if (on(entity)) {
            Optional<IInternalSupply> found = find(entity);
            if (found.isPresent() && delivers(found.get())) {
                return new Active(found.get(), delivered(found.get()));
            }
        }
        return first(entity, supply -> supply.alwaysOn() && delivers(supply))
                .map(supply -> new Active(supply, delivered(supply)))
                .orElse(null);
    }

    /** The first internal supply an entity has, in LIFE-028's order. */
    public static Optional<IInternalSupply> find(LivingEntity entity) {
        return first(entity, supply -> true);
    }

    /** The first internal supply an entity has that passes a test, in LIFE-028's order. */
    private static Optional<IInternalSupply> first(LivingEntity entity, Predicate<IInternalSupply> wanted) {
        for (EquipmentSlot slot : ARMOR) {
            Optional<IInternalSupply> s = supply(entity.getItemBySlot(slot)).filter(wanted);
            if (s.isPresent()) {
                return s;
            }
        }
        Optional<IInternalSupply> offhand = supply(entity.getOffhandItem()).filter(wanted);
        if (offhand.isPresent()) {
            return offhand;
        }
        if (entity instanceof Player player) {
            for (int i = 0; i < Inventory.getSelectionSize(); i++) {
                Optional<IInternalSupply> s =
                        supply(player.getInventory().getItem(i)).filter(wanted);
                if (s.isPresent()) {
                    return s;
                }
            }
        } else {
            Optional<IInternalSupply> main = supply(entity.getMainHandItem()).filter(wanted);
            if (main.isPresent()) {
                return main;
            }
        }
        ILifeSupport own = Protections.of(entity);
        return own == null ? Optional.empty() : own.internalSupply().filter(wanted);
    }

    private static Optional<IInternalSupply> supply(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ILifeSupport s = GuardedLifeSupport.of(stack);
        return s == null ? Optional.empty() : s.internalSupply();
    }

    /** Whether a supply delivers its distribution pressure: it is above 0 and the tank holds at least that (LIFE-046). */
    public static boolean delivers(IInternalSupply supply) {
        double dist = supply.distributionPressure();
        GasMixture contents = supply.contents();
        return dist > 0 && contents.totalMoles() > 0 && contents.pressure() >= dist;
    }

    /** The breath a supply delivers: its mixture at its distribution pressure (LIFE-044). */
    public static GasMixture delivered(IInternalSupply supply) {
        GasMixture contents = supply.contents();
        double scale = supply.distributionPressure() / contents.pressure();
        Map<ResourceLocation, Double> moles = new HashMap<>();
        contents.moles().forEach((gas, n) -> moles.put(gas, n * scale));
        return new GasMixture(moles, contents.temperature(), contents.volume());
    }

    /** LIFE-043's draw in mol/s: P_dist × 0.0625 L / (R · T), at a distribution pressure in kPa and a temperature in K. */
    public static double drawRate(double distributionPressure, double temperature) {
        if (!(temperature > 0) || !(distributionPressure > 0)) {
            return 0;
        }
        return distributionPressure * LITRES_PER_SECOND / (GasMixture.GAS_CONSTANT * temperature);
    }

    // ---- The draw and the exhale ---------------------------------------------------------------

    /**
     * Breathes from a supply for {@code seconds}: draws LIFE-043's moles and exhales them at the
     * entity's eyes after respiration (LIFE-045).
     *
     * @return the gas exhaled, empty if none was drawn
     */
    public static GasParcel breathe(ServerLevel level, LivingEntity entity, IInternalSupply supply, double seconds) {
        if (!(seconds > 0)) {
            return GasParcel.EMPTY;
        }
        GasMixture contents = supply.contents();
        double want = drawRate(supply.distributionPressure(), contents.temperature()) * seconds;
        if (!(want > 0)) {
            return GasParcel.EMPTY;
        }
        GasParcel drawn = supply.draw(want);
        GasParcel exhaled = respire(drawn, BreathingConfig.respirationMolesPerSecond() * seconds);
        if (exhaled.isEmpty()) {
            return exhaled;
        }
        AtmosService service = AtmosService.current();
        if (service != null) {
            PointResolver.Cell cell = PointResolver.resolve(level, entity.getEyePosition());
            service.addForLife(level, cell.pos(), cell.sub(), exhaled, Respiration.SOURCE);
        }
        return exhaled;
    }

    /** A breath after respiration: up to {@code o2Used} moles of its O₂ turned into as much CO₂. */
    static GasParcel respire(GasParcel breath, double o2Used) {
        double o2 = breath.moles().getOrDefault(StandardAir.OXYGEN, 0.0);
        double used = Math.min(o2, Math.max(0, o2Used));
        if (!(used > 0)) {
            return breath;
        }
        Map<ResourceLocation, Double> out = new HashMap<>(breath.moles());
        if (o2 - used > 0) {
            out.put(StandardAir.OXYGEN, o2 - used);
        } else {
            out.remove(StandardAir.OXYGEN);
        }
        out.merge(Respiration.CARBON_DIOXIDE, used, Double::sum);
        return new GasParcel(out, breath.temperature());
    }
}
