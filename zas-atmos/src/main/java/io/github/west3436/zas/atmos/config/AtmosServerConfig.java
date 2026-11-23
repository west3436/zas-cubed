package io.github.west3436.zas.atmos.config;

import io.github.west3436.zas.api.gas.GasPurity;
import io.github.west3436.zas.atmos.controller.Thermostat;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import io.github.west3436.zas.atmos.pipe.PipeLeak;
import io.github.west3436.zas.atmos.radiator.RadiatorMath;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The content mod's server config, {@code zas_atmos-server.toml} (per world, like the framework's).
 * Its {@code [power]} section holds the FE values, all starting points for playtesting (D-37,
 * CONT-065).
 */
public final class AtmosServerConfig {
    public static final ModConfigSpec SPEC;

    /** The vent's draw while it runs, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue VENT_DRAW;

    /** The heater's draw from its battery at full power, in FE/t; less in proportion below it (CONT-087). */
    public static final ModConfigSpec.IntValue HEATER_DRAW;

    /** A scrubber's draw while it scrubs or siphons, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue SCRUBBER_DRAW;

    /** A scrubber's draw in panic, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue SCRUBBER_PANIC_DRAW;

    /** The most heat a heater adds, in W (CONT-087). */
    public static final ModConfigSpec.IntValue HEATER_WATTS;

    /** The air conditioner's draw from its battery at full power, in FE/t; less in proportion below it (CONT-087). */
    public static final ModConfigSpec.IntValue AIR_CONDITIONER_DRAW;

    /** The most heat an air conditioner takes, in W (CONT-087). */
    public static final ModConfigSpec.IntValue AIR_CONDITIONER_WATTS;

    /** The portable scrubber's draw from its battery while it moves gas, in FE/t (CONT-018). */
    public static final ModConfigSpec.IntValue PORTABLE_SCRUBBER_DRAW;

    /** The portable pump's draw from its battery while it moves gas, in FE/t (CONT-018). */
    public static final ModConfigSpec.IntValue PORTABLE_PUMP_DRAW;

    /** The recharger's draw from its controller while it charges, in FE/t (CONT-113). */
    public static final ModConfigSpec.IntValue RECHARGER_DRAW;

    /** The igniter's draw from its controller while it gets a redstone signal, in FE/t (CONT-105). */
    public static final ModConfigSpec.IntValue IGNITER_DRAW;

    /** The pressure pump's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue PRESSURE_PUMP_DRAW;

    /** What a volume pump draws from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue VOLUME_PUMP_DRAW;

    /** The filter's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue FILTER_DRAW;

    /** The mixer's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue MIXER_DRAW;

    /** The electrolyzer's draw from its controller while it is on, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue ELECTROLYZER_DRAW;

    /** What the recharger puts into its battery while it charges, in FE/t (CONT-113). */
    public static final ModConfigSpec.IntValue RECHARGER_RATE;

    /** The area air controller's own draw, in FE/t (CONT-065). */
    public static final ModConfigSpec.IntValue CONTROLLER_DRAW;

    /** The fire alarm's draw, in FE/t (CONT-044, CONT-065). */
    public static final ModConfigSpec.IntValue FIRE_ALARM_DRAW;

    public static final ModConfigSpec.IntValue AIRLOCK_CONTROLLER_DRAW;

    /** The area air controller's buffer, in FE (CONT-062). */
    public static final ModConfigSpec.IntValue CONTROLLER_BUFFER;

    /** The central atmospherics controller's own draw, in FE/t (CONT-065, CONT-115). */
    public static final ModConfigSpec.IntValue CENTRAL_DRAW;

    /** The central atmospherics controller's buffer, in FE (CONT-115). */
    public static final ModConfigSpec.IntValue CENTRAL_BUFFER;

    /** The most chunks a central atmospherics controller reaches, and its range when placed (CONT-116, CONT-117). */
    public static final ModConfigSpec.IntValue CENTRAL_MAX_RANGE;

    /** Whether a central atmospherics controller keeps its chunk and the 8 around it loaded (CONT-122). */
    public static final ModConfigSpec.BooleanValue CENTRAL_FORCE_LOAD;

    /** What the generator makes while it burns, in FE/t (CONT-061). */
    public static final ModConfigSpec.IntValue GENERATOR_OUTPUT;

    /** The generator's own store, in FE. */
    public static final ModConfigSpec.IntValue GENERATOR_BUFFER;

    /** How long one coal burns in the generator, in seconds; other fuels scale by burn time (CONT-066). */
    public static final ModConfigSpec.IntValue GENERATOR_SECONDS_PER_FUEL;

    /** The pressure fall within one atmos tick that alarms a controller at once, in kPa (CONT-043). */
    /** J per FE in the TEG and the turbine (CONT-158, CONT-162). */
    public static final ModConfigSpec.DoubleValue JOULES_PER_FE;

    /** The TEG's efficiency as a share of Carnot's (CONT-158). */
    public static final ModConfigSpec.DoubleValue TEG_CARNOT_FRACTION;

    /** The TEG's FE store (CONT-158). */
    public static final ModConfigSpec.IntValue TEG_BUFFER;

    /** The pressure difference a circulator keeps, kPa (CONT-157). */
    public static final ModConfigSpec.DoubleValue CIRCULATOR_FRICTION_KPA;

    /** A circulator's most flow, L/s (CONT-157). */
    public static final ModConfigSpec.DoubleValue CIRCULATOR_MAX_FLOW;

    /** The turbine's top speed, rpm (CONT-161). */
    public static final ModConfigSpec.IntValue TURBINE_MAX_RPM;

    /** The shaft's energy at top speed, kJ (CONT-161). */
    public static final ModConfigSpec.DoubleValue TURBINE_SPIN_ENERGY_KJ;

    /** The turbine's generator at top speed, FE/t (CONT-162). */
    public static final ModConfigSpec.IntValue TURBINE_MAX_OUTPUT;

    /** The turbine's share of ideal expansion work (CONT-161). */
    public static final ModConfigSpec.DoubleValue TURBINE_EFFICIENCY;

    /** The compressor's efficiency (CONT-160). */
    public static final ModConfigSpec.DoubleValue COMPRESSOR_EFFICIENCY;

    /** The most a turbine controller's burn rate may be set to, mol/s of fuel (CONT-160, CONT-163). */
    public static final ModConfigSpec.DoubleValue TURBINE_MAX_BURN;

    /** The oxidizer's share of the chamber the compressor keeps it at, at least (CONT-160). */
    public static final ModConfigSpec.DoubleValue TURBINE_OXIDIZER_FRACTION;

    /** The turbine's nozzle, mol/s per kPa its chamber is above its exhaust (CONT-161). */
    public static final ModConfigSpec.DoubleValue TURBINE_NOZZLE;

    /** The turbine's most flow, L/s (CONT-161). */
    public static final ModConfigSpec.DoubleValue TURBINE_MAX_FLOW;

    /** The share of shaft energy lost per second (CONT-161). */
    public static final ModConfigSpec.DoubleValue TURBINE_FRICTION;

    /** The share of shaft energy its brake takes per second while its engine is off (CONT-161). */
    public static final ModConfigSpec.DoubleValue TURBINE_BRAKE;

    /** The turbine controller's FE store (CONT-162). */
    public static final ModConfigSpec.IntValue TURBINE_BUFFER;

    public static final ModConfigSpec.DoubleValue BREACH_DROP_KPA;

    /** A thermostat's dead band, in K (CONT-165). */
    public static final ModConfigSpec.DoubleValue THERMOSTAT_BAND;

    /** A pipe network's burst pressure over a segment's cell, in kPa; 0 turns bursting off (CONT-005, CONT-108). */
    public static final ModConfigSpec.DoubleValue PIPE_BURST_KPA;

    /** A pipe network's leak pressure over a segment's cell, in kPa; 0 turns leaks off (CONT-005, CONT-168). */
    public static final ModConfigSpec.DoubleValue PIPE_LEAK_KPA;

    /** The share per second of what would even a leaking segment out with its cell that its leak lets out (CONT-168). */
    public static final ModConfigSpec.DoubleValue PIPE_LEAK_RATE;

    /** The most power a tank's or canister's explosion can have (CONT-109). TNT is 4. */
    public static final ModConfigSpec.DoubleValue EXPLOSION_POWER_CAP;

    /** The share of movement speed each hardsuit piece worn takes away (CONT-089). */
    public static final ModConfigSpec.DoubleValue HARDSUIT_SLOWDOWN;

    /** A radiator pipe segment's conductance to the gas around it, in W/K (HEAT-020). */
    public static final ModConfigSpec.DoubleValue RADIATOR_CONDUCTANCE;

    /** A radiator pipe segment's emissivity in a vacuum (HEAT-021). */
    public static final ModConfigSpec.DoubleValue RADIATOR_EMISSIVITY;

    /** How pure a pipe's gas must be to leave for another mod's fluid pipe or tank (COMPAT-142). */
    public static final ModConfigSpec.DoubleValue FLUID_MIN_PURITY;

    /** The most a gas miner adds, in mol/s (CONT-181). */
    public static final ModConfigSpec.IntValue GAS_MINER_RATE;

    /** The highest target a gas miner's window or a computer sets, in kPa (CONT-180). */
    public static final ModConfigSpec.DoubleValue GAS_MINER_MAX_TARGET;

    /** How long a gas miner whose gas went to the open air waits before it tries again, in s (CONT-181). */
    public static final ModConfigSpec.IntValue GAS_MINER_OPEN_AIR_WAIT;

    /** How far a remote signaler's signal reaches, in blocks; 0 is the whole dimension (CONT-186). */
    public static final ModConfigSpec.IntValue SIGNALER_RANGE;

    /** How long an armed proximity sensor waits before it watches, in s (CONT-187). */
    public static final ModConfigSpec.IntValue PROXIMITY_ARMING_DELAY;

    /** The widest range a proximity sensor's window sets, in blocks (CONT-187). */
    public static final ModConfigSpec.IntValue PROXIMITY_MAX_RANGE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Power: what each device draws while it runs, in FE/t (CONT-065), and the power sources.")
                .push("power");
        VENT_DRAW = builder.comment("Vent").defineInRange("vent", 10, 0, 100_000);
        SCRUBBER_DRAW =
                builder.comment("Scrubber, while it scrubs or siphons").defineInRange("scrubber", 15, 0, 100_000);
        SCRUBBER_PANIC_DRAW = builder.comment("Scrubber, in panic").defineInRange("scrubber_panic", 30, 0, 100_000);
        HEATER_DRAW = builder.comment(
                        "Heater, from its battery while it heats at full power; in proportion below it (CONT-087)")
                .defineInRange("heater", 60, 0, 100_000);
        HEATER_WATTS = builder.comment("The most heat a heater adds, in W, not FE/t (CONT-087)")
                .defineInRange("heater_watts", 5_000, 0, 1_000_000);
        AIR_CONDITIONER_DRAW = builder.comment(
                        "Air conditioner, from its battery while it cools at full power; in proportion below it (CONT-087)")
                .defineInRange("air_conditioner", 60, 0, 100_000);
        AIR_CONDITIONER_WATTS = builder.comment("The most heat an air conditioner takes, in W, not FE/t (CONT-087)")
                .defineInRange("air_conditioner_watts", 5_000, 0, 1_000_000);
        PORTABLE_SCRUBBER_DRAW = builder.comment("Portable scrubber, from its battery while it moves gas (CONT-018)")
                .defineInRange("portable_scrubber", 15, 0, 100_000);
        PORTABLE_PUMP_DRAW = builder.comment("Portable pump, from its battery while it moves gas (CONT-018)")
                .defineInRange("portable_pump", 20, 0, 100_000);
        RECHARGER_DRAW = builder.comment("Recharger, from its controller while it charges (CONT-113)")
                .defineInRange("recharger", 40, 0, 100_000);
        RECHARGER_RATE = builder.comment("What a recharger puts into its battery while it charges, in FE/t (CONT-113)")
                .defineInRange("recharger_rate", 28, 0, 100_000);
        IGNITER_DRAW = builder.comment("Igniter, from its controller while it gets a redstone signal (CONT-105)")
                .defineInRange("igniter", 20, 0, 100_000);
        PRESSURE_PUMP_DRAW = builder.comment("Pressure pump, from its controller while it is on (CONT-065)")
                .defineInRange("pressure_pump", 20, 0, 100_000);
        VOLUME_PUMP_DRAW = builder.comment("Volume pump, from its controller while it is on (CONT-065)")
                .defineInRange("volume_pump", 20, 0, 100_000);
        FILTER_DRAW = builder.comment("Filter, from its controller while it is on (CONT-065)")
                .defineInRange("filter", 20, 0, 100_000);
        MIXER_DRAW = builder.comment("Mixer, from its controller while it is on (CONT-065)")
                .defineInRange("mixer", 20, 0, 100_000);
        ELECTROLYZER_DRAW = builder.comment("Electrolyzer, from its controller while it is on (CONT-065)")
                .defineInRange("electrolyzer", 80, 0, 100_000);
        CONTROLLER_DRAW = builder.comment("Area air controller, its own base draw (CONT-065)")
                .defineInRange("area_air_controller", 5, 0, 100_000);
        FIRE_ALARM_DRAW = builder.comment("Fire alarm, from its controller (CONT-044)")
                .defineInRange("fire_alarm", 1, 0, 100_000);
        AIRLOCK_CONTROLLER_DRAW = builder.comment(
                        "Airlock controller, its own draw, from its controller; its vents' draws add to it (CONT-141)")
                .defineInRange("airlock_controller", 1, 0, 100_000);
        CONTROLLER_BUFFER = builder.comment(
                        "The area air controller's buffer, in FE (CONT-062). It fills from FE blocks next to it.")
                .defineInRange("area_air_controller_buffer", 10_000, 1_000, 1_000_000);
        CENTRAL_DRAW = builder.comment("Central atmospherics controller, its own base draw (CONT-115)")
                .defineInRange("central_controller", 5, 0, 100_000);
        CENTRAL_BUFFER = builder.comment(
                        "The central atmospherics controller's buffer, in FE (CONT-115). It fills from FE blocks next to it.")
                .defineInRange("central_controller_buffer", 100_000, 1_000, 10_000_000);
        GENERATOR_OUTPUT = builder.comment("What the generator makes while it burns, in FE/t (CONT-061)")
                .defineInRange("generator_output", 40, 0, 100_000);
        GENERATOR_BUFFER = builder.comment("The generator's own store, in FE. It stops taking new fuel when full.")
                .defineInRange("generator_buffer", 8_000, 1_000, 30_000);
        GENERATOR_SECONDS_PER_FUEL = builder.comment(
                        "How long one coal burns in the generator, in seconds. Other furnace fuels burn",
                        "for this times their furnace burn time over coal's (CONT-066)")
                .defineInRange("generator_seconds_per_fuel", 80, 1, 1_600);
        builder.pop();

        builder.comment("The thermoelectric generator and the gas turbine (CONT-157 to CONT-164).")
                .push("generators");
        JOULES_PER_FE = builder.comment(
                        "The work, in J, that makes one FE in the TEG and the turbine. Their heat and work",
                        "are reckoned in J, so the energy that leaves the gas as FE is this many J each.",
                        "The default, 2.5, is Mekanism's own rate of its joules to FE.")
                .defineInRange("joules_per_fe", 2.5, 0.01, 1_000_000.0);
        TEG_CARNOT_FRACTION = builder.comment(
                        "The TEG's efficiency as a share of the Carnot efficiency of its two streams' inlet",
                        "temperatures (CONT-158). It never turns more heat into work than the second law allows.")
                .defineInRange("teg_carnot_fraction", 0.5, 0.0, 1.0);
        TEG_BUFFER = builder.comment("The TEG's own FE store. It makes no FE while full.")
                .defineInRange("teg_buffer", 100_000, 1_000, 10_000_000);
        CIRCULATOR_FRICTION_KPA = builder.comment(
                        "The pressure difference a circulator keeps across itself, in kPa: it moves gas only",
                        "while its inlet is more than this above its outlet (CONT-157)")
                .defineInRange("circulator_friction_kpa", 5.0, 0.0, 1_000.0);
        CIRCULATOR_MAX_FLOW = builder.comment(
                        "The most a circulator moves per second, in L at its inlet's conditions (CONT-157)")
                .defineInRange("circulator_max_flow", 500.0, 1.0, 100_000.0);
        TURBINE_MAX_RPM = builder.comment("The gas turbine's top speed, in rpm (CONT-161)")
                .defineInRange("turbine_max_rpm", 10_000, 100, 1_000_000);
        TURBINE_SPIN_ENERGY_KJ = builder.comment(
                        "The energy in the turbine's shaft at its top speed, in kJ: how long it takes to spin",
                        "up and down (CONT-161)")
                .defineInRange("turbine_spin_energy_kj", 500.0, 1.0, 1_000_000.0);
        TURBINE_MAX_OUTPUT = builder.comment(
                        "What the turbine's generator makes at top speed, in FE/t; it makes this times",
                        "(rpm / top speed)^0.8, and never more than the shaft holds (CONT-162)")
                .defineInRange("turbine_max_output", 1_000, 0, 1_000_000);
        TURBINE_EFFICIENCY = builder.comment(
                        "The share of an ideal expansion's work the turbine takes from the gas it lets out (CONT-161)")
                .defineInRange("turbine_efficiency", 0.8, 0.0, 1.0);
        COMPRESSOR_EFFICIENCY = builder.comment(
                        "An ideal compression's work over what the compressor spends; the rest heats the gas too",
                        "(CONT-160)")
                .defineInRange("compressor_efficiency", 0.8, 0.05, 1.0);
        TURBINE_MAX_BURN = builder.comment(
                        "The most a turbine controller's burn rate may be set to, in mol of fuel per second",
                        "(CONT-160, CONT-163)")
                .defineInRange("turbine_max_burn", 4.0, 0.01, 100.0);
        TURBINE_OXIDIZER_FRACTION = builder.comment(
                        "The oxidizer's share of the turbine's chamber that its compressor keeps it at, at least,",
                        "while it has oxidizer to give (CONT-160)")
                .defineInRange("turbine_oxidizer_fraction", 0.2, 0.0, 1.0);
        TURBINE_NOZZLE = builder.comment(
                        "The turbine's nozzle: the mol it lets out each second for each kPa its chamber is above",
                        "its exhaust, never more than would even the two out. Narrower holds the chamber's",
                        "pressure higher, so it takes more work from the gas (CONT-161)")
                .defineInRange("turbine_nozzle_flow", 0.005, 0.0, 10.0);
        TURBINE_MAX_FLOW = builder.comment(
                        "The most the turbine lets out per second, in L at the chamber's conditions (CONT-161)")
                .defineInRange("turbine_max_flow", 2_500.0, 0.0, 1_000_000.0);
        TURBINE_FRICTION = builder.comment(
                        "The share of its shaft's energy the turbine loses each second to friction, as heat",
                        "into its chamber (CONT-161)")
                .defineInRange("turbine_friction", 0.01, 0.0, 1.0);
        TURBINE_BRAKE = builder.comment(
                        "The share of its shaft's energy the turbine's brake takes each second while its engine",
                        "is off, as heat into its chamber; below the slowest its blades show turning, it takes",
                        "the rest. Without it a shaft with nowhere to send its power coasts for minutes (CONT-161)")
                .defineInRange("turbine_brake", 0.3, 0.0, 1.0);
        TURBINE_BUFFER = builder.comment(
                        "A turbine controller's FE store. Its turbine's generator makes no FE while it is full",
                        "(CONT-162)")
                .defineInRange("turbine_buffer", 100_000, 1_000, 10_000_000);
        builder.pop();

        builder.comment("The area air controller (CONT-040, CONT-043).").push("controller");
        BREACH_DROP_KPA = builder.comment(
                        "A fall in its zone's pressure, in kPa within one atmos tick, that sends a controller to danger 2 at once, as a",
                        "breach does (CONT-043). Only for presets with a pressure min2 above 0, and only while the pressure is below min1. 0 turns it off.")
                .defineInRange("breach_drop_kpa", 10.0, 0.0, 1_000.0);
        THERMOSTAT_BAND = builder.comment(
                        "How far, in K, its zone may drift from a controller's target temperature before its vents start",
                        "heating or its scrubbers start cooling (CONT-165). Each stops at the target.")
                .defineInRange("thermostat_band_k", Thermostat.DEFAULT_BAND, 0.0, 100.0);
        builder.pop();

        builder.comment("Pipe overpressure (CONT-005, CONT-108, CONT-168).").push("pipes");
        PIPE_BURST_KPA = builder.comment(
                        "A network more than this over a segment's cell, in kPa, bursts one such segment: it is destroyed",
                        "and the network's gas goes into its cell (CONT-108). 10,132.5 is 100 atm. 0 turns bursting off.")
                .defineInRange("burst_kpa", PipeBurst.THRESHOLD_KPA, 0.0, 1_000_000.0);
        PIPE_LEAK_KPA = builder.comment(
                        "A network more than this over a segment's cell, in kPa, and not bursting, leaks there: one such",
                        "segment, chosen at random, lets gas into its cell until the network is back under (CONT-168).",
                        "0 turns leaks off. 7,599 is 75 atm.")
                .defineInRange("leak_kpa", 0.0, 0.0, 1_000_000.0);
        PIPE_LEAK_RATE = builder.comment(
                        "The share per second of what would even the leaking segment's network out with its cell that a",
                        "leak lets out, so it leaks faster the higher the pressure difference (CONT-168). An open pipe end",
                        "is 0.163.")
                .defineInRange("leak_rate", PipeLeak.DEFAULT_RATE, 0.0, 1.0);
        builder.pop();

        builder.comment("The central atmospherics controller (CONT-116, CONT-122).")
                .push("central_controller");
        CENTRAL_MAX_RANGE = builder.comment(
                        "The most chunks a central controller reaches from its own, as a square of chunks at any height,",
                        "and its range when placed. A player may lower one's range in its GUI (CONT-117).")
                .defineInRange("max_range_chunks", 10, 1, 32);
        CENTRAL_FORCE_LOAD = builder.comment(
                        "Whether a central controller keeps its own chunk and the 8 around it loaded (CONT-122)")
                .define("force_load", true);
        builder.pop();

        builder.comment("How tanks and canisters fail (CONT-038, CONT-102).").push("containers");
        EXPLOSION_POWER_CAP = builder.comment(
                        "The most power a tank's or canister's explosion can have (CONT-109). TNT is 4.")
                .defineInRange("explosion_power_cap", ContainerFailure.DEFAULT_POWER_CAP, 0.0, 64.0);
        builder.pop();

        builder.comment("Breathing gear (CONT-089).").push("gear");
        HARDSUIT_SLOWDOWN = builder.comment(
                        "The share of movement speed each hardsuit piece worn takes away (CONT-089): 0.1 is 10 %,",
                        "so the full suit leaves 60 %.")
                .defineInRange("hardsuit_slowdown", 0.1, 0.0, 0.25);
        builder.pop();

        builder.comment("Radiator pipe (HEAT-020, HEAT-021).").push("radiators");
        RADIATOR_CONDUCTANCE = builder.comment(
                        "Each segment's conductance to the gas of its cell, in W/K (HEAT-020). 0 stops it exchanging",
                        "heat with gas; it still radiates in a vacuum.")
                .defineInRange("conductance", RadiatorMath.DEFAULT_CONDUCTANCE, 0.0, 10_000.0);
        RADIATOR_EMISSIVITY = builder.comment(
                        "Each segment's emissivity in the open air of a vacuum, over 1 m² (HEAT-021). 0 stops it radiating.")
                .defineInRange("emissivity", RadiatorMath.DEFAULT_EMISSIVITY, 0.0, 1.0);
        builder.pop();

        builder.comment("Other mods' fluid tanks and pipes that hold gases as fluids (COMPAT-141, COMPAT-142).")
                .push("fluids");
        FLUID_MIN_PURITY = builder.comment(
                        "A fluid tank holds one fluid, and a pipe holds a mixture. Gas leaves a pipe for another",
                        "mod's fluid pipe or tank only while one gas is at least this share of what the pipe holds,",
                        "by moles. Then that gas alone leaves, and only as much of it as keeps the rest at this",
                        "share. At 0.99 a pipe of air gives nothing and a pipe of oxygen gives all of it. 0 turns",
                        "the rule off: each gas then leaves on its own, as a filter would take it.")
                .defineInRange("min_purity", GasPurity.DEFAULT_MIN_SHARE, 0.0, 1.0);
        builder.pop();
        builder.comment("The creative gas miner (CONT-180, CONT-181).").push("gas_miner");
        GAS_MINER_RATE = builder.comment(
                        "The most gas a miner adds to the room in front of it, in mol/s. It adds less as the",
                        "room nears its target.")
                .defineInRange("rate", 1_000, 1, 100_000);
        GAS_MINER_MAX_TARGET = builder.comment("The highest target pressure a miner can be set to, in kPa")
                .defineInRange("max_target", 10_000.0, 1.0, 30_000.0);
        GAS_MINER_OPEN_AIR_WAIT = builder.comment(
                        "How long a miner whose gas went to the open air waits before it tries again, in seconds")
                .defineInRange("open_air_wait", 10, 1, 600);
        builder.pop();

        builder.comment("A tank transfer valve's remote signaler and proximity sensor (CONT-186, CONT-187).")
                .push("ttv_triggers");
        SIGNALER_RANGE = builder.comment(
                        "How far a remote signaler's signal reaches, in blocks, within its dimension and its",
                        "loaded chunks. 0 is the whole dimension.")
                .defineInRange("signaler_range", 128, 0, 30_000_000);
        PROXIMITY_ARMING_DELAY = builder.comment(
                        "How long a primed TTV with a proximity sensor waits before it watches, in seconds")
                .defineInRange("proximity_arming_delay", 5, 0, 60);
        PROXIMITY_MAX_RANGE = builder.comment("The widest range a proximity sensor can be set to, in blocks")
                .defineInRange("proximity_max_range", 5, 1, 16);
        builder.pop();
        SPEC = builder.build();
    }

    private AtmosServerConfig() {}

    /** Registers the config with the mod. */
    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /** A config value, or its default before the config loads. */
    public static int get(ModConfigSpec.IntValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** A config value, or its default before the config loads. */
    public static double get(ModConfigSpec.DoubleValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }

    /** A config value, or its default before the config loads. */
    public static boolean get(ModConfigSpec.BooleanValue value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
