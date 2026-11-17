package io.github.west3436.zas.compat.ae2.gametest;

import static io.github.west3436.zas.compat.ae2.gametest.Lab.N2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.O2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.T;

import appeng.api.parts.IPart;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.KeyCounter;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.compat.ae2.GasKey;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * AE2's buses on ZAS³'s own blocks (COMPAT-073): a large gas tank offers its gas on its bottom face,
 * as it does to any mod's pipe, so a bus under it reaches it. These need the content mod, so they are
 * in a namespace of their own, which a run with the content off leaves out.
 */
@GameTestHolder(Ae2ContentTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Ae2ContentTests {
    /** The namespace of the tests that build with the content mod's blocks. */
    public static final String NAMESPACE = "zas_ae2_content";

    private static final int WAIT = 600;

    private Ae2ContentTests() {}

    /** Stands a large tank with its lower half at a place, the upper half first, as a player's would. */
    private static BlockPos tank(Lab lab, int x, int y, int z) {
        BlockState lower = AtmosBlocks.LARGE_GAS_TANK.get().defaultBlockState();
        lab.set(x, y + 1, z, lower.setValue(LargeTankBlock.HALF, DoubleBlockHalf.UPPER));
        lab.set(x, y, z, lower);
        return lab.abs(x, y, z);
    }

    /** The gas handler on a large tank's bottom face, which is what a bus under it reaches. */
    private static IGasHandler handler(Lab lab, BlockPos tank) {
        IGasHandler handler = lab.level.getCapability(IGasHandler.BLOCK, tank, Direction.DOWN);
        if (handler == null) {
            throw new GameTestAssertException("the large tank at " + tank + " offers no gas handler below");
        }
        return handler;
    }

    /**
     * A mixture in one large tank goes, through an import bus under it, into ME storage, and through a
     * storage bus under another large tank into that one. A storage bus shows what the tank holds,
     * each gas on its own.
     */
    @GameTest(template = "empty", timeoutTicks = WAIT)
    public static void compat_073_buses_reach_large_gas_tanks(GameTestHelper h) {
        Lab lab = new Lab(h, 10);
        BlockPos from = tank(lab, 0, 2, 0);
        BlockPos to = tank(lab, 2, 2, 0);
        h.runAfterDelay(2, () -> {
            GasParcel mixture = new GasParcel(Map.of(O2, 3.0, N2, 9.0), T);
            GasParcel took = handler(lab, from).fill(mixture, IGasHandler.Action.EXECUTE);
            lab.close(took.totalMoles(), 12, "the gas the first tank took");
            lab.cable(0, 1, 0);
            lab.part(0, 1, 0, Direction.UP, "import_bus");
            lab.cable(1, 1, 0);
            lab.energy(1, 0, 0);
            lab.cable(2, 1, 0);
            IPart storage = lab.part(2, 1, 0, Direction.UP, "storage_bus");
            h.succeedWhen(() -> {
                GasMixture left = handler(lab, from).contents();
                GasMixture got = handler(lab, to).contents();
                h.assertTrue(left.moles(O2) < 0.001 && left.moles(N2) < 0.001, "the first tank still holds " + left);
                lab.close(got.moles(O2), 3, "the O2 in the second tank");
                lab.close(got.moles(N2), 9, "the N2 in the second tank");
                h.assertValueEqual(Lab.stored(storage, O2), 3000L, "the O2 the network shows, in mmol");
                h.assertValueEqual(Lab.stored(storage, N2), 9000L, "the N2 the network shows, in mmol");
                lab.done();
            });
        });
    }

    /**
     * Where a fluid is mapped to a gas, as NuclearCraft's nitrogen is, a gas store also offers its
     * gas as that fluid. AE2's cable bus is given no such view, so a storage bus under a large tank
     * of nitrogen, and one on another mod's gas block, shows the nitrogen once, as gas, and no
     * fluid. Anything else asking still gets the view.
     */
    @GameTest(template = "empty", timeoutTicks = WAIT)
    public static void compat_073_a_storage_bus_shows_gas_once_where_it_is_also_a_fluid(GameTestHelper h) {
        Lab lab = new Lab(h, 15);
        BlockPos large = tank(lab, 0, 2, 0);
        TestTank other = lab.tank(3, 1, 0, 1000, T).with(N2, 2.0);
        h.runAfterDelay(2, () -> {
            handler(lab, large).fill(new GasParcel(Map.of(N2, 5.0), T), IGasHandler.Action.EXECUTE);
            IFluidHandler shown =
                    lab.level.getCapability(Capabilities.FluidHandler.BLOCK, lab.abs(3, 1, 0), Direction.UP);
            h.assertTrue(shown != null, "the gas block offers no fluid view to a block that isn't AE2's");
            h.assertTrue(
                    shown.getFluidInTank(0).is(TestGasFluid.fluid()),
                    "the fluid view shows " + shown.getFluidInTank(0).getFluid());
            lab.cable(0, 1, 0);
            IPart bus = lab.part(0, 1, 0, Direction.UP, "storage_bus");
            lab.cable(1, 1, 0);
            lab.energy(1, 0, 0);
            lab.cable(2, 1, 0);
            lab.part(2, 1, 0, Direction.EAST, "storage_bus");
            h.succeedWhen(() -> {
                KeyCounter stored = Lab.stored(bus);
                h.assertValueEqual(stored.get(GasKey.of(N2)), 7000L, "the N2 the network shows, in mmol");
                h.assertValueEqual(
                        stored.get(AEFluidKey.of(TestGasFluid.fluid())), 0L, "the N2 the network shows as a fluid");
                h.assertValueEqual(stored.size(), 1, "the kinds of stack the network shows: " + stored);
                h.assertTrue(
                        lab.level.getCapability(Capabilities.FluidHandler.BLOCK, large, Direction.DOWN) == null,
                        "the large tank offers AE2's cable bus a fluid view");
                lab.close(other.moles(N2), 2.0, "the N2 in the other block");
                lab.done();
            });
        });
    }
}
