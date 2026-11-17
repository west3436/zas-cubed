package io.github.west3436.zas.atmos.fluid;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.Millibuckets;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.slf4j.Logger;

/**
 * The lookup the fluid connection converts with (COMPAT-140): which gas a fluid is, and which
 * fluids a gas may leave as, at the unit factor every mod shares (COMPAT-143).
 *
 * <p>The map comes from datapacks ({@link FluidGasFormat}), resolved against the server's fluid
 * tags and gases when it is first used after a load: every fluid in a mapped tag takes that tag's
 * mapping, except a fluid in {@link #NEVER_GAS}. A fluid in several mapped tags takes the mapping
 * of the tag whose id sorts first, and if the tags' gases differ the conflict is logged. So a fluid
 * is mapped once, however many packs and tags name it.
 *
 * <p><b>Server side.</b> Every method answers "unmapped" before a server has loaded its data. Each
 * lookup is one hash lookup, so it can be made for every transfer. Call them on the server thread.
 */
public final class FluidGases {
    /**
     * Fluids that never become gas, whatever tags they are in: a mod's liquefied gases, which share
     * a {@code c:} tag with the gas, such as Mekanism's liquid oxygen. Packs add to it.
     */
    public static final TagKey<Fluid> NEVER_GAS = TagKey.create(Registries.FLUID, ZasAtmosMod.id("never_gas"));

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Those that may leave, hottest first, then by fluid id: the order a gas tries them in. */
    private static final Comparator<Mapped> HOTTEST_FIRST = Comparator.comparingDouble(Mapped::leastTemperature)
            .reversed()
            .thenComparing(mapped -> BuiltInRegistries.FLUID.getKey(mapped.fluid()));

    /** The mappings as the datapacks give them, by tag id. */
    private static volatile Map<ResourceLocation, FluidGasMapping> loaded = Map.of();

    /** {@link #loaded} against this server's tags and gases; null until it is next asked for. */
    private static volatile Resolved resolved;

    private FluidGases() {}

    /**
     * One fluid and the mapping it takes.
     *
     * @param fluid the fluid
     * @param mapping its tag's mapping
     */
    public record Mapped(Fluid fluid, FluidGasMapping mapping) {
        /** The gas the fluid is. */
        public ResourceLocation gas() {
            return mapping.gas();
        }

        /**
         * How hot gas must be to leave as this fluid (COMPAT-145).
         *
         * @return the least temperature, in K
         */
        public double leastTemperature() {
            return mapping.leastTemperature(fluid.getFluidType().getTemperature());
        }

        /**
         * The gas some of the fluid is, at the unit factor now set.
         *
         * @param stack the fluid, for its type's temperature
         * @param millibuckets how much of it, not negative
         * @return its gas, at the mapping's temperature
         */
        public GasParcel gas(FluidStack stack, long millibuckets) {
            return mapping.gas(
                    millibuckets, molesPerMillibucket(), stack.getFluidType().getTemperature(stack));
        }

        /**
         * One millibucket of the fluid.
         *
         * @return a stack of it
         */
        public FluidStack unit() {
            return new FluidStack(fluid, 1);
        }
    }

    /**
     * The mappings that apply on this server.
     *
     * @param byFluid each mapped fluid's
     * @param byGas the source fluids each gas may leave as, hottest first
     * @param sources every mapped source fluid, by id
     */
    private record Resolved(
            Map<Fluid, Mapped> byFluid, Map<ResourceLocation, List<Mapped>> byGas, List<Mapped> sources) {}

    /**
     * What a stack's fluid is in ZAS³.
     *
     * @param stack the stack
     * @return its mapping, or empty if the stack is empty or its fluid isn't mapped: it never
     *     becomes gas
     */
    @ServerThread
    public static Optional<Mapped> mapping(FluidStack stack) {
        return stack.isEmpty() ? Optional.empty() : mapping(stack.getFluid());
    }

    /**
     * What a fluid is in ZAS³.
     *
     * @param fluid the fluid
     * @return its mapping, or empty if it isn't mapped
     */
    @ServerThread
    public static Optional<Mapped> mapping(Fluid fluid) {
        return Optional.ofNullable(resolved().byFluid().get(fluid));
    }

    /**
     * The fluids a gas may leave as at a temperature: those it is at least as hot as (COMPAT-145),
     * hottest first, then by fluid id. Only source fluids, and none of a one-way mapping.
     *
     * @param gas the gas
     * @param temperature its temperature, in K
     * @return the fluids, in the order to try them; empty if it may leave as none
     */
    @ServerThread
    public static List<Mapped> leavingAs(ResourceLocation gas, double temperature) {
        List<Mapped> all = resolved().byGas().get(gas);
        if (all == null) {
            return List.of();
        }
        List<Mapped> reached = new ArrayList<>(all.size());
        for (Mapped mapped : all) {
            if (mapped.leastTemperature() <= temperature) {
                reached.add(mapped);
            }
        }
        return reached;
    }

    /**
     * Every mapped source fluid, for asking a tank whether it would hold one.
     *
     * @return them, by fluid id, unmodifiable
     */
    @ServerThread
    public static List<Mapped> sources() {
        return resolved().sources();
    }

    /**
     * The unit factor now set (COMPAT-143), the same as every mod's.
     *
     * @return moles per mB
     */
    public static double molesPerMillibucket() {
        return Millibuckets.molesPerMillibucket();
    }

    /**
     * The moles in some millibuckets, at the unit factor now set.
     *
     * @param millibuckets the amount, not negative
     * @return the moles
     */
    public static double moles(long millibuckets) {
        return Millibuckets.moles(millibuckets, molesPerMillibucket());
    }

    /**
     * The whole millibuckets some gas makes, rounded down (COMPAT-144): take {@link #moles} of the
     * result and leave the rest, and nothing is made or lost.
     *
     * @param moles the gas, in moles
     * @return the whole millibuckets, 0 or more
     */
    public static long wholeMillibuckets(double moles) {
        return Millibuckets.wholeMillibuckets(moles, molesPerMillibucket());
    }

    /**
     * The mappings as the datapacks give them, whether or not this server has each tag and gas.
     *
     * @return them by tag id, sorted
     */
    public static Collection<FluidGasMapping> loadedMappings() {
        return loaded.values();
    }

    /** Takes newly loaded mappings. Called by the loader, on the server's reload. */
    static void loaded(Collection<FluidGasMapping> mappings) {
        TreeMap<ResourceLocation, FluidGasMapping> byTag = new TreeMap<>();
        mappings.forEach(mapping -> byTag.put(mapping.tag(), mapping));
        loaded = Map.copyOf(byTag);
        resolved = null;
    }

    /** Forgets what was resolved, because the server's tags or gases may have changed. */
    public static void forget() {
        resolved = null;
    }

    private static Resolved resolved() {
        Resolved now = resolved;
        if (now == null) {
            now = resolve(new TreeMap<>(loaded));
            resolved = now;
        }
        return now;
    }

    private static Resolved resolve(TreeMap<ResourceLocation, FluidGasMapping> mappings) {
        Map<Fluid, Mapped> byFluid = new IdentityHashMap<>();
        Map<Fluid, ResourceLocation> from = new HashMap<>();
        for (FluidGasMapping mapping : mappings.values()) {
            Optional<HolderSet.Named<Fluid>> tag =
                    BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, mapping.tag()));
            if (tag.isEmpty() || tag.get().size() == 0) {
                // No mod here fills the tag.
                continue;
            }
            if (!GasProperties.isDefined(mapping.gas())) {
                LOGGER.warn(
                        "Ignoring {} {}: the server defines no gas {}",
                        FluidGasFormat.KIND,
                        FluidGasFormat.file(mapping.tag()),
                        mapping.gas());
                continue;
            }
            for (Holder<Fluid> holder : tag.get()) {
                Fluid fluid = holder.value();
                if (fluid == Fluids.EMPTY || holder.is(NEVER_GAS)) {
                    continue;
                }
                Mapped first = byFluid.get(fluid);
                if (first == null) {
                    byFluid.put(fluid, new Mapped(fluid, mapping));
                    from.put(fluid, mapping.tag());
                } else if (!first.gas().equals(mapping.gas())) {
                    LOGGER.warn(
                            "The fluid {} is in #{}, mapped to {}, and in #{}, mapped to {}. It is {}, as the tag "
                                    + "that sorts first says",
                            BuiltInRegistries.FLUID.getKey(fluid),
                            from.get(fluid),
                            first.gas(),
                            mapping.tag(),
                            mapping.gas(),
                            first.gas());
                }
            }
        }
        Map<ResourceLocation, List<Mapped>> byGas = new TreeMap<>();
        List<Mapped> sources = new ArrayList<>();
        for (Mapped mapped : byFluid.values()) {
            Fluid fluid = mapped.fluid();
            if (!fluid.isSource(fluid.defaultFluidState())) {
                continue;
            }
            sources.add(mapped);
            if (!mapped.mapping().oneWay()) {
                byGas.computeIfAbsent(mapped.gas(), gas -> new ArrayList<>()).add(mapped);
            }
        }
        byGas.replaceAll((gas, list) -> {
            list.sort(HOTTEST_FIRST);
            return List.copyOf(list);
        });
        sources.sort(Comparator.comparing(mapped -> BuiltInRegistries.FLUID.getKey(mapped.fluid())));
        return new Resolved(Map.copyOf(byFluid), Map.copyOf(byGas), List.copyOf(sources));
    }
}
