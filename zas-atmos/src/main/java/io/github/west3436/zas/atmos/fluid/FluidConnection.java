package io.github.west3436.zas.atmos.fluid;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.GasHandlerView;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The direct connection between other mods' fluid handlers and ZAS³'s gas handlers (COMPAT-141,
 * COMPAT-142): a block whose fluid tanks hold gases as fluids joins a pipe with no block in between,
 * and another mod's fluid pipe or machine joins a pipe the same way. Each side is given the other's
 * capability as a view of what is already there, and conversion happens in the call, at the unit
 * factor every mod shares (COMPAT-143) and through the fluid tags' gases (COMPAT-140).
 *
 * <ul>
 *   <li>A block that offers a fluid handler and no gas handler also offers {@link FluidsAsGas}: its
 *       tanks, as one vessel of gas, wherever they hold a gas or would. So a pipe grows an arm to
 *       it, and shares gas with it as with any gas handler (API-025), and never to a water tank.
 *   <li>A block that offers a gas handler and no fluid handler also offers {@link GasAsFluids}: its
 *       gas store, as a tank of one fluid. So another mod's fluid pipe connects to it, and a machine
 *       fills it and drains it.
 * </ul>
 *
 * <p><b>Generic.</b> Nothing here names a block or a mod. It works for any mod's block that offers
 * either capability, through the capabilities' public interfaces.
 *
 * <p><b>Never a view of a view.</b> {@link #register} runs last among the capability listeners and
 * asks, for every block, which of the two capabilities is registered. A block gets a view only of
 * the capability it has, and only if it lacks the other: one that speaks both needs no translation.
 * Each view also checks what it finds when it is used. {@link GasAsFluids} offers nothing for a gas
 * handler that is a {@link GasHandlerView}, such as another adapter's view of a chemical tank, and
 * {@link FluidsAsGas} offers nothing for a fluid handler that is a {@link GasAsFluids}.
 *
 * <p><b>Not twice through one block.</b> A block in {@link #NO_FLUID_VIEW}, which reaches gas as
 * gas, is given no fluid view of a gas store it touches.
 *
 * <p><b>Reached once</b> (COMPAT-146). The Mekanism adapter's connection to chemical handlers runs
 * at the same priority but before this one, since {@code zas} loads before {@code zas_atmos}. A
 * Mekanism block that offers a chemical handler and a fluid handler has a gas handler from there
 * already, so this connection leaves it alone, and it is reached through its chemicals alone.
 * Mekanism's liquefied gases are in {@link FluidGases#NEVER_GAS}, so a pipe's fluid view never takes
 * Mekanism's liquid oxygen from a machine that also offers it oxygen as a chemical.
 */
public final class FluidConnection {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Blocks given no {@link GasAsFluids} view of a gas store they touch: they reach ZAS³ gas as gas,
     * so through a fluid view too they would count it twice. Data, so a pack can add a block; the AE2
     * adapter adds AE2's cable bus.
     */
    public static final TagKey<Block> NO_FLUID_VIEW = TagKey.create(Registries.BLOCK, ZasAtmosMod.id("no_fluid_view"));

    private FluidConnection() {}

    /**
     * Starts the connection: the views, the datapack loader of the fluid tags' gases, and the
     * listeners that make the lookup forget what it resolved when tags or gases may have changed.
     *
     * @param modBus the content mod's bus
     * @param gameBus the game's bus
     */
    public static void init(IEventBus modBus, IEventBus gameBus) {
        // Last of all, so that it sees what every mod registered, the Mekanism adapter's views too.
        modBus.addListener(EventPriority.LOWEST, FluidConnection::register);
        gameBus.addListener((AddReloadListenerEvent event) -> event.addListener(new FluidGasLoader()));
        gameBus.addListener((TagsUpdatedEvent event) -> FluidGases.forget());
        gameBus.addListener((ServerAboutToStartEvent event) -> FluidGases.forget());
        gameBus.addListener((ServerStoppedEvent event) -> FluidGases.forget());
    }

    /**
     * Gives every block that has one capability a view of it as the other. Listen at the lowest
     * priority: every mod's own providers are then registered, whatever order the mods load in.
     *
     * @param event the event
     */
    static void register(RegisterCapabilitiesEvent event) {
        List<Block> stores = new ArrayList<>();
        List<Block> vessels = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            boolean gas = event.isBlockRegistered(IGasHandler.BLOCK, block);
            boolean fluid = event.isBlockRegistered(Capabilities.FluidHandler.BLOCK, block);
            if (gas == fluid) {
                // Neither, or both: a block that speaks both languages needs no translation.
                continue;
            }
            (gas ? stores : vessels).add(block);
        }
        // Only now: every answer above was about what was registered before. The game refuses to
        // register a provider for no blocks.
        if (!stores.isEmpty()) {
            event.registerBlock(Capabilities.FluidHandler.BLOCK, GasAsFluids::of, stores.toArray(Block[]::new));
        }
        if (!vessels.isEmpty()) {
            event.registerBlock(IGasHandler.BLOCK, FluidsAsGas::of, vessels.toArray(Block[]::new));
        }
        LOGGER.debug("Fluid connection: {} gas stores, {} fluid vessels", stores.size(), vessels.size());
    }

    /** Whether a fluid handler is the connection's view, which no view may look through. */
    static boolean isView(@Nullable IFluidHandler handler) {
        return handler instanceof GasAsFluids;
    }

    /** Whether a gas handler is a view, this connection's or another adapter's. */
    static boolean isView(@Nullable IGasHandler handler) {
        return handler instanceof GasHandlerView;
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
