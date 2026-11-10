package io.github.west3436.zas.compat.mekanism;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ServerThread;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalBuilder;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

/**
 * ZAS³'s gases that Mekanism has no chemical for, registered as Mekanism chemicals (MEK-011), so
 * that nitrogen, carbon dioxide and the rest can go into Mekanism's tubes and tanks. Each chemical
 * has its gas's id, {@code zas:nitrogen} for the gas {@code zas:nitrogen}, takes its name from the
 * gas, and is mapped to it both ways by a file in the adapter's data pack, like any other mapping
 * ({@link ChemicalMapFormat}).
 *
 * <p><b>Which gases.</b> A fixed list: Mekanism's registry is filled once, when the game starts,
 * and gases come from datapacks, which load with each server. So a gas a datapack adds can't get a
 * chemical here. A pack can still map it onto a chemical that exists, and {@link #withoutChemical}
 * says which gases have none.
 *
 * <p><b>Colours.</b> The public API has no colour for a gas, so the tints below repeat the colours
 * the content mod's windows show these gases in.
 */
public final class ZasChemicals {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The gases, each with its chemical's tint as RRGGBB. */
    private static final Map<ResourceLocation, Integer> TINTS;

    static {
        Map<ResourceLocation, Integer> tints = new LinkedHashMap<>();
        tints.put(gas("nitrogen"), 0xD92626);
        tints.put(gas("carbon_dioxide"), 0x7A7A7A);
        tints.put(gas("nitrous_oxide"), 0xE7A0A0);
        tints.put(gas("carbon_monoxide"), 0x8A4FD0);
        tints.put(gas("methane"), 0xA96A3C);
        TINTS = Collections.unmodifiableMap(tints);
    }

    private ZasChemicals() {}

    private static ResourceLocation gas(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    /**
     * The gases that are registered as chemicals. A gas's id is its chemical's id too.
     *
     * @return their ids, unmodifiable
     */
    public static Set<ResourceLocation> gases() {
        return TINTS.keySet();
    }

    /**
     * The tint of a gas's chemical.
     *
     * @param gas one of {@link #gases}
     * @return the tint, as RRGGBB
     * @throws IllegalArgumentException if the gas isn't one of them
     */
    public static int tint(ResourceLocation gas) {
        Integer tint = TINTS.get(gas);
        if (tint == null) {
            throw new IllegalArgumentException(gas + " isn't registered as a chemical");
        }
        return tint;
    }

    /** Registers the chemicals, when Mekanism's registry is filled. */
    static void register(RegisterEvent event) {
        event.register(
                MekanismAPI.CHEMICAL_REGISTRY_NAME,
                helper -> TINTS.forEach((gas, tint) -> helper.register(gas, new GasChemical(gas, tint))));
    }

    /**
     * The gases this server defines that never become a chemical: no mapping leads back from them,
     * or the one that does leads to a radioactive chemical (MEK-023). They are gases a datapack
     * added, and the gases of a mod's chemicals when that mod isn't installed.
     *
     * @return their ids, sorted
     */
    @ServerThread
    public static Set<ResourceLocation> withoutChemical() {
        Set<ResourceLocation> gases = new TreeSet<>(GasProperties.defined());
        for (ChemicalMapping mapping : ChemicalGases.map().mappings()) {
            if (mapping.bothWays()) {
                ResourceLocation gas = mapping.gases().keySet().iterator().next();
                double temperature = mapping.entryTemperature(ChemicalUnits.STANDARD_TEMPERATURE);
                if (ChemicalConnection.leavesAs(gas, temperature) != null) {
                    gases.remove(gas);
                }
            }
        }
        return gases;
    }

    /** Says once, when a server has started, which gases stay out of Mekanism. */
    static void logWithoutChemical() {
        Set<ResourceLocation> gases = withoutChemical();
        if (!gases.isEmpty()) {
            LOGGER.info(
                    "These gases have no Mekanism chemical, so they stay out of Mekanism's tubes and tanks: {}."
                            + " A datapack can map one onto a chemical with a file in {}",
                    gases,
                    ChemicalMapFormat.DIRECTORY);
        }
    }

    /** A chemical that is one of ZAS³'s gases, and is named as the gas is. */
    private static final class GasChemical extends Chemical {
        private final ResourceLocation gas;

        GasChemical(ResourceLocation gas, int tint) {
            super(ChemicalBuilder.builder().tint(tint));
            this.gas = gas;
        }

        /** The gas's own name, so the two never differ and the chemical needs no lang entry. */
        @Override
        public String getTranslationKey() {
            return GasProperties.translationKey(gas);
        }
    }
}
