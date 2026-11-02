package io.github.west3436.zas.atmos.gear;

import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
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
