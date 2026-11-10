package io.github.west3436.zas.compat.mekanism;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * The lookup every part of the adapter converts with: which gas a Mekanism chemical is, which
 * chemical a gas is (MEK-010), and how much (MEK-002).
 *
 * <pre>{@code
 * // Chemical to gas: what 250 mB of a stack is, arriving from somewhere at 293.15 K.
 * Optional<ChemicalMapping> mapping = ChemicalGases.mapping(stack);
 * if (mapping.isPresent()) {            // unmapped chemicals never become gas
 *     GasParcel gas = mapping.get().gas(250, ChemicalGases.molesPerMillibucket(), 293.15);
 * }
 *
 * // Gas to chemical: what 12.5 mol of a gas at 400 K is.
 * Optional<Holder<Chemical>> chemical = ChemicalGases.chemical(gasId, 400);
 * long millibuckets = ChemicalGases.wholeMillibuckets(12.5);   // rounded down
 * double taken = ChemicalGases.moles(millibuckets);             // take this much, leave the rest
 * }</pre>
 *
 * <p>The map comes from datapacks ({@link ChemicalMapFormat}) and the unit factor from the server
 * config ({@link MekanismSettings}); both are read on each call, so a {@code /reload} or an edited
 * config applies at once. A mapping applies only if Mekanism has registered its chemical, the
 * chemical isn't radioactive and the server defines every gas it names; one that is radioactive or
 * names a gas the server doesn't define is logged once for each load.
 *
 * <p><b>Server side.</b> The map is the running server's, so every method answers "unmapped" on a
 * client that is connected to a server elsewhere, and before a server has started. Each lookup is
 * one hash lookup, so it can be made for every transfer. Call them on the server thread.
 */
public final class ChemicalGases {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The map as the datapacks give it. */
    private static volatile ChemicalMap loaded = ChemicalMap.EMPTY;

    /** {@link #loaded} against this server's chemicals and gases; null until it is next asked for. */
    private static volatile Resolved resolved;

    /**
     * The mappings that apply on this server.
     *
     * @param byChemical them by chemical
     * @param map them by id, for the way back
     * @param holders the chemicals by id
     */
    private record Resolved(
            Map<Chemical, ChemicalMapping> byChemical,
            ChemicalMap map,
            Map<ResourceLocation, Holder<Chemical>> holders) {}

    private ChemicalGases() {}

    /**
     * What a chemical is in ZAS³.
     *
     * @param chemical the chemical
     * @return its mapping, or empty if it isn't mapped: it never becomes gas
     */
    @ServerThread
    public static Optional<ChemicalMapping> mapping(Chemical chemical) {
        return Optional.ofNullable(resolved().byChemical().get(chemical));
    }

    /**
     * What a chemical is in ZAS³.
     *
     * @param chemical the chemical's holder
     * @return its mapping, or empty if it isn't mapped: it never becomes gas
     */
    @ServerThread
    public static Optional<ChemicalMapping> mapping(Holder<Chemical> chemical) {
        return mapping(chemical.value());
    }

    /**
     * What a stack's chemical is in ZAS³.
     *
     * @param stack the stack
     * @return its chemical's mapping, or empty if the stack is empty or its chemical isn't mapped
     */
    @ServerThread
    public static Optional<ChemicalMapping> mapping(ChemicalStack stack) {
        return stack.isEmpty() ? Optional.empty() : mapping(stack.getChemical());
    }

    /**
     * The gas a stack is, at the unit factor now set.
     *
     * @param stack the stack
     * @param sourceTemperature the temperature of wherever it comes from, in K, finite and not
     *     negative; the mapping may raise it ({@link ChemicalMapping#entryTemperature})
     * @return its gas, or {@link GasParcel#EMPTY} if the stack is empty or its chemical isn't mapped
     */
    @ServerThread
    public static GasParcel gas(ChemicalStack stack, double sourceTemperature) {
        return mapping(stack)
                .map(mapping -> mapping.gas(stack.getAmount(), molesPerMillibucket(), sourceTemperature))
                .orElse(GasParcel.EMPTY);
    }

    /**
     * The chemical a gas is in Mekanism.
     *
     * @param gas the gas's id
     * @param temperature the gas's temperature, in K, which picks among several chemicals of one
     *     gas ({@link ChemicalMap#forGas})
     * @return the chemical, or empty if no mapping leads back from the gas: it never becomes a
     *     chemical
     */
    @ServerThread
    public static Optional<Holder<Chemical>> chemical(ResourceLocation gas, double temperature) {
        Resolved now = resolved();
        return now.map().forGas(gas, temperature).map(mapping -> now.holders().get(mapping.chemical()));
    }

    /**
     * The unit factor now set (MEK-002).
     *
     * @return moles per mB
     */
    public static double molesPerMillibucket() {
        return MekanismSettings.molesPerMillibucket();
    }

    /**
     * The moles in an amount of a chemical, at the unit factor now set.
     *
     * @param millibuckets the amount, not negative
     * @return the moles
     */
    public static double moles(long millibuckets) {
        return ChemicalUnits.moles(millibuckets, molesPerMillibucket());
    }

    /**
     * The whole millibuckets an amount of gas makes, at the unit factor now set, rounded down
     * ({@link ChemicalUnits#wholeMillibuckets}): take {@link #moles} of the result out of the gas
     * and leave the rest, and nothing is made or lost.
     *
     * @param moles the gas, in moles
     * @return the whole millibuckets, 0 or more
     */
    public static long wholeMillibuckets(double moles) {
        return ChemicalUnits.wholeMillibuckets(moles, molesPerMillibucket());
    }

    /**
     * The map as the datapacks give it, whether or not this server has each chemical and gas.
     *
     * @return the loaded map; empty before a server has loaded its data
     */
    public static ChemicalMap loadedMap() {
        return loaded;
    }

    /**
     * The mappings that apply on this server: those whose chemical Mekanism has registered and
     * isn't radioactive, and whose gases the server defines.
     *
     * @return the map the lookups answer from
     */
    @ServerThread
    public static ChemicalMap map() {
        return resolved().map();
    }

    /** Takes a newly loaded map. Called by the loader, on the server's reload. */
    static void loaded(ChemicalMap map) {
        loaded = map;
        resolved = null;
    }

    /** Forgets what was resolved, because the server's gases may have changed: one starts or stops. */
    static void forget() {
        resolved = null;
    }

    private static Resolved resolved() {
        Resolved now = resolved;
        if (now == null) {
            now = resolve(loaded);
            resolved = now;
        }
        return now;
    }

    private static Resolved resolve(ChemicalMap map) {
        Map<Chemical, ChemicalMapping> byChemical = new IdentityHashMap<>();
        Map<ResourceLocation, Holder<Chemical>> holders = new HashMap<>();
        List<ChemicalMapping> usable = new ArrayList<>();
        for (ChemicalMapping mapping : map.mappings()) {
            Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                    ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, mapping.chemical()));
            if (holder.isEmpty()) {
                // Another mod's chemical, and the mod isn't here: Mekanism Generators' are the usual case.
                continue;
            }
            if (holder.get().value().isRadioactive()) {
                // Never mapped, whatever a datapack says (MEK-010): ZAS³ has no radiation, so as a
                // gas the chemical would shed its hazard.
                LOGGER.warn(
                        "Ignoring {} {}: a radioactive chemical is never a gas",
                        ChemicalMapFormat.KIND,
                        ChemicalMapFormat.file(mapping.chemical()));
                continue;
            }
            List<ResourceLocation> undefined = mapping.gases().keySet().stream()
                    .filter(gas -> !GasProperties.isDefined(gas))
                    .toList();
            if (!undefined.isEmpty()) {
                LOGGER.warn(
                        "Ignoring {} {}: the server defines no gas {}",
                        ChemicalMapFormat.KIND,
                        ChemicalMapFormat.file(mapping.chemical()),
                        undefined);
                continue;
            }
            byChemical.put(holder.get().value(), mapping);
            holders.put(mapping.chemical(), holder.get());
            usable.add(mapping);
        }
        return new Resolved(byChemical, ChemicalMap.of(usable), Map.copyOf(holders));
    }
}
