package io.github.west3436.zas.compat.mekanism.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * What a connection test builds with: the content mod's pipes and Mekanism's tubes and tanks, in the
 * test's own structure, where the game ticks them as it would anywhere. Blocks are found by id and
 * gas is put in and read through the two public capabilities, as another mod would, so the tests
 * need nothing of the content mod's code. They reach into Mekanism for two things a player sets by
 * hand: a tube's side mode, with a configurator, and whether a tank ejects, in its side configuration.
 */
final class Bench {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;
    static final ResourceLocation H2 = id("zas:hydrogen");
    static final ResourceLocation CO2 = id("zas:carbon_dioxide");
    static final ResourceLocation H2O = id("zas:water_vapor");

    /** The temperature a chemical arrives at: standard air's. */
    static final double T = StandardAir.TEMPERATURE;

    /** The highest pressure a pipe takes gas at, in kPa: the most a ZAS³ pump reaches. */
    static final double PIPE_LIMIT = 4_500;

    /** Mekanism's chemical handler capability, by its name, as the adapter finds it. */
    static final BlockCapability<IChemicalHandler, @Nullable Direction> CHEMICAL = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    /** How close two amounts must be to count as the same, as a share of the larger. */
    private static final double REL = 1e-9;

    final GameTestHelper h;
    final ServerLevel level;

    Bench(GameTestHelper h) {
        this.h = h;
        this.level = h.getLevel();
    }

    static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }

    BlockPos at(int x, int y, int z) {
        return h.absolutePos(new BlockPos(x, y, z));
    }

    // ---- Building ------------------------------------------------------------------------------

    private static Block block(String name) {
        return BuiltInRegistries.BLOCK
                .getOptional(id(name))
                .orElseThrow(() -> new GameTestAssertException("no block " + name + " on this server"));
    }

    /**
     * Places a block as a player's item would: shaped by its neighbours, given the item's components,
     * which is where Mekanism keeps what each side of a new block does, then told it was placed.
     */
    void place(int x, int y, int z, BlockState state) {
        BlockPos pos = at(x, y, z);
        BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
        level.setBlockAndUpdate(pos, shaped);
        ItemStack item = new ItemStack(shaped.getBlock());
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity != null && !item.isEmpty()) {
            entity.applyComponentsFromItemStack(item);
            entity.setChanged();
        }
        shaped.getBlock().setPlacedBy(level, pos, shaped, null, item);
    }

    /** A ZAS³ pipe. */
    void pipe(int x, int y, int z) {
        place(x, y, z, block("zas_atmos:pipe").defaultBlockState());
    }

    /** A ZAS³ pipe cap, closing the pipe it faces. */
    void cap(int x, int y, int z, Direction facing) {
        place(x, y, z, block("zas_atmos:pipe_cap").defaultBlockState().setValue(BlockStateProperties.FACING, facing));
    }

    /** A cap at x and a pipe at x + 1: a closed network of two parts whose east end is free. */
    void cappedPipe(int x, int y, int z) {
        cap(x, y, z, Direction.EAST);
        pipe(x + 1, y, z);
    }

    /** A Basic Pressurized Tube. */
    void tube(int x, int y, int z) {
        place(x, y, z, block("mekanism:basic_pressurized_tube").defaultBlockState());
    }

    /**
     * A Basic Chemical Tank, 64,000 mB, as a player places one: its front gives and ejects, 1,000 mB
     * a tick, into whatever takes chemicals there, and its other five faces take.
     */
    void tank(int x, int y, int z, Direction front) {
        place(x, y, z, with(block("mekanism:basic_chemical_tank").defaultBlockState(), "facing", front));
    }

    /**
     * A Creative Chemical Tank that holds a chemical, its sides as a Basic tank's. It is the one tank
     * that holds a radioactive chemical, and it never runs down: what is taken from it is still there.
     */
    void creativeTank(int x, int y, int z, Direction front, ChemicalStack holding) {
        place(x, y, z, with(block("mekanism:creative_chemical_tank").defaultBlockState(), "facing", front));
        chemical(x, y, z, front).setChemicalInTank(0, holding);
        equal(name(held(x, y, z)), name(holding), "what the creative tank holds");
    }

    /** A tank whose front gives when asked and ejects nothing, as a player sets in its side configuration. */
    void quietTank(int x, int y, int z, Direction front) {
        tank(x, y, z, front);
        if (level.getBlockEntity(at(x, y, z)) instanceof ISideConfiguration tile) {
            tile.getConfig().getConfig(TransmissionType.CHEMICAL).setEjecting(false);
        } else {
            throw new GameTestAssertException("the tank at " + x + ", " + y + ", " + z + " has no side configuration");
        }
    }

    /**
     * Turns a face of a Mekanism block off for chemicals, as a player does in its side configuration:
     * the block then offers no chemical handler there.
     */
    void faceOff(int x, int y, int z, Direction face) {
        if (level.getBlockEntity(at(x, y, z)) instanceof ISideConfiguration tile) {
            RelativeSide side = RelativeSide.fromDirections(tile.getDirection(), face);
            h.assertTrue(
                    tile.getConfig().getConfig(TransmissionType.CHEMICAL).setDataType(DataType.NONE, side),
                    "the " + side + " side of the block at " + x + ", " + y + ", " + z + " was off already");
            tile.getConfig().sideChanged(TransmissionType.CHEMICAL, side);
        } else {
            throw new GameTestAssertException("the block at " + x + ", " + y + ", " + z + " has no side configuration");
        }
    }

    /** Another block of the game or a mod, in its default state. */
    void other(int x, int y, int z, String name) {
        place(x, y, z, block(name).defaultBlockState());
    }

    /** Puts a block's default state somewhere and tells it nothing: for looking at it, not for using it. */
    void bare(int x, int y, int z, String name) {
        level.setBlockAndUpdate(at(x, y, z), block(name).defaultBlockState());
    }

    private static <V extends Comparable<V>> BlockState with(BlockState state, String property, Object value) {
        @SuppressWarnings("unchecked")
        Property<V> found = (Property<V>) state.getBlock().getStateDefinition().getProperty(property);
        if (found == null) {
            throw new GameTestAssertException(state + " has no property " + property);
        }
        @SuppressWarnings("unchecked")
        V typed = (V) value;
        return state.setValue(found, typed);
    }

    /** Whether a pipe has grown an arm on a face. */
    boolean arm(int x, int y, int z, Direction face) {
        BlockState state = level.getBlockState(at(x, y, z));
        return state.getValue(
                switch (face) {
                    case NORTH -> BlockStateProperties.NORTH;
                    case SOUTH -> BlockStateProperties.SOUTH;
                    case EAST -> BlockStateProperties.EAST;
                    case WEST -> BlockStateProperties.WEST;
                    case UP -> BlockStateProperties.UP;
                    case DOWN -> BlockStateProperties.DOWN;
                });
    }

    // ---- Tubes ---------------------------------------------------------------------------------

    private Transmitter<?, ?, ?> transmitter(int x, int y, int z) {
        if (level.getBlockEntity(at(x, y, z)) instanceof TileEntityTransmitter tile) {
            return tile.getTransmitter();
        }
        throw new GameTestAssertException("no tube at " + x + ", " + y + ", " + z);
    }

    /** Whether a tube draws a connection on a face: it has found something there to connect to. */
    boolean tubeConnects(int x, int y, int z, Direction face) {
        return transmitter(x, y, z).getConnectionType(face) != ConnectionType.NONE;
    }

    /** Sets a tube's face to pull, as a player does with a configurator. */
    void pull(int x, int y, int z, Direction face) {
        Transmitter<?, ?, ?> tube = transmitter(x, y, z);
        tube.setConnectionTypeRaw(face, ConnectionType.PULL);
        tube.onModeChange(face);
        tube.refreshConnections();
        tube.notifyTileChange();
    }

    // ---- Gas and chemicals ---------------------------------------------------------------------

    @Nullable
    IGasHandler gasOrNull(int x, int y, int z, Direction face) {
        return level.getCapability(IGasHandler.BLOCK, at(x, y, z), face);
    }

    IGasHandler gas(int x, int y, int z, Direction face) {
        IGasHandler handler = gasOrNull(x, y, z, face);
        if (handler == null) {
            throw new GameTestAssertException("no gas handler at " + x + ", " + y + ", " + z + " on " + face);
        }
        return handler;
    }

    @Nullable
    IChemicalHandler chemicalOrNull(int x, int y, int z, Direction face) {
        return level.getCapability(CHEMICAL, at(x, y, z), face);
    }

    IChemicalHandler chemical(int x, int y, int z, Direction face) {
        IChemicalHandler handler = chemicalOrNull(x, y, z, face);
        if (handler == null) {
            throw new GameTestAssertException("no chemical handler at " + x + ", " + y + ", " + z + " on " + face);
        }
        return handler;
    }

    /** What a pipe's network holds, read from its top, where nothing is connected. */
    GasMixture inPipe(int x, int y, int z) {
        return gas(x, y, z, Direction.UP).contents();
    }

    /** Puts gas into a pipe's network through its top, and checks that all of it went in. */
    void fillPipe(int x, int y, int z, Map<ResourceLocation, Double> moles, double temperature) {
        GasParcel took = gas(x, y, z, Direction.UP).fill(new GasParcel(moles, temperature), IGasHandler.Action.EXECUTE);
        moles.forEach((gas, n) -> close(took.moles(gas), n, "the " + gas + " the pipe took"));
    }

    /** Moles for amounts given in millibuckets of chemical, at the unit factor now set. */
    static Map<ResourceLocation, Double> millibuckets(Map<ResourceLocation, Double> amounts) {
        Map<ResourceLocation, Double> moles = new TreeMap<>();
        amounts.forEach((gas, mb) -> moles.put(gas, mb * ChemicalGases.molesPerMillibucket()));
        return moles;
    }

    Holder<Chemical> holder(String name) {
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id(name)));
        if (holder.isEmpty()) {
            throw new GameTestAssertException("no chemical " + name + " on this server");
        }
        return holder.get();
    }

    ChemicalStack stack(String name, long millibuckets) {
        return new ChemicalStack(holder(name), millibuckets);
    }

    /** The name of the chemical in a handler's tank, or "empty". */
    static String name(ChemicalStack stack) {
        return stack.isEmpty()
                ? "empty"
                : stack.getChemicalHolder().unwrapKey().orElseThrow().location().toString();
    }

    /** A face of a Mekanism block that takes a chemical now. */
    Direction inputFace(int x, int y, int z, ChemicalStack probe) {
        for (Direction face : Direction.values()) {
            IChemicalHandler handler = chemicalOrNull(x, y, z, face);
            if (handler != null
                    && handler.insertChemical(probe, Action.SIMULATE).isEmpty()) {
                return face;
            }
        }
        throw new GameTestAssertException("no face at " + x + ", " + y + ", " + z + " takes " + name(probe));
    }

    /** Puts a chemical into a Mekanism block, through a face that takes it. */
    void fillTank(int x, int y, int z, ChemicalStack stack) {
        Direction face = inputFace(x, y, z, stack);
        ChemicalStack left = chemical(x, y, z, face).insertChemical(stack, Action.EXECUTE);
        h.assertTrue(left.isEmpty(), "the tank left " + left.getAmount() + " mB of " + name(stack) + " out");
    }

    /**
     * What a Mekanism tank or tube holds, read from no face in particular, where Mekanism lets
     * anything look and nothing change.
     */
    ChemicalStack held(int x, int y, int z) {
        IChemicalHandler handler = level.getCapability(CHEMICAL, at(x, y, z), null);
        if (handler == null) {
            throw new GameTestAssertException("no chemical handler at " + x + ", " + y + ", " + z);
        }
        return handler.getChemicalTanks() == 0 ? ChemicalStack.EMPTY : handler.getChemicalInTank(0);
    }

    // ---- The content mod's analyzer -------------------------------------------------------------

    /**
     * What the content mod's analyzer read: the kind of thing, by its name in the content mod, the
     * name its window gives it, and the gas.
     */
    record Analysis(String kind, Component name, GasMixture gas) {}

    /**
     * A player at a block holding the content mod's analyzer, which exists only with the content on.
     * A fake player opens no window, so this one keeps the menu it is given, as a real one does.
     */
    FakePlayer analyst(int x, int y, int z) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_mek_analyst")) {
            @Override
            public OptionalInt openMenu(
                    @Nullable MenuProvider provider, @Nullable Consumer<RegistryFriendlyByteBuf> extra) {
                AbstractContainerMenu menu = provider == null ? null : provider.createMenu(1, getInventory(), this);
                if (menu == null) {
                    return OptionalInt.empty();
                }
                containerMenu = menu;
                return OptionalInt.of(menu.containerId);
            }
        };
        player.moveTo(Vec3.atBottomCenterOf(at(x, y, z)));
        player.setItemInHand(
                InteractionHand.MAIN_HAND,
                new ItemStack(BuiltInRegistries.ITEM
                        .getOptional(id("zas_atmos:atmos_analyzer"))
                        .orElseThrow(() -> new GameTestAssertException("no analyzer on this server"))));
        return player;
    }

    /**
     * Uses the analyzer on the middle of a block's face, as a right-click does, and gives what its
     * window reads. The adapter builds against the API alone, so the window's reading is taken by
     * the names of its public accessors.
     */
    Analysis analyze(FakePlayer player, int x, int y, int z, Direction face) {
        BlockPos pos = at(x, y, z);
        Vec3 on = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        player.containerMenu = player.inventoryMenu;
        boolean used = player.gameMode
                .useItemOn(
                        player,
                        level,
                        player.getMainHandItem(),
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(on, face, pos, false))
                .consumesAction();
        h.assertTrue(used, "using the analyzer at " + x + ", " + y + ", " + z + " on " + face + " did nothing");
        Object menu = player.containerMenu;
        try {
            Object reading = menu.getClass().getMethod("reading").invoke(menu);
            Class<?> type = reading.getClass();
            return new Analysis(
                    String.valueOf(type.getMethod("kind").invoke(reading)),
                    (Component) type.getMethod("target").invoke(reading),
                    (GasMixture) type.getMethod("gas").invoke(reading));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new GameTestAssertException("the window open after using the analyzer is " + menu + ": " + e);
        }
    }

    // ---- Checks --------------------------------------------------------------------------------

    void close(double actual, double expected, String what) {
        if (Math.abs(actual - expected) > REL * Math.max(1, Math.abs(expected))) {
            throw new GameTestAssertException(what + ": expected " + expected + " and got " + actual);
        }
    }

    void equal(Object actual, Object expected, String what) {
        h.assertValueEqual(actual, expected, what);
    }
}
