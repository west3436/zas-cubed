package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.airlock.AirSensorBlock;
import io.github.west3436.zas.atmos.airlock.AirlockBlock;
import io.github.west3436.zas.atmos.airlock.AirlockControllerBlock;
import io.github.west3436.zas.atmos.airlock.LargeAirlockBlock;
import io.github.west3436.zas.atmos.battery.RechargerBlock;
import io.github.west3436.zas.atmos.canister.CanisterBlock;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlock;
import io.github.west3436.zas.atmos.controller.CentralControllerBlock;
import io.github.west3436.zas.atmos.electrolyzer.ElectrolyzerBlock;
import io.github.west3436.zas.atmos.filter.FilterBlock;
import io.github.west3436.zas.atmos.firealarm.FireAlarmBlock;
import io.github.west3436.zas.atmos.firelock.FirelockBlock;
import io.github.west3436.zas.atmos.firelock.FirelockDoorBlock;
import io.github.west3436.zas.atmos.heater.AirConditionerBlock;
import io.github.west3436.zas.atmos.heater.HeaterBlock;
import io.github.west3436.zas.atmos.holder.TankHolderBlock;
import io.github.west3436.zas.atmos.igniter.IgniterBlock;
import io.github.west3436.zas.atmos.mixer.MixerBlock;
import io.github.west3436.zas.atmos.pipe.EncasedPipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeBlock;
import io.github.west3436.zas.atmos.pipe.PipeCapBlock;
import io.github.west3436.zas.atmos.port.ConnectorPortBlock;
import io.github.west3436.zas.atmos.portable.PortablePumpBlock;
import io.github.west3436.zas.atmos.portable.PortableScrubberBlock;
import io.github.west3436.zas.atmos.power.GeneratorBlock;
import io.github.west3436.zas.atmos.pump.PressurePumpBlock;
import io.github.west3436.zas.atmos.pump.VolumePumpBlock;
import io.github.west3436.zas.atmos.radiator.HeatExchangerBlock;
import io.github.west3436.zas.atmos.radiator.RadiatorAdaptorBlock;
import io.github.west3436.zas.atmos.radiator.RadiatorPipeBlock;
import io.github.west3436.zas.atmos.scrubber.ScrubberBlock;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import io.github.west3436.zas.atmos.ttv.TransferValveBlock;
import io.github.west3436.zas.atmos.valve.ManualValveBlock;
import io.github.west3436.zas.atmos.valve.RedstoneValveBlock;
import io.github.west3436.zas.atmos.vent.PassiveVentBlock;
import io.github.west3436.zas.atmos.vent.VentBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The content mod's blocks. Each block with an item also gets one in {@link AtmosItems}. */
public final class AtmosBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ZasAtmosMod.MOD_ID);

    /** A pipe segment (CONT-001, CONT-007). */
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe", PipeBlock::new, metal());

    /** A pipe end cap, which closes a pipe's open end (CONT-004). */
    public static final DeferredBlock<PipeCapBlock> PIPE_CAP =
            BLOCKS.registerBlock("pipe_cap", PipeCapBlock::new, metal());

    /** A connector port, which a canister stands on to join its network (CONT-083). */
    public static final DeferredBlock<ConnectorPortBlock> CONNECTOR_PORT =
            BLOCKS.registerBlock("connector_port", ConnectorPortBlock::new, metal());

    /** A gas canister (CONT-017). */
    public static final DeferredBlock<CanisterBlock> CANISTER =
            BLOCKS.registerBlock("canister", CanisterBlock::new, metal());

    /** A vent (CONT-014). */
    public static final DeferredBlock<VentBlock> VENT = BLOCKS.registerBlock("vent", VentBlock::new, metal());
    /** A scrubber (CONT-015). */
    public static final DeferredBlock<ScrubberBlock> SCRUBBER =
            BLOCKS.registerBlock("scrubber", ScrubberBlock::new, metal());

    /** The passive vent: it equalizes with the room, with no power (CONT-016). */
    public static final DeferredBlock<PassiveVentBlock> PASSIVE_VENT =
            BLOCKS.registerBlock("passive_vent", PassiveVentBlock::new, metal());

    /** The area air controller, a wall panel (CONT-040, D-68). */
    public static final DeferredBlock<AreaAirControllerBlock> AREA_AIR_CONTROLLER =
            BLOCKS.registerBlock("area_air_controller", AreaAirControllerBlock::new, metal().strength(1.5f, 4.8f));

    /** The central atmospherics controller, a console (CONT-115, D-75). */
    public static final DeferredBlock<CentralControllerBlock> CENTRAL_ATMOS_CONTROLLER =
            BLOCKS.registerBlock("central_atmos_controller", CentralControllerBlock::new, metal());

    /** The fire alarm, a small box on a wall (CONT-044). As hard as the controller. */
    public static final DeferredBlock<FireAlarmBlock> FIRE_ALARM =
            BLOCKS.registerBlock("fire_alarm", FireAlarmBlock::new, metal().strength(1.5f, 4.8f));

    /** The solid-fuel generator (CONT-061). */
    public static final DeferredBlock<GeneratorBlock> GENERATOR =
            BLOCKS.registerBlock("generator", GeneratorBlock::new, metal());

    /** A standalone firelock, one block wide and two high (CONT-043). As hard as an iron door. */
    public static final DeferredBlock<FirelockBlock> FIRELOCK = BLOCKS.registerBlock(
            "firelock", FirelockBlock::new, metal().strength(5.0f, 6.0f).pushReaction(PushReaction.BLOCK));

    // ---- Airlocks (CONT-133 to CONT-142, D-84) ----

    /** An airlock, one block wide and two high (CONT-133). As hard as the firelock. */
    public static final DeferredBlock<AirlockBlock> AIRLOCK = BLOCKS.registerBlock(
            "airlock", AirlockBlock::new, metal().strength(5.0f, 6.0f).pushReaction(PushReaction.BLOCK));

    /** The large airlock, two blocks wide and two high (CONT-134). */
    public static final DeferredBlock<LargeAirlockBlock> LARGE_AIRLOCK = BLOCKS.registerBlock(
            "large_airlock",
            LargeAirlockBlock::new,
            metal().strength(5.0f, 6.0f).pushReaction(PushReaction.BLOCK));

    /** The airlock controller, a wall panel (CONT-138). As hard as the area air controller. */
    public static final DeferredBlock<AirlockControllerBlock> AIRLOCK_CONTROLLER =
            BLOCKS.registerBlock("airlock_controller", AirlockControllerBlock::new, metal().strength(1.5f, 4.8f));

    /** The air sensor, a wall panel (CONT-142). */
    public static final DeferredBlock<AirSensorBlock> AIR_SENSOR =
            BLOCKS.registerBlock("air_sensor", AirSensorBlock::new, metal().strength(1.5f, 4.8f));

    /** A vanilla door with a firelock fitted (CONT-137). It has no item: it drops the door and the firelock. */
    public static final DeferredBlock<FirelockDoorBlock> FIRELOCK_DOOR = BLOCKS.registerBlock(
            "firelock_door",
            FirelockDoorBlock::new,
            metal().strength(5.0f, 6.0f).pushReaction(PushReaction.BLOCK));

    /** The heater (CONT-019). */
    public static final DeferredBlock<HeaterBlock> HEATER = BLOCKS.registerBlock("heater", HeaterBlock::new, metal());

    /** The air conditioner (CONT-019). */
    public static final DeferredBlock<AirConditionerBlock> AIR_CONDITIONER =
            BLOCKS.registerBlock("air_conditioner", AirConditionerBlock::new, metal());

    /** The portable scrubber (CONT-018). */
    public static final DeferredBlock<PortableScrubberBlock> PORTABLE_SCRUBBER =
            BLOCKS.registerBlock("portable_scrubber", PortableScrubberBlock::new, metal());

    /** The portable pump (CONT-018). */
    public static final DeferredBlock<PortablePumpBlock> PORTABLE_PUMP =
            BLOCKS.registerBlock("portable_pump", PortablePumpBlock::new, metal());

    /** The recharger (CONT-113). */
    public static final DeferredBlock<RechargerBlock> RECHARGER =
            BLOCKS.registerBlock("recharger", RechargerBlock::new, metal());

    /** The igniter (CONT-105). */
    public static final DeferredBlock<IgniterBlock> IGNITER =
            BLOCKS.registerBlock("igniter", IgniterBlock::new, metal());

    /** The pressure pump (CONT-011). */
    public static final DeferredBlock<PressurePumpBlock> PRESSURE_PUMP =
            BLOCKS.registerBlock("pressure_pump", PressurePumpBlock::new, metal());

    /** The volume pump (CONT-012). */
    public static final DeferredBlock<VolumePumpBlock> VOLUME_PUMP =
            BLOCKS.registerBlock("volume_pump", VolumePumpBlock::new, metal());

    /** The filter (CONT-013). */
    public static final DeferredBlock<FilterBlock> FILTER = BLOCKS.registerBlock("filter", FilterBlock::new, metal());

    /** The mixer (CONT-077). */
    public static final DeferredBlock<MixerBlock> MIXER = BLOCKS.registerBlock("mixer", MixerBlock::new, metal());

    /** The manual valve (CONT-010). */
    public static final DeferredBlock<ManualValveBlock> MANUAL_VALVE =
            BLOCKS.registerBlock("manual_valve", ManualValveBlock::new, metal());

    /** The redstone valve (CONT-010). */
    public static final DeferredBlock<RedstoneValveBlock> REDSTONE_VALVE =
            BLOCKS.registerBlock("redstone_valve", RedstoneValveBlock::new, metal());

    /** The water electrolyzer (CONT-055, CONT-056). */
    public static final DeferredBlock<ElectrolyzerBlock> ELECTROLYZER =
            BLOCKS.registerBlock("electrolyzer", ElectrolyzerBlock::new, metal());

    // ---- Station blocks (CONT-129 to CONT-131, D-80, D-81) ----
    // Full blocks, so they seal as any full block does. None lets heat through: their U is 0, in
    // data/zas_atmos/zas/thermal/station_blocks.json (HEAT-019).
    // Hardness and tool are the owner's (2026-11-07): the station wall at 3, a stone pickaxe or
    // better; the reinforced wall and the glass at 25, a diamond pickaxe or better.

    /** The station wall: conducts no heat, with stone's blast resistance (CONT-129). */
    public static final DeferredBlock<Block> STATION_WALL = BLOCKS.registerSimpleBlock("station_wall", stationWall());

    /** The reinforced station wall: conducts no heat, with obsidian's blast resistance (CONT-130). */
    public static final DeferredBlock<Block> REINFORCED_STATION_WALL =
            BLOCKS.registerSimpleBlock("reinforced_station_wall", reinforcedStationWall());

    // ---- Pipes in walls (#487) ----
    // A wall with a pipe through it: for the air it is the wall, a full block, and for the pipes a
    // pipe. Each has its wall's properties, its U of 0 and its mining tags, and no item: it is made
    // by using a pipe on the wall, or the wall on a pipe, and drops both.

    /** A pipe in a station wall. */
    public static final DeferredBlock<EncasedPipeBlock> STATION_WALL_PIPE =
            BLOCKS.registerBlock("station_wall_pipe", p -> new EncasedPipeBlock(p, STATION_WALL), stationWall());

    /** A pipe in a reinforced station wall. */
    public static final DeferredBlock<EncasedPipeBlock> REINFORCED_STATION_WALL_PIPE = BLOCKS.registerBlock(
            "reinforced_station_wall_pipe",
            p -> new EncasedPipeBlock(p, REINFORCED_STATION_WALL),
            reinforcedStationWall());

    /**
     * Reinforced glass: seen through as glass is, conducts no heat, never broken by a pressure
     * difference, since it isn't in {@code #zas:pressure_fragile}, with obsidian's blast resistance
     * (CONT-131). A {@link
     * TransparentBlock}, as glass is, so it hides none of its neighbours' faces and skips its faces
     * against itself. It drops itself, without Silk Touch.
     */
    public static final DeferredBlock<TransparentBlock> REINFORCED_GLASS_BLOCK = BLOCKS.registerBlock(
            "reinforced_glass_block",
            TransparentBlock::new,
            BlockBehaviour.Properties.of()
                    .instrument(NoteBlockInstrument.HAT)
                    .strength(25.0f, 1200.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false));

    // ---- Radiators and heat exchangers (CONT-143 to CONT-148, D-84) ----

    /** Radiator pipe, which exchanges heat with the gas around it (CONT-143, HEAT-020). */
    public static final DeferredBlock<RadiatorPipeBlock> RADIATOR_PIPE =
            BLOCKS.registerBlock("radiator_pipe", RadiatorPipeBlock::new, metal());

    /** The radiator adaptor, which joins pipe to radiator pipe (CONT-145). */
    public static final DeferredBlock<RadiatorAdaptorBlock> RADIATOR_ADAPTOR =
            BLOCKS.registerBlock("radiator_adaptor", RadiatorAdaptorBlock::new, metal());

    /** The heat exchanger, which works in pairs (CONT-147, HEAT-022). */
    public static final DeferredBlock<HeatExchangerBlock> HEAT_EXCHANGER =
            BLOCKS.registerBlock("heat_exchanger", HeatExchangerBlock::new, metal());

    // ---- The tank transfer valve, placed (CONT-103, D-86) ----

    /**
     * A tank transfer valve standing on the ground, which its item places: small, metal, and broken at
     * once by hand, as a lantern is, but needing no support.
     */
    public static final DeferredBlock<TransferValveBlock> TANK_TRANSFER_VALVE = BLOCKS.registerBlock(
            "tank_transfer_valve",
            TransferValveBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .instabreak()
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .forceSolidOn()
                    .pushReaction(PushReaction.DESTROY));

    // ---- The tank holder (CONT-149 to CONT-151, D-84) ----

    /** The tank holder, a rack of ten personal tanks. */
    public static final DeferredBlock<TankHolderBlock> TANK_HOLDER =
            BLOCKS.registerBlock("tank_holder", TankHolderBlock::new, metal());

    // ---- The large gas tank ----

    /** The large gas tank, one block wide and two high: a fixed store on a pipe network. */
    public static final DeferredBlock<LargeTankBlock> LARGE_GAS_TANK =
            BLOCKS.registerBlock("large_gas_tank", LargeTankBlock::new, metal().pushReaction(PushReaction.BLOCK));

    private AtmosBlocks() {}

    /** The pipes in casings, one for each block a pipe goes into. */
    public static List<DeferredBlock<EncasedPipeBlock>> encasedPipes() {
        return List.of(STATION_WALL_PIPE, REINFORCED_STATION_WALL_PIPE);
    }

    /** The pipe, bare and in each casing: the blocks of a pipe's block entity, and those a dye tints. */
    public static Block[] pipes() {
        List<Block> pipes = new ArrayList<>();
        pipes.add(PIPE.get());
        encasedPipes().forEach(cased -> pipes.add(cased.get()));
        return pipes.toArray(Block[]::new);
    }

    /** The station wall's properties: hardness 3, stone's blast resistance. */
    private static BlockBehaviour.Properties stationWall() {
        return wall().strength(3.0f, 6.0f);
    }

    /** The reinforced station wall's properties: hardness 25, obsidian's blast resistance. */
    private static BlockBehaviour.Properties reinforcedStationWall() {
        return wall().strength(25.0f, 1200.0f);
    }

    /** The properties of a station wall, before its strength: metal, mined with a pickaxe. */
    private static BlockBehaviour.Properties wall() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    /** The properties of a metal atmospherics part: as hard as a hopper, mined with a pickaxe. */
    public static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 4.8f)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
