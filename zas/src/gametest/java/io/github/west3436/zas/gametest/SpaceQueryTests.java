package io.github.west3436.zas.gametest;

import static io.github.west3436.zas.gametest.Rooms.AIR;
import static io.github.west3436.zas.gametest.Rooms.N2;
import static io.github.west3436.zas.gametest.Rooms.O2;
import static io.github.west3436.zas.gametest.Rooms.oxygen;
import static io.github.west3436.zas.gametest.Rooms.source;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.fire.Fires;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.LocalAtmosphereProvider;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.FireConfig;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.hud.AtmosHudPayload;
import io.github.west3436.zas.hud.HudLevels;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.life.BreathingRules;
import io.github.west3436.zas.life.Internals;
import io.github.west3436.zas.life.Protections;
import io.github.west3436.zas.simulation.DimensionAtmos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The API a space-mod adapter is built on, as ZAS³ answers it: the preset (SPACE-004), whether a
 * zone touches a block (SPACE-014, SPACE-018), whether fire burns in some air (SPACE-015), the
 * air of another mod's breathable places outside zones (SPACE-017), with the guard around their
 * providers (NFR-013), and an item that only holds breathing gas (SPACE-023). Ambient seeds
 * (SPACE-020) are with the ambient providers' tests. The adapters' own GameTests run these with
 * the other mod present; these need none.
 *
 * <p>Each test builds in its own area ({@link Rooms}, areas 750 and up) and runs within one game
 * tick, so no other test's entity reaches a provider it registers.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceQueryTests {
    private static final ResourceLocation FIRST = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "space_017_a");
    private static final ResourceLocation SECOND = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "space_017_b");
    private static final ResourceLocation THIRD = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "space_017_c");

    /** Area x in each of the two chunks a room across the border at x 16 is in. */
    private static final int WEST = 0;

    private static final int EAST = 16;

    private SpaceQueryTests() {}

    /** One cell's worth of gas at these partial pressures, in kPa at 293.15 K. */
    private static GasMixture air(double nitrogenKpa, double oxygenKpa) {
        double perKpa = StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
        return new GasMixture(
                Map.of(N2, nitrogenKpa * perKpa, O2, oxygenKpa * perKpa),
                StandardAir.TEMPERATURE,
                StandardAir.CELL_VOLUME);
    }

    private static Pig pig(GameTestHelper h, Rooms r, int x, int y, int z) {
        Pig e = EntityType.PIG.create(r.level);
        h.assertTrue(e != null, "couldn't create a pig");
        e.moveTo(Vec3.atBottomCenterOf(r.abs(x, y, z)));
        e.setNoAi(true);
        r.level.addFreshEntity(e);
        return e;
    }

    private static float lost(LivingEntity e) {
        return e.getMaxHealth() - e.getHealth();
    }

    /** What an older provider would answer: the same question asked of the samples (API-010). */
    private static boolean bySamples(Rooms r, BlockPos pos) {
        if (Atmospheres.at(r.level, pos).state() != AtmosphereState.AMBIENT) {
            return true;
        }
        for (Direction side : Direction.values()) {
            AtmosphereState beside = Atmospheres.at(r.level, pos.relative(side)).state();
            if (beside == AtmosphereState.ZONED || beside == AtmosphereState.PAUSED) {
                return true;
            }
        }
        return false;
    }

    /** The API's preset is the server's, from the next read after it changes (SPACE-004, OPS-007). */
    @GameTest(template = "empty")
    public static void space_004_the_api_reports_the_servers_preset(GameTestHelper h) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(AtmospherePreset.NORMAL);
            h.assertValueEqual(
                    Atmospheres.preset(),
                    io.github.west3436.zas.api.atmosphere.AtmospherePreset.NORMAL,
                    "under Normal");
            ZasServerConfig.setPreset(AtmospherePreset.HARDCORE);
            h.assertValueEqual(
                    Atmospheres.preset(),
                    io.github.west3436.zas.api.atmosphere.AtmospherePreset.HARDCORE,
                    "under Hardcore");
        } finally {
            ZasServerConfig.setPreset(before);
        }
        h.succeed();
    }

    /**
     * A zone touches each of its cells and each block with a cell of it on one of its six sides: of a
     * sealed room with a 3 × 3 × 3 interior, the 27 cells and the 54 blocks of its walls, floor and
     * ceiling that face one. It doesn't touch the edges and corners of the shell, the open air
     * outside or the blocks beyond the walls. The answer is the one the samples give, position by
     * position (SPACE-014).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_014_a_zone_touches_its_cells_and_the_blocks_facing_them(GameTestHelper h) {
        Rooms r = new Rooms(h, 750);
        r.clear(0, 14);
        r.shell(2, 6);
        h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(4, 2, 2)), "a room with no zone yet");
        h.assertValueEqual(
                r.settle(GasMutations.add(r.level, r.point(4, 2, 2), oxygen(50), source("space_014")))
                        .outcome(),
                MutationOutcome.ZONE,
                "the room didn't zone");

        h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(4, 2, 2)), "a cell of the zone");
        h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(4, 0, 2)), "the floor under a cell");
        h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(2, 2, 2)), "the wall beside a cell");
        h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(4, 4, 2)), "the ceiling over a cell");
        h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(2, 0, 2)), "an edge of the shell");
        h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(2, 0, 0)), "a corner of the shell");
        h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(1, 2, 2)), "the open air against the wall");
        h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(10, 2, 2)), "the open air");

        int touched = 0;
        for (BlockPos p : BlockPos.betweenClosed(r.abs(0, -2, -2), r.abs(8, 6, 6))) {
            int x = p.getX() - r.origin.getX();
            int y = p.getY() - r.origin.getY();
            int z = p.getZ() - r.origin.getZ();
            boolean inShell = x >= 2 && x <= 6 && y >= 0 && y <= 4 && z >= 0 && z <= 4;
            int sides = (x == 2 || x == 6 ? 1 : 0) + (y == 0 || y == 4 ? 1 : 0) + (z == 0 || z == 4 ? 1 : 0);
            // In the shell's box: a cell is on none of its sides, a block facing one on exactly one.
            boolean expected = inShell && sides <= 1;
            boolean answer = Atmospheres.zoneTouches(r.level, p);
            h.assertValueEqual(answer, expected, "at " + x + ", " + y + ", " + z);
            h.assertValueEqual(answer, bySamples(r, p), "against the samples at " + x + ", " + y + ", " + z);
            if (answer) {
                touched++;
            }
        }
        h.assertValueEqual(touched, 81, "positions the zone touches");
        h.succeed();
    }

    /**
     * A paused zone still touches its blocks, and a position whose atmosphere isn't resolved counts
     * as touched, so that a mod acting on the open air leaves a room alone while a chunk of it is
     * unloaded to the atmosphere, and after its chunks load until its saved zone is back. Open air in
     * a resolved chunk isn't touched (SPACE-014, API-010).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_014_a_paused_zone_and_an_unresolved_position_count_as_touched(GameTestHelper h) {
        Rooms r = new Rooms(h, 751);
        r.clear(8, 23);
        r.shell(10, 21);
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(60), source("space_014")));
        h.assertValueEqual(r.zone(12, 2, 2).id(), r.zone(19, 2, 2).id(), "one zone across the chunks");
        BlockPos west = r.abs(12, 2, 2);
        BlockPos westOpen = r.abs(9, 6, 6);
        BlockPos east = r.abs(19, 2, 2);
        BlockPos eastFloor = r.abs(19, 0, 2);
        BlockPos eastOpen = r.abs(22, 6, 6);
        h.assertFalse(Atmospheres.zoneTouches(r.level, eastOpen), "the open air before the unload");

        DimensionAtmos d = r.dimension();
        Map<ChunkPos, byte[]> unloaded = new LinkedHashMap<>();
        try {
            // The east chunk unloads: the zone pauses whole, and the chunk is unresolved.
            unloaded.put(r.chunk(EAST), d.simulateUnload(r.chunk(EAST)));
            r.tick();
            h.assertValueEqual(Atmospheres.at(r.level, west).state(), AtmosphereState.PAUSED, "the loaded part");
            h.assertTrue(Atmospheres.zoneTouches(r.level, west), "a cell of the paused zone");
            h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(12, 0, 2)), "the floor under the paused zone");
            h.assertValueEqual(
                    Atmospheres.at(r.level, eastOpen).state(), AtmosphereState.UNRESOLVED, "the unloaded chunk");
            h.assertTrue(Atmospheres.zoneTouches(r.level, east), "the room's unloaded part");
            h.assertTrue(Atmospheres.zoneTouches(r.level, eastOpen), "the unloaded chunk's open air");
            h.assertFalse(Atmospheres.zoneTouches(r.level, westOpen), "the loaded chunk's open air");

            // The west chunk goes too, and both load again: until the saved zone is back the room
            // is unresolved, as it is for a moment whenever a player comes back to a base.
            unloaded.put(r.chunk(WEST), d.simulateUnload(r.chunk(WEST)));
            r.tick();
            unloaded.forEach(d::simulateLoad);
            unloaded.clear();
            h.assertTrue(d.isSettling(r.chunkKey(EAST)), "the chunk isn't settling after its load");
            h.assertValueEqual(
                    Atmospheres.at(r.level, east).state(), AtmosphereState.UNRESOLVED, "loaded, its zone not back");
            h.assertTrue(Atmospheres.zoneTouches(r.level, east), "the room, while its zone isn't back");
            h.assertTrue(Atmospheres.zoneTouches(r.level, eastFloor), "its floor, while its zone isn't back");

            r.ticks(2);
            h.assertValueEqual(Atmospheres.at(r.level, east).state(), AtmosphereState.ZONED, "once its zone is back");
            h.assertTrue(Atmospheres.zoneTouches(r.level, east), "the room, zoned again");
            h.assertTrue(Atmospheres.zoneTouches(r.level, eastFloor), "its floor, zoned again");
            h.assertFalse(Atmospheres.zoneTouches(r.level, eastOpen), "the open air once the chunk is resolved");
            h.assertFalse(Atmospheres.zoneTouches(r.level, westOpen), "the other chunk's open air, resolved");
        } finally {
            if (!unloaded.isEmpty()) {
                unloaded.forEach(d::simulateLoad);
                r.ticks(2);
            }
        }
        h.succeed();
    }

    /**
     * The cells of a region held unresolved at a breach, because it reaches an unloaded chunk, count
     * as touched, and so do the blocks facing them, though no zone holds them yet (SPACE-014,
     * GEO-026).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_014_a_region_held_at_a_breach_counts_as_touched(GameTestHelper h) {
        Rooms r = new Rooms(h, 752);
        r.clear(0, 23);
        r.shell(2, 9); // A, in the west chunk
        r.shell(9, 21); // B, unzoned, across both, sharing the wall at x = 9
        r.settle(GasMutations.add(r.level, r.point(5, 2, 2), oxygen(50), source("space_014")));
        h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(12, 2, 2)), "B before the breach");
        DimensionAtmos d = r.dimension();
        byte[] saved = d.simulateUnload(r.chunk(EAST));
        try {
            r.tick();
            r.set(9, 2, 2, AIR);
            r.tick();
            h.assertValueEqual(
                    Atmospheres.at(r.level, r.abs(12, 2, 2)).state(), AtmosphereState.UNRESOLVED, "B's loaded part");
            h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(12, 2, 2)), "a held cell of B");
            h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(12, 0, 2)), "the floor under a held cell");
            h.assertTrue(Atmospheres.zoneTouches(r.level, r.abs(5, 2, 2)), "a cell of A, paused");
            h.assertFalse(Atmospheres.zoneTouches(r.level, r.abs(5, 6, 6)), "the open air over A");
        } finally {
            d.simulateLoad(r.chunk(EAST), saved);
            r.ticks(3);
        }
        h.succeed();
    }

    /**
     * Fire can burn in air that meets both of the server's O₂ minimums, as they are when asked: the
     * overworld's open air and a sample of it burn by the defaults, and no longer once the server
     * asks for half the gas to be O₂ (SPACE-015, FIRE-030).
     */
    @GameTest(template = "empty")
    public static void space_015_fire_burns_in_air_by_the_servers_minimums(GameTestHelper h) {
        Rooms r = new Rooms(h, 753);
        r.clear(0, 4);
        GasMixture open = Atmospheres.at(r.level, r.abs(2, 2, 2)).mixture();
        FireSettings before = FireConfig.settings();
        try {
            FireConfig.overrideForTest(FireSettings.DEFAULT);
            h.assertTrue(Fires.canBurnIn(open), "the overworld's open air");
            h.assertTrue(Fires.canBurnIn(air(61.5, 10.1)), "just over both minimums");
            h.assertFalse(Fires.canBurnIn(air(90, 14)), "13.5 % O2 at 14 kPa");
            h.assertFalse(Fires.canBurnIn(air(0, 9.9)), "pure O2 at 9.9 kPa");
            h.assertFalse(Fires.canBurnIn(air(101.325, 0)), "nitrogen");
            h.assertFalse(Fires.canBurnIn(air(0, 0)), "a vacuum");

            FireConfig.overrideForTest(new FireSettings(0.5, 30, FireSettings.DEFAULT.incompleteBelowFraction()));
            h.assertFalse(Fires.canBurnIn(open), "open air, with the minimum fraction at 0.5");
            h.assertFalse(Fires.canBurnIn(air(0, 29)), "pure O2 at 29 kPa, with the minimum at 30");
            h.assertTrue(Fires.canBurnIn(air(30, 31)), "half O2 at 31 kPa, with the minimums at 0.5 and 30");
        } finally {
            FireConfig.overrideForTest(null);
        }
        h.assertValueEqual(FireConfig.settings(), before, "the server's settings afterwards");
        h.succeed();
    }

    /**
     * A provider's air is what the rules take a living thing to be in outside zones, and only there.
     * With thin nitrogen named for a box on the overworld, a pig in the open in the box can't breathe
     * and is harmed by the low pressure, while the atmosphere query at its eyes still reports the
     * ambient. A pig in a room in the box, whose zone holds breathable gas, is governed by the zone,
     * and the provider isn't asked about it. A pig outside the box breathes the ambient. The
     * provider is asked on the server thread about the entity's eyes, and not once unregistered
     * (SPACE-017).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_017_a_local_atmosphere_is_the_air_outside_zones_for_the_rules_only(GameTestHelper h) {
        Rooms r = new Rooms(h, 754);
        r.clear(0, 14);
        r.shell(0, 4);
        r.settle(GasMutations.add(r.level, r.point(2, 2, 2), oxygen(100), source("space_017")));
        GasMixture thin = air(15, 0);
        AABB box = AABB.encapsulatingFullBlocks(r.abs(0, 0, 0), r.abs(9, 6, 6));
        List<Vec3> asked = new ArrayList<>();
        boolean[] onServerThread = {true};
        LocalAtmosphereProvider provider = (level, point) -> {
            asked.add(point);
            onServerThread[0] &= Thread.currentThread() == level.getServer().getRunningThread();
            return level == r.level && box.contains(point) ? thin : null;
        };
        Pig inRoom = pig(h, r, 2, 1, 2);
        Pig inBox = pig(h, r, 7, 1, 2);
        Pig outside = pig(h, r, 12, 1, 2);
        AtmosphereSample ambient = Atmospheres.of(inBox);
        long t = r.level.getGameTime();
        try {
            h.assertValueEqual(ambient.state(), AtmosphereState.AMBIENT, "the open air in the box");
            h.assertValueEqual(Breathing.of(inBox).reason(), BreathReason.BREATHABLE, "before a provider");

            LocalAtmospheres.register(FIRST, provider);
            try {
                Breathability verdict = Breathing.of(inBox);
                h.assertValueEqual(verdict.reason(), BreathReason.LOW_OXYGEN, "in the box");
                h.assertValueEqual(verdict.breath(), Optional.of(thin), "what it breathes in the box");
                h.assertTrue(verdict.hazards().contains(Hazard.LOW_PRESSURE), "its hazards: " + verdict.hazards());
                h.assertValueEqual(asked, List.of(inBox.getEyePosition()), "what the provider was asked");
                h.assertTrue(onServerThread[0], "the provider wasn't asked on the server thread");
                h.assertValueEqual(Atmospheres.of(inBox), ambient, "the atmosphere query in the box");
                h.assertValueEqual(
                        Atmospheres.at(r.level, r.abs(7, 2, 2)), ambient, "the atmosphere query at a position there");

                asked.clear();
                Breathability zoned = Breathing.of(inRoom);
                h.assertValueEqual(zoned.reason(), BreathReason.BREATHABLE, "in the room");
                r.close(
                        zoned.breath().orElseThrow().partialPressure(O2),
                        r.zone(2, 2, 2).mixture().partialPressure(O2),
                        "the O2 it breathes in the room, kPa");
                h.assertValueEqual(asked, List.of(), "what the provider was asked about the room");

                h.assertValueEqual(Breathing.of(outside).reason(), BreathReason.BREATHABLE, "outside the box");
                h.assertValueEqual(asked, List.of(outside.getEyePosition()), "what it was asked outside the box");

                for (Pig each : List.of(inBox, inRoom, outside)) {
                    Breathers.checkAt(each, t);
                    Breathers.checkAt(each, t + 40);
                }
                h.assertTrue(lost(inBox) > 0, "the low pressure in the box did no harm");
                h.assertValueEqual(lost(inRoom), 0f, "harm in the room");
                h.assertValueEqual(lost(outside), 0f, "harm outside the box");
            } finally {
                h.assertTrue(LocalAtmospheres.unregister(FIRST), "the provider wasn't registered");
            }
            asked.clear();
            h.assertValueEqual(Breathing.of(inBox).reason(), BreathReason.BREATHABLE, "once unregistered");
            h.assertValueEqual(asked, List.of(), "what it was asked once unregistered");
        } finally {
            inRoom.discard();
            inBox.discard();
            outside.discard();
        }
        h.succeed();
    }

    /**
     * What shows a player the air at a point asks for the local air there. Outside every zone, at a
     * point a provider names, it is the provider's air, the same the rules give an entity whose eyes
     * are there. It is null in a zone, where the provider isn't asked, at a point no provider names,
     * and once the provider is unregistered. The atmosphere query at each point is unchanged, and
     * the lookup is refused off the server thread (SPACE-017, API-045).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_017_the_local_air_at_a_point_is_the_providers_outside_zones(GameTestHelper h) {
        Rooms r = new Rooms(h, 762);
        r.clear(0, 14);
        r.shell(0, 4);
        r.settle(GasMutations.add(r.level, r.point(2, 2, 2), oxygen(100), source("space_017")));
        GasMixture thin = air(15, 0);
        AABB box = AABB.encapsulatingFullBlocks(r.abs(0, 0, 0), r.abs(9, 6, 6));
        List<Vec3> asked = new ArrayList<>();
        LocalAtmosphereProvider provider = (level, point) -> {
            asked.add(point);
            return level == r.level && box.contains(point) ? thin : null;
        };
        Vec3 inRoom = r.point(2, 2, 2);
        Vec3 inBox = r.point(7, 2, 2);
        Vec3 outside = r.point(12, 2, 2);
        Pig pig = pig(h, r, 7, 1, 2);
        AtmosphereSample ambient = Atmospheres.at(r.level, inBox);
        try {
            h.assertValueEqual(ambient.state(), AtmosphereState.AMBIENT, "the open air in the box");
            h.assertTrue(LocalAtmospheres.at(r.level, inBox) == null, "local air with no provider");

            LocalAtmospheres.register(FIRST, provider);
            try {
                h.assertValueEqual(LocalAtmospheres.at(r.level, inBox), thin, "the local air in the box");
                h.assertValueEqual(asked, List.of(inBox), "what the provider was asked");
                h.assertValueEqual(
                        LocalAtmospheres.at(r.level, pig.getEyePosition()),
                        Breathing.of(pig).breath().orElseThrow(),
                        "the local air at a pig's eyes, against what it breathes");
                h.assertValueEqual(Atmospheres.at(r.level, inBox), ambient, "the atmosphere query in the box");

                asked.clear();
                h.assertValueEqual(Atmospheres.at(r.level, inRoom).state(), AtmosphereState.ZONED, "the room");
                h.assertTrue(LocalAtmospheres.at(r.level, inRoom) == null, "local air in the room's zone");
                h.assertValueEqual(asked, List.of(), "what the provider was asked about the room");

                h.assertTrue(LocalAtmospheres.at(r.level, outside) == null, "local air outside the box");
                h.assertValueEqual(asked, List.of(outside), "what it was asked outside the box");

                Throwable[] thrown = new Throwable[1];
                Thread other = new Thread(
                        () -> {
                            try {
                                LocalAtmospheres.at(r.level, inBox);
                            } catch (Throwable t) {
                                thrown[0] = t;
                            }
                        },
                        "zas-space-017");
                other.start();
                try {
                    other.join(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                h.assertTrue(thrown[0] instanceof IllegalStateException, "off the server thread: got " + thrown[0]);
                h.assertValueEqual(asked, List.of(outside), "what it was asked off the server thread");
            } finally {
                h.assertTrue(LocalAtmospheres.unregister(FIRST), "the provider wasn't registered");
            }
            asked.clear();
            h.assertTrue(LocalAtmospheres.at(r.level, inBox) == null, "local air once unregistered");
            h.assertValueEqual(asked, List.of(), "what it was asked once unregistered");
        } finally {
            pig.discard();
        }
        h.succeed();
    }

    /**
     * No provider is asked where a zone is paused or the atmosphere is unresolved: the paused zone
     * governs with its last-known gas, and an unresolved position stays the guess it is, which a
     * check holds rather than acts on (SPACE-017, LIFE-048).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void space_017_no_provider_is_asked_in_a_paused_zone_or_an_unresolved_place(GameTestHelper h) {
        Rooms r = new Rooms(h, 755);
        r.clear(8, 23);
        r.shell(10, 21);
        r.settle(GasMutations.add(r.level, r.point(12, 2, 2), oxygen(60), source("space_017")));
        GasMixture thin = air(15, 0);
        int[] asked = new int[1];
        Pig inZone = pig(h, r, 12, 1, 2);
        Pig unresolved = pig(h, r, 22, 5, 5);
        DimensionAtmos d = r.dimension();
        byte[] saved = d.simulateUnload(r.chunk(EAST));
        LocalAtmospheres.register(FIRST, (level, point) -> {
            asked[0]++;
            return thin;
        });
        try {
            r.tick();
            h.assertValueEqual(Atmospheres.of(inZone).state(), AtmosphereState.PAUSED, "the room's loaded part");
            h.assertValueEqual(Breathing.of(inZone).reason(), BreathReason.BREATHABLE, "in the paused zone");
            h.assertValueEqual(Atmospheres.of(unresolved).state(), AtmosphereState.UNRESOLVED, "the unloaded chunk");
            h.assertValueEqual(Breathing.of(unresolved).reason(), BreathReason.BREATHABLE, "the last-known ambient");
            h.assertTrue(LocalAtmospheres.at(r.level, inZone.getEyePosition()) == null, "local air in the paused zone");
            h.assertTrue(
                    LocalAtmospheres.at(r.level, unresolved.getEyePosition()) == null,
                    "local air in the unresolved place");
            h.assertValueEqual(asked[0], 0, "times the provider was asked");
        } finally {
            LocalAtmospheres.unregister(FIRST);
            d.simulateLoad(r.chunk(EAST), saved);
            r.ticks(2);
            inZone.discard();
            unresolved.discard();
        }
        h.succeed();
    }

    /**
     * Providers are asked in order of id and the first answer stands. One that throws, an exception
     * or a linkage error, is taken as having nothing to say for that check, so the next is asked;
     * each fault is logged at most once a minute (SPACE-017, NFR-013).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nfr_013_a_local_atmosphere_provider_that_throws_is_skipped_and_logged_once_a_minute(
            GameTestHelper h) {
        Rooms r = new Rooms(h, 756);
        r.clear(0, 4);
        Pig pig = pig(h, r, 2, 1, 2);
        GasMixture thin = air(15, 0);
        GasMixture rich = air(0, 60);
        List<ResourceLocation> order = new ArrayList<>();
        boolean[] faulty = {true};
        // Registered out of order: the registry sorts them.
        LocalAtmospheres.register(THIRD, (level, point) -> {
            order.add(THIRD);
            return rich;
        });
        LocalAtmospheres.register(FIRST, (level, point) -> {
            order.add(FIRST);
            if (faulty[0]) {
                throw new IllegalStateException("a GameTest's faulty provider");
            }
            return null;
        });
        LocalAtmospheres.register(SECOND, (level, point) -> {
            order.add(SECOND);
            if (faulty[0]) {
                throw new NoClassDefFoundError("a GameTest's provider built against another version");
            }
            return thin;
        });
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            for (int i = 0; i < 4; i++) {
                Breathability verdict = Breathing.of(pig);
                h.assertValueEqual(verdict.breath(), Optional.of(rich), "the breath, with two providers throwing");
                h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, "the verdict");
            }
            h.assertValueEqual(order.subList(0, 3), List.of(FIRST, SECOND, THIRD), "the order asked");
            h.assertValueEqual(order.size(), 12, "times asked: each provider at each check");
            h.assertValueEqual(log.naming("Local atmosphere provider " + FIRST), 1, "errors about the first");
            h.assertValueEqual(log.naming("Local atmosphere provider " + SECOND), 1, "errors about the second");

            FaultLog.aMinutePasses();
            Breathing.of(pig);
            h.assertValueEqual(log.naming("Local atmosphere provider " + FIRST), 2, "errors a minute later");

            faulty[0] = false;
            order.clear();
            Breathability verdict = Breathing.of(pig);
            h.assertValueEqual(verdict.breath(), Optional.of(thin), "the breath: the first answer, not the last");
            h.assertValueEqual(order, List.of(FIRST, SECOND), "asked until one answers");

            faulty[0] = true;
            order.clear();
            h.assertValueEqual(
                    LocalAtmospheres.at(r.level, pig.getEyePosition()), rich, "the local air, with two throwing");
            h.assertValueEqual(order, List.of(FIRST, SECOND, THIRD), "the order asked for the local air");
        } finally {
            LocalAtmospheres.unregister(FIRST);
            LocalAtmospheres.unregister(SECOND);
            LocalAtmospheres.unregister(THIRD);
            pig.discard();
        }
        h.succeed();
    }

    /** One cell's worth of nitrogen at a pressure, in kPa, and a temperature, in K. */
    private static GasMixture nitrogen(double kPa, double kelvin) {
        return new GasMixture(
                Map.of(N2, kPa * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * kelvin)),
                kelvin,
                StandardAir.CELL_VOLUME);
    }

    /** What {@code /zas life} tells an entity about itself, a line each. */
    static List<String> life(Entity entity) {
        List<String> said = new ArrayList<>();
        CommandSource listener = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        ServerLevel level = (ServerLevel) entity.level();
        CommandSourceStack source = new CommandSourceStack(
                listener,
                entity.position(),
                Vec2.ZERO,
                level,
                4,
                "zas_space_017",
                Component.literal("zas_space_017"),
                level.getServer(),
                entity);
        try {
            level.getServer().getCommands().getDispatcher().execute("zas life", source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/zas life failed: " + e.getMessage());
        }
        return said;
    }

    /**
     * {@code /zas life} reports the air the rules apply. For a pig in a provider's thin, cold
     * nitrogen, in the open on the overworld, the exposure line gives that air's pressure and
     * temperature and says {@code local}, and the damage line the hazards the verdict above them
     * names. It used to give the open air an atmosphere query finds there, 101.325 kPa at 293.15 K
     * and no damage, under a verdict of low pressure. Outside the provider's box, and once the
     * provider is gone, the lines are the ambient's (SPACE-017, LIFE-010, LIFE-011).
     */
    @GameTest(template = "empty")
    public static void space_017_the_life_command_reports_the_air_the_rules_apply(GameTestHelper h) {
        Rooms r = new Rooms(h, 760);
        r.clear(0, 14);
        GasMixture thin = nitrogen(15, 200);
        AABB box = AABB.encapsulatingFullBlocks(r.abs(0, 0, 0), r.abs(9, 6, 6));
        Pig inBox = pig(h, r, 7, 1, 2);
        Pig outside = pig(h, r, 12, 1, 2);
        String ambient = "  Exposure: 101.325 kPa (effective 101.325 kPa), 293.15 K (ambient)";
        String unharmed = "  Damage per second: -";
        try {
            List<String> before = life(inBox);
            h.assertTrue(before.contains(ambient), "before a provider, the exposure: " + before);
            h.assertTrue(before.contains(unharmed), "before a provider, the damage: " + before);

            LocalAtmospheres.register(FIRST, (level, point) -> level == r.level && box.contains(point) ? thin : null);
            try {
                h.assertValueEqual(Atmospheres.of(inBox).state(), AtmosphereState.AMBIENT, "the query in the box");
                Breathability verdict = Breathing.of(inBox);
                h.assertTrue(verdict.hazards().contains(Hazard.LOW_PRESSURE), "its hazards: " + verdict.hazards());
                h.assertTrue(verdict.hazards().contains(Hazard.COLD), "its hazards: " + verdict.hazards());

                List<String> in = life(inBox);
                h.assertTrue(
                        in.contains("  Hazards: low pressure, cold; protection: -"), "in the box, the hazards: " + in);
                h.assertTrue(
                        in.contains("  Exposure: 15 kPa (effective 15 kPa), 200 K (local)"),
                        "in the box, the exposure: " + in);
                String damage = in.stream()
                        .filter(line -> line.startsWith("  Damage per second: "))
                        .findFirst()
                        .orElse("");
                h.assertTrue(damage.contains("decompression "), "in the box, the damage: " + in);
                h.assertTrue(damage.contains("frostbite "), "in the box, the damage: " + in);

                List<String> out = life(outside);
                h.assertTrue(out.contains(ambient), "outside the box, the exposure: " + out);
                h.assertTrue(out.contains(unharmed), "outside the box, the damage: " + out);
            } finally {
                LocalAtmospheres.unregister(FIRST);
            }
            h.assertValueEqual(life(inBox), before, "once the provider is gone");
        } finally {
            inBox.discard();
            outside.discard();
        }
        h.succeed();
    }

    /**
     * The HUD reads the air the rules apply for a player nothing harms too. A creative player
     * holding what shows every reading, in a provider's thin, cold nitrogen, is shown that air's
     * pressure and temperature beside its oxygen, with no alert. It used to be shown the open air's
     * pressure and temperature beside the provider's oxygen. A survival player there gets the same
     * readings, with the alerts (SPACE-017, CLIENT-008).
     */
    @GameTest(template = "empty")
    public static void space_017_the_hud_reads_the_air_the_rules_apply(GameTestHelper h) {
        Rooms r = new Rooms(h, 761);
        r.clear(0, 8);
        GasMixture thin = nitrogen(15, 200);
        AABB box = AABB.encapsulatingFullBlocks(r.abs(0, 0, 0), r.abs(9, 6, 6));
        FakePlayer survival = wearer(r, "zas_space_017_hud");
        FakePlayer creative = wearer(r, "zas_space_017_hud_creative");
        creative.setGameMode(GameType.CREATIVE);
        creative.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CLOCK));

        AtmosHudPayload open = HudLevels.of(creative);
        h.assertTrue(open != null && open.readings(), "the creative player isn't shown every reading");
        h.assertTrue(Math.abs(open.pressureKpa() - 101.325) < 1e-3, "in the open, kPa shown: " + open);

        LocalAtmospheres.register(FIRST, (level, point) -> level == r.level && box.contains(point) ? thin : null);
        try {
            AtmosHudPayload shown = HudLevels.of(creative);
            h.assertTrue(shown != null, "the creative player's HUD has no state");
            h.assertTrue(Math.abs(shown.pressureKpa() - 15) < 1e-3, "creative: kPa shown: " + shown);
            h.assertTrue(Math.abs(shown.temperatureK() - 200) < 1e-3, "creative: K shown: " + shown);
            h.assertTrue(Math.abs(shown.oxygenKpa()) < 1e-6, "creative: O2 shown: " + shown);
            h.assertFalse(shown.any(), "creative: an alert: " + shown);

            AtmosHudPayload harmed = HudLevels.of(survival);
            h.assertTrue(harmed != null, "the survival player's HUD has no state");
            h.assertTrue(Math.abs(harmed.pressureKpa() - 15) < 1e-3, "survival: kPa shown: " + harmed);
            h.assertTrue(Math.abs(harmed.temperatureK() - 200) < 1e-3, "survival: K shown: " + harmed);
            h.assertValueEqual(harmed.pressure(), -HudLevels.DANGER, "survival: the pressure alert");
            h.assertValueEqual(harmed.temperature(), -HudLevels.DANGER, "survival: the temperature alert");
            h.assertValueEqual(harmed.oxygen(), HudLevels.DANGER, "survival: the O2 alert");
        } finally {
            LocalAtmospheres.unregister(FIRST);
        }
        h.succeed();
    }

    // ---- SPACE-023: an item that only holds gas -------------------------------------------------

    private static FakePlayer wearer(Rooms r, String name) {
        FakePlayer player = TestPlayers.hurtable(r.level, name);
        player.moveTo(Vec3.atBottomCenterOf(r.abs(2, 1, 2)));
        return player;
    }

    /**
     * An item with no life support in code is an internals tank through {@link IInternalSupply#ITEM},
     * and keeps the protection data gives it (SPACE-023, LIFE-026, LIFE-028). Worn on the head, the
     * gas holder is sealed by its data file and breathed from by its capability; internals find it in
     * the offhand and the hotbar too, and draw from it at LIFE-043's rate.
     */
    @GameTest(template = "empty")
    public static void space_023_an_item_that_only_holds_gas_is_an_internals_tank(GameTestHelper h) {
        Rooms r = new Rooms(h, 757);
        r.clear(0, 8);
        FakePlayer p = wearer(r, "zas_space_023");
        ItemStack holder = TestLifeSupport.gasHolder(Map.of(O2, 1.0), 21.3);
        h.assertTrue(holder.getCapability(ILifeSupport.ITEM) == null, "the gas holder has life support in code");
        ILifeSupport data = Protections.of(holder);
        h.assertTrue(data != null && data.sealedBreathing(), "the data file doesn't seal the gas holder");
        h.assertValueEqual(data.minPressure(), 0.0, "the low pressure the data file rates it for");
        h.assertTrue(data.internalSupply().isEmpty(), "protection from data holds a supply");

        p.setItemSlot(EquipmentSlot.HEAD, holder);
        h.assertTrue(Internals.active(p) == null, "internals work while switched off");
        Internals.set(p, true);
        Internals.Active active = Internals.active(p);
        h.assertTrue(active != null, "internals don't breathe from the gas holder");
        Breathability verdict = BreathingRules.assess(p).verdict();
        h.assertValueEqual(verdict.source(), BreathSource.INTERNALS, "what it breathes from");
        h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, "the verdict");
        GasMixture breath = verdict.breath().orElseThrow();
        h.assertTrue(Math.abs(breath.partialPressure(O2) - 21.3) < 1e-9, "the breath's O2: " + breath);

        // Three checks 2 s apart draw 4 s.
        double rate = Internals.drawRate(21.3, StandardAir.TEMPERATURE);
        long t0 = r.level.getGameTime();
        for (int k = 0; k < 3; k++) {
            Breathers.checkAt(p, t0 + 40L * k);
        }
        double drawn = 1
                - TestLifeSupport.contents(p.getItemBySlot(EquipmentSlot.HEAD)).totalMoles();
        h.assertTrue(Math.abs(drawn - rate * 4) < 1e-12, "three checks drew " + drawn + " mol, not " + rate * 4);

        // Wherever internals look: the offhand, then the hotbar, and nowhere else.
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.gasHolder(Map.of(O2, 1.0), 40));
        p.getInventory().setItem(3, TestLifeSupport.gasHolder(Map.of(O2, 1.0), 50));
        p.getInventory().setItem(9, TestLifeSupport.gasHolder(Map.of(O2, 1.0), 60));
        h.assertValueEqual(Internals.find(p).orElseThrow().distributionPressure(), 40.0, "found in the offhand");
        p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        h.assertValueEqual(Internals.find(p).orElseThrow().distributionPressure(), 50.0, "found in the hotbar");
        p.getInventory().setItem(3, ItemStack.EMPTY);
        h.assertTrue(Internals.find(p).isEmpty(), "a gas holder outside the hotbar was found");
        h.succeed();
    }

    /**
     * On an item that gives a supply both ways, its life support's is the one used and the item's
     * own isn't asked. Life support in code with no supply leaves the item's own in use, and
     * replaces the item's protection data as a whole, as it does on any item (SPACE-023, LIFE-026).
     */
    @GameTest(template = "empty")
    public static void space_023_an_items_life_support_supply_comes_before_its_own(GameTestHelper h) {
        Rooms r = new Rooms(h, 758);
        r.clear(0, 8);
        FakePlayer p = wearer(r, "zas_space_023_both");
        p.setItemInHand(InteractionHand.OFF_HAND, TestLifeSupport.doubleTank(Map.of(O2, 1.0), 30));
        IInternalSupply found = Internals.find(p).orElseThrow();
        h.assertValueEqual(found.distributionPressure(), 30.0, "the supply found on an item that gives two");
        h.assertValueEqual(TestLifeSupport.SECOND_SUPPLY.distributionPressure(), 99.0, "the supply it wasn't to find");

        ItemStack coded = TestLifeSupport.marked(TestLifeSupport.gasHolder(Map.of(O2, 1.0), 40), "Coded");
        p.setItemInHand(InteractionHand.OFF_HAND, coded);
        h.assertValueEqual(
                Internals.find(p).orElseThrow().distributionPressure(),
                40.0,
                "the supply of an item whose life support holds none");
        ILifeSupport own = Protections.of(coded);
        h.assertTrue(own != null, "the marked gas holder has no life support");
        h.assertValueEqual(own.airflowResistance(), 0.125, "its life support in code");
        h.assertFalse(own.sealedBreathing(), "its data still applies beside life support in code");
        h.succeed();
    }

    /**
     * A lookup of {@link IInternalSupply#ITEM} that throws counts as no supply, so the search goes on
     * to the next place, and is logged at most once a minute for the item (NFR-013, SPACE-023).
     */
    @GameTest(template = "empty")
    public static void nfr_013_an_item_supply_lookup_that_throws_is_skipped_and_logged_once_a_minute(GameTestHelper h) {
        Rooms r = new Rooms(h, 759);
        r.clear(0, 8);
        FakePlayer p = wearer(r, "zas_space_023_faulty");
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(TestLifeSupport.item()));
        p.setItemInHand(
                InteractionHand.OFF_HAND,
                TestLifeSupport.marked(TestLifeSupport.gasHolder(Map.of(O2, 1.0), 40), "Throw"));
        p.getInventory().setItem(0, TestLifeSupport.tank(Map.of(O2, 1.0), 50));
        Internals.set(p, true);
        String source = "Internal supply of item " + TestLifeSupport.GAS_HOLDER_ID;
        try (FaultLog log = new FaultLog()) {
            FaultLog.aMinutePasses();
            for (int i = 0; i < 4; i++) {
                h.assertValueEqual(
                        Internals.find(p).orElseThrow().distributionPressure(), 50.0, "the tank found behind it");
                h.assertValueEqual(
                        BreathingRules.assess(p).verdict().source(), BreathSource.INTERNALS, "what it breathes from");
            }
            h.assertValueEqual(log.naming(source), 1, "errors about the lookup");
            FaultLog.aMinutePasses();
            Internals.find(p);
            h.assertValueEqual(log.naming(source), 2, "errors a minute later");
        }
        h.succeed();
    }
}
