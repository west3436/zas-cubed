package io.github.west3436.zas.example.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.api.atmosphere.ZoneId;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.capability.ISealProvider;
import io.github.west3436.zas.api.capability.SealGeometry;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.event.FireExtinguishedEvent;
import io.github.west3436.zas.api.event.FireIgnitedEvent;
import io.github.west3436.zas.api.event.HazardTickEvent;
import io.github.west3436.zas.api.event.OpenAirGasEvent;
import io.github.west3436.zas.api.event.ZoneBreachedEvent;
import io.github.west3436.zas.api.event.ZoneCreatedEvent;
import io.github.west3436.zas.api.event.ZoneDissolvedEvent;
import io.github.west3436.zas.api.event.ZoneMergedEvent;
import io.github.west3436.zas.api.event.ZonePausedEvent;
import io.github.west3436.zas.api.event.ZoneResumedEvent;
import io.github.west3436.zas.api.event.ZoneSealedEvent;
import io.github.west3436.zas.api.event.ZoneSplitEvent;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.api.life.Hazard;
import io.github.west3436.zas.api.life.HazardExposure;
import io.github.west3436.zas.api.life.HazardHandler;
import io.github.west3436.zas.api.life.HazardHandlers;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.api.reaction.ReactionChange;
import io.github.west3436.zas.api.reaction.ReactionContext;
import io.github.west3436.zas.api.reaction.ReactionType;
import io.github.west3436.zas.api.reaction.Reactions;
import io.github.west3436.zas.example.AtmosphereAlarm;
import io.github.west3436.zas.example.CatalyticConverter;
import io.github.west3436.zas.example.ExampleAddonMod;
import io.github.west3436.zas.example.ExampleContent;
import io.github.west3436.zas.example.ExampleHazards;
import io.github.west3436.zas.example.ExampleLifeSupport;
import io.github.west3436.zas.example.GasTankBlockEntity;
import io.github.west3436.zas.example.OutpostAir;
import io.github.west3436.zas.example.ShutterBlockEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The example addon's code does what it says, on this server without ZAS³ (API-047, API-048): what ZAS³
 * would ask of each extension point is asked here directly, and events are posted by hand. {@code
 * ExampleWithZasTests} runs them with ZAS³ behind them. Each test takes a tick.
 */
@GameTestHolder(ExampleAddonMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExampleAddonTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final ResourceLocation CO = ResourceLocation.fromNamespaceAndPath("zas", "carbon_monoxide");
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final double EPS = 1e-9;

    private ExampleAddonTests() {}

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < EPS, what + ": expected " + expected + ", got " + actual);
    }

    private static IGasHandler tank(GameTestHelper h, BlockPos pos) {
        h.setBlock(pos, ExampleContent.GAS_TANK.get());
        IGasHandler tank = h.getLevel().getCapability(IGasHandler.BLOCK, h.absolutePos(pos), Direction.UP);
        h.assertTrue(tank != null, "the gas tank gives no gas handler");
        return tank;
    }

    /** Filling and draining, by simulating and executing, up to what it can hold and what it has (API-021). */
    @GameTest(template = "empty")
    public static void api_047_the_gas_tank_fills_and_drains(GameTestHelper h) {
        IGasHandler tank = tank(h, POS);
        GasParcel gas = new GasParcel(Map.of(StandardAir.OXYGEN, 10.0), 300);

        close(h, tank.fill(gas, IGasHandler.Action.SIMULATE).totalMoles(), 10, "a simulated fill's answer");
        h.assertTrue(tank.contents().isEmpty(), "a simulated fill changed it");
        close(h, tank.fill(gas, IGasHandler.Action.EXECUTE).totalMoles(), 10, "a fill's answer");
        close(h, tank.contents().moles(StandardAir.OXYGEN), 10, "what a fill left");
        close(h, tank.contents().temperature(), 300, "the temperature it was filled at");

        Map<ResourceLocation, Double> wanted = Map.of(StandardAir.OXYGEN, 4.0, StandardAir.NITROGEN, 1.0);
        GasParcel simulated = tank.drain(wanted, IGasHandler.Action.SIMULATE);
        close(h, simulated.moles(StandardAir.OXYGEN), 4, "a simulated drain's answer");
        close(h, tank.contents().totalMoles(), 10, "what a simulated drain left");
        GasParcel drained = tank.drain(wanted, IGasHandler.Action.EXECUTE);
        close(h, drained.totalMoles(), 4, "a drain of a gas it has and one it hasn't");
        close(h, drained.temperature(), 300, "the temperature of what it gave");
        close(h, tank.contents().totalMoles(), 6, "what a drain left");

        GasParcel flood = new GasParcel(Map.of(StandardAir.NITROGEN, 1000.0), 300);
        double room = GasTankBlockEntity.CAPACITY_MOLES - 6;
        close(h, tank.fill(flood, IGasHandler.Action.EXECUTE).totalMoles(), room, "a fill past its capacity");
        close(h, tank.fill(flood, IGasHandler.Action.EXECUTE).totalMoles(), 0, "a fill when full");
        h.succeed();
    }

    /** A tank's gas is saved with it. */
    @GameTest(template = "empty")
    public static void api_047_the_gas_tank_saves_its_gas(GameTestHelper h) {
        IGasHandler tank = tank(h, POS);
        tank.fill(new GasParcel(Map.of(StandardAir.OXYGEN, 10.0), 300), IGasHandler.Action.EXECUTE);
        var registries = h.getLevel().registryAccess();
        CompoundTag saved = ((GasTankBlockEntity) tank).saveWithoutMetadata(registries);

        BlockPos other = POS.east(2);
        IGasHandler loaded = tank(h, other);
        ((GasTankBlockEntity) loaded).loadWithComponents(saved, registries);
        h.assertValueEqual(loaded.contents(), tank.contents(), "the loaded tank's contents");
        h.succeed();
    }

    /** The airlock answers for itself from its data, with no answer from the plain shutter (API-023). */
    @GameTest(template = "empty")
    public static void api_047_the_airlock_gives_its_seal(GameTestHelper h) {
        h.setBlock(POS, ExampleContent.AIRLOCK.get());
        BlockPos pos = h.absolutePos(POS);
        ShutterBlockEntity airlock = h.getBlockEntity(POS);
        ISealProvider seal = h.getLevel().getCapability(ISealProvider.BLOCK, pos);
        h.assertTrue(seal != null, "the airlock gives no seal provider");
        h.assertValueEqual(seal.geometry().orElseThrow(), SealGeometry.SEALED, "a shut airlock");
        airlock.setOpen(true);
        seal = h.getLevel().getCapability(ISealProvider.BLOCK, pos);
        h.assertValueEqual(seal.geometry().orElseThrow(), SealGeometry.OPEN, "an open airlock");

        h.setBlock(POS.east(2), ExampleContent.SHUTTER.get());
        h.assertTrue(
                h.getLevel().getCapability(ISealProvider.BLOCK, h.absolutePos(POS.east(2))) == null,
                "the plain shutter gives a seal provider");
        h.succeed();
    }

    /** A shutter's collision shape follows its data, which is why it says dynamicShape (GEO-043). */
    @GameTest(template = "empty")
    public static void api_047_the_shutters_shape_follows_its_data(GameTestHelper h) {
        h.setBlock(POS, ExampleContent.SHUTTER.get());
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(POS);
        Block block = ExampleContent.SHUTTER.get();
        h.assertTrue(block.hasDynamicShape(), "the shutter doesn't say its shape is dynamic");
        ShutterBlockEntity shutter = h.getBlockEntity(POS);
        h.assertFalse(
                level.getBlockState(pos)
                        .getCollisionShape(level, pos, CollisionContext.empty())
                        .isEmpty(),
                "a shut shutter has no shape");
        shutter.setOpen(true);
        h.assertTrue(
                level.getBlockState(pos)
                        .getCollisionShape(level, pos, CollisionContext.empty())
                        .isEmpty(),
                "an open shutter has a shape");
        h.succeed();
    }

    /** The helmet gives each part, a stray its own cold-proofing, and a pig nothing (API-022). */
    @GameTest(template = "empty")
    public static void api_047_the_helmet_and_the_stray_give_life_support(GameTestHelper h) {
        ILifeSupport helmet = new ItemStack(ExampleContent.PRESSURE_HELMET.get()).getCapability(ILifeSupport.ITEM);
        h.assertTrue(helmet != null, "the helmet gives no life support");
        close(h, helmet.minPressure(), 0, "its lowest pressure");
        close(h, helmet.maxPressure(), 300, "its highest pressure");
        close(h, helmet.minTemperature(), 200, "its lowest temperature");
        close(h, helmet.maxTemperature(), 400, "its highest temperature");
        h.assertTrue(helmet.sealedBreathing(), "it doesn't seal");
        h.assertTrue(helmet.gasFiltering(), "it doesn't filter");
        close(h, helmet.airflowResistance(), 0.5, "its airflow resistance");

        Stray stray = h.spawnWithNoFreeWill(EntityType.STRAY, POS);
        ILifeSupport own = stray.getCapability(ILifeSupport.ENTITY);
        h.assertTrue(own != null, "a stray has no life support");
        close(h, own.minTemperature(), 0, "a stray's lowest temperature");
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, POS);
        h.assertTrue(pig.getCapability(ILifeSupport.ENTITY) == null, "a pig has life support");
        h.succeed();
    }

    /** The tank is a supply: a draw takes gas from the stack and changes what it answers next (LIFE-028). */
    @GameTest(template = "empty")
    public static void api_047_the_oxygen_tank_is_a_supply(GameTestHelper h) {
        ItemStack stack = new ItemStack(ExampleContent.OXYGEN_TANK.get());
        ILifeSupport support = stack.getCapability(ILifeSupport.ITEM);
        h.assertTrue(support != null, "the tank gives no life support");
        IInternalSupply tank = support.internalSupply().orElseThrow();
        double full = ExampleLifeSupport.OxygenTank.FULL_MOLES;
        close(h, tank.contents().moles(StandardAir.OXYGEN), full, "a new tank");
        close(h, tank.distributionPressure(), 21.3, "its distribution pressure");
        close(h, tank.draw(1).moles(StandardAir.OXYGEN), 1, "a draw");
        close(h, tank.contents().totalMoles(), full - 1, "what a draw left");
        close(h, tank.draw(full).totalMoles(), full - 1, "a draw of more than it holds");
        h.assertTrue(tank.contents().isEmpty(), "the tank isn't empty");
        h.succeed();
    }

    /** The provider answers thin air, and without ZAS³ is kept for a dimension with no server level here (ENV-004). */
    @GameTest(template = "empty")
    public static void api_047_the_outposts_air_is_thin(GameTestHelper h) {
        List<AmbientProviders.Registration> providers = AmbientProviders.registered(OutpostAir.OUTPOST);
        h.assertValueEqual(providers.size(), 1, "the outpost's providers");
        h.assertValueEqual(providers.get(0).id(), OutpostAir.ID, "the provider");
        AmbientDefinition air = providers.get(0).provider().ambient(h.getLevel());
        close(h, air.pressure(), 20, "its pressure");
        h.assertTrue(air.temperature() >= 190 && air.temperature() <= 230, "its temperature: " + air.temperature());
        h.succeed();
    }

    private static HazardHandler handler(ResourceLocation id) {
        return HazardHandlers.registered().stream()
                .filter(r -> r.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException(id + " isn't registered"))
                .handler();
    }

    /**
     * The radon handler hurts by what's around an entity, and the tag's sickens by what it breathes, which
     * without ZAS³ is nothing (LIFE-032).
     */
    @GameTest(template = "empty")
    public static void api_047_the_hazard_handlers_do_what_they_say(GameTestHelper h) {
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, POS);
        float full = pig.getHealth();
        GasMixture room = new GasMixture(Map.of(ExampleHazards.RADON, 100.0), StandardAir.TEMPERATURE, 27_000);
        HazardExposure exposure =
                new HazardExposure(pig, ExampleHazards.RADON, Breathing.of(pig), Optional.of(room), 2);

        handler(ExampleHazards.DOSE).apply(exposure);
        h.assertTrue(pig.getHealth() < full, "the dose did no harm");
        handler(ExampleHazards.SICKNESS).apply(exposure);
        h.assertFalse(pig.hasEffect(MobEffects.WEAKNESS), "a pig breathing none of the gas got sick");
        h.succeed();
    }

    /** The listener halves heat, spares hypoxia and airflow impact, and leaves the rest alone (API-033). */
    @GameTest(template = "empty")
    public static void api_047_the_hazard_tick_listener_changes_the_tick(GameTestHelper h) {
        Pig pig = h.spawnWithNoFreeWill(EntityType.PIG, POS);
        HazardTickEvent plain = NeoForge.EVENT_BUS.post(new HazardTickEvent(pig, Hazard.HEAT, 2));
        close(h, plain.damage(), 2, "heat with no effect");

        pig.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        pig.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 200));
        pig.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200));
        close(
                h,
                NeoForge.EVENT_BUS
                        .post(new HazardTickEvent(pig, Hazard.HEAT, 2))
                        .damage(),
                1,
                "heat");
        h.assertTrue(
                NeoForge.EVENT_BUS
                        .post(new HazardTickEvent(pig, Hazard.HYPOXIA, 2))
                        .isCanceled(),
                "hypoxia wasn't spared");
        close(
                h,
                NeoForge.EVENT_BUS
                        .post(new HazardTickEvent(pig, Hazard.AIRFLOW_IMPACT, 2))
                        .damage(),
                0,
                "an impact");
        close(
                h,
                NeoForge.EVENT_BUS
                        .post(new HazardTickEvent(pig, Hazard.COLD, 2))
                        .damage(),
                2,
                "another hazard");
        h.succeed();
    }

    private static ReactionType converter(String parameters) {
        return Reactions.type(CatalyticConverter.ID)
                .orElseThrow()
                .withParameters(new Dynamic<>(JsonOps.INSTANCE, JsonParser.parseString(parameters)));
    }

    /** The type is registered, takes its parameters, refuses bad ones, and converts by them (API-020). */
    @GameTest(template = "empty")
    public static void api_047_the_reaction_type_converts_by_its_parameters(GameTestHelper h) {
        ReactionType type = converter("{\"efficiency\": 0.5}");
        GasMixture gas = new GasMixture(Map.of(CO, 2.0, StandardAir.OXYGEN, 2.0), 600, 70);
        ReactionChange change = type.react(new ReactionContext(gas, gas.moles(), 1, false));
        close(h, change.consumed().get(CO), 1, "the CO used");
        close(h, change.consumed().get(StandardAir.OXYGEN), 0.5, "the O2 used");
        close(h, change.produced().get(CO2), 1, "the CO2 made");
        close(h, change.heat(), 283_000, "the heat");

        GasMixture noCo = new GasMixture(Map.of(StandardAir.OXYGEN, 2.0), 600, 70);
        h.assertTrue(type.react(new ReactionContext(noCo, noCo.moles(), 1, false)) == null, "it reacted without CO");
        try {
            converter("{\"efficiency\": 2}");
            h.fail("an efficiency of 2 was taken");
        } catch (IllegalArgumentException expected) {
            h.succeed();
        }
    }

    /**
     * The alarm is on the bus for every event. Fire goes by the event alone. A zone goes by what {@code
     * Atmospheres.zone} says, and without ZAS³ there's no such zone, so none is ever open (API-030, API-031).
     */
    @GameTest(template = "empty")
    public static void api_047_the_alarm_hears_every_event(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos pos = h.absolutePos(POS);
        ZoneId zone = new ZoneId(1);
        ZoneId other = new ZoneId(2);
        NeoForge.EVENT_BUS.post(new ZoneCreatedEvent(level, zone, pos));
        NeoForge.EVENT_BUS.post(new ZoneMergedEvent(level, zone, other, pos));
        NeoForge.EVENT_BUS.post(new ZoneSplitEvent(level, zone, other, pos));
        NeoForge.EVENT_BUS.post(new ZoneBreachedEvent(level, zone, pos));
        NeoForge.EVENT_BUS.post(new ZoneSealedEvent(level, zone, pos));
        NeoForge.EVENT_BUS.post(new ZonePausedEvent(level, zone, pos));
        NeoForge.EVENT_BUS.post(new ZoneResumedEvent(level, zone, pos));
        NeoForge.EVENT_BUS.post(new ZoneDissolvedEvent(level, zone, pos));
        h.assertTrue(AtmosphereAlarm.openZones(level).isEmpty(), "a zone is open without ZAS³");

        NeoForge.EVENT_BUS.post(new FireIgnitedEvent(level, zone, pos));
        h.assertValueEqual(AtmosphereAlarm.burningZones(level), Set.of(zone), "the burning zones");
        NeoForge.EVENT_BUS.post(new FireExtinguishedEvent(level, zone, pos));
        h.assertTrue(AtmosphereAlarm.burningZones(level).isEmpty(), "a zone that went out still burns");

        SourceId source = SourceId.of(ExampleAddonMod.MOD_ID, "api_047");
        GasParcel gas = new GasParcel(Map.of(StandardAir.OXYGEN, 1.0), StandardAir.TEMPERATURE);
        NeoForge.EVENT_BUS.post(new OpenAirGasEvent(level, pos, true, gas, source));
        h.succeed();
    }

    /** The addon's data puts a block in the tag, which the game loads with or without ZAS³ (API-053). */
    @GameTest(template = "empty")
    public static void api_047_the_lit_lamp_is_an_ignition_source(GameTestHelper h) {
        TagKey<Block> sources =
                TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("zas", "ignition_sources"));
        h.assertTrue(Blocks.REDSTONE_LAMP.defaultBlockState().is(sources), "the lamp isn't in the tag");
        h.succeed();
    }
}
