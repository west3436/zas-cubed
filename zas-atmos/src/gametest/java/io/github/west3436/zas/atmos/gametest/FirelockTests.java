package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.atmos.controller.ControllerPreset;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlockEntity;
import io.github.west3436.zas.atmos.firelock.Lockdown;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.CrowbarItem;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.vent.VentBlock;
import io.github.west3436.zas.atmos.vent.VentBlockEntity;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import io.github.west3436.zas.engine.heat.HeatSettings;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.geometry.Geometries;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The firelock (CONT-043, CONT-092 to CONT-094) between two sealed 3 × 3 × 3 rooms that share the
 * wall at x = 4: room A (x 0–4) and room B (x 4–8). The firelock stands in that wall at (4, 1, 2),
 * facing east, so its panel seals its east face, towards B, and its cells join A. Each controller is
 * on its room's north wall with the test FE block behind it. Each test steps the controllers and the
 * firelock itself and drives the atmos ticks, within one game tick, in a batch of its own.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FirelockTests {
    private static final String BATCH = "zas_atmos_firelock";
    private static final ResourceLocation CO2 = ControllerPreset.CO2;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    private FirelockTests() {}

    /** Rooms A and B and the open firelock between them. */
    private static FirelockBlockEntity rooms(Site s) {
        s.clear();
        s.room(0, 4);
        s.room(4, 8);
        BlockState lower = AtmosBlocks.FIRELOCK
                .get()
                .defaultBlockState()
                .setValue(FirelockBlock.FACING, Direction.EAST)
                .setValue(FirelockBlock.OPEN, true);
        s.set(4, 1, 2, lower);
        s.set(4, 2, 2, lower.setValue(FirelockBlock.HALF, DoubleBlockHalf.UPPER));
        seed(s, 2);
        seed(s, 6);
        return s.entity(4, 1, 2, FirelockBlockEntity.class);
    }

    /** Adds a little N₂ to the room at x, which gives it a zone. */
    static void seed(Site s, int x) {
        s.settle(GasMutations.add(s.level, s.point(x, 2, 2), new GasParcel(Map.of(N2, 0.01), 293.15), Site.SOURCE));
    }

    /** Removes the CO₂ of the room at x, if it has any. */
    private static void clearCo2(Site s, int x) {
        if (s.zone(x, 2, 2).mixture().moles(CO2) > 0) {
            s.removeGas(x, 2, 2, CO2);
        }
    }

    /** A controller on a room's north wall, at x, with the test FE block behind it. */
    private static AreaAirControllerBlockEntity controller(Site s, int x) {
        s.set(x, 2, 0, TestParts.battery().defaultBlockState());
        return s.controller(x);
    }

    private static boolean open(Site s) {
        return s.level.getBlockState(s.abs(4, 1, 2)).getValue(FirelockBlock.OPEN);
    }

    private static boolean lockedState(Site s) {
        return s.level.getBlockState(s.abs(4, 1, 2)).getValue(FirelockBlock.LOCKED);
    }

    private static void run(GameTestHelper h, Site s, Runnable checks) {
        try {
            checks.run();
        } finally {
            s.clear();
        }
        h.succeed();
    }

    /**
     * {@link #run} with heat off (HEAT): for checks of exact temperatures and pressures, which
     * conduction through the walls would otherwise move within the atmos tick.
     */
    private static void runWithoutHeat(GameTestHelper h, Site s, Runnable checks) {
        ZasServerConfig.overrideHeatForTest(HeatSettings.OFF);
        try {
            run(h, s, checks);
        } finally {
            ZasServerConfig.overrideHeatForTest(null);
        }
    }

    /** Uses the firelock by hand, as a right click with an empty hand does. */
    private static void use(Site s, Player player, int y) {
        BlockPos pos = s.abs(4, y, 2);
        s.level
                .getBlockState(pos)
                .useWithoutItem(s.level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.WEST, pos, false));
    }

    /** Empties the zone at a point and fills it with N₂ at a pressure and temperature. */
    private static void fillAt(Site s, int x, double kpa, double kelvin) {
        s.empty(x, 2, 2);
        ZoneInfo zone = s.zone(x, 2, 2);
        double moles = kpa * zone.volume() / (GasMixture.GAS_CONSTANT * kelvin);
        s.settle(GasMutations.add(s.level, s.point(x, 2, 2), new GasParcel(Map.of(N2, moles), kelvin), Site.SOURCE));
    }

    // ---- GEO-007 for the firelock ---------------------------------------------------------

    /**
     * The firelock is in {@code zas:zone_boundary}, and the framework makes the face its panel
     * seals, and only that face, its boundary, in both states. Open, it passes gas but keeps A and
     * B separate zones, with its cells in A; closed, it passes none; and opening and closing it
     * never changes a zone's cells (GEO-007, GEO-044, CONT-043). There are no controllers, so
     * nothing closes it but the hand.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_043_firelock_is_a_zone_boundary_on_its_panel_face(GameTestHelper h) {
        Site s = new Site(h, 20);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            BlockState state = f.getBlockState();
            h.assertTrue(
                    state.is(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.BLOCK, Geometries.ZONE_BOUNDARY.location())),
                    "the firelock isn't in zas:zone_boundary");
            for (BlockState st : AtmosBlocks.FIRELOCK.get().getStateDefinition().getPossibleStates()) {
                Face facing = Face.valueOf(st.getValue(FirelockBlock.FACING).name());
                Geometry g = Geometries.table().geometry(st);
                h.assertValueEqual(
                        g.flags() & Geometry.FLAG_ZONE_BOUNDARY,
                        Geometry.boundaryFlag(facing),
                        "the boundary faces of " + st);
                h.assertValueEqual(g.isFaceFull(facing), !st.getValue(FirelockBlock.OPEN), "the panel face of " + st);
                h.assertValueEqual(g.cellCount(), 1, "the cells of " + st);
            }
            h.assertFalse(state.is(BlockTags.DOORS), "the firelock is a vanilla door");

            s.cycle(f);
            ZoneInfo za = s.zone(2, 2, 2);
            ZoneInfo zb = s.zone(6, 2, 2);
            h.assertFalse(za.id().equals(zb.id()), "the open firelock joined A and B");
            h.assertValueEqual(za.cellCount(), 29, "A's cells, with the firelock's two");
            h.assertValueEqual(zb.cellCount(), 27, "B's cells");
            h.assertValueEqual(s.zone(4, 1, 2).id(), za.id(), "the firelock's zone");

            s.addKpa(6, 2, 2, N2, 50);
            double b0 = s.zone(6, 2, 2).mixture().pressure();
            s.cycles(3, f);
            h.assertTrue(s.zone(6, 2, 2).mixture().pressure() < b0 - 1, "no gas went through the open firelock");

            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            use(s, player, 2); // the upper half
            h.assertFalse(open(s), "closing by hand");
            h.assertFalse(
                    s.level.getBlockState(s.abs(4, 2, 2)).getValue(FirelockBlock.OPEN), "the upper half stayed open");
            s.cycle(f);
            s.addKpa(6, 2, 2, N2, 20);
            double a1 = s.zone(2, 2, 2).mixture().pressure();
            double b1 = s.zone(6, 2, 2).mixture().pressure();
            s.cycles(3, f);
            // Heat moves the rooms' pressures a little through their walls; gas through the firelock
            // would move them by kilopascals.
            s.close(s.zone(2, 2, 2).mixture().pressure(), a1, 0.05, "A behind the closed firelock");
            s.close(s.zone(6, 2, 2).mixture().pressure(), b1, 0.05, "B behind the closed firelock");
            h.assertValueEqual(s.zone(2, 2, 2).cellCount(), 29, "A's cells once closed");
            h.assertValueEqual(s.zone(6, 2, 2).cellCount(), 27, "B's cells once closed");
            player.discard();
        });
    }

    // ---- CONT-043, CONT-092 ---------------------------------------------------------------

    /**
     * A controller at danger 2 on either side closes the firelock, and once it falls below danger
     * 2 the firelock reopens (CONT-043, CONT-092). First A's controller, behind the panel, then
     * B's, in front of it. A firelock closed by hand doesn't reopen.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_043_controller_at_danger_2_on_either_side_closes_and_reopens(GameTestHelper h) {
        Site s = new Site(h, 21);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            AreaAirControllerBlockEntity a = controller(s, 2);
            s.cycles(2, a, f);
            h.assertTrue(open(s), "the firelock closed with A safe");
            h.assertValueEqual(a.danger(), 0, "A's danger");

            s.addKpa(2, 2, 2, CO2, 20);
            s.cycle(a, f);
            h.assertValueEqual(a.danger(), 2, "A's danger with CO2");
            h.assertTrue(f.alarm(), "the firelock isn't alarmed");
            h.assertFalse(open(s), "A at danger 2 didn't close the firelock");
            h.assertTrue(f.closedByAlarm(), "closed, but not by the alarm");

            clearCo2(s, 2);
            clearCo2(s, 6);
            for (int i = 0; i < 9; i++) {
                s.cycle(a, f);
                h.assertFalse(open(s), "reopened " + (i + 1) + " s after A's air cleared");
            }
            s.cycle(a, f);
            h.assertValueEqual(a.danger(), 0, "A's danger after 10 s");
            h.assertTrue(open(s), "the firelock didn't reopen once A fell below danger 2 (CONT-092)");

            // B's controller, in front of the panel. A's is removed.
            s.set(2, 2, 1, Site.AIR);
            AreaAirControllerBlockEntity b = controller(s, 6);
            s.cycles(2, b, f);
            h.assertTrue(open(s), "the firelock closed with B safe");
            s.addKpa(6, 2, 2, CO2, 20);
            s.cycle(b, f);
            h.assertValueEqual(b.danger(), 2, "B's danger");
            h.assertFalse(open(s), "B at danger 2 didn't close the firelock");
            clearCo2(s, 6);
            clearCo2(s, 2);
            s.cycles(10, b, f);
            h.assertTrue(open(s), "the firelock didn't reopen once B fell below danger 2");

            // Closed by hand, it stays closed through an alarm and after it.
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            use(s, player, 1);
            h.assertFalse(open(s), "closing by hand");
            s.addKpa(6, 2, 2, CO2, 20);
            s.cycle(b, f);
            h.assertTrue(f.alarm(), "B's alarm");
            clearCo2(s, 6);
            s.cycles(11, b, f);
            h.assertValueEqual(b.danger(), 0, "B's danger");
            h.assertFalse(open(s), "a firelock closed by hand reopened after the alarm");
            player.discard();
        });
    }

    /**
     * While the alarm holds, a firelock opened by hand closes again after 5 s.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_043_an_opening_during_the_alarm_closes_after_5_s(GameTestHelper h) {
        Site s = new Site(h, 22);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            AreaAirControllerBlockEntity a = controller(s, 2);
            s.cycles(2, a, f);
            s.addKpa(2, 2, 2, CO2, 20);
            s.addKpa(6, 2, 2, CO2, 20); // the same on both sides, so it isn't locked down
            s.cycle(a, f);
            h.assertFalse(open(s), "the alarm didn't close the firelock");
            h.assertTrue(f.lockdown().isEmpty(), "locked down: " + f.lockdown());
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            use(s, player, 1);
            h.assertTrue(open(s), "the firelock refused to open by hand");
            for (int i = 0; i < FirelockBlockEntity.RECLOSE_STEPS - 1; i++) {
                s.cycle(a, f);
                h.assertTrue(open(s), "closed again after " + (i + 1) + " s");
            }
            s.cycle(a, f);
            h.assertFalse(open(s), "still open 5 s into the alarm");
            h.assertTrue(f.closedByAlarm(), "the re-closing isn't the alarm's");
            player.discard();
        });
    }

    /**
     * A real vent in room B, linked with the multitool to the controller in room A across the
     * firelock, is powered from the controller's buffer and follows its orders, and the controller
     * lists it as linked, not in its zone (CONT-042, CONT-068).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_042_a_vent_linked_across_a_firelock_is_powered_and_commanded(GameTestHelper h) {
        Site s = new Site(h, 26);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            AreaAirControllerBlockEntity a = controller(s, 2);
            VentBlockEntity vent = s.place(
                    6,
                    1,
                    2,
                    AtmosBlocks.VENT.get().defaultBlockState().setValue(VentBlock.FACING, Direction.UP),
                    VentBlockEntity.class);
            s.cycles(2, a, vent, f);
            h.assertFalse(vent.power().isPowered(), "the unlinked vent in B has power");

            ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
            MultitoolItem.select(tool, s.level, a.getBlockPos());
            MultitoolItem.link(tool, s.level, vent.getBlockPos());
            s.cycle(a, vent, f);
            h.assertTrue(vent.power().isPowered(), "the vent linked across the firelock has no power");
            h.assertTrue(vent.power().source() == a, "the vent's power isn't the controller's");
            h.assertTrue(vent.controlled(), "the linked vent isn't controlled");
            h.assertValueEqual(vent.controller(), java.util.Optional.of(a.getBlockPos()), "the vent's controller");
            h.assertValueEqual(a.parts().size(), 1, "parts listed");
            AreaAirControllerBlockEntity.Part listed = a.parts().get(0);
            h.assertTrue(listed.linked() && !listed.inZone() && listed.powered(), "the vent's listing: " + listed);
            h.assertTrue(open(s), "the firelock closed");
        });
    }

    /**
     * A link covers only the firelock linked (CONT-043, changed 2026-11-02 by the owner): a part
     * in room B linked to a controller in room C doesn't bring B's firelock under it, but linking
     * the firelock itself does. The multitool links it from its upper half, and doesn't open or
     * close it. B and the room behind the firelock share the wall at x = 4; C is apart, at x 10–14.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_043_a_link_covers_only_the_linked_firelock(GameTestHelper h) {
        Site s = new Site(h, 23);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            s.room(10, 14);
            AreaAirControllerBlockEntity c = controller(s, 12);
            TestParts.Vent vent =
                    s.place(6, 1, 3, TestParts.Kind.VENT.block().defaultBlockState(), TestParts.Vent.class);
            s.cycles(2, c, f);
            ItemStack tool = new ItemStack(AtmosItems.MULTITOOL.get());
            MultitoolItem.select(tool, s.level, c.getBlockPos());
            MultitoolItem.link(tool, s.level, vent.getBlockPos());
            s.addKpa(12, 2, 2, CO2, 20);
            s.cycle(c, vent, f);
            h.assertValueEqual(c.danger(), 2, "C's danger");
            h.assertTrue(vent.power().isPowered(), "the linked vent has no power");
            h.assertTrue(open(s), "C, linked to a vent in B, closed B's firelock");

            // Link the firelock, from its upper half, as a click with the multitool does.
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setItemInHand(InteractionHand.MAIN_HAND, tool);
            BlockPos upper = s.abs(4, 2, 2);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(upper), Direction.WEST, upper, false);
            h.assertValueEqual(
                    s.level.getBlockState(upper).useItemOn(tool, s.level, player, InteractionHand.MAIN_HAND, hit),
                    ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION,
                    "the firelock's answer to the multitool");
            tool.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            h.assertTrue(c.links().contains(f.getBlockPos()), "the firelock's lower half isn't linked: " + c.links());
            h.assertTrue(open(s), "the multitool opened or closed the firelock");
            s.cycle(c, vent, f);
            h.assertFalse(open(s), "C didn't close the firelock linked to it");
            h.assertTrue(f.closedByAlarm(), "closed, but not by the alarm");
            player.discard();
        });
    }

    // ---- The breach alarm (CONT-043) -------------------------------------------------------

    /**
     * In a vacuum, a breach in room A alarms A's controller at once, at the first atmos tick that
     * sees it, though A still holds nearly 1 atm, and the firelock to B closes in that same atmos
     * tick: the controller steps it, without the firelock's own step (CONT-043, changed
     * 2026-11-02 by the owner). B keeps its air.
     */
    @GameTest(template = "empty", batch = "zas_atmos_firelock_breach")
    public static void cont_043_a_breach_to_space_closes_the_firelock_at_once(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Site s = new Site(h, end, 33);
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            run(h, s, () -> {
                FirelockBlockEntity f = rooms(s);
                AreaAirControllerBlockEntity a = controller(s, 2);
                for (int x : new int[] {2, 6}) {
                    s.addKpa(x, 2, 2, N2, 79);
                    s.addKpa(x, 2, 2, StandardAir.OXYGEN, 21);
                }
                s.cycles(3, a, f);
                h.assertValueEqual(a.danger(), 0, "A's danger before the breach");
                h.assertTrue(open(s), "the firelock closed before the breach");

                s.set(2, 2, 4, Site.AIR); // a hole in A's south wall
                s.tick(); // the framework sees it
                h.assertTrue(s.zone(2, 2, 2).breached(), "A isn't breached");
                double aKpa = s.zone(2, 2, 2).mixture().pressure();
                a.step(); // the controller alone: no step of the firelock's own
                h.assertTrue(a.breach(), "the controller didn't see the breach, with A at " + aKpa + " kPa");
                h.assertValueEqual(a.danger(), 2, "A's danger on the breach");
                h.assertFalse(open(s), "the firelock didn't close as the controller alarmed");
                h.assertTrue(f.closedByAlarm(), "closed, but not by the alarm");
                double b0 = s.zone(6, 2, 2).mixture().pressure();
                h.assertTrue(b0 > 95, "B lost too much before the firelock closed: " + b0 + " kPa");
                s.cycles(3, a, f);
                s.close(s.zone(6, 2, 2).mixture().pressure(), b0, 0.5, "B behind the closed firelock");
            });
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
    }

    /**
     * In the Overworld, opening room A to the outside air doesn't alarm its controller: the ambient
     * isn't below the preset's pressure min2, and A's pressure doesn't fall (CONT-043).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_043_a_door_to_outside_air_does_not_alarm(GameTestHelper h) {
        Site s = new Site(h, 27);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            AreaAirControllerBlockEntity a = controller(s, 2);
            s.cycles(2, a, f);
            h.assertValueEqual(a.danger(), 0, "A's danger");
            s.set(2, 2, 4, Site.AIR);
            s.set(2, 1, 4, Site.AIR); // a doorway in A's south wall
            s.tick();
            h.assertTrue(s.zone(2, 2, 2).breached(), "A isn't open to the outside");
            s.cycles(3, a, f);
            h.assertFalse(a.breach(), "a door to the outside air counted as a breach");
            h.assertValueEqual(a.danger(), 0, "A's danger with its door open");
            h.assertTrue(open(s), "the firelock closed");
        });
    }

    // ---- CONT-093, CONT-094 ---------------------------------------------------------------

    /**
     * A closed firelock locks down at a 25 kPa difference, at 323.15 K and at 273.15 K on a side;
     * locked down it refuses to open by hand and shows the locked state, and a crowbar forces it
     * open (CONT-093, CONT-094).
     *
     * <p>Heat is off: the rooms hold 1 kPa of gas at the thresholds, and conduction through their
     * walls would carry them off the exact temperatures within the atmos tick.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_093_lockdown_refuses_the_hand_and_a_crowbar_forces_it(GameTestHelper h) {
        Site s = new Site(h, 24);
        runWithoutHeat(h, s, () -> {
            FirelockBlockEntity f = rooms(s); // no controllers, so no alarm
            s.cycle(f);
            Player player = h.makeMockPlayer(GameType.SURVIVAL);
            use(s, player, 1);
            h.assertFalse(open(s), "closing by hand");

            fillAt(s, 2, 0.5, 293.15);
            fillAt(s, 6, 24.9, 293.15);
            s.cycle(f);
            h.assertFalse(f.alarm(), "alarmed");
            h.assertTrue(f.lockdown().isEmpty(), "locked down at 24.4 kPa");
            s.addKpa(6, 2, 2, N2, 0.7);
            s.cycle(f);
            h.assertValueEqual(
                    f.lockdown().map(Lockdown::reason).orElse(null), Lockdown.Reason.PRESSURE, "at 25.1 kPa");
            h.assertTrue(lockedState(s), "the locked state isn't shown");

            use(s, player, 2);
            h.assertFalse(open(s), "a locked-down firelock opened by hand");

            fillAt(s, 2, 1, 293.15);
            fillAt(s, 6, 1, 323.15);
            s.cycle(f);
            h.assertValueEqual(f.lockdown().map(Lockdown::reason).orElse(null), Lockdown.Reason.HOT, "at 50 °C");
            fillAt(s, 6, 1, 323.0);
            s.cycle(f);
            h.assertTrue(f.lockdown().isEmpty(), "locked down at 323 K");
            h.assertFalse(lockedState(s), "the locked state stayed");
            fillAt(s, 2, 1, 273.15);
            s.cycle(f);
            h.assertValueEqual(f.lockdown().map(Lockdown::reason).orElse(null), Lockdown.Reason.COLD, "at 0 °C");
            use(s, player, 1);
            h.assertFalse(open(s), "opened by hand when cold");

            h.assertTrue(CrowbarItem.force(s.level, s.abs(4, 1, 2), player), "the crowbar didn't open it");
            h.assertTrue(open(s), "the crowbar didn't open the locked-down firelock");
            h.assertFalse(lockedState(s), "an open firelock shows the lockdown");
            s.cycle(f);
            h.assertTrue(open(s), "the forced firelock closed with no alarm");
            h.assertFalse(CrowbarItem.force(s.level, s.abs(4, 1, 2), player), "the crowbar didn't shut it");
            h.assertFalse(open(s), "the crowbar didn't shut it");
            player.discard();
        });
    }

    /**
     * A firelock the alarm closed doesn't reopen while it is locked down: the room behind it was
     * emptied, so its controller stays at danger 2 until it is refilled, and then the pressure
     * difference holds it until both sides match (CONT-092, CONT-093).
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_092_a_locked_down_firelock_waits_to_reopen(GameTestHelper h) {
        Site s = new Site(h, 25);
        run(h, s, () -> {
            FirelockBlockEntity f = rooms(s);
            AreaAirControllerBlockEntity b = controller(s, 6);
            s.cycles(2, b, f);
            h.assertTrue(open(s), "closed while safe");
            s.empty(6, 2, 2);
            s.cycle(b, f);
            h.assertValueEqual(b.danger(), 2, "B's danger in vacuum");
            h.assertFalse(open(s), "B emptied didn't close the firelock");

            // B refilled to 101 kPa is safe after 10 s, but A, at 130 kPa, holds the lockdown.
            fillAt(s, 6, 80, 293.15);
            s.addKpa(6, 2, 2, StandardAir.OXYGEN, 21);
            fillAt(s, 2, 130, 293.15);
            s.cycles(11, b, f);
            h.assertValueEqual(b.danger(), 0, "B's danger once refilled");
            h.assertFalse(f.alarm(), "still alarmed");
            h.assertTrue(f.lockdown().isPresent(), "not locked down");
            h.assertFalse(open(s), "reopened while locked down");
            fillAt(s, 2, 80, 293.15);
            s.addKpa(2, 2, 2, StandardAir.OXYGEN, 21);
            s.cycle(b, f);
            h.assertTrue(open(s), "didn't reopen once the lockdown ended");
        });
    }
}
