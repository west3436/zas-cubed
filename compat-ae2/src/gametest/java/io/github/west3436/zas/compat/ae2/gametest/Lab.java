package io.github.west3436.zas.compat.ae2.gametest;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.IConfigInvHost;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.compat.ae2.GasKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A GameTest's own place in the level, far from the framework's test areas, with what the tests
 * build there: AE2 cables and buses, a creative energy cell, and {@link TestTank}s. Lab {@code index}
 * is at x = 8192 + 64 × index, y = 100, z = 36864. It is cleared, and its tanks removed, when the
 * test ends.
 */
final class Lab {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;
    static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    static final double T = StandardAir.TEMPERATURE;

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;
    private final List<BlockPos> tanks = new ArrayList<>();

    Lab(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        this.origin = new BlockPos(8192 + 64 * index, 100, 36864);
        forceLoad(true);
        clear();
    }

    /** Ends the test's use of the place: clears it and lets its chunks go. Call it as the test succeeds. */
    void done() {
        clear();
        tanks.forEach(TestTank::remove);
        forceLoad(false);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(abs(x, y, z), state);
    }

    /** A test tank at a place, holding up to some moles at a temperature. */
    TestTank tank(int x, int y, int z, double capacity, double temperature) {
        return place(x, y, z, TestTank.put(abs(x, y, z), capacity, temperature));
    }

    /** A test tank whose handler is a view of another mod's storage ({@code GasHandlerView}). */
    TestTank view(int x, int y, int z, double capacity, double temperature) {
        return place(x, y, z, TestTank.putView(abs(x, y, z), capacity, temperature));
    }

    private TestTank place(int x, int y, int z, TestTank tank) {
        BlockPos pos = abs(x, y, z);
        tanks.add(pos);
        level.setBlockAndUpdate(pos, TestTank.BLOCK);
        level.invalidateCapabilities(pos);
        return tank;
    }

    /** A creative energy cell, which powers the network it joins. */
    void energy(int x, int y, int z) {
        set(
                x,
                y,
                z,
                BuiltInRegistries.BLOCK
                        .get(ResourceLocation.fromNamespaceAndPath("ae2", "creative_energy_cell"))
                        .defaultBlockState());
    }

    /** A fluix glass cable, the core of a cable bus. */
    void cable(int x, int y, int z) {
        part(x, y, z, null, "fluix_glass_cable");
    }

    /** A part of AE2's on a side of a cable bus, such as {@code import_bus}. */
    IPart part(int x, int y, int z, @Nullable Direction side, String name) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", name));
        if (!(item instanceof IPartItem<?> partItem)) {
            throw new GameTestAssertException("ae2:" + name + " is no part");
        }
        IPart part = PartHelper.setPart(level, abs(x, y, z), side, null, partItem);
        if (part == null) {
            throw new GameTestAssertException("ae2:" + name + " wasn't placed at " + x + ", " + y + ", " + z);
        }
        return part;
    }

    /** Sets the first slot of a bus's filter to a gas. */
    static void filter(IPart bus, ResourceLocation gas) {
        filter(bus, 0, gas);
    }

    static void filter(IPart bus, int slot, ResourceLocation gas) {
        if (!(bus instanceof IConfigInvHost host)) {
            throw new GameTestAssertException(bus + " has no filter");
        }
        host.getConfig().setStack(slot, new GenericStack(GasKey.of(gas), 1));
    }

    /** The grid a part is on, or null while its node isn't ready. */
    static @Nullable IGrid grid(IPart part) {
        IGridNode node = part.getGridNode();
        return node == null ? null : node.getGrid();
    }

    /** What the network a part is on holds, in units by key; empty while its node isn't ready. */
    static KeyCounter stored(IPart part) {
        IGrid grid = grid(part);
        return grid == null
                ? new KeyCounter()
                : grid.getStorageService().getInventory().getAvailableStacks();
    }

    /** The units of a gas the network a part is on holds. */
    static long stored(IPart part, ResourceLocation gas) {
        return stored(part).get(GasKey.of(gas));
    }

    /** Clears x -4 to 11, 8 high and 8 deep, to air. */
    void clear() {
        for (BlockPos p : BlockPos.betweenClosed(abs(-4, -1, -4), abs(11, 6, 4))) {
            level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
        }
    }

    /** Keeps the lab's chunks loaded while the test waits in real time, or lets them go. */
    private void forceLoad(boolean on) {
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(cx + dx, cz + dz, on);
            }
        }
    }

    /** Whether two amounts agree to a float's precision. */
    static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected));
    }

    void close(double actual, double expected, String what) {
        if (!close(actual, expected)) {
            throw new GameTestAssertException(what + ": " + actual + ", expected " + expected);
        }
    }
}
