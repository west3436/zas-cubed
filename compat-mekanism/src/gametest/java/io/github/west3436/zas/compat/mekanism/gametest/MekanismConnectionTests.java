package io.github.west3436.zas.compat.mekanism.gametest;

import static io.github.west3436.zas.compat.mekanism.gametest.Bench.CO2;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.H2;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.H2O;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.N2;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.O2;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.PIPE_LIMIT;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.T;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.id;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.millibuckets;
import static io.github.west3436.zas.compat.mekanism.gametest.Bench.name;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.compat.mekanism.ChemicalMapping;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import io.github.west3436.zas.compat.mekanism.ZasChemicals;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismAPITags;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.radiation.IRadiationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * The direct connection on a server that has Mekanism (MEK-011, MEK-013): ZAS³'s gases as Mekanism
 * chemicals, a tube joined to a pipe, a pipe joined to a tank, and what crosses between them.
 *
 * <p>The pipes are the content mod's own and the tubes and tanks are Mekanism's, placed in the
 * test's structure and left to the game: a tube moves a chemical in its own tick and a pipe shares
 * gas in its atmos tick, so the tests that watch them wait. The others ask a block's capability
 * what a tube or a pipe would ask it, in one game tick.
 *
 * <p>Those pipes exist only while the content is on (CONT-155), so the tests here are held in
 * {@link #NAMESPACE}, which a run with the content off leaves out. The few that build with no pipe
 * say so by naming the adapter's usual namespace, and run either way.
 */
@GameTestHolder(MekanismConnectionTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismConnectionTests {
    /**
     * The namespace of the adapter's GameTests that build with the content mod's blocks. The
     * adapter's test run enables it only while the content is on ({@code -Pcontent=off} turns it
     * off), so a test held in it needs no check of its own. A class of such tests names it in its
     * {@code @GameTestHolder}, and has its structures under it.
     */
    public static final String NAMESPACE = "zas_mekanism_content";

    /** For a test here that builds with no pipe: the namespace that every run enables. */
    private static final String ALWAYS = MekanismFoundationTests.NAMESPACE;

    /** A structure of air, 7 × 3 × 3. */
    private static final String LAB = "lab";

    /**
     * How long a test waits for the game to move gas. The test server runs game ticks as fast as it
     * can while atmos ticks take real time, so this is far more ticks than a passing test uses.
     */
    private static final int WAIT = 6_000;

    /** The size of a Basic Chemical Tank, in mB. */
    private static final long TANK = 64_000;

    private static final String ADAPTER = "io.github.west3436.zas.compat.mekanism.";

    private static final Logger LOGGER = LogUtils.getLogger();

    private MekanismConnectionTests() {}

    private static double factor() {
        return ChemicalGases.molesPerMillibucket();
    }

    /** A cap, a pipe and a cap: a closed network that a test asks directly. */
    private static void closedPipe(Bench b, int y, int z) {
        b.cappedPipe(1, y, z);
        b.cap(3, y, z, Direction.WEST);
    }

    /** The view of a closed pipe that a tube above it would get. */
    private static IChemicalHandler view(Bench b, int y, int z) {
        return b.chemical(2, y, z, Direction.UP);
    }

    private static Set<Direction> faces(Bench b, int x, int y, int z, boolean chemical) {
        Set<Direction> faces = EnumSet.noneOf(Direction.class);
        for (Direction face : Direction.values()) {
            if ((chemical ? b.chemicalOrNull(x, y, z, face) : b.gasOrNull(x, y, z, face)) != null) {
                faces.add(face);
            }
        }
        return faces;
    }

    // ---- MEK-011: ZAS³'s gases as chemicals ----------------------------------------------------

    /**
     * Nitrogen, carbon dioxide, nitrous oxide, carbon monoxide and methane are chemicals in
     * Mekanism's registry, each under its gas's id, named by the gas's own translation key, tinted,
     * tagged as gaseous so Mekanism draws them as gas, and mapped to their gas both ways. Mekanism's
     * own oxygen, hydrogen and water vapour aren't doubled.
     */
    @GameTest(template = "empty", templateNamespace = ALWAYS)
    public static void mek_011_the_gases_mekanism_lacks_are_chemicals_named_as_the_gases(GameTestHelper h) {
        Bench b = new Bench(h);
        Set<ResourceLocation> expected = new TreeSet<>(Set.of(
                id("zas:nitrogen"),
                id("zas:carbon_dioxide"),
                id("zas:nitrous_oxide"),
                id("zas:carbon_monoxide"),
                id("zas:methane")));
        b.equal(new TreeSet<>(ZasChemicals.gases()), expected, "the gases registered as chemicals");
        for (ResourceLocation gas : expected) {
            Holder<Chemical> holder = b.holder(gas.toString());
            Chemical chemical = holder.value();
            h.assertTrue(GasProperties.isDefined(gas), "the server defines no gas " + gas);
            String key = "gas.zas." + gas.getPath();
            b.equal(GasProperties.translationKey(gas), key, gas + "'s name key");
            b.equal(chemical.getTranslationKey(), key, "the name key of the chemical " + gas);
            h.assertTrue(
                    chemical.getTextComponent().getContents() instanceof TranslatableContents name
                            && name.getKey().equals(key),
                    "the chemical " + gas + " is shown as " + chemical.getTextComponent());
            b.equal(chemical.getTint(), ZasChemicals.tint(gas), "the tint of the chemical " + gas);
            h.assertTrue(holder.is(MekanismAPITags.Chemicals.GASEOUS), "the chemical " + gas + " isn't gaseous");
            h.assertFalse(chemical.isRadioactive(), "the chemical " + gas + " is radioactive");

            ChemicalMapping mapping = ChemicalGases.mapping(holder).orElse(null);
            h.assertTrue(mapping != null, "the chemical " + gas + " isn't mapped");
            b.equal(mapping.gases(), Map.of(gas, 1.0), "what the chemical " + gas + " is");
            b.equal(
                    ChemicalGases.chemical(gas, T)
                            .map(c -> c.unwrapKey().orElseThrow().location()),
                    Optional.of(gas),
                    "the chemical the gas " + gas + " leaves as");
        }
        Set<ResourceLocation> registered = new TreeSet<>();
        MekanismAPI.CHEMICAL_REGISTRY.keySet().stream()
                .filter(chemical -> chemical.getNamespace().equals("zas"))
                .forEach(registered::add);
        b.equal(registered, expected, "the chemicals in ZAS³'s namespace");
        h.succeed();
    }

    /**
     * Every gas ZAS³ and the adapter's pack define leaves as some chemical, on a server that has
     * Mekanism Generators for deuterium and tritium, so none stays out of Mekanism. The adapter's
     * list of gases that do stay out holds the one gas these tests' data adds, whose only chemical
     * is radioactive.
     */
    @GameTest(template = "empty", templateNamespace = ALWAYS)
    public static void mek_011_every_gas_of_zas_has_a_chemical_and_the_adapter_lists_those_without(GameTestHelper h) {
        Bench b = new Bench(h);
        int checked = 0;
        for (ResourceLocation gas : GasProperties.defined()) {
            if (gas.getNamespace().equals("zas")) {
                checked++;
                h.assertTrue(ChemicalGases.chemical(gas, T).isPresent(), "the gas " + gas + " has no chemical");
            }
        }
        h.assertTrue(checked >= 14, "only " + checked + " gases of ZAS³ are defined on this server");
        b.equal(ZasChemicals.withoutChemical(), Set.of(id("zas_mekanism:radon")), "the gases with no chemical");
        h.succeed();
    }

    /**
     * What MEK-011 is for: carbon dioxide in a pipe goes into a Mekanism tank beside it, as the
     * chemical the adapter registers, and nitrogen in a Mekanism tank comes out into a pipe.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_011_carbon_dioxide_fills_a_mekanism_tank_and_nitrogen_comes_out_of_one(GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        // Row z = 0: a pipe of carbon dioxide beside a tank's input face.
        b.cappedPipe(1, 1, 0);
        b.tank(3, 1, 0, Direction.NORTH);
        b.fillPipe(2, 1, 0, millibuckets(Map.of(CO2, 300.0)), T);
        // Row z = 2: an empty pipe at the front of a tank of nitrogen, which is where a tank gives.
        b.cappedPipe(1, 1, 2);
        b.tank(3, 1, 2, Direction.WEST);
        b.fillTank(3, 1, 2, b.stack("zas:nitrogen", 3000));
        h.startSequence()
                .thenWaitUntil(() -> {
                    b.equal(name(b.held(3, 1, 0)), "zas:carbon_dioxide", "what the first tank holds");
                    h.assertTrue(b.inPipe(2, 1, 2).moles(N2) > 0, "no nitrogen has reached the second pipe");
                })
                .thenExecuteAfter(60, () -> {
                    ChemicalStack first = b.held(3, 1, 0);
                    b.equal(name(first), "zas:carbon_dioxide", "what the first tank holds");
                    b.close(
                            first.getAmount() * factor + b.inPipe(2, 1, 0).moles(CO2),
                            300 * factor,
                            "the carbon dioxide in the first pipe and tank");
                    ChemicalStack second = b.held(3, 1, 2);
                    GasMixture pipe = b.inPipe(2, 1, 2);
                    b.close(
                            second.getAmount() * factor + pipe.moles(N2),
                            3000 * factor,
                            "the nitrogen in the second pipe and tank");
                    b.close(pipe.totalMoles(), pipe.moles(N2), "the second pipe holds nitrogen only");
                })
                .thenSucceed();
    }

    // ---- MEK-013: what connects ----------------------------------------------------------------

    /**
     * Each side gets one view, of what the block itself offers: a pipe's chemical handler is the
     * adapter's and its gas handler is the pipe's own, a tank's gas handler is the adapter's and its
     * chemical handler Mekanism's own, and a tube's gas handler is the joint, which holds nothing. No
     * view is of another view, and none answers for no face in particular.
     */
    @GameTest(template = LAB)
    public static void mek_013_each_block_gets_one_view_and_never_a_view_of_a_view(GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 0);
        b.tank(3, 1, 0, Direction.NORTH);
        b.tube(5, 1, 2);

        String pipeGas = b.gas(2, 1, 0, Direction.EAST).getClass().getName();
        String pipeChemical = b.chemical(2, 1, 0, Direction.EAST).getClass().getName();
        h.assertFalse(pipeGas.startsWith(ADAPTER), "a pipe's gas handler is the adapter's: " + pipeGas);
        b.equal(pipeChemical, ADAPTER + "GasAsChemicals", "a pipe's chemical handler");

        String tankChemical = b.chemical(3, 1, 0, Direction.WEST).getClass().getName();
        String tankGas = b.gas(3, 1, 0, Direction.WEST).getClass().getName();
        h.assertFalse(tankChemical.startsWith(ADAPTER), "a tank's chemical handler is the adapter's: " + tankChemical);
        b.equal(tankGas, ADAPTER + "ChemicalsAsGas", "a tank's gas handler");

        String tubeChemical = b.chemical(5, 1, 2, Direction.WEST).getClass().getName();
        IGasHandler joint = b.gas(5, 1, 2, Direction.WEST);
        h.assertFalse(tubeChemical.startsWith(ADAPTER), "a tube's chemical handler is the adapter's: " + tubeChemical);
        b.equal(joint.getClass().getName(), ADAPTER + "TubeJoint", "a tube's gas handler");
        h.assertTrue(joint.contents().isEmpty(), "the joint holds gas");
        GasParcel offered = new GasParcel(Map.of(O2, 5.0), T);
        b.equal(joint.fill(offered, IGasHandler.Action.EXECUTE), GasParcel.EMPTY, "what the joint took");
        b.equal(joint.drain(Map.of(O2, 5.0), IGasHandler.Action.EXECUTE), GasParcel.EMPTY, "what the joint gave");

        h.assertTrue(h.getLevel().getCapability(Bench.CHEMICAL, b.at(2, 1, 0), null) == null, "a pipe, on no face");
        h.assertTrue(h.getLevel().getCapability(IGasHandler.BLOCK, b.at(3, 1, 0), null) == null, "a tank, on no face");
        h.assertTrue(h.getLevel().getCapability(IGasHandler.BLOCK, b.at(5, 1, 2), null) == null, "a tube, on no face");

        // A block with neither capability gets neither view.
        b.other(5, 1, 0, "minecraft:stone");
        b.equal(faces(b, 5, 1, 0, true), EnumSet.noneOf(Direction.class), "stone's chemical faces");
        b.equal(faces(b, 5, 1, 0, false), EnumSet.noneOf(Direction.class), "stone's gas faces");
        h.succeed();
    }

    /**
     * A tube finds a chemical handler on exactly the faces where a part offers its gas handler: a
     * pipe on every face, a cap where it points, a connector port below, and a canister nowhere.
     */
    @GameTest(template = LAB)
    public static void mek_013_a_part_takes_a_tube_on_the_faces_where_a_pipe_would_join_it(GameTestHelper h) {
        Bench b = new Bench(h);
        b.pipe(1, 1, 1);
        b.cap(3, 1, 1, Direction.EAST);
        b.other(5, 1, 1, "zas_atmos:connector_port");
        b.other(5, 2, 1, "zas_atmos:canister");

        b.equal(faces(b, 1, 1, 1, true), EnumSet.allOf(Direction.class), "a pipe's faces");
        b.equal(faces(b, 3, 1, 1, true), EnumSet.of(Direction.EAST), "a cap's faces");
        b.equal(faces(b, 5, 1, 1, true), EnumSet.of(Direction.DOWN), "a connector port's faces");
        b.equal(faces(b, 5, 2, 1, true), EnumSet.noneOf(Direction.class), "a canister's faces");
        for (int[] part : new int[][] {{1, 1, 1}, {3, 1, 1}, {5, 1, 1}, {5, 2, 1}}) {
            b.equal(
                    faces(b, part[0], part[1], part[2], true),
                    faces(b, part[0], part[1], part[2], false),
                    "the chemical faces and the gas faces of the part at x = " + part[0] + ", y = " + part[1]);
        }
        h.succeed();
    }

    /**
     * The same holds for every block of the content mod, each in its default state with nothing
     * around it: a tube finds a chemical handler wherever the block offers gas, and nowhere else. The
     * parts that take a tube are logged, for whoever lists them for players.
     */
    @GameTest(template = LAB)
    public static void mek_013_every_part_takes_a_tube_exactly_where_it_offers_gas(GameTestHelper h) {
        Bench b = new Bench(h);
        Map<String, String> taking = new TreeMap<>();
        int parts = 0;
        for (ResourceLocation part : new TreeSet<>(BuiltInRegistries.BLOCK.keySet())) {
            if (!part.getNamespace().equals("zas_atmos")) {
                continue;
            }
            parts++;
            b.bare(3, 1, 1, part.toString());
            Set<Direction> chemical = faces(b, 3, 1, 1, true);
            b.equal(chemical, faces(b, 3, 1, 1, false), "the chemical faces of " + part + ", against its gas faces");
            if (!chemical.isEmpty()) {
                taking.put(part.toString(), chemical.toString());
            }
            b.bare(3, 1, 1, "minecraft:air");
        }
        LOGGER.info("Of {} blocks of zas_atmos, these take a Mekanism tube, on these faces: {}", parts, taking);
        h.assertTrue(parts > 20, "only " + parts + " blocks of zas_atmos are registered");
        h.assertTrue(taking.containsKey("zas_atmos:pipe"), "the pipe isn't among them: " + taking);
        h.assertFalse(taking.containsKey("zas_atmos:canister"), "the canister is among them: " + taking);
        h.succeed();
    }

    /**
     * A tube beside a pipe draws its connection to it, and the pipe grows an arm to the tube, so its
     * end is closed. A tank that ejects into the tube sends its chemical through to the pipe, where it
     * arrives as its gas: 1,000 mB are the moles of 1,000 L at 101.325 kPa and 293.15 K, at 293.15 K.
     * The pipe keeps it: it neither leaks nor hands it back to the tube.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_a_tube_joins_a_pipe_and_delivers_a_chemical_at_the_unit_factor(GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 1);
        b.tube(3, 1, 1);
        b.tank(4, 1, 1, Direction.WEST);
        double factor = factor();
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(b.tubeConnects(3, 1, 1, Direction.WEST), "the tube draws no connection to the pipe");
                    h.assertTrue(b.tubeConnects(3, 1, 1, Direction.EAST), "the tube draws no connection to the tank");
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe has grown no arm to the tube");
                })
                .thenExecute(() -> {
                    for (Direction face : EnumSet.complementOf(EnumSet.of(Direction.WEST, Direction.EAST))) {
                        h.assertFalse(b.tubeConnects(3, 1, 1, face), "the tube connects to nothing on " + face);
                        h.assertFalse(b.arm(2, 1, 1, face), "the pipe has an arm to nothing on " + face);
                    }
                    b.fillTank(4, 1, 1, b.stack("mekanism:oxygen", 1000));
                })
                .thenWaitUntil(() -> b.close(b.inPipe(2, 1, 1).moles(O2), 1000 * factor, "the oxygen in the pipe"))
                .thenExecuteAfter(100, () -> {
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    b.close(pipe.moles(O2), 1000 * factor, "the oxygen in the pipe, 100 ticks later");
                    b.close(pipe.totalMoles(), 1000 * factor, "everything in the pipe");
                    b.close(pipe.temperature(), T, "the temperature it arrived at");
                    b.close(pipe.pressure(), 1000 * 101.325 / pipe.volume(), "the pressure 1,000 L make in the pipe");
                    h.assertTrue(b.held(3, 1, 1).isEmpty(), "the tube still holds " + name(b.held(3, 1, 1)));
                    h.assertTrue(b.held(4, 1, 1).isEmpty(), "the tank still holds " + name(b.held(4, 1, 1)));
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe lost its arm to the tube");
                })
                .thenSucceed();
    }

    /**
     * A tube in its usual mode takes nothing from a pipe, and the pipe pushes nothing into it. Set to
     * pull, the tube draws the pipe's gas and passes it on: whole millibuckets, here 500 of the 500.5
     * the pipe held, and the half stays in the pipe, at the pipe's temperature. Nothing is made or
     * lost on the way.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_a_pulling_tube_draws_whole_millibuckets_and_the_rest_stays_in_the_pipe(
            GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 1);
        b.tube(3, 1, 1);
        b.tank(4, 1, 1, Direction.NORTH);
        double factor = factor();
        double start = 500.5 * factor;
        b.fillPipe(2, 1, 1, Map.of(O2, start), 350);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(b.tubeConnects(3, 1, 1, Direction.WEST), "the tube draws no connection to the pipe");
                    h.assertTrue(b.tubeConnects(3, 1, 1, Direction.EAST), "the tube draws no connection to the tank");
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe has grown no arm to the tube");
                })
                .thenExecuteAfter(100, () -> {
                    b.close(b.inPipe(2, 1, 1).moles(O2), start, "the pipe's oxygen beside a tube in its usual mode");
                    h.assertTrue(b.held(3, 1, 1).isEmpty(), "the tube took gas without being set to pull");
                    b.pull(3, 1, 1, Direction.WEST);
                })
                .thenWaitUntil(() -> {
                    ChemicalStack tank = b.held(4, 1, 1);
                    b.equal(name(tank), "mekanism:oxygen", "what the tank holds");
                    b.equal(tank.getAmount(), 500L, "the millibuckets in the tank");
                })
                .thenExecuteAfter(40, () -> {
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    b.close(pipe.moles(O2), start - 500 * factor, "what stayed in the pipe");
                    h.assertTrue(pipe.moles(O2) < factor, "a whole millibucket was left in the pipe");
                    b.close(pipe.temperature(), 350, "the pipe's temperature");
                    b.equal(b.held(4, 1, 1).getAmount(), 500L, "the millibuckets in the tank, 40 ticks later");
                    h.assertTrue(b.held(3, 1, 1).isEmpty(), "the tube still holds " + name(b.held(3, 1, 1)));
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe lost its arm to the tube");
                })
                .thenSucceed();
    }

    /**
     * A pulling tube gets nothing from a pipe of air: neither gas is 99 % of it, so the pipe shows the
     * tube an empty tank. A chemical still enters that pipe, whatever it holds.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_a_pulling_tube_gets_nothing_from_a_pipe_of_air_and_a_chemical_still_enters(
            GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 1);
        b.tube(3, 1, 1);
        double factor = factor();
        b.fillPipe(2, 1, 1, millibuckets(Map.of(N2, 790.0, O2, 210.0)), T);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        b.tubeConnects(3, 1, 1, Direction.WEST), "the tube draws no connection to the pipe"))
                .thenExecute(() -> b.pull(3, 1, 1, Direction.WEST))
                .thenExecuteAfter(100, () -> {
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    b.close(pipe.moles(N2), 790 * factor, "the nitrogen in the pipe");
                    b.close(pipe.moles(O2), 210 * factor, "the oxygen in the pipe");
                    h.assertTrue(b.held(3, 1, 1).isEmpty(), "the tube drew " + name(b.held(3, 1, 1)) + " out of air");
                    h.assertTrue(b.tubeConnects(3, 1, 1, Direction.WEST), "the tube let go of the pipe");

                    IChemicalHandler seen = b.chemical(2, 1, 1, Direction.EAST);
                    b.equal(seen.getChemicalTanks(), 1, "the tanks a pipe of air shows");
                    h.assertTrue(seen.getChemicalInTank(0).isEmpty(), "a pipe of air shows a chemical");
                    h.assertTrue(seen.extractChemical(1000, Action.EXECUTE).isEmpty(), "air gave a chemical");

                    // As a machine ejecting into the pipe's top would.
                    ChemicalStack left = b.chemical(2, 1, 1, Direction.UP)
                            .insertChemical(b.stack("mekanism:hydrogen", 100), Action.EXECUTE);
                    h.assertTrue(left.isEmpty(), "the pipe of air refused " + left.getAmount() + " mB of hydrogen");
                    b.close(b.inPipe(2, 1, 1).moles(H2), 100 * factor, "the hydrogen in the pipe");
                    b.close(b.inPipe(2, 1, 1).totalMoles(), 1100 * factor, "everything in the pipe");
                })
                .thenSucceed();
    }

    /**
     * A pipe grows an arm to a tank and shares its gas with it, as with any gas handler, until they
     * are level: the tank stands for a volume of one litre per millibucket, so its 64,000 mB beside a
     * pipe's few litres take nearly all of it, in whole millibuckets. The tank shows the pipe one
     * vessel at 293.15 K, and what stays in the pipe keeps the pipe's temperature.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_a_pipe_fills_a_tank_until_they_are_level(GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 1);
        b.tank(3, 1, 1, Direction.NORTH);
        double factor = factor();
        double start = 2000.25 * factor;
        h.assertTrue(
                b.chemical(3, 1, 1, Direction.WEST)
                        .insertChemical(b.stack("mekanism:oxygen", 1), Action.SIMULATE)
                        .isEmpty(),
                "the tank's side takes no oxygen, so this test's layout is wrong");
        b.fillPipe(2, 1, 1, Map.of(O2, start), 400);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe has grown no arm to the tank");
                    double stillToMove =
                            toMove(b.inPipe(2, 1, 1), O2, b.held(3, 1, 1).getAmount());
                    h.assertTrue(
                            stillToMove < factor + 1e-3,
                            "the pipe would still give the tank " + stillToMove / factor + " mB");
                })
                .thenExecuteAfter(60, () -> {
                    ChemicalStack tank = b.held(3, 1, 1);
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    b.equal(name(tank), "mekanism:oxygen", "what the tank holds");
                    b.close(tank.getAmount() * factor + pipe.moles(O2), start, "the oxygen in the pipe and the tank");
                    double stillToMove = toMove(pipe, O2, tank.getAmount());
                    h.assertTrue(
                            stillToMove > -1e-3 && stillToMove < factor + 1e-3,
                            "the pipe and the tank aren't level: " + stillToMove / factor + " mB would move");
                    h.assertTrue(tank.getAmount() > 1900, "the tank took only " + tank.getAmount() + " mB");
                    b.close(pipe.temperature(), 400, "the pipe's temperature");

                    GasMixture seen = b.gas(3, 1, 1, Direction.WEST).contents();
                    b.close(seen.volume(), TANK * MekanismSettings.litresPerMillibucket(), "the tank's volume");
                    b.close(seen.moles(O2), tank.getAmount() * factor, "the oxygen the tank shows");
                    b.close(seen.temperature(), T, "the temperature the tank shows");
                    b.close(seen.pressure(), tank.getAmount() * 101.325 / TANK, "the pressure the tank shows");
                })
                .thenSucceed();
    }

    /** The moles a pipe's sharing would still move into a tank of the standard size: toward level. */
    private static double toMove(GasMixture pipe, ResourceLocation gas, long inTank) {
        double tankLitres = TANK * MekanismSettings.litresPerMillibucket();
        return (pipe.moles(gas) * tankLitres - inTank * factor() * pipe.volume()) / (pipe.volume() + tankLitres);
    }

    /**
     * A tank's front gives, and a tank a player has just placed ejects there. Facing a pipe, it pushes
     * its chemical into the pipe at its own rate, as gas at 293.15 K, up to the 4,500 kPa a pump could
     * reach and no further: what doesn't fit, in whole millibuckets, stays in the tank. Every mole is in
     * the pipe or the tank.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_a_tank_ejects_into_a_pipe_up_to_the_pipes_limit(GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 1);
        b.tank(3, 1, 1, Direction.WEST);
        double factor = factor();
        long start = 20_000;
        b.fillTank(3, 1, 1, b.stack("mekanism:hydrogen", start));
        // 1 mB is 1 L at 101.325 kPa, so it adds this much pressure to the pipe at 293.15 K.
        double perMillibucket = 101.325 / b.inPipe(2, 1, 1).volume();
        h.assertTrue(PIPE_LIMIT / perMillibucket < start, "the pipe would take all the tank holds");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        b.inPipe(2, 1, 1).pressure() > PIPE_LIMIT - perMillibucket * (1 + 1e-9),
                        "the pipe is at " + b.inPipe(2, 1, 1).pressure() + " kPa"))
                .thenExecuteAfter(60, () -> {
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    long inTank = b.held(3, 1, 1).getAmount();
                    b.close(inTank * factor + pipe.moles(H2), start * factor, "the hydrogen in the pipe and the tank");
                    b.close(pipe.moles(H2), (start - inTank) * factor, "the pipe's hydrogen, in whole millibuckets");
                    b.equal(
                            start - inTank,
                            (long) Math.floor(PIPE_LIMIT / perMillibucket),
                            "the millibuckets that fit");
                    h.assertTrue(
                            pipe.pressure() <= PIPE_LIMIT * (1 + 1e-9), "the pipe is at " + pipe.pressure() + " kPa");
                    b.close(pipe.totalMoles(), pipe.moles(H2), "the pipe holds hydrogen only");
                    b.close(pipe.temperature(), T, "the temperature it arrived at");
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe lost its arm to the tank");
                })
                .thenSucceed();
    }

    /**
     * A tank set not to eject still gives at its front when asked, and a pipe asks: it draws the
     * tank's chemical as gas at 293.15 K, in whole millibuckets, until the two are level, and no
     * further. Every mole is in the pipe or the tank.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_a_pipe_drains_a_tank_that_does_not_eject_until_they_are_level(GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 1);
        b.quietTank(3, 1, 1, Direction.WEST);
        double factor = factor();
        b.fillTank(3, 1, 1, b.stack("mekanism:hydrogen", 3000));
        h.assertFalse(
                b.chemical(3, 1, 1, Direction.WEST)
                        .extractChemical(1, Action.SIMULATE)
                        .isEmpty(),
                "the tank's front gives no hydrogen, so this test's layout is wrong");
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(b.arm(2, 1, 1, Direction.EAST), "the pipe has grown no arm to the tank");
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    h.assertTrue(pipe.moles(H2) > 0, "no hydrogen has reached the pipe");
                    double stillToCome = -toMove(pipe, H2, b.held(3, 1, 1).getAmount());
                    h.assertTrue(
                            stillToCome < factor + 1e-3,
                            "the tank would still give the pipe " + stillToCome / factor + " mB");
                })
                .thenExecuteAfter(60, () -> {
                    GasMixture pipe = b.inPipe(2, 1, 1);
                    long inTank = b.held(3, 1, 1).getAmount();
                    b.close(inTank * factor + pipe.moles(H2), 3000 * factor, "the hydrogen in the pipe and the tank");
                    b.close(pipe.moles(H2), (3000 - inTank) * factor, "the pipe's hydrogen, in whole millibuckets");
                    double stillToCome = -toMove(pipe, H2, inTank);
                    h.assertTrue(
                            stillToCome > -1e-3 && stillToCome < factor + 1e-3,
                            "the pipe and the tank aren't level: " + stillToCome / factor + " mB would move");
                    b.close(pipe.totalMoles(), pipe.moles(H2), "the pipe holds hydrogen only");
                    b.close(pipe.temperature(), T, "the temperature it arrived at");
                })
                .thenSucceed();
    }

    // ---- MEK-013: what crosses ------------------------------------------------------------------

    /**
     * An unmapped chemical never becomes gas. Sulfuric acid that a tank ejects into a tube beside a
     * pipe stays in the tube; the pipe says it isn't valid and takes none of it, nor of an infuse type;
     * and a tank that holds it and faces a pipe stays joined to the pipe, shows it nothing and gives it
     * nothing.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_an_unmapped_chemical_stays_in_mekanism(GameTestHelper h) {
        Bench b = new Bench(h);
        b.cappedPipe(1, 1, 0);
        b.tube(3, 1, 0);
        b.tank(4, 1, 0, Direction.WEST);
        b.cappedPipe(1, 1, 2);
        b.tank(3, 1, 2, Direction.WEST);
        b.fillTank(3, 1, 2, b.stack("mekanism:sulfuric_acid", 2000));
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(b.tubeConnects(3, 1, 0, Direction.WEST), "the tube draws no connection to the pipe");
                    h.assertTrue(b.tubeConnects(3, 1, 0, Direction.EAST), "the tube draws no connection to the tank");
                    h.assertTrue(b.arm(2, 1, 2, Direction.EAST), "the pipe has grown no arm to the tank");
                })
                .thenExecute(() -> b.fillTank(4, 1, 0, b.stack("mekanism:sulfuric_acid", 500)))
                .thenExecuteAfter(100, () -> {
                    ChemicalStack tube = b.held(3, 1, 0);
                    b.equal(name(tube), "mekanism:sulfuric_acid", "what the tube holds");
                    b.equal(tube.getAmount(), 500L, "the millibuckets in the tube");
                    h.assertTrue(b.held(4, 1, 0).isEmpty(), "the tank still holds " + name(b.held(4, 1, 0)));
                    h.assertTrue(b.inPipe(2, 1, 0).isEmpty(), "the pipe holds " + b.inPipe(2, 1, 0));

                    IChemicalHandler pipe = b.chemical(2, 1, 0, Direction.EAST);
                    for (String chemical :
                            new String[] {"mekanism:sulfuric_acid", "mekanism:carbon", "mekanism:brine"}) {
                        ChemicalStack offered = b.stack(chemical, 100);
                        h.assertFalse(pipe.isValid(0, offered), "the pipe says " + chemical + " is valid");
                        b.equal(pipe.insertChemical(offered, Action.EXECUTE).getAmount(), 100L, chemical + " refused");
                        b.equal(
                                pipe.insertChemical(0, offered, Action.SIMULATE).getAmount(),
                                100L,
                                chemical + " refused");
                    }
                    h.assertTrue(pipe.isValid(0, b.stack("mekanism:oxygen", 100)), "the pipe says oxygen isn't valid");
                    h.assertTrue(b.inPipe(2, 1, 0).isEmpty(), "the pipe holds " + b.inPipe(2, 1, 0));

                    // The tank of acid: still joined, so the pipe's end stays closed, and inert.
                    IGasHandler tank = b.gas(3, 1, 2, Direction.WEST);
                    h.assertTrue(tank.contents().isEmpty(), "a tank of acid shows gas: " + tank.contents());
                    b.equal(
                            tank.drain(Map.of(O2, 50.0, H2, 50.0), IGasHandler.Action.EXECUTE),
                            GasParcel.EMPTY,
                            "what a tank of acid gave");
                    b.equal(b.held(3, 1, 2).getAmount(), 2000L, "the acid in the tank");
                    h.assertTrue(b.inPipe(2, 1, 2).isEmpty(), "the second pipe holds " + b.inPipe(2, 1, 2));
                    h.assertTrue(b.arm(2, 1, 2, Direction.EAST), "the pipe lost its arm to the tank of acid");
                })
                .thenSucceed();
    }

    /**
     * A pipe of air beside a tank gives it nothing. A pipe of oxygen with half a percent of nitrogen
     * gives the tank oxygen only, and only until the pipe is down to 99 % oxygen: the nitrogen is all
     * still in the pipe, with 99 times as much oxygen.
     */
    @GameTest(template = LAB, timeoutTicks = WAIT)
    public static void mek_013_air_stays_in_its_pipe_and_nearly_pure_oxygen_leaves_until_it_is_99_percent(
            GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        b.cappedPipe(1, 1, 0);
        b.tank(3, 1, 0, Direction.NORTH);
        b.fillPipe(2, 1, 0, millibuckets(Map.of(N2, 790.0, O2, 210.0)), T);
        b.cappedPipe(1, 1, 2);
        b.tank(3, 1, 2, Direction.NORTH);
        b.fillPipe(2, 1, 2, millibuckets(Map.of(O2, 995.0, N2, 5.0)), T);
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(b.arm(2, 1, 0, Direction.EAST), "the first pipe has grown no arm to its tank");
                    h.assertFalse(b.held(3, 1, 2).isEmpty(), "the second tank has taken no oxygen");
                })
                .thenExecuteAfter(100, () -> {
                    h.assertTrue(b.held(3, 1, 0).isEmpty(), "air gave the tank " + name(b.held(3, 1, 0)));
                    GasMixture air = b.inPipe(2, 1, 0);
                    b.close(air.moles(N2), 790 * factor, "the nitrogen in the pipe of air");
                    b.close(air.moles(O2), 210 * factor, "the oxygen in the pipe of air");

                    ChemicalStack tank = b.held(3, 1, 2);
                    GasMixture pipe = b.inPipe(2, 1, 2);
                    b.equal(name(tank), "mekanism:oxygen", "what the second tank holds");
                    b.close(pipe.moles(N2), 5 * factor, "the nitrogen in the second pipe");
                    b.close(tank.getAmount() * factor + pipe.moles(O2), 995 * factor, "the oxygen in both");
                    h.assertTrue(
                            pipe.moles(O2) >= 0.99 * pipe.totalMoles() * (1 - 1e-9),
                            "the pipe is left less than 99 % oxygen: " + pipe);
                    // 5 mB of nitrogen keep 495 of oxygen; the rest, in whole millibuckets, has left.
                    h.assertTrue(
                            tank.getAmount() > 400 && tank.getAmount() <= 500,
                            "the tank took " + tank.getAmount() + " mB of the 500 that could leave");
                })
                .thenSucceed();
    }

    /**
     * The rule for mixtures is one server setting. At its default a pipe of air shows one empty tank.
     * Set to 0, the pipe shows a tank for each gas and an empty one, each gas leaves on its own by
     * tank, by amount or by chemical, and a tank takes the first gas of a mixed parcel it can.
     */
    @GameTest(template = LAB)
    public static void mek_013_with_the_rule_off_each_gas_leaves_on_its_own(GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        closedPipe(b, 1, 0);
        b.fillPipe(2, 1, 0, millibuckets(Map.of(N2, 790.0, O2, 210.0)), T);
        b.tank(5, 1, 2, Direction.NORTH);
        IChemicalHandler pipe = view(b, 1, 0);
        IGasHandler tank = b.gas(5, 1, 2, Direction.WEST);
        GasParcel mixed = new GasParcel(millibuckets(Map.of(N2, 10.0, O2, 20.0)), T);

        b.equal(MekanismSettings.minPurity(), 0.99, "the default share");
        b.equal(pipe.getChemicalTanks(), 1, "the tanks a pipe of air shows");
        h.assertTrue(pipe.getChemicalInTank(0).isEmpty(), "a pipe of air shows a chemical");
        h.assertTrue(pipe.getChemicalTankCapacity(0) > 0, "a pipe of air shows no room");
        h.assertTrue(pipe.extractChemical(0, 1000, Action.EXECUTE).isEmpty(), "air gave a chemical");
        b.equal(tank.fill(mixed, IGasHandler.Action.EXECUTE), GasParcel.EMPTY, "what a tank took of a mixture");

        // No test runs between the change and its undoing, since both are in this one call.
        try {
            MekanismSettings.MIN_PURITY.set(0.0);
            b.equal(pipe.getChemicalTanks(), 3, "the tanks a pipe of air shows with the rule off");
            b.equal(name(pipe.getChemicalInTank(0)), "zas:nitrogen", "the first tank");
            b.equal(pipe.getChemicalInTank(0).getAmount(), 790L, "the nitrogen it shows");
            b.equal(name(pipe.getChemicalInTank(1)), "mekanism:oxygen", "the second tank");
            b.equal(pipe.getChemicalInTank(1).getAmount(), 210L, "the oxygen it shows");
            h.assertTrue(pipe.getChemicalInTank(2).isEmpty(), "the last tank isn't empty");

            ChemicalStack byTank = pipe.extractChemical(1, 100, Action.EXECUTE);
            b.equal(name(byTank) + " " + byTank.getAmount(), "mekanism:oxygen 100", "drawn by tank");
            ChemicalStack byChemical = pipe.extractChemical(b.stack("zas:nitrogen", 90), Action.EXECUTE);
            b.equal(name(byChemical) + " " + byChemical.getAmount(), "zas:nitrogen 90", "drawn by chemical");
            ChemicalStack byAmount = pipe.extractChemical(50, Action.EXECUTE);
            b.equal(name(byAmount) + " " + byAmount.getAmount(), "zas:nitrogen 50", "drawn by amount");
            // A chemical the pipe doesn't hold goes in through the tanks, as Mekanism's own loop does it.
            h.assertTrue(
                    pipe.insertChemical(b.stack("mekanism:hydrogen", 40), Action.EXECUTE)
                            .isEmpty(),
                    "the pipe refused hydrogen");
            GasMixture held = b.inPipe(2, 1, 0);
            b.close(held.moles(N2), 650 * factor, "the nitrogen left");
            b.close(held.moles(O2), 110 * factor, "the oxygen left");
            b.close(held.moles(H2), 40 * factor, "the hydrogen put in");

            // The tank has one tank: it takes the first gas offered, and then no other.
            GasParcel took = tank.fill(mixed, IGasHandler.Action.EXECUTE);
            b.close(took.moles(N2), 10 * factor, "the nitrogen the tank took");
            b.close(took.totalMoles(), 10 * factor, "all the tank took");
            b.equal(name(b.held(5, 1, 2)), "zas:nitrogen", "what the tank holds");
        } finally {
            MekanismSettings.MIN_PURITY.set(0.99);
        }

        // With the rule on again: one gas only, and not a second gas beside one the tank holds.
        b.equal(pipe.getChemicalTanks(), 1, "the tanks the pipe shows with the rule on again");
        GasParcel oxygen = new GasParcel(millibuckets(Map.of(O2, 20.0)), T);
        b.equal(tank.fill(oxygen, IGasHandler.Action.EXECUTE), GasParcel.EMPTY, "oxygen into a tank of nitrogen");
        GasParcel nitrogen = new GasParcel(millibuckets(Map.of(N2, 5.5)), T);
        b.close(tank.fill(nitrogen, IGasHandler.Action.SIMULATE).moles(N2), 5 * factor, "what it would take");
        b.equal(b.held(5, 1, 2).getAmount(), 10L, "the tank after a simulated fill");
        b.close(tank.fill(nitrogen, IGasHandler.Action.EXECUTE).moles(N2), 5 * factor, "whole millibuckets taken");
        b.equal(b.held(5, 1, 2).getAmount(), 15L, "the nitrogen in the tank");
        h.succeed();
    }

    /**
     * A chemical goes into a pipe only as far as a pump could push gas, 4,500 kPa, in whole
     * millibuckets: one more would be over. A simulated insert says the same and changes nothing. The
     * pipe then reads as a full tank, and takes no other chemical either.
     */
    @GameTest(template = LAB)
    public static void mek_013_a_chemical_stops_at_the_pipes_pressure_limit(GameTestHelper h) {
        Bench b = new Bench(h);
        closedPipe(b, 1, 1);
        IChemicalHandler pipe = view(b, 1, 1);
        double volume = b.inPipe(2, 1, 1).volume();
        // 1 mB is 1 L at 101.325 kPa, so it adds this much pressure to the pipe at 293.15 K.
        double perMillibucket = 101.325 / volume;
        long offered = 10_000_000;

        h.assertTrue(pipe.getChemicalTankCapacity(0) > 0, "an empty pipe shows no room");
        long wouldTake = offered
                - pipe.insertChemical(b.stack("mekanism:oxygen", offered), Action.SIMULATE)
                        .getAmount();
        h.assertTrue(b.inPipe(2, 1, 1).isEmpty(), "a simulated insert put gas into the pipe");
        long took = offered
                - pipe.insertChemical(b.stack("mekanism:oxygen", offered), Action.EXECUTE)
                        .getAmount();
        b.equal(took, wouldTake, "what it took, against what it said it would");
        b.equal(took, (long) Math.floor(PIPE_LIMIT / perMillibucket), "the millibuckets that fit under 4,500 kPa");

        GasMixture held = b.inPipe(2, 1, 1);
        b.close(held.moles(O2), took * factor(), "the oxygen in the pipe");
        h.assertTrue(held.pressure() <= PIPE_LIMIT * (1 + 1e-9), "the pipe is at " + held.pressure() + " kPa");
        h.assertTrue(
                held.pressure() > PIPE_LIMIT - perMillibucket * (1 + 1e-9),
                "another millibucket would have fitted: " + held.pressure() + " kPa");

        b.equal(
                pipe.insertChemical(b.stack("mekanism:oxygen", 1000), Action.EXECUTE)
                        .getAmount(),
                1000L,
                "more oxygen");
        b.equal(
                pipe.insertChemical(b.stack("mekanism:hydrogen", 1000), Action.EXECUTE)
                        .getAmount(),
                1000L,
                "hydrogen");
        b.equal(pipe.getChemicalInTank(0).getAmount(), took, "the oxygen the pipe shows");
        b.equal(pipe.getChemicalTankCapacity(0), took, "the size of a tank that is full");
        b.close(b.inPipe(2, 1, 1).totalMoles(), took * factor(), "everything in the pipe");
        h.succeed();
    }

    /**
     * Rounding goes down, and the fraction stays where it was. A pipe holding 10.75 mB of oxygen
     * shows 10; drawing more than that gives 10 and leaves the three quarters in the pipe; what a
     * simulated draw says is what a real one gives. Offered 5.5 mB of gas, a tank takes 5 and says so,
     * and asked for 2.5 mB it gives 2. It takes gas on a face that takes chemicals and gives it on the
     * face that gives them, and not the other way round.
     */
    @GameTest(template = LAB)
    public static void mek_013_whole_millibuckets_cross_and_the_fraction_stays_behind(GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        closedPipe(b, 1, 0);
        IChemicalHandler pipe = view(b, 1, 0);
        b.fillPipe(2, 1, 0, Map.of(O2, 10.75 * factor), 320);

        b.equal(name(pipe.getChemicalInTank(0)), "mekanism:oxygen", "what the pipe shows");
        b.equal(pipe.getChemicalInTank(0).getAmount(), 10L, "the millibuckets the pipe shows");
        b.equal(pipe.extractChemical(0, 4, Action.SIMULATE).getAmount(), 4L, "a simulated draw of 4");
        b.close(b.inPipe(2, 1, 0).moles(O2), 10.75 * factor, "the pipe after a simulated draw");
        b.equal(pipe.extractChemical(0, 4, Action.EXECUTE).getAmount(), 4L, "a draw of 4");
        b.close(b.inPipe(2, 1, 0).moles(O2), 6.75 * factor, "the pipe after a draw of 4");
        b.equal(pipe.extractChemical(0, 1000, Action.EXECUTE).getAmount(), 6L, "a draw of all there is");
        GasMixture left = b.inPipe(2, 1, 0);
        b.close(left.moles(O2), 0.75 * factor, "the fraction left in the pipe");
        b.close(left.temperature(), 320, "the pipe's temperature");
        h.assertTrue(pipe.getChemicalInTank(0).isEmpty(), "the pipe shows a chemical for less than 1 mB");
        h.assertTrue(pipe.extractChemical(0, 1000, Action.EXECUTE).isEmpty(), "the fraction left as a chemical");
        // Going in, the amounts are whole already: 3 mB more make 3.75.
        h.assertTrue(
                pipe.insertChemical(b.stack("mekanism:oxygen", 3), Action.EXECUTE)
                        .isEmpty(),
                "3 mB refused");
        b.close(b.inPipe(2, 1, 0).moles(O2), 3.75 * factor, "the pipe after 3 mB more");

        b.tank(5, 1, 2, Direction.WEST);
        IGasHandler back = b.gas(5, 1, 2, Direction.EAST);
        IGasHandler front = b.gas(5, 1, 2, Direction.WEST);
        GasParcel offered = new GasParcel(Map.of(O2, 5.5 * factor), 320);
        b.equal(front.fill(offered, IGasHandler.Action.EXECUTE), GasParcel.EMPTY, "what the tank's front took");
        b.close(back.fill(offered, IGasHandler.Action.EXECUTE).moles(O2), 5 * factor, "what the tank took of 5.5 mB");
        b.equal(b.held(5, 1, 2).getAmount(), 5L, "the millibuckets in the tank");
        Map<ResourceLocation, Double> asked = Map.of(O2, 2.5 * factor);
        b.equal(back.drain(asked, IGasHandler.Action.EXECUTE), GasParcel.EMPTY, "what the tank's back gave");
        GasParcel gave = front.drain(asked, IGasHandler.Action.EXECUTE);
        b.close(gave.moles(O2), 2 * factor, "what the tank gave of 2.5 mB");
        b.close(gave.temperature(), T, "the temperature it gave it at");
        b.equal(b.held(5, 1, 2).getAmount(), 3L, "the millibuckets left in the tank");
        b.equal(
                front.drain(Map.of(O2, 0.9 * factor), IGasHandler.Action.EXECUTE),
                GasParcel.EMPTY,
                "what the tank gave of less than 1 mB");
        h.succeed();
    }

    /**
     * A chemical has no temperature, so its gas arrives at 293.15 K, and steam at 373.15 K. On the
     * way out a gas's temperature picks its chemical: hot water vapour shows as steam, cool as water
     * vapour. Fusion fuel arrives as half deuterium and half tritium, which is a mixture, so neither
     * leaves again.
     */
    @GameTest(template = LAB)
    public static void mek_013_steam_arrives_hot_and_fusion_fuel_arrives_as_its_two_gases(GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        closedPipe(b, 0, 0);
        IChemicalHandler hot = view(b, 0, 0);
        h.assertTrue(
                hot.insertChemical(b.stack("mekanism:steam", 100), Action.EXECUTE)
                        .isEmpty(),
                "steam refused");
        GasMixture steam = b.inPipe(2, 0, 0);
        b.close(steam.moles(H2O), 100 * factor, "the water vapour 100 mB of steam make");
        b.close(steam.temperature(), 373.15, "the temperature steam arrives at");
        b.equal(name(hot.getChemicalInTank(0)), "mekanism:steam", "what hot water vapour shows as");
        b.equal(hot.getChemicalInTank(0).getAmount(), 100L, "the steam the pipe shows");

        closedPipe(b, 0, 2);
        IChemicalHandler cool = view(b, 0, 2);
        b.fillPipe(2, 0, 2, Map.of(H2O, 100 * factor), 300);
        b.equal(name(cool.getChemicalInTank(0)), "mekanism:water_vapor", "what cool water vapour shows as");
        h.assertTrue(
                cool.insertChemical(b.stack("mekanism:water_vapor", 50), Action.EXECUTE)
                        .isEmpty(),
                "water vapour refused");
        b.close(b.inPipe(2, 0, 2).moles(H2O), 150 * factor, "the water vapour in the cool pipe");
        h.assertTrue(b.inPipe(2, 0, 2).temperature() < 300, "water vapour at 293.15 K warmed a pipe at 300 K");

        closedPipe(b, 2, 0);
        IChemicalHandler fuel = view(b, 2, 0);
        h.assertTrue(
                fuel.insertChemical(b.stack("mekanismgenerators:fusion_fuel", 1000), Action.EXECUTE)
                        .isEmpty(),
                "fusion fuel refused");
        GasMixture gases = b.inPipe(2, 2, 0);
        b.close(gases.moles(id("zas:deuterium")), 500 * factor, "the deuterium 1,000 mB of fuel make");
        b.close(gases.moles(id("zas:tritium")), 500 * factor, "the tritium 1,000 mB of fuel make");
        b.close(gases.totalMoles(), 1000 * factor, "everything 1,000 mB of fuel make");
        b.close(gases.temperature(), T, "the temperature fuel arrives at");
        h.assertTrue(fuel.getChemicalInTank(0).isEmpty(), "the two gases show as " + name(fuel.getChemicalInTank(0)));
        h.succeed();
    }

    // ---- MEK-023: radiation on the connection ---------------------------------------------------

    /**
     * A radioactive chemical that a pack mapped releases its radiation once, through Mekanism, as it
     * enters a pipe: none for a simulated insert, and exactly what Mekanism itself would release for
     * that amount. Its gas carries none, so taking the gas out again releases no more. A gas whose
     * only chemical is radioactive never goes back: the pipe shows no tank for it, and a tank
     * refuses it.
     */
    @GameTest(template = LAB)
    public static void mek_023_a_mapped_radioactive_chemical_radiates_once_as_it_enters_a_pipe(GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        IRadiationManager radiation = IRadiationManager.INSTANCE;
        h.assertTrue(radiation.isRadiationEnabled(), "Mekanism's radiation is off on this server");
        closedPipe(b, 1, 0);
        IChemicalHandler pipe = view(b, 1, 0);
        BlockPos at = b.at(2, 1, 0);
        ChemicalStack waste = b.stack("mekanism:nuclear_waste", 1000);
        h.assertTrue(waste.isRadioactive(), "nuclear waste isn't radioactive");

        double before = radiation.getRadiationLevel(h.getLevel(), at);
        h.assertTrue(pipe.insertChemical(waste, Action.SIMULATE).isEmpty(), "the waste would be refused");
        b.close(radiation.getRadiationLevel(h.getLevel(), at), before, "the radiation after a simulated insert");
        h.assertTrue(pipe.insertChemical(waste, Action.EXECUTE).isEmpty(), "the waste was refused");
        double after = radiation.getRadiationLevel(h.getLevel(), at);
        b.close(after - before, waste.getRadioactivity(), "the radiation 1,000 mB of waste released");
        // The tests' data maps the waste to carbon dioxide, one way: it leaves as carbon dioxide.
        b.close(b.inPipe(2, 1, 0).moles(CO2), 1000 * factor, "the carbon dioxide the waste became");
        b.equal(name(pipe.getChemicalInTank(0)), "zas:carbon_dioxide", "what the pipe shows");
        b.equal(pipe.extractChemical(0, 1000, Action.EXECUTE).getAmount(), 1000L, "the carbon dioxide drawn");
        b.close(radiation.getRadiationLevel(h.getLevel(), at), after, "the radiation after the gas left");

        // Polonium is mapped both ways to a gas of its own, so only this rule keeps it from going back.
        ChemicalStack polonium = b.stack("mekanism:polonium", 200);
        ResourceLocation radon = id("zas_mekanism:radon");
        h.assertTrue(pipe.insertChemical(polonium, Action.EXECUTE).isEmpty(), "the polonium was refused");
        b.close(
                radiation.getRadiationLevel(h.getLevel(), at) - after,
                polonium.getRadioactivity(),
                "the radiation 200 mB of polonium released");
        b.close(b.inPipe(2, 1, 0).moles(radon), 200 * factor, "the gas the polonium became");
        b.equal(
                ChemicalGases.chemical(radon, T)
                        .map(c -> c.unwrapKey().orElseThrow().location()),
                Optional.of(id("mekanism:polonium")),
                "the chemical the map leads back to");
        h.assertTrue(pipe.getChemicalInTank(0).isEmpty(), "the pipe shows " + name(pipe.getChemicalInTank(0)));
        h.assertTrue(pipe.extractChemical(0, 200, Action.EXECUTE).isEmpty(), "the gas went back as polonium");
        b.tank(5, 1, 2, Direction.NORTH);
        b.equal(
                b.gas(5, 1, 2, Direction.WEST)
                        .fill(new GasParcel(Map.of(radon, 100 * factor), T), IGasHandler.Action.EXECUTE),
                GasParcel.EMPTY,
                "what a tank took of the gas");
        h.assertTrue(b.held(5, 1, 2).isEmpty(), "the tank holds " + name(b.held(5, 1, 2)));
        b.close(b.inPipe(2, 1, 0).moles(radon), 200 * factor, "the gas still in the pipe");
        h.succeed();
    }

    /**
     * The same on the other side: when a pipe drains a tank of a mapped radioactive chemical, the
     * radiation of what it took is released once at the tank, and none for a simulated drain. The tank
     * is a creative one, which never runs down: Mekanism's other tanks hold no radioactive chemical.
     */
    @GameTest(template = LAB, templateNamespace = ALWAYS)
    public static void mek_023_a_mapped_radioactive_chemical_radiates_once_as_a_pipe_drains_it(GameTestHelper h) {
        Bench b = new Bench(h);
        double factor = factor();
        IRadiationManager radiation = IRadiationManager.INSTANCE;
        b.creativeTank(3, 1, 1, Direction.WEST, b.stack("mekanism:nuclear_waste", 1000));
        IGasHandler tank = b.gas(3, 1, 1, Direction.WEST);
        BlockPos at = b.at(3, 1, 1);
        b.close(tank.contents().moles(CO2), 1000 * factor, "the gas a tank of waste shows");

        double before = radiation.getRadiationLevel(h.getLevel(), at);
        Map<ResourceLocation, Double> wanted = Map.of(CO2, 100.5 * factor);
        b.close(tank.drain(wanted, IGasHandler.Action.SIMULATE).moles(CO2), 100 * factor, "what it would give");
        b.close(radiation.getRadiationLevel(h.getLevel(), at), before, "the radiation after a simulated drain");
        b.equal(b.held(3, 1, 1).getAmount(), 1000L, "the waste after a simulated drain");

        GasParcel gave = tank.drain(wanted, IGasHandler.Action.EXECUTE);
        b.close(gave.moles(CO2), 100 * factor, "what it gave");
        b.equal(b.held(3, 1, 1).getAmount(), 1000L, "the waste in the creative tank");
        b.close(
                radiation.getRadiationLevel(h.getLevel(), at) - before,
                b.stack("mekanism:nuclear_waste", 100).getRadioactivity(),
                "the radiation 100 mB of waste released");

        // An ordinary tank refuses a radioactive chemical, so the gas of one never goes into it as that.
        b.tank(5, 1, 1, Direction.NORTH);
        ChemicalStack offered = b.stack("mekanism:nuclear_waste", 10);
        b.equal(
                b.chemical(5, 1, 1, Direction.WEST)
                        .insertChemical(offered, Action.SIMULATE)
                        .getAmount(),
                10L,
                "the nuclear waste a Basic Chemical Tank refuses");
        h.succeed();
    }
}
