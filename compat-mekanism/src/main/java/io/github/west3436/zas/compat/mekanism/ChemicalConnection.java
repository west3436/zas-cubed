package io.github.west3436.zas.compat.mekanism;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.IGasHandler;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.IAlloyInteraction;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.radiation.IRadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The direct connection between Mekanism's chemical handlers and ZAS³'s gas handlers (MEK-013):
 * with no block in between, a tube joins a pipe and a pipe joins a tank or machine. Each side is
 * given the other's capability as a view of what is already there, and conversion happens in the
 * call, at the unit factor (MEK-002) and through the chemical map (MEK-010).
 *
 * <ul>
 *   <li>A block that offers a gas handler and no chemical handler also offers {@link
 *       GasAsChemicals}: its gas store, as tanks of chemicals. So a tube connects to it, and a
 *       machine ejects into it.
 *   <li>A block that offers a chemical handler and no gas handler also offers {@link
 *       ChemicalsAsGas}: its tanks, as one vessel of gas. So a pipe grows an arm to it, and shares
 *       gas with it as with any gas handler (API-025).
 *   <li>A tube, and any other transmitter of chemicals, gets {@link TubeJoint} instead: a pipe
 *       joins it and leaves the moving to the tube.
 * </ul>
 *
 * <p><b>Generic.</b> Nothing here names a block. It works for any mod's block that offers either
 * capability, the content mod's pipes and Mekanism's machines being two cases, and it uses only
 * the two capabilities' public interfaces.
 *
 * <p><b>Never a view of a view.</b> {@link #register} runs last among the capability listeners and
 * asks, for every block, which of the two capabilities other mods registered. A block gets a view
 * only of the capability that was there before any view was added, and only if it lacks the other.
 * Each view also checks what it finds when it is used, so one that reaches another view, through a
 * proxying block for instance, holds nothing.
 */
final class ChemicalConnection {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Mekanism's chemical handler capability on blocks. Mekanism's API has no field for it; a
     * capability is made by name, and making it again gives the one Mekanism made.
     */
    static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(MekanismVersions.MOD_ID, "chemical_handler"), IChemicalHandler.class);

    /**
     * The capability Mekanism gives each of its transmitters, for upgrading one with an alloy, and
     * no other block: how a tube is told from a tank, since the API has nothing else that does.
     */
    private static final BlockCapability<IAlloyInteraction, @Nullable Direction> ALLOY = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(MekanismVersions.MOD_ID, "alloy_interaction"),
            IAlloyInteraction.class);

    private ChemicalConnection() {}

    /**
     * Gives every block that has one capability a view of it as the other. Listen at the lowest
     * priority: every mod's own providers are then registered, whatever order the mods load in.
     */
    static void register(RegisterCapabilitiesEvent event) {
        List<Block> stores = new ArrayList<>();
        List<Block> vessels = new ArrayList<>();
        List<Block> tubes = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean gas = event.isBlockRegistered(IGasHandler.BLOCK, block);
            boolean chemical = event.isBlockRegistered(CHEMICAL, block);
            if (gas == chemical) {
                // Neither, or both: a block that speaks both languages needs no translation.
                continue;
            }
            if (gas) {
                stores.add(block);
            } else if (event.isBlockRegistered(ALLOY, block)) {
                tubes.add(block);
            } else {
                vessels.add(block);
            }
        }
        // Only now: every answer above was about what other mods registered. A kind may have no
        // block, as gas stores have none with the content off (CONT-155), and the game refuses to
        // register a provider for no blocks.
        if (!stores.isEmpty()) {
            event.registerBlock(CHEMICAL, GasAsChemicals::of, stores.toArray(Block[]::new));
        }
        if (!vessels.isEmpty()) {
            event.registerBlock(IGasHandler.BLOCK, ChemicalsAsGas::of, vessels.toArray(Block[]::new));
        }
        if (!tubes.isEmpty()) {
            event.registerBlock(IGasHandler.BLOCK, TubeJoint::of, tubes.toArray(Block[]::new));
        }
        LOGGER.debug(
                "Chemical connection: {} gas stores, {} chemical vessels, {} transmitters",
                stores.size(),
                vessels.size(),
                tubes.size());
    }

    /** Whether a gas handler is one of the adapter's views, which no view may look through. */
    static boolean isView(@Nullable IGasHandler handler) {
        return handler instanceof ChemicalsAsGas || handler instanceof TubeJoint;
    }

    /** Whether a chemical handler is one of the adapter's views, which no view may look through. */
    static boolean isView(@Nullable IChemicalHandler handler) {
        return handler instanceof GasAsChemicals;
    }

    /**
     * The chemical a gas leaves as, at its temperature. A gas never becomes a radioactive chemical,
     * even if a pack's mapping leads back to one: its radiation was released when it arrived
     * ({@link #radiate}), so going back would bring it into being again (MEK-023).
     *
     * @return the chemical, or null if the gas stays in ZAS³
     */
    @Nullable
    static Holder<Chemical> leavesAs(ResourceLocation gas, double temperature) {
        Holder<Chemical> chemical = ChemicalGases.chemical(gas, temperature).orElse(null);
        return chemical == null || chemical.value().isRadioactive() ? null : chemical;
    }

    /**
     * Releases the radiation of a chemical that has just become gas, where it did so. This is the
     * one place it is released (MEK-023): the gas carries none, and Mekanism releases none for a
     * chemical that is taken out of a tank in the usual way. Mekanism does it, so its own setting
     * for radiation decides whether anything happens.
     */
    static void radiate(ServerLevel level, BlockPos pos, ChemicalStack arrived) {
        if (!arrived.isEmpty() && arrived.getChemical().isRadioactive()) {
            IRadiationManager.INSTANCE.dumpRadiation(level, pos, arrived);
        }
    }

    /** Logs a fault of the block a view is of, at most once a minute for each kind of block (NFR-013). */
    static void failed(ServerLevel level, BlockPos pos, String what, @Nullable Throwable error) {
        String source = what + " of "
                + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (ThirdPartyFaults.shouldLog(source)) {
            if (error == null) {
                LOGGER.warn("{} didn't do what it said it would (logged at most once a minute)", source);
            } else {
                LOGGER.error("{} threw; nothing was moved (logged at most once a minute)", source, error);
            }
        }
    }
}
