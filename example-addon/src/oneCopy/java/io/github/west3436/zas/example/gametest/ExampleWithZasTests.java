package io.github.west3436.zas.example.gametest;

import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.example.AtmosphereAlarm;
import io.github.west3436.zas.example.ExampleAddonMod;
import io.github.west3436.zas.example.ExampleContent;
import io.github.west3436.zas.example.ExampleHazards;
import io.github.west3436.zas.example.ShutterBlockEntity;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * API-047: the example addon's extension points with ZAS³ behind them, on the server of built jars.
 * {@code ExampleAddonTests} asks the same code what it answers without ZAS³.
 *
 * <p>Not here, since this server can't show them: the ambient provider's dimension (a GameTest server
 * has only the vanilla dimensions), the gas tank's pipes (they're in the content mod) and the oxygen
 * tank's internals (they need a player).
 *
 * <p>These wait on real atmos ticks and breath checks, in a chunk that starts ticking in real time, while
 * the test server runs thousands of game ticks a second. So their long timeouts bound a failure only: a
 * test that passes takes a second or two. Each room test has a batch to itself and a height of its own,
 * since the server leaves an earlier batch's blocks standing close by, often in the same chunk.
 */
@GameTestHolder(ExampleAddonMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleWithZasTests {
    private static final int WAIT_TICKS = 60_000;
    private static final SourceId SOURCE = SourceId.of(ExampleAddonMod.MOD_ID, "api_047");
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final double EPS = 1e-6;

    private ExampleWithZasTests() {}

    /**
     * A sealed stone box 5 × 5 × 5, a 3 × 3 × 3 room inside it, in the test's forced chunk and the given
     * height above its structure block, with a block in place of one wall's middle.
     */
    private record Room(GameTestHelper h, ServerLevel level, BlockPos origin) {
        static Room build(GameTestHelper h, Block inWall, int height) {
            ServerLevel level = h.getLevel();
            BlockPos at = h.absolutePos(BlockPos.ZERO);
            BlockPos origin = new ChunkPos(at).getWorldPosition().atY(at.getY() + height);
            for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(4, 4, 4))) {
                int x = p.getX() - origin.getX();
                int y = p.getY() - origin.getY();
                int z = p.getZ() - origin.getZ();
                boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                level.setBlockAndUpdate(p, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            }
            Room room = new Room(h, level, origin);
            level.setBlockAndUpdate(room.door(), inWall.defaultBlockState());
            return room;
        }

        BlockPos door() {
            return origin.offset(4, 2, 2);
        }

        Vec3 middle() {
            return Vec3.atCenterOf(origin.offset(2, 2, 2));
        }

        void addRadon() {
            GasParcel radon = new GasParcel(Map.of(ExampleHazards.RADON, 100.0), StandardAir.TEMPERATURE);
            GasMutations.add(level, middle(), radon, SOURCE);
        }

        ZoneInfo zone() {
            return Atmospheres.zoneAt(level, middle()).orElseThrow(() -> new GameTestAssertException("no zone yet"));
        }

        Pig pig(int x, ItemStack helmet) {
            Pig pig = EntityType.PIG.create(level);
            h.assertTrue(pig != null, "couldn't create a pig");
            pig.moveTo(Vec3.atBottomCenterOf(origin.offset(x, 1, 2)));
            pig.setNoAi(true);
            pig.setItemSlot(EquipmentSlot.HEAD, helmet);
            level.addFreshEntity(pig);
            return pig;
        }

        void open() {
            ((ShutterBlockEntity) level.getBlockEntity(door())).setOpen(true);
        }

        void assertAlarmHeard() {
            h.assertTrue(
                    AtmosphereAlarm.openZones(level).contains(zone().id()), "the alarm hasn't heard the room open");
        }
    }

    /** ZAS³ loads the addon's gas, tags and reaction, and runs the code type the reaction names (API-020). */
    @GameTest(template = "empty")
    public static void api_047_the_addons_data_loads_and_its_reaction_type_runs(GameTestHelper h) {
        h.assertTrue(
                Math.abs(GasProperties.molarHeatCapacity(ExampleHazards.RADON) - 12.47) < EPS,
                "radon's heat capacity isn't its definition's");
        h.assertTrue(GasTags.contains(ExampleHazards.RADIOACTIVE, ExampleHazards.RADON), "radon isn't radioactive");
        h.assertTrue(GasTags.contains(GasTags.TOXIC, ExampleHazards.RADON), "radon isn't in #zas:toxic");

        GasMixture cold = new GasMixture(Map.of(CO, 1.0, StandardAir.OXYGEN, 1.0), 400, 70);
        h.assertValueEqual(Reactions.applyStandalone(cold, 1, false), cold, "the mixture below min_temperature");

        GasMixture warm = new GasMixture(Map.of(CO, 1.0, StandardAir.OXYGEN, 1.0), 600, 70);
        GasMixture after = Reactions.applyStandalone(warm, 1, false);
        h.assertTrue(Math.abs(after.moles(CO) - 0.2) < EPS, "the CO left: " + after);
        h.assertTrue(Math.abs(after.moles(StandardAir.OXYGEN) - 0.6) < EPS, "the O2 left: " + after);
        h.assertTrue(Math.abs(after.moles(CO2) - 0.8) < EPS, "the CO2 made: " + after);
        h.assertTrue(after.temperature() > warm.temperature(), "the reaction released no heat: " + after);
        h.succeed();
    }

    /**
     * Radon in a sealed room reaches two pigs (LIFE-032). The dose handler harms both. The tag's handler
     * sickens only the bare one: the helmet's filter (API-022) keeps radon, which the addon's data puts
     * in {@code #zas:toxic}, out of its wearer's breath. Then the airlock opens by {@code markDirty}
     * (API-023), and the alarm hears the room is open (API-030).
     */
    @GameTest(template = "empty", batch = "api_047_airlock", timeoutTicks = WAIT_TICKS)
    public static void api_047_radon_harms_and_the_airlock_opens_the_room(GameTestHelper h) {
        Room room = Room.build(h, ExampleContent.AIRLOCK.get(), 2);
        room.addRadon();
        ItemStack helmet = new ItemStack(ExampleContent.PRESSURE_HELMET.get());
        Pig bare = room.pig(1, ItemStack.EMPTY);
        Pig helmeted = room.pig(3, helmet);
        float full = bare.getMaxHealth();
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(bare.getHealth() < full, "the dose hasn't harmed the bare pig");
                    h.assertTrue(helmeted.getHealth() < full, "the dose hasn't harmed the helmeted pig");
                    h.assertTrue(bare.hasEffect(MobEffects.WEAKNESS), "the bare pig isn't sick");
                })
                .thenExecute(() -> {
                    h.assertFalse(helmeted.hasEffect(MobEffects.WEAKNESS), "the helmet's filter let radon through");
                    h.assertFalse(room.zone().breached(), "the room is open with the airlock shut");
                    room.open();
                })
                .thenWaitUntil(room::assertAlarmHeard)
                .thenSucceed();
    }

    /** A shutter has no provider: ZAS³ reads its shape, and {@code markDirty} has it read again (GEO-043). */
    @GameTest(template = "empty", batch = "api_047_shutter", timeoutTicks = WAIT_TICKS)
    public static void api_047_the_shutters_shape_opens_the_room(GameTestHelper h) {
        Room room = Room.build(h, ExampleContent.SHUTTER.get(), 8);
        room.addRadon();
        h.startSequence()
                .thenWaitUntil(() -> h.assertFalse(room.zone().breached(), "the room is open with the shutter shut"))
                .thenExecute(room::open)
                .thenWaitUntil(room::assertAlarmHeard)
                .thenSucceed();
    }
}
