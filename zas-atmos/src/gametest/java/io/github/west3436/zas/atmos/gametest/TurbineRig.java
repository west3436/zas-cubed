package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;

import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.device.CellWatch;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.turbine.CompressorBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlock;
import io.github.west3436.zas.atmos.turbine.TurbineControllerBlockEntity;
import io.github.west3436.zas.atmos.turbine.TurbinePartBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A gas turbine as the tests build it, in an {@link Area}: a stone box from x = 0 to 12 split by walls
 * at x = 4 and x = 8 into a feed room (x 1 to 3), the combustion chamber (x 5 to 7) and an exhaust room
 * (x 9 to 11), each 3 × 3 × 3. The compressor sits in the first wall at (4, 2, 2) and the turbine in the
 * second at (8, 2, 2), both passing gas east. Behind the compressor a pipe at (3, 2, 2) and a cap at
 * (2, 2, 2) make its feed network. An igniter lies on the chamber's floor at (6, 1, 2), and the
 * controller stands on the box's roof at (6, 5, 2), facing south. Nothing is linked until a test links
 * it. The chamber and the exhaust room hold the ambient air, with a little more nitrogen.
 */
final class TurbineRig {
    static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    static final ResourceLocation O2 = Area.O2;

    final Area a;
    final TurbineControllerBlockEntity controller;
    final TurbineBlockEntity turbine;
    final CompressorBlockEntity compressor;
    final IgniterBlockEntity igniter;

    private TurbineRig(Area a) {
        this.a = a;
        a.box(0, 0, 12, 4);
        for (int x : new int[] {4, 8}) {
            for (int y = 1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    a.set(x, y, z, Area.STONE);
                }
            }
        }
        PipeTests.cap(a, 2, 2, 2, Direction.EAST);
        PipeTests.pipe(a, 3, 2, 2);
        a.place(4, 2, 2, part(true));
        a.place(8, 2, 2, part(false));
        a.place(6, 1, 2, AtmosBlocks.IGNITER.get().defaultBlockState());
        a.place(
                6,
                5,
                2,
                AtmosBlocks.TURBINE_CONTROLLER
                        .get()
                        .defaultBlockState()
                        .setValue(TurbineControllerBlock.FACING, Direction.SOUTH));
        for (int x : new int[] {6, 10}) {
            a.fill(x, 2, 2, Map.of(N2, 1.0));
        }
        controller = a.entity(6, 5, 2);
        turbine = a.entity(8, 2, 2);
        compressor = a.entity(4, 2, 2);
        igniter = a.entity(6, 1, 2);
    }

    /** Builds it. */
    static TurbineRig build(Area a) {
        return new TurbineRig(a);
    }

    static BlockState part(boolean compressor) {
        return (compressor ? AtmosBlocks.COMPRESSOR : AtmosBlocks.TURBINE)
                .get()
                .defaultBlockState()
                .setValue(TurbinePartBlock.FACING, Direction.EAST);
    }

    /** Links the turbine and the compressor to the controller, and the igniter too if asked. */
    TurbineRig link(boolean withIgniter) {
        controller.toggleLink(a.level, turbine.getBlockPos());
        controller.toggleLink(a.level, compressor.getBlockPos());
        if (withIgniter) {
            controller.toggleLink(a.level, igniter.getBlockPos());
        }
        return this;
    }

    /** Sets the controller to burn hydrogen in oxygen at a rate, mol/s, with auto relight on or off, and switches it on. */
    TurbineRig burn(double rate, boolean auto) {
        controller.setFuel(H2);
        controller.setOxidizer(O2);
        controller.setBurnRate(rate);
        controller.setAutoIgnite(auto);
        controller.setOn(true);
        return this;
    }

    /** The compressor's feed network. */
    PipeNetwork feed() {
        return PipeTests.network(a, 3, 2, 2);
    }

    /** Puts hydrogen and oxygen into the feed network, mol, at the ambient temperature. */
    void feed(double h2, double o2) {
        Map<ResourceLocation, Double> moles = new HashMap<>();
        if (h2 > 0) {
            moles.put(H2, h2);
        }
        if (o2 > 0) {
            moles.put(O2, o2);
        }
        feed().gas().add(new GasParcel(moles, Area.T));
    }

    GasStore feedGas() {
        return feed().gas();
    }

    ZoneInfo chamber() {
        return a.zone(6, 2, 2);
    }

    ZoneInfo exhaust() {
        return a.zone(10, 2, 2);
    }

    /** Whether the chamber is burning. */
    boolean burning() {
        return Fires.isBurning(a.level, chamber().id());
    }

    /** Moles of gas in the feed network, the chamber, the exhaust room and held by the turbine between them. */
    double moles() {
        return feed().gas().totalMoles()
                + chamber().mixture().totalMoles()
                + exhaust().mixture().totalMoles()
                + turbine.held().totalMoles();
    }

    /** Adds a gas to the chamber, mol, at its temperature. */
    void addToChamber(ResourceLocation gas, double moles) {
        GasMixture m = chamber().mixture();
        a.settle(GasMutations.add(
                a.level, a.point(6, 2, 2), new GasParcel(Map.of(gas, moles), m.temperature()), Area.SOURCE));
    }

    /** Raises the chamber to {@code kpa} with nitrogen. */
    void pressurise(double kpa) {
        GasMixture m = chamber().mixture();
        double extra = (kpa - m.pressure()) * m.volume() / (GasMixture.GAS_CONSTANT * m.temperature());
        if (extra > 0) {
            addToChamber(N2, extra);
        }
    }

    /** One round, the controller's FE drawn off after as a load would; returns the FE drawn. */
    int step() {
        a.step();
        return controller.energy().extractEnergy(Integer.MAX_VALUE, false);
    }

    /** Rounds until the devices' cell watches trust their cells. */
    void settle() {
        for (int i = 0; i < CellWatch.SETTLE_STEPS + 1; i++) {
            step();
        }
    }

    /** The blades' spin state of the part at x. */
    int spin(int x) {
        return a.get(x, 2, 2).getValue(TurbinePartBlock.SPIN);
    }
}
