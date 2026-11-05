package io.github.west3436.zas.atmos.gametest;

import static io.github.west3436.zas.atmos.gametest.Area.N2;
import static io.github.west3436.zas.atmos.gametest.Area.O2;
import static io.github.west3436.zas.atmos.gametest.Area.T;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.canister.CanisterBlockEntity;
import io.github.west3436.zas.atmos.device.AtmosTicks;
import io.github.west3436.zas.atmos.failure.ContainerBlast;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.igniter.IgniterBlockEntity;
import io.github.west3436.zas.atmos.inline.InlineDeviceBlock;
import io.github.west3436.zas.atmos.pump.PressurePumpBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.atmos.vent.VentSettings;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * S-13, the toxins lab: from a redstone-triggered H₂ burn to a tank bomb.
 *
 * <p>The burn and the pumping run within the first game tick, with the test driving device steps and
 * atmos ticks. The TTV's timer then runs in real time, ticked by the level, so the chunk is
 * force-loaded and a pig can stand in the blast. It has a batch of its own, since it drives atmos
 * ticks.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ToxinsLabTests {
    private static final String BATCH = "zas_atmos_toxins_lab";
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");

    /** Hydrogen's autoignition point, K (FIRE-003). */
    private static final double AUTOIGNITION = 858;

    /** How far the pump charges the exhaust canister, kPa: above the most a canister fills a tank to. */
    private static final double PUMP_TARGET = 1_100;

    /** How far the canister fills the hot tank, kPa: as far as a canister goes (CONT-039). */
    private static final double HOT_FILL_KPA = 1_013;

    /** The H₂ tank's fill, kPa at room temperature: about 5 mol in 70 L. */
    private static final double H2_FILL_KPA = 174;

    private ToxinsLabTests() {}

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void s_13_toxins_lab(GameTestHelper h) {
        h.assertValueEqual(ZasServerConfig.preset(), AtmospherePreset.NORMAL, "the preset");
        Blasts.listen();
        Area a = new Area(h, 109);
        a.forceLoad(true);

        // The burn chamber: an igniter on the floor, and a vent in the east wall leading through a
        // pressure pump to a canister on a port. The canister stands outside: it would fail in the
        // chamber's hot air (CONT-085), and the gas inside it is no danger to it.
        a.box(0, 0, 4, 4);
        a.place(1, 1, 2, AtmosBlocks.IGNITER.get().defaultBlockState());
        IgniterBlockEntity igniter = a.entity(1, 1, 2);
        igniter.power().setTestPowered(true);
        a.place(4, 2, 2, GasDeviceTests.vent(Direction.WEST));
        a.place(5, 2, 2, GasDeviceTests.pipe());
        a.place(
                6,
                2,
                2,
                AtmosBlocks.PRESSURE_PUMP.get().defaultBlockState().setValue(InlineDeviceBlock.FACING, Direction.EAST));
        a.place(7, 2, 2, GasDeviceTests.pipe());
        a.place(7, 3, 2, GasDeviceTests.port());
        a.place(7, 4, 2, GasDeviceTests.canister());
        VentBlockEntity vent = a.entity(4, 2, 2);
        vent.power().setTestPowered(true);
        PressurePumpBlockEntity pump = a.entity(6, 2, 2);
        pump.power().setTestPowered(true);
        CanisterBlockEntity exhaust = a.entity(7, 4, 2);

        // 12 % H2 in O2 at one atmosphere. A zone in a sealed room starts with its air, so its
        // nitrogen has to come out: with it there, too much of the heat goes into the nitrogen and
        // the exhaust ends up below H2's autoignition.
        double moles = 101.325 * 27_000 / (GasMixture.GAS_CONSTANT * T);
        a.fill(2, 2, 2, Map.of(O2, 0.88 * moles, H2, 0.12 * moles));
        a.settle(GasMutations.remove(
                a.level, a.point(2, 2, 2), Map.of(N2, a.zone(2, 2, 2).mixture().moles(N2)), Area.SOURCE));

        // A redstone signal lights it. Nothing burns until then.
        for (int i = 0; i < 4; i++) {
            a.step();
        }
        h.assertValueEqual(a.zone(2, 2, 2).mixture().moles(H2O), 0.0, "water before the igniter fired");
        a.set(0, 1, 2, Blocks.REDSTONE_BLOCK.defaultBlockState());
        for (int i = 0; i < 90 && a.zone(2, 2, 2).mixture().moles(H2) > 0.005 * moles; i++) {
            a.step();
        }
        a.set(0, 1, 2, Blocks.STONE.defaultBlockState());
        GasMixture burnt = a.zone(2, 2, 2).mixture();
        h.assertTrue(burnt.moles(H2O) > 0.11 * moles, "the chamber hardly burned: " + burnt);
        h.assertTrue(burnt.temperature() > AUTOIGNITION, "the exhaust isn't hot enough: " + burnt);

        // The vent draws the exhaust out and the pump drives it into the canister, up to its target.
        h.assertTrue(pump.input().network() != pump.output().network(), "the pump doesn't divide the line");
        h.assertTrue(pump.output().network() == exhaust.network(), "the pump doesn't feed the canister");
        pump.set(PUMP_TARGET);
        pump.setOn(true);
        vent.command(VentSettings.siphon(0));
        GasStore canisterGas = exhaust.network().gas();
        for (int i = 0; i < 120 && canisterGas.pressure() < PUMP_TARGET - 1; i++) {
            a.step();
        }
        vent.command(VentSettings.OFF);
        pump.setOn(false);
        h.assertTrue(canisterGas.pressure() >= HOT_FILL_KPA, "the canister isn't full enough: " + canisterGas);
        h.assertTrue(canisterGas.temperature() > AUTOIGNITION, "the exhaust cooled in the line: " + canisterGas);

        // A tank filled from it, above autoignition.
        ItemStack hot = new ItemStack(AtmosItems.EMPTY_TANK.get());
        exhaust.setReleasePressure(HOT_FILL_KPA);
        h.assertTrue(exhaust.fillTank(hot) > 0, "the exhaust canister didn't fill the tank");
        GasMixture hotGas = GasTankItem.contents(hot);
        h.assertTrue(hotGas.temperature() > AUTOIGNITION, "the hot tank isn't above autoignition: " + hotGas);
        h.assertTrue(hotGas.moles(O2) > hotGas.totalMoles() / 2, "the exhaust isn't O2-rich: " + hotGas);

        // An H2 tank filled from an H2 canister.
        a.place(9, 1, 0, GasDeviceTests.canister());
        CanisterBlockEntity fuel = a.entity(9, 1, 0);
        fuel.network().gas().add(new GasParcel(Map.of(H2, 100.0), T));
        fuel.setReleasePressure(H2_FILL_KPA);
        ItemStack hydrogen = new ItemStack(AtmosItems.EMPTY_TANK.get());
        h.assertTrue(fuel.fillTank(hydrogen) > 0, "the H2 canister didn't fill the tank");

        // What the two will do when the valve opens, worked out on copies of the tanks: the mix
        // stays above autoignition, and burning takes it past the 50 atm a tank explodes at.
        TransferValve trial = new TransferValve(hot.copy(), hydrogen.copy(), false);
        trial.open();
        GasMixture mix = GasTankItem.contents(trial.second());
        h.assertTrue(mix.temperature() > AUTOIGNITION, "the mix is below autoignition: " + mix);
        GasMixture burned = Reactions.applyStandalone(mix, AtmosTicks.SECONDS, false);
        ContainerFailure.Verdict verdict = ContainerFailure.tank(burned.pressure(), ContainerBlast.powerCap());
        h.assertValueEqual(verdict.kind(), ContainerFailure.Kind.EXPLODE, "what the burnt mix does: " + burned);

        // Loaded into a TTV with a short timer, it's left on the floor of an open test pad.
        for (int x = 10; x <= 16; x++) {
            for (int z = 0; z <= 4; z++) {
                a.set(x, 0, z, Blocks.STONE.defaultBlockState());
            }
        }
        a.set(13, 1, 0, Blocks.GLASS.defaultBlockState());
        a.set(13, 1, 4, Blocks.OAK_PLANKS.defaultBlockState());

        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValve valve = TransferValveItem.valve(stack);
        h.assertTrue(valve.attach(hot), "the TTV refused the hot tank");
        h.assertTrue(valve.attach(hydrogen), "the TTV refused the H2 tank");
        TransferValveItem.save(stack, valve);
        TransferValveItem.setTimer(stack, 2);

        FakePlayer p = FakePlayerFactory.get(a.level, new GameProfile(UUID.randomUUID(), "zas_toxins"));
        p.moveTo(Vec3.atBottomCenterOf(a.abs(13, 1, 2)));
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        PrimedTransferValve primed = TransferValveItem.start(p, InteractionHand.MAIN_HAND);
        h.assertTrue(primed != null, "the TTV refused to prime");
        h.assertFalse(primed.valve().isOpen(), "the valve opened with 2 s on the timer");

        Pig pig = EntityType.PIG.create(a.level);
        pig.moveTo(Vec3.atBottomCenterOf(a.abs(15, 1, 2)));
        pig.setNoAi(true);
        a.level.addFreshEntity(pig);
        float full = pig.getHealth();
        List<Explosion> blasts = new ArrayList<>();
        boolean[] outlasted = {false};

        // The tank explodes with the power the burnt mix's pressure gives: it hurts the pig, and under
        // Normal it breaks nothing.
        h.succeedWhen(() -> {
            blasts.addAll(Blasts.take(a.point(13, 1, 2)));
            h.assertValueEqual(blasts.size(), 1, "explosions");
            // Asked as soon as the blast is seen: a TTV left over would be gone 5 s later as a dud.
            outlasted[0] |= !primed.isRemoved();
            h.assertFalse(outlasted[0], "the TTV outlasted its tank");
            h.assertTrue(
                    Math.abs(blasts.get(0).radius() - verdict.power()) < 1e-4,
                    "the explosion's power: expected " + verdict.power() + ", got "
                            + blasts.get(0).radius());
            h.assertTrue(pig.getHealth() < full, "the blast didn't hurt the pig");
            h.assertTrue(a.get(13, 1, 0).is(Blocks.GLASS), "the blast broke the glass under Normal");
            h.assertTrue(a.get(13, 1, 4).is(Blocks.OAK_PLANKS), "the blast broke the planks under Normal");
            for (int x = 10; x <= 16; x++) {
                for (int z = 0; z <= 4; z++) {
                    h.assertTrue(a.get(x, 0, z).is(Blocks.STONE), "the blast broke the floor under Normal");
                }
            }
            pig.discard();
            a.clear(-1, -1, 17, 5);
            a.forceLoad(false);
        });
    }
}
