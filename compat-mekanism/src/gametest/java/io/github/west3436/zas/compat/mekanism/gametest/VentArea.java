package io.github.west3436.zas.compat.mekanism.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.simulation.AtmosService;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.radiation.IRadiationManager;
import mekanism.api.radiation.IRadiationSource;
import mekanism.common.lib.transmitter.TransmitterNetworkRegistry;
import mekanism.common.network.to_server.PacketDropperUse;
import mekanism.common.network.to_server.PacketDropperUse.DropperAction;
import mekanism.common.network.to_server.PacketDropperUse.TankType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.transmitter.TileEntityPressurizedTube;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * A venting GameTest's own area of the level: a sealed stone room far from every other test's, with
 * Mekanism's machines in it, and the gas of the room read through the public API.
 *
 * <p>A test builds its room, drives Mekanism's own code and an atmos tick itself, and checks the
 * room, all within one game tick. So nothing here waits for a chunk to tick, and what a test
 * changes, a setting above all, is changed back before any other test runs.
 *
 * <p>Atmos ticks are run with the framework's {@link AtmosService#tickNow}, which GameTests may use
 * and the adapter never does. The tests that use it are in a batch of their own.
 */
final class VentArea {
    /** The batch of every test that drives atmos ticks itself. */
    static final String BATCH = "zas_mekanism_venting";

    static final double REL = 1e-9;
    static final ResourceLocation OXYGEN = StandardAir.OXYGEN;
    static final ResourceLocation NITROGEN = StandardAir.NITROGEN;
    static final ResourceLocation HYDROGEN = ResourceLocation.parse("zas:hydrogen");
    static final ResourceLocation WATER_VAPOR = ResourceLocation.parse("zas:water_vapor");

    /** What these tests' data makes of {@link #NUCLEAR_WASTE}. */
    static final ResourceLocation CARBON_DIOXIDE = ResourceLocation.parse("zas:carbon_dioxide");

    /** A chemical no mapping makes a gas. */
    static final String LITHIUM = "mekanism:lithium";

    /** A radioactive chemical these tests' data maps, as a datapack may (MEK-023). */
    static final String NUCLEAR_WASTE = "mekanism:nuclear_waste";

    /** A radioactive chemical nothing maps, as none is by default. */
    static final String PLUTONIUM = "mekanism:plutonium";

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final SourceId SOURCE = SourceId.of(MekanismFoundationTests.NAMESPACE, "gametest");
    private static final GameProfile PLAYER =
            new GameProfile(UUID.fromString("7a0c1c5e-3f0b-4d0e-9a51-2f6d3b8e4c11"), "zas_mekanism_test");

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;

    /** The far corner of what this area has built, for {@link #clear}. */
    private int extentX = 4;

    private int extentY = 4;
    private int extentZ = 4;

    /** Whether the game has been told to keep ticking this area's chunk. */
    private boolean ticking;

    /**
     * @param h the test
     * @param index the area's number, one for each test
     */
    VentArea(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        // Apart from the framework's and the content mod's numbered areas, which are at z = 16384.
        this.origin = new BlockPos(8192 + 64 * index, 100, 24576);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(abs(x, y, z), state);
    }

    /** Fills a box of blocks with stone. */
    void stone(int x0, int y0, int z0, int x1, int y1, int z1) {
        grow(x1, y1, z1);
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, y0, z0), abs(x1, y1, z1))) {
            level.setBlockAndUpdate(p, STONE);
        }
    }

    /** A hollow stone box with corners (0, 0, 0) and (x1, y1, z1), in cleared air. */
    void box(int x1, int y1, int z1) {
        grow(x1, y1, z1);
        clear();
        for (BlockPos p : BlockPos.betweenClosed(abs(0, 0, 0), abs(x1, y1, z1))) {
            int x = p.getX() - origin.getX();
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == x1 || y == 0 || y == y1 || z == 0 || z == z1;
            level.setBlockAndUpdate(p, wall ? STONE : AIR);
        }
    }

    /** The usual room: 3 by 3 by 3 of air, (1, 1, 1) to (3, 3, 3), in a stone box. */
    void room() {
        box(4, 4, 4);
    }

    private void grow(int x, int y, int z) {
        extentX = Math.max(extentX, x);
        extentY = Math.max(extentY, y);
        extentZ = Math.max(extentZ, z);
    }

    /**
     * Has the game tick this area's blocks from its next tick on, as it does those near a player,
     * until the area is cleared: for a test that leaves a pipe or a machine to the game. The area is
     * within one chunk, which is the one kept.
     */
    void keepTicking() {
        ChunkPos chunk = new ChunkPos(origin);
        level.setChunkForced(chunk.x, chunk.z, true);
        ticking = true;
    }

    /** Sets everything this area built back to air, with a block of margin, and settles Mekanism. */
    void clear() {
        if (ticking) {
            ChunkPos chunk = new ChunkPos(origin);
            level.setChunkForced(chunk.x, chunk.z, false);
            ticking = false;
        }
        for (BlockPos p : BlockPos.betweenClosed(abs(-1, -1, -1), abs(extentX + 1, extentY + 1, extentZ + 1))) {
            if (!level.getBlockState(p).isAir()) {
                level.setBlockAndUpdate(p, AIR);
            }
        }
        // So that no tube removed here is still waiting for Mekanism when the next test runs.
        networkTick();
    }

    /** One of Mekanism's blocks, by its id. */
    BlockState block(String id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
        h.assertTrue(block != Blocks.AIR, "Mekanism registers no block " + id);
        return block.defaultBlockState();
    }

    /** Places one of Mekanism's blocks and returns its block entity. */
    <B extends BlockEntity> B place(int x, int y, int z, String id, Class<B> type) {
        grow(x, y, z);
        BlockPos pos = abs(x, y, z);
        level.setBlockAndUpdate(pos, block(id));
        BlockEntity be = level.getBlockEntity(pos);
        if (!type.isInstance(be)) {
            throw new GameTestAssertException(id + " has the block entity " + be + ", not a " + type.getSimpleName());
        }
        return type.cast(be);
    }

    /**
     * Places a pressurized tube and has it join Mekanism's networks, as its first tick does. {@link
     * #networkTick} then gives it a network.
     */
    TileEntityPressurizedTube tube(int x, int y, int z) {
        TileEntityPressurizedTube tube =
                place(x, y, z, "mekanism:basic_pressurized_tube", TileEntityPressurizedTube.class);
        TileEntityTransmitter.tickServer(level, tube.getBlockPos(), tube.getBlockState(), tube);
        return tube;
    }

    /**
     * The end of a server tick for Mekanism's tube networks: tubes placed get their network, and
     * the networks of tubes removed deal with what they lost.
     */
    void networkTick() {
        TransmitterNetworkRegistry.onTick(new ServerTickEvent.Post(() -> true, level.getServer()));
    }

    /** Runs a machine's server tick, as the level does once a game tick. */
    static void run(TileEntityMekanism machine, int ticks) {
        for (int i = 0; i < ticks; i++) {
            TileEntityMekanism.tickServer(machine.getLevel(), machine.getBlockPos(), machine.getBlockState(), machine);
        }
    }

    // ---- The atmosphere -----------------------------------------------------------------

    private AtmosService service() {
        AtmosService s = AtmosService.current();
        if (s == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        return s;
    }

    /** One atmos tick: what was released so far is applied. */
    void tick() {
        service().tickNow(level);
    }

    /** Forms the zone of the room that holds a cell, by adding nitrogen there, and waits for it. */
    void form(int x, int y, int z) {
        CompletableFuture<MutationResult> future = GasMutations.add(
                level,
                Vec3.atCenterOf(abs(x, y, z)),
                new GasParcel(Map.of(NITROGEN, 100.0), StandardAir.TEMPERATURE),
                SOURCE);
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the room's gas didn't arrive within 8 atmos ticks");
        zone(x, y, z);
    }

    ZoneInfo zone(int x, int y, int z) {
        return Atmospheres.zoneAt(level, abs(x, y, z))
                .orElseThrow(() -> new GameTestAssertException("no zone at " + x + ", " + y + ", " + z));
    }

    /** The moles of one gas in the room that holds a cell. */
    double moles(int x, int y, int z, ResourceLocation gas) {
        return zone(x, y, z).mixture().moles(gas);
    }

    /** The moles of every gas in the room that holds a cell. */
    double total(int x, int y, int z) {
        return zone(x, y, z).mixture().totalMoles();
    }

    /** The gas an amount of chemical is, at the server's unit factor (MEK-002). */
    static double mol(long millibuckets) {
        return millibuckets * ChemicalGases.molesPerMillibucket();
    }

    void close(double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    // ---- Chemicals ----------------------------------------------------------------------

    Holder<Chemical> chemical(String name) {
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, ResourceLocation.parse(name)));
        h.assertTrue(holder.isPresent(), "Mekanism registers no chemical " + name);
        return holder.orElseThrow();
    }

    ChemicalStack stack(String name, long amount) {
        return new ChemicalStack(chemical(name), amount);
    }

    // ---- Radiation ----------------------------------------------------------------------

    /** The radiation Mekanism holds at a block: the magnitude of its source there, or 0. */
    double radiation(BlockPos pos) {
        double magnitude = 0;
        for (IRadiationSource source :
                IRadiationManager.INSTANCE.getRadiationSources(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            if (source.getPosition().equals(pos)) {
                magnitude += source.getMagnitude();
            }
        }
        return magnitude;
    }

    /** Removes the radiation a test left at a block. */
    void scrub(BlockPos pos) {
        IRadiationManager.INSTANCE.removeRadiationSource(level, pos);
    }

    // ---- A player with a gauge dropper ---------------------------------------------------

    /** The tests' player, with empty hands, standing in a cell. Not in the level's player list. */
    FakePlayer player(int x, int y, int z) {
        FakePlayer player = FakePlayerFactory.get(level, PLAYER);
        BlockPos feet = abs(x, y, z);
        player.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    /** Mekanism's gauge dropper, empty. */
    ItemStack dropper() {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:gauge_dropper"));
        h.assertTrue(item != Items.AIR, "Mekanism registers no gauge dropper");
        return new ItemStack(item);
    }

    /**
     * A player sneak-clicks the first chemical gauge of a machine's window with a gauge dropper: the
     * packet the client sends, handled as the server handles it.
     */
    void dropperDump(FakePlayer player, BlockPos machine) {
        player.containerMenu.setCarried(dropper());
        IPayloadContext context = (IPayloadContext) Proxy.newProxyInstance(
                IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("player")) {
                        return player;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        new PacketDropperUse(machine, DropperAction.DUMP_TANK, TankType.CHEMICAL_TANK, 0).handle(context);
        player.containerMenu.setCarried(ItemStack.EMPTY);
    }
}
