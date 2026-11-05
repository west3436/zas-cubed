package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.Breathability;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.MutationResult;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import io.github.west3436.zas.environment.DimensionAmbient;
import io.github.west3436.zas.life.Breathers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What players get where nothing is simulated: an excluded dimension keeps its ambient and its
 * hazards (ENV-018), and under the Normal preset the vanilla dimensions cause no atmospheric harm
 * (ENV-009) and leave fire and furnaces as vanilla has them (ENV-013).
 *
 * <p>Each test runs within one game tick and drives what it checks itself: {@link Breathers#checkAt}
 * and {@code baseTick} for a player, direct ticks for a fire and a furnace, whose chunks don't tick.
 * Everything is built at y = 200, above every dimension's terrain and the Nether's roof, so a pocket
 * of generated stone can't seal it. Excluding the End changes it for anyone else, and {@code tickNow}
 * needs a batch of its own, so these tests have one.
 */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnvironmentRulesTests {
    private static final String BATCH = "env_rules";

    /** Chunk-aligned, and clear of the other tests' areas in each dimension. */
    private static final BlockPos AREA = new BlockPos(-16256, 200, 16384);

    private static final List<ResourceKey<Level>> VANILLA = List.of(Level.OVERWORLD, Level.NETHER, Level.END);

    private static final List<OpenAirGasEvent> EVENTS = new CopyOnWriteArrayList<>();

    static {
        NeoForge.EVENT_BUS.addListener((OpenAirGasEvent e) -> EVENTS.add(e));
    }

    private EnvironmentRulesTests() {}

    // ---- Helpers ----------------------------------------------------------------------

    private static ServerLevel level(GameTestHelper h, ResourceKey<Level> dimension) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        if (level == null) {
            throw new GameTestAssertException("no " + dimension.location());
        }
        return level;
    }

    /** Runs a body under the Normal preset, then restores the configured one. */
    private static void underNormal(GameTestHelper h, Runnable body) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(AtmospherePreset.NORMAL);
            h.assertValueEqual(ZasServerConfig.preset(), AtmospherePreset.NORMAL, "the active preset");
            body.run();
        } finally {
            ZasServerConfig.setPreset(before);
        }
    }

    /** Runs a body on the End with this definition in force and its zones dropped, then undoes it. */
    private static void onExcludedEnd(GameTestHelper h, DimensionAmbient definition, Consumer<ServerLevel> body) {
        ServerLevel end = level(h, Level.END);
        h.assertFalse(Ambients.isExcluded(Level.END), "the End is excluded before the test");
        Ambients.overrideDefinitionForTest(Level.END, definition);
        try {
            h.assertTrue(Ambients.isExcluded(Level.END), "the definition doesn't exclude the End");
            Rooms.service().tickNow(end);
            body.accept(end);
        } finally {
            Ambients.overrideDefinitionForTest(Level.END, null);
            Rooms.service().tickNow(end);
        }
        h.succeed();
    }

    private static DimensionAmbient excluded(Ambient ambient) {
        return new DimensionAmbient(Optional.of(ambient), Optional.of(ambient), true);
    }

    private static FakePlayer playerAt(ServerLevel level, BlockPos feet, String name) {
        FakePlayer p = TestPlayers.hurtable(level, name);
        p.moveTo(Vec3.atBottomCenterOf(feet));
        return p;
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < 1e-9, what + ": expected " + expected + ", got " + actual);
    }

    /** The open-air events for a source since a test began. */
    private static List<OpenAirGasEvent> eventsFor(SourceId source) {
        return EVENTS.stream().filter(e -> source.equals(e.source())).toList();
    }

    // ---- ENV-018 ------------------------------------------------------------------------

    /**
     * ENV-018's plan: an excluded vacuum dimension keeps its vacuum for queries, and an unsuited
     * player there is harmed.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_018_an_excluded_vacuum_harms_an_unsuited_player(GameTestHelper h) {
        onExcludedEnd(h, excluded(Ambient.VACUUM), end -> {
            Rooms r = new Rooms(h, end, AREA);
            r.clear(0, 3);
            BlockPos feet = r.abs(1, 1, 1);
            AtmosphereSample open = Atmospheres.at(end, feet);
            h.assertValueEqual(open.state(), AtmosphereState.AMBIENT, "the state");
            h.assertValueEqual(open.pressure(), 0.0, "the pressure");
            close(h, open.temperature(), Ambient.COSMIC_BACKGROUND, "the temperature");

            FakePlayer player = playerAt(end, feet, "zas_env_018");
            Breathability verdict = Breathing.of(player);
            h.assertFalse(verdict.canBreathe(), "the player can breathe: " + verdict);
            h.assertTrue(verdict.hazards().contains(Hazard.LOW_PRESSURE), "the hazards: " + verdict);

            long t = end.getGameTime();
            Breathers.checkAt(player, t);
            Breathers.checkAt(player, t + 40);
            h.assertTrue(player.getHealth() < player.getMaxHealth(), "the player wasn't harmed");
            int air = player.getAirSupply();
            player.baseTick();
            h.assertTrue(player.getAirSupply() < air, "the player's air didn't fall: " + player.getAirSupply());
        });
    }

    /**
     * ENV-018's plan: gas added in an excluded dimension goes to the open air, whatever is around it,
     * and gas taken from a vacuum finds none (CORE-053, CORE-057).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_018_gas_operations_in_an_excluded_vacuum_act_on_the_open_air(GameTestHelper h) {
        onExcludedEnd(h, excluded(Ambient.VACUUM), end -> {
            Rooms r = new Rooms(h, end, AREA);
            r.clear(0, 5);
            r.shell(0, 4);
            SourceId source = Rooms.source("env_018_vacuum");
            for (Vec3 point : List.of(r.point(2, 2, 2), r.point(5, 2, 2))) {
                int before = eventsFor(source).size();
                GasParcel parcel = new GasParcel(Map.of(StandardAir.OXYGEN, 10.0), StandardAir.TEMPERATURE);
                MutationResult added = r.settle(GasMutations.add(end, point, parcel, source));
                h.assertValueEqual(added.outcome(), MutationOutcome.AMBIENT, "adding at " + point);
                h.assertValueEqual(added.moved(), parcel, "what went to the open air at " + point);
                h.assertValueEqual(eventsFor(source).size(), before + 1, "open-air events at " + point);
                OpenAirGasEvent event = eventsFor(source).get(before);
                h.assertTrue(event.added(), "the event isn't an addition");
                h.assertValueEqual(event.gas(), parcel, "the event's gas");

                MutationResult taken =
                        r.settle(GasMutations.remove(end, point, Map.of(StandardAir.OXYGEN, 10.0), source));
                h.assertValueEqual(taken.outcome(), MutationOutcome.AMBIENT, "taking at " + point);
                h.assertTrue(taken.moved().isEmpty(), "a vacuum supplied " + taken.moved());
            }
            r.ticks(2);
            h.assertFalse(r.zoned(2, 2, 2), "a zone formed in the excluded dimension");
            h.assertValueEqual(Atmospheres.at(end, r.abs(2, 2, 2)).pressure(), 0.0, "the sealed room's pressure");
            h.assertValueEqual(Atmospheres.at(end, r.abs(5, 2, 2)).pressure(), 0.0, "the open air's pressure");
        });
    }

    /** An excluded dimension's ambient with gas supplies what's taken, without depleting (CORE-057). */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_018_an_excluded_ambient_with_gas_supplies_what_is_taken(GameTestHelper h) {
        Ambient air = new Ambient(
                new TreeMap<>(Map.of(StandardAir.OXYGEN.toString(), 30.0, StandardAir.NITROGEN.toString(), 70.0)), 280);
        onExcludedEnd(h, excluded(air), end -> {
            Rooms r = new Rooms(h, end, AREA);
            r.clear(0, 3);
            Vec3 point = r.point(1, 1, 1);
            MutationResult taken = r.settle(
                    GasMutations.remove(end, point, Map.of(StandardAir.OXYGEN, 500.0), Rooms.source("env_018_air")));
            h.assertValueEqual(taken.outcome(), MutationOutcome.AMBIENT, "the outcome");
            close(h, taken.moved().moles().get(StandardAir.OXYGEN), 500, "the oxygen supplied");
            close(h, taken.moved().temperature(), 280, "its temperature");
            AtmosphereSample open = Atmospheres.at(end, point);
            close(h, open.partialPressures().get(StandardAir.OXYGEN), 30, "the oxygen left");
        });
    }

    /**
     * A dimension whose definition only excludes it takes its ambient from the next source, and its
     * provider goes on running there, since an excluded dimension closes no engine's batch (ENV-015,
     * ENV-018).
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_018_a_provider_still_sets_an_excluded_dimensions_ambient(GameTestHelper h) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("zas_gametest", "env_018_provider");
        double[] answer = {150};
        AmbientProviders.register(id, Level.END, level -> AmbientDefinition.vacuum(answer[0]));
        try {
            onExcludedEnd(h, new DimensionAmbient(Optional.empty(), Optional.empty(), true), end -> {
                close(h, Ambients.ambient(Level.END).temperature(), 150, "the provider's answer");
                answer[0] = 160;
                Rooms.service().tickNow(end);
                close(h, Ambients.ambient(Level.END).temperature(), 160, "its next answer");
            });
        } finally {
            AmbientProviders.unregister(id, Level.END);
            Rooms.service().tickNow(level(h, Level.END));
        }
    }

    // ---- ENV-009 ------------------------------------------------------------------------

    /**
     * ENV-009's plan: under Normal, a player in the open air of each vanilla dimension takes no
     * atmospheric harm over 20 s of checks. {@code env_018_an_excluded_vacuum_harms_an_unsuited_player}
     * shows the same player and the same checks do notice harm.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_009_no_atmospheric_harm_in_any_vanilla_dimension(GameTestHelper h) {
        underNormal(h, () -> {
            for (ResourceKey<Level> dimension : VANILLA) {
                String where = dimension.location().toString();
                ServerLevel level = level(h, dimension);
                Rooms r = new Rooms(h, level, AREA);
                r.clear(0, 3);
                BlockPos feet = r.abs(1, 1, 1);
                FakePlayer player = playerAt(level, feet, "zas_env_009");
                long t = level.getGameTime();
                for (int check = 0; check <= 10; check++) {
                    Breathers.checkAt(player, t + 40L * check);
                    for (int tick = 0; tick < 40; tick++) {
                        player.baseTick();
                    }
                }
                h.assertValueEqual(player.getHealth(), player.getMaxHealth(), where + ": the player's health");
                h.assertValueEqual(player.getAirSupply(), player.getMaxAirSupply(), where + ": the player's air");
                Breathability verdict = Breathing.of(player);
                h.assertValueEqual(verdict.reason(), BreathReason.BREATHABLE, where + ": the verdict");
                h.assertTrue(verdict.hazards().isEmpty(), where + ": the hazards: " + verdict);
                h.assertFalse(r.zoned(1, 1, 1), where + ": a zone formed around the player");
                h.assertValueEqual(Atmospheres.at(level, feet).state(), AtmosphereState.AMBIENT, where + ": the state");
            }
        });
        h.succeed();
    }

    // ---- ENV-013 ------------------------------------------------------------------------

    /**
     * ENV-013's plan: under Normal, a fire outside zones spreads to flammable blocks, and no zone
     * forms, in each vanilla dimension. The fire sits on netherrack, which burns forever in all three,
     * and its neighbours are planks. Fire is random, so it gets enough ticks that the chance of never
     * spreading is negligible.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_013_fire_spreads_outside_zones_in_every_vanilla_dimension(GameTestHelper h) {
        GameRules.BooleanValue rule = h.getLevel().getGameRules().getRule(GameRules.RULE_DOFIRETICK);
        boolean was = rule.get();
        rule.set(true, h.getLevel().getServer());
        try {
            underNormal(h, () -> {
                for (ResourceKey<Level> dimension : VANILLA) {
                    spreads(h, level(h, dimension));
                }
            });
        } finally {
            rule.set(was, h.getLevel().getServer());
        }
        h.succeed();
    }

    private static void spreads(GameTestHelper h, ServerLevel level) {
        String where = level.dimension().location().toString();
        Rooms r = new Rooms(h, level, AREA);
        r.clear(0, 6);
        // A roof, so rain can't put anything out.
        for (BlockPos p : BlockPos.betweenClosed(r.abs(0, 5, 0), r.abs(6, 5, 6))) {
            level.setBlockAndUpdate(p, Rooms.STONE);
        }
        r.set(3, 0, 3, Blocks.NETHERRACK.defaultBlockState());
        BlockPos fire = r.abs(3, 1, 3);
        r.set(3, 1, 3, Blocks.FIRE.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(fire.relative(d).below(), Blocks.OAK_PLANKS.defaultBlockState());
        }
        r.ticks(2);
        h.assertFalse(r.zoned(3, 2, 3), where + ": a zone formed around the fire");

        int fires = 0;
        for (int tick = 0; tick < 500 && fires == 0; tick++) {
            level.getBlockState(fire).tick(level, fire, level.random);
            h.assertTrue(level.getBlockState(fire).is(Blocks.FIRE), where + ": the fire on netherrack went out");
            fires = firesBeside(r, fire);
        }
        h.assertTrue(fires > 0, where + ": the fire didn't spread in 500 of its ticks");
        r.ticks(2);
        h.assertFalse(r.zoned(3, 2, 3), where + ": a zone formed around the fires");
        r.clear(0, 6);
        for (BlockPos p : BlockPos.betweenClosed(r.abs(0, 5, 0), r.abs(6, 5, 6))) {
            level.setBlockAndUpdate(p, Rooms.AIR);
        }
    }

    private static int firesBeside(Rooms r, BlockPos fire) {
        int fires = 0;
        for (BlockPos p : BlockPos.betweenClosed(fire.offset(-1, -1, -1), fire.offset(1, 3, 1))) {
            if (!p.equals(fire) && r.level.getBlockState(p).is(Blocks.FIRE)) {
                fires++;
            }
        }
        return fires;
    }

    /**
     * ENV-013's plan: under Normal, a furnace outside zones smelts at vanilla's rate in each vanilla
     * dimension: one item per 200 of its ticks, with one coal lighting it for 1,600.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void env_013_furnace_smelts_at_vanillas_rate_outside_zones(GameTestHelper h) {
        underNormal(h, () -> {
            for (ResourceKey<Level> dimension : VANILLA) {
                smelts(h, level(h, dimension));
            }
        });
        h.succeed();
    }

    private static void smelts(GameTestHelper h, ServerLevel level) {
        String where = level.dimension().location().toString();
        Rooms r = new Rooms(h, level, AREA);
        r.clear(0, 3);
        r.set(1, 1, 1, Blocks.FURNACE.defaultBlockState());
        BlockPos at = r.abs(1, 1, 1);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(at);
        h.assertTrue(furnace != null, where + ": no furnace");
        furnace.setItem(0, new ItemStack(Items.COBBLESTONE, 8));
        furnace.setItem(1, new ItemStack(Items.COAL, 4));

        tickFurnace(level, at, furnace, 199);
        h.assertTrue(furnace.getItem(2).isEmpty(), where + ": smelted an item within 199 ticks");
        h.assertTrue(level.getBlockState(at).getValue(AbstractFurnaceBlock.LIT), where + ": the furnace isn't lit");
        h.assertValueEqual(furnace.getItem(1).getCount(), 3, where + ": coal after lighting");
        CompoundTag tag = furnace.saveWithoutMetadata(level.registryAccess());
        h.assertValueEqual(tag.getInt("CookTime"), 199, where + ": cooking progress");
        h.assertValueEqual(tag.getInt("BurnTime"), 1600 - 198, where + ": burn time");

        tickFurnace(level, at, furnace, 1);
        h.assertValueEqual(furnace.getItem(2).getItem(), Items.STONE, where + ": the result");
        h.assertValueEqual(furnace.getItem(2).getCount(), 1, where + ": results after 200 ticks");
        h.assertValueEqual(furnace.getItem(0).getCount(), 7, where + ": cobblestone after 200 ticks");
        tickFurnace(level, at, furnace, 200);
        h.assertValueEqual(furnace.getItem(2).getCount(), 2, where + ": results after 400 ticks");
        h.assertValueEqual(furnace.getItem(1).getCount(), 3, where + ": coal after 400 ticks");
        h.assertFalse(r.zoned(1, 2, 1), where + ": a zone formed around the furnace");
        r.clear(0, 3);
    }

    private static void tickFurnace(ServerLevel level, BlockPos at, AbstractFurnaceBlockEntity furnace, int ticks) {
        for (int i = 0; i < ticks; i++) {
            AbstractFurnaceBlockEntity.serverTick(level, at, level.getBlockState(at), furnace);
        }
    }
}
