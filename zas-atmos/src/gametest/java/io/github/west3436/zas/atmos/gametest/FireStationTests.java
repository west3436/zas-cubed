package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.Map;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fire scenarios that need the content mod: S-03, the Overworld gas leak.
 *
 * <p>Each test builds in its own {@link Area} and drives device steps and atmos ticks itself,
 * within one game tick.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireStationTests {
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");

    /** Hydrogen's lower flammability limit in air (FIRE-003). */
    private static final double H2_LOWER_LIMIT = 0.04;

    /** A release that stays below the lower limit in the workshop: about 1.6 % H₂. */
    private static final double LEAN_KPA = 103.0;

    /** A release well inside the limits: about 19 % H₂. */
    private static final double RICH_KPA = 125.0;

    private FireStationTests() {}

    /** The workshop's zone, if it has one. */
    private static Optional<ZoneInfo> workshop(Area a) {
        return Atmospheres.zoneAt(a.level, a.abs(3, 2, 2));
    }

    private static double fraction(GasMixture m, ResourceLocation gas) {
        return m.totalMoles() > 0 ? m.moles(gas) / m.totalMoles() : 0;
    }

    /**
     * S-03: an H₂ canister's release valve, opened in a sealed Overworld workshop, forms a zone. A
     * torch in the workshop doesn't ignite a lean leak, below H₂'s 4 % lower limit, but ignites the
     * mixture once it is flammable: then O₂ and H₂ fall while H₂O, the temperature and the pressure
     * rise, the pressure past what the valve releases to (S-03, FIRE-003, FIRE-014, CONT-084).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void s_03_gas_leak_in_a_sealed_workshop(GameTestHelper h) {
        Area a = new Area(h, 62);
        // A 5 × 3 × 3 workshop: the canister at one end, a torch on the floor at the other.
        a.box(0, 0, 6, 4);
        a.set(5, 1, 2, Blocks.TORCH.defaultBlockState());
        a.tick();
        h.assertTrue(workshop(a).isEmpty(), "the sealed workshop is zoned before the leak (Normal)");

        a.place(1, 1, 2, AtmosBlocks.CANISTER.get().defaultBlockState());
        a.tick();
        CanisterBlockEntity can = a.entity(1, 1, 2);
        can.network().gas().add(new GasParcel(Map.of(H2, 2_000.0), T));

        // A lean leak: a zone forms, and nothing burns.
        can.setReleasePressure(LEAN_KPA);
        can.setValveOpen(true);
        for (int i = 0; i < 10; i++) {
            a.step();
        }
        ZoneInfo lean = workshop(a)
                .orElseThrow(() -> new GameTestAssertException("the leak formed no zone in the sealed workshop"));
        GasMixture before = lean.mixture();
        h.assertTrue(before.moles(H2) > 0, "no H₂ reached the workshop");
        h.assertTrue(
                fraction(before, H2) < H2_LOWER_LIMIT,
                "the lean leak reached " + fraction(before, H2) + " H₂, not below the lower limit");
        h.assertTrue(before.moles(H2O) == 0, "a lean leak burned: " + before.moles(H2O) + " mol H₂O");
        double o2Lean = before.moles(O2);
        for (int i = 0; i < 5; i++) {
            a.step();
        }
        h.assertTrue(
                workshop(a).orElseThrow().mixture().moles(O2) >= o2Lean * (1 - 1e-9),
                "O₂ fell while the leak was lean");

        // A rich leak: once flammable, the torch ignites it.
        can.setReleasePressure(RICH_KPA);
        double flammableO2 = Double.NaN;
        double flammableT = Double.NaN;
        boolean burning = false;
        GasMixture m = before;
        for (int i = 0; i < 60 && !burning; i++) {
            GasMixture last = workshop(a).orElseThrow().mixture();
            a.step();
            m = workshop(a).orElseThrow().mixture();
            if (m.moles(H2O) > 0) {
                burning = true;
                h.assertTrue(
                        fraction(last, H2) >= H2_LOWER_LIMIT,
                        "it ignited at " + fraction(last, H2) + " H₂, below the lower limit");
                flammableO2 = last.moles(O2);
                flammableT = last.temperature();
            }
        }
        h.assertTrue(burning, "the torch never ignited the flammable mixture; it ended at " + m);

        // It burns: O₂ falls while H₂O, temperature and pressure rise.
        can.setValveOpen(false);
        for (int i = 0; i < 20; i++) {
            a.step();
        }
        GasMixture after = workshop(a).orElseThrow().mixture();
        h.assertTrue(after.moles(O2) < flammableO2 * 0.95, "O₂ hardly fell: " + flammableO2 + " → " + after.moles(O2));
        h.assertTrue(after.moles(H2O) > 1, "little H₂O formed: " + after.moles(H2O) + " mol");
        h.assertTrue(
                after.temperature() > flammableT + 20,
                "the air hardly heated: " + flammableT + " → " + after.temperature() + " K");
        h.assertTrue(
                after.pressure() > RICH_KPA,
                "the pressure didn't rise past the valve's " + RICH_KPA + " kPa: " + after.pressure());
        h.succeed();
    }
}
