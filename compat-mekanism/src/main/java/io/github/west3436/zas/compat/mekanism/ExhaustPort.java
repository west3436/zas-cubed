package io.github.west3436.zas.compat.mekanism;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.mutation.SourceId;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/**
 * The exhaust port (MEK-022): a block that takes Mekanism's chemicals through its back face, from
 * a pressurized tube or a machine's side set to eject, and releases them as gas in front of its
 * grille. It is the adapter's one block, a sibling of the content mod's passive vent for
 * Mekanism's machinery, and needs none of the venting's mixins.
 *
 * <p>It is registered by the adapter, under the id of the mod the adapter ships in, so it exists
 * exactly when Mekanism is installed in a supported version, whether or not ZAS³'s own content is
 * switched on (D-88). Its recipe, loot table and tool tag are in the adapter's data pack, which
 * loads on the same condition.
 *
 * <p>The only capability it offers is Mekanism's chemical handler, on its back face.
 *
 * <p><b>No pipe joins it.</b> A pipe grows an arm to any neighbour that offers a gas handler toward
 * it and shares gas with it, and the adapter gives a block that offers Mekanism's chemical handler
 * and no gas handler a view of its tanks as one. Through that view a pipe would empty itself into
 * the room at the port, with none of a vent's rates or pressure checks. The port is for Mekanism's
 * machinery, and a pipe has vents of its own. So the port registers the gas-handler capability
 * itself, with a provider that offers nothing on any face: a block that has registered both
 * capabilities is given no view, and a pipe that asks the port finds no handler. Nothing can draw
 * from it either.
 */
public final class ExhaustPort {
    /** The path of the block's and the item's id. */
    public static final String ID = "exhaust_port";

    /** Who the gas a port releases is from, in ZAS³'s ledger and logs. */
    public static final SourceId SOURCE = SourceId.of("zas", "mekanism_exhaust_port");

    /**
     * Mekanism's chemical capability for blocks. Mekanism's API has the handler's interface but not
     * the capability, which a mod makes for itself under Mekanism's name for it: NeoForge hands
     * out one capability per name and type, so this is the one Mekanism's tubes and machines ask.
     */
    public static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath(MekanismVersions.MOD_ID, "chemical_handler"), IChemicalHandler.class);

    /** The content mod's creative tab and its passive vent, by id: the adapter can't name its classes. */
    private static final ResourceLocation ATMOSPHERICS_TAB =
            ResourceLocation.fromNamespaceAndPath("zas_atmos", "atmospherics");

    private static final ResourceLocation PASSIVE_VENT =
            ResourceLocation.fromNamespaceAndPath("zas_atmos", "passive_vent");

    private static volatile DeferredBlock<ExhaustPortBlock> block;
    private static volatile DeferredItem<BlockItem> item;

    private ExhaustPort() {}

    /**
     * Registers the block, its item, its capability and its place in a creative tab. Called once,
     * from {@link MekanismCompat#init}.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param modId that mod's id, the namespace of the block
     */
    static void register(IEventBus modBus, String modId) {
        DeferredRegister.Blocks blocks = DeferredRegister.createBlocks(modId);
        DeferredRegister.Items items = DeferredRegister.createItems(modId);
        // As hard as a hopper and mined with a pickaxe, like the content mod's metal parts.
        block = blocks.registerBlock(
                ID,
                ExhaustPortBlock::new,
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(3.0f, 4.8f)
                        .requiresCorrectToolForDrops()
                        .sound(SoundType.METAL));
        item = items.registerSimpleBlockItem(block);
        blocks.register(modBus);
        items.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent event) -> {
            event.registerBlock(CHEMICAL, ExhaustPort::handler, block.get());
            // On purpose, and not a gas handler: registering says the port has answered for itself,
            // so nothing gives it a view of its chemical handler, and the answer is always none.
            event.registerBlock(IGasHandler.BLOCK, (level, pos, state, blockEntity, side) -> null, block.get());
        });
        modBus.addListener(ExhaustPort::addToTab);
    }

    /**
     * The block.
     *
     * @return the exhaust port's block
     * @throws IllegalStateException if the adapter hasn't started
     */
    public static ExhaustPortBlock block() {
        DeferredBlock<ExhaustPortBlock> registered = block;
        if (registered == null) {
            throw new IllegalStateException("The Mekanism adapter hasn't started");
        }
        return registered.get();
    }

    /**
     * The block's item.
     *
     * @return the exhaust port's item
     * @throws IllegalStateException if the adapter hasn't started
     */
    public static Item item() {
        DeferredItem<BlockItem> registered = item;
        if (registered == null) {
            throw new IllegalStateException("The Mekanism adapter hasn't started");
        }
        return registered.get();
    }

    /** The port's handler for a face: its back's on a server, and nothing anywhere else. */
    private static @Nullable IChemicalHandler handler(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, @Nullable Direction side) {
        if (!(level instanceof ServerLevel server) || side == null) {
            return null;
        }
        Direction facing = state.getValue(ExhaustPortBlock.FACING);
        return side == facing.getOpposite() ? new ExhaustPortHandler(server, pos, facing) : null;
    }

    /**
     * Puts the port after the passive vent in the content mod's tab. With the content switched off
     * there is no such tab, and it goes with the game's functional blocks.
     */
    private static void addToTab(BuildCreativeModeTabContentsEvent event) {
        ItemStack port = new ItemStack(item());
        if (BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(ATMOSPHERICS_TAB)) {
            if (!event.getTabKey().location().equals(ATMOSPHERICS_TAB)) {
                return;
            }
            ItemStack vent = new ItemStack(BuiltInRegistries.ITEM.get(PASSIVE_VENT));
            if (!vent.isEmpty()
                    && event.getParentEntries().contains(vent)
                    && event.getSearchEntries().contains(vent)) {
                event.insertAfter(vent, port, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            } else {
                event.accept(port);
            }
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(port);
        }
    }
}
