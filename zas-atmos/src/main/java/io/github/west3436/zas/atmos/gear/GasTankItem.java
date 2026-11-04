package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.failure.TankFailure;
import io.github.west3436.zas.atmos.gas.ContainerReactions;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A personal gas tank (CONT-030): a fixed volume holding a real mixture, carried as {@link
 * AtmosComponents#GAS}, that internals breathe from (LIFE-021). It delivers its gas at its
 * distribution pressure, adjustable from 0 to 304 kPa in its settings screen (LIFE-044), and is
 * filled from a canister (CONT-039). It counts as an internal supply wherever internals look for
 * one: a suit's tank slot, the offhand or the hotbar (LIFE-028).
 */
public class GasTankItem extends Item {
    /** The highest distribution pressure, kPa (LIFE-044). */
    public static final double MAX_DISTRIBUTION_KPA = 304.0;

    /** The default distribution pressure of an O₂ tank, kPa (LIFE-044). */
    public static final double OXYGEN_DISTRIBUTION_KPA = 21.3;

    /** The default distribution pressure of an air tank, kPa (LIFE-044). */
    public static final double AIR_DISTRIBUTION_KPA = 101.3;

    /** The temperature tanks are filled at, K. */
    public static final double FILL_K = StandardAir.TEMPERATURE;

    /** The client's hook that opens a tank's settings screen; set by the client entry point. */
    public static Consumer<InteractionHand> settingsScreen = hand -> {};

    private final double volume;
    private final double defaultDistribution;

    /**
     * @param properties the item's properties; its default {@link AtmosComponents#GAS} is its gas when new
     * @param volume its volume in L
     * @param defaultDistribution its default distribution pressure in kPa
     */
    public GasTankItem(Properties properties, double volume, double defaultDistribution) {
        super(properties.stacksTo(1));
        this.volume = volume;
        this.defaultDistribution = defaultDistribution;
    }

    /** Its volume, L. */
    public double volume() {
        return volume;
    }

    /** Its default distribution pressure, kPa. */
    public double defaultDistribution() {
        return defaultDistribution;
    }

    /**
     * The gas a new tank is filled with: a mixture at a pressure in a volume, at {@link #FILL_K}.
     *
     * @param fractions each gas's share of the moles
     * @param kPa the pressure
     * @param volume the volume, L
     */
    public static GasParcel fill(Map<ResourceLocation, Double> fractions, double kPa, double volume) {
        double total = kPa * volume / (GasMixture.GAS_CONSTANT * FILL_K);
        Map<ResourceLocation, Double> m = new TreeMap<>();
        fractions.forEach((gas, f) -> m.put(gas, total * f));
        return new GasParcel(m, FILL_K);
    }

    // ---- The kind of a 70 L tank ---------------------------------------------------------------

    /** The O₂ share of the moles at or above which a filled 70 L tank is an O₂ tank. */
    public static final double OXYGEN_TANK_SHARE = 0.95;

    /** The O₂ shares between which a filled 70 L tank of N₂ and O₂ is an air tank. */
    public static final double AIR_MIN_OXYGEN = 0.19;

    public static final double AIR_MAX_OXYGEN = 0.23;

    /** The share of the moles a filled air tank's N₂ and O₂ make up at least. */
    public static final double AIR_MIN_N2_O2 = 0.98;

    /**
     * The kind a 70 L tank becomes when filled from a canister (CONT-039, docs/crafting.md: an
     * empty tank filled from an O₂ canister is an O₂ tank, and from air an air tank): an O₂ tank if
     * its gas is at least 95 % O₂, an air tank if it is N₂ and O₂ with 19–23 % O₂, else the plain
     * tank, which is named a gas tank while it holds gas. The emergency tank stays what it is.
     *
     * @param tank the tank as filled
     * @return the tank of its kind: the same stack if its kind hasn't changed, else a new one with
     *     the same gas and settings
     */
    public static ItemStack ofItsKind(ItemStack tank) {
        if (!(tank.getItem() instanceof GasTankItem item) || !item.followsContents()) {
            return tank;
        }
        GasParcel gas = gas(tank);
        Item kind = kindOf(gas);
        if (kind == tank.getItem()) {
            return tank;
        }
        ItemStack out = tank.transmuteCopy(kind, tank.getCount());
        setGas(out, gas);
        return out;
    }

    private static Item kindOf(GasParcel gas) {
        double total = gas.totalMoles();
        if (!(total > 0)) {
            return AtmosItems.EMPTY_TANK.get();
        }
        double o2 = gas.moles(StandardAir.OXYGEN) / total;
        double n2 = gas.moles(StandardAir.NITROGEN) / total;
        if (o2 >= OXYGEN_TANK_SHARE) {
            return AtmosItems.OXYGEN_TANK.get();
        }
        if (o2 >= AIR_MIN_OXYGEN && o2 <= AIR_MAX_OXYGEN && o2 + n2 >= AIR_MIN_N2_O2) {
            return AtmosItems.AIR_TANK.get();
        }
        return AtmosItems.EMPTY_TANK.get();
    }

    /** Whether its kind follows its contents when filled: the 70 L tanks. */
    private boolean followsContents() {
        return this == AtmosItems.EMPTY_TANK.get()
                || this == AtmosItems.OXYGEN_TANK.get()
                || this == AtmosItems.AIR_TANK.get();
    }

    /** The plain tank is named a gas tank while it holds gas. */
    @Override
    public Component getName(ItemStack stack) {
        if (this == AtmosItems.EMPTY_TANK.get() && !gas(stack).isEmpty()) {
            return Component.translatable("item.zas_atmos.empty_tank.filled");
        }
        return super.getName(stack);
    }

    // ---- A tank stack's state --------------------------------------------------------------

    /** The gas a tank stack holds. */
    public static GasParcel gas(ItemStack stack) {
        GasParcel gas = stack.get(AtmosComponents.GAS.get());
        return gas == null ? GasParcel.EMPTY : gas;
    }

    /** The gas a tank stack holds, in its volume. */
    public static GasMixture contents(ItemStack stack) {
        GasParcel gas = gas(stack);
        return new GasMixture(gas.moles(), gas.temperature(), ((GasTankItem) stack.getItem()).volume);
    }

    /** Replaces a tank stack's gas. */
    public static void setGas(ItemStack stack, GasParcel gas) {
        stack.set(AtmosComponents.GAS.get(), gas);
    }

    /** Adds gas to a tank stack, conserving thermal energy (CONT-008). */
    public static void add(ItemStack stack, GasParcel gas) {
        GasStore store = store(stack);
        store.add(gas);
        setGas(stack, new GasParcel(store.moles(), store.temperature()));
    }

    /** Takes up to {@code moles} from a tank stack, in proportion to what it holds. */
    public static GasParcel take(ItemStack stack, double moles) {
        GasStore store = store(stack);
        double total = store.totalMoles();
        if (!(moles > 0) || !(total > 0)) {
            return GasParcel.EMPTY;
        }
        GasParcel taken = store.takeFraction(moles / total);
        setGas(stack, new GasParcel(store.moles(), store.temperature()));
        return taken;
    }

    private static GasStore store(ItemStack stack) {
        GasStore store = new GasStore(((GasTankItem) stack.getItem()).volume);
        store.set(gas(stack));
        return store;
    }

    /** A tank stack's distribution pressure, kPa (LIFE-044). */
    public static double distributionPressure(ItemStack stack) {
        Double kPa = stack.get(AtmosComponents.DISTRIBUTION_PRESSURE.get());
        return kPa != null ? kPa : ((GasTankItem) stack.getItem()).defaultDistribution;
    }

    /** Sets a tank stack's distribution pressure, clamped to 0–304 kPa (LIFE-044); NaN is refused. */
    public static void setDistributionPressure(ItemStack stack, double kPa) {
        if (Double.isNaN(kPa)) {
            return;
        }
        stack.set(AtmosComponents.DISTRIBUTION_PRESSURE.get(), Math.max(0, Math.min(MAX_DISTRIBUTION_KPA, kPa)));
    }

    /**
     * A tank stack as an internal supply: reading and changing the stack as it is then.
     *
     * @param stack the tank
     * @param changed called after each draw, to save a stack held somewhere other than a slot
     */
    public static IInternalSupply supply(ItemStack stack, Runnable changed) {
        return new IInternalSupply() {
            @Override
            public GasMixture contents() {
                return GasTankItem.contents(stack);
            }

            @Override
            public double distributionPressure() {
                return GasTankItem.distributionPressure(stack);
            }

            @Override
            public GasParcel draw(double moles) {
                GasParcel taken = take(stack, moles);
                changed.run();
                return taken;
            }
        };
    }

    /** A tank's life support: its gas, as an internal supply (LIFE-028). */
    public static ILifeSupport lifeSupport(ItemStack stack) {
        return new ILifeSupport() {
            @Override
            public Optional<IInternalSupply> internalSupply() {
                return Optional.of(supply(stack, () -> {}));
            }
        };
    }

    // ---- Atmos ticks --------------------------------------------------------------------------

    /**
     * One atmos tick of a tank, wherever it is: its gas reacts (CONT-100), then it fails if its
     * pressure says so (CONT-038), at the middle of its holder's body. The reaction goes first, so
     * that the check sees the pressure it raised in the same tick. Whatever carries a tank calls
     * this once per atmos tick ({@link AtmosTicks#due}): the tank itself while it is in a player's
     * inventory or dropped, and a suit or a tank transfer valve for the tanks inside it. A tank
     * anywhere else, such as a chest, waits until it is carried again (architecture §11.9).
     *
     * @param tank the tank's stack, changed in place, and used up if the tank is destroyed
     * @param level the level it is in
     * @param holder the entity carrying it: the player, or the dropped item
     * @return whether the stack changed, so that a holder of a copy knows to write it back
     */
    public static boolean atmosTick(ItemStack tank, ServerLevel level, Entity holder) {
        boolean reacted = ContainerReactions.react(tank, ((GasTankItem) tank.getItem()).volume);
        ContainerFailure.Verdict verdict = TankFailure.check(level, TankFailure.pointOf(holder), tank, null);
        return reacted || verdict.kind() != ContainerFailure.Kind.NONE;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level instanceof ServerLevel server && AtmosTicks.due(server, entity)) {
            atmosTick(stack, server, entity);
        }
    }

    /** A tank that is destroyed on the ground takes its item entity with it. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level() instanceof ServerLevel server && AtmosTicks.due(server, entity)) {
            atmosTick(stack, server, entity);
            if (stack.isEmpty()) {
                entity.discard();
                return true;
            }
        }
        return false;
    }

    // ---- Use and tooltip --------------------------------------------------------------------

    /** Opens the settings screen, to adjust the distribution pressure. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            settingsScreen.accept(hand);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        lines(stack, tooltip);
    }

    /** A tank's contents and distribution pressure, as its tooltip and a suit's list them. */
    public static void lines(ItemStack stack, List<Component> out) {
        GasParcel gas = gas(stack);
        if (gas.isEmpty()) {
            out.add(Component.translatable("item.zas_atmos.canister.empty").withStyle(ChatFormatting.GRAY));
        } else {
            out.add(Component.translatable(
                            "item.zas_atmos.canister.pressure",
                            String.format(Locale.ROOT, "%.0f", contents(stack).pressure()),
                            String.format(Locale.ROOT, "%.1f", gas.temperature()))
                    .withStyle(ChatFormatting.GRAY));
            gas.moles()
                    .forEach((id, n) -> out.add(Component.translatable(
                                    "item.zas_atmos.canister.gas",
                                    GasCodecs.name(id),
                                    String.format(Locale.ROOT, "%.2f", n))
                            .withStyle(ChatFormatting.GRAY)));
        }
        out.add(Component.translatable(
                        "item.zas_atmos.gas_tank.distribution",
                        String.format(Locale.ROOT, "%.1f", distributionPressure(stack)))
                .withStyle(ChatFormatting.GRAY));
    }
}
