package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The content mod's sound events, named for what happens. The files, pitches and volumes behind
 * them are in {@code assets/zas_atmos/sounds.json}, so a sound can be swapped with no code change.
 * Devices share vg's few sounds at different pitches there; which file each one is and why are in
 * {@code docs/research/vg-sounds.md}.
 */
public final class AtmosSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ZasAtmosMod.MOD_ID);

    // Gas blasts
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_NEAR = event("blast.near");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL = event("blast.small");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_FAR = event("blast.far", 256);
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_FAR = event("blast.small_far", 128);

    // Running loops: quiet, about 8 blocks' range, and only while a device moves gas
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_RUN = event("device.pump.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLUME_PUMP_RUN = event("device.volume_pump.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> FILTER_RUN = event("device.filter.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_RUN = event("device.mixer.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_RUN = event("device.vent.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> PASSIVE_VENT_RUN = event("device.passive_vent.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCRUBBER_RUN = event("device.scrubber.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANISTER_RUN = event("device.canister.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_LEAK = event("device.pipe.leak");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEATER_RUN = event("device.heater.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_CONDITIONER_RUN =
            event("device.air_conditioner.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTABLE_PUMP_RUN = event("device.portable_pump.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTABLE_SCRUBBER_RUN =
            event("device.portable_scrubber.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECHARGER_RUN = event("device.recharger.run");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_RUN = event("device.generator.run");

    // On, off and event sounds
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_ON = event("device.pump.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> PUMP_OFF = event("device.pump.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLUME_PUMP_ON = event("device.volume_pump.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> VOLUME_PUMP_OFF = event("device.volume_pump.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_ON = event("device.vent.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> VENT_OFF = event("device.vent.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCRUBBER_ON = event("device.scrubber.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCRUBBER_OFF = event("device.scrubber.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> FILTER_ON = event("device.filter.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> FILTER_OFF = event("device.filter.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_ON = event("device.mixer.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> MIXER_OFF = event("device.mixer.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEATER_ON = event("device.heater.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> HEATER_OFF = event("device.heater.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_CONDITIONER_ON = event("device.air_conditioner.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_CONDITIONER_OFF =
            event("device.air_conditioner.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTABLE_PUMP_ON = event("device.portable_pump.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTABLE_PUMP_OFF = event("device.portable_pump.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTABLE_SCRUBBER_ON =
            event("device.portable_scrubber.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORTABLE_SCRUBBER_OFF =
            event("device.portable_scrubber.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_ON = event("device.generator.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> GENERATOR_OFF = event("device.generator.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROLLER_ON = event("device.controller.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROLLER_OFF = event("device.controller.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECHARGER_ON = event("device.recharger.on");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECHARGER_OFF = event("device.recharger.off");
    public static final DeferredHolder<SoundEvent, SoundEvent> VALVE_OPEN = event("device.valve.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> VALVE_CLOSE = event("device.valve.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> REDSTONE_VALVE_OPEN =
            event("device.redstone_valve.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> REDSTONE_VALVE_CLOSE =
            event("device.redstone_valve.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANISTER_OPEN = event("device.canister.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANISTER_CLOSE = event("device.canister.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORT_CONNECT = event("device.port.connect");
    public static final DeferredHolder<SoundEvent, SoundEvent> PORT_DISCONNECT = event("device.port.disconnect");
    public static final DeferredHolder<SoundEvent, SoundEvent> PIPE_BURST = event("device.pipe.burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTAINER_LEAK = event("device.container.leak");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTAINER_BURST = event("device.container.burst");
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_OPEN = event("device.tank.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_CLOSE = event("device.tank.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> TANK_LOW = event("device.tank.low");
    public static final DeferredHolder<SoundEvent, SoundEvent> IGNITER_CLICK = event("device.igniter.click");
    public static final DeferredHolder<SoundEvent, SoundEvent> IGNITER_SPARK = event("device.igniter.spark");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROLLER_WARNING = event("device.controller.warning");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROLLER_DANGER =
            event("device.controller.danger", 24);
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_ALARM_ALARM = event("device.fire_alarm.alarm", 32);
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_ALARM_RESET = event("device.fire_alarm.reset");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRELOCK_OPEN = event("device.firelock.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRELOCK_CLOSE = event("device.firelock.close");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRELOCK_FORCE = event("device.firelock.force");
    public static final DeferredHolder<SoundEvent, SoundEvent> RECHARGER_DONE = event("device.recharger.done");
    public static final DeferredHolder<SoundEvent, SoundEvent> TTV_ARM = event("device.ttv.arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> TTV_TICK = event("device.ttv.tick");
    public static final DeferredHolder<SoundEvent, SoundEvent> TTV_OPEN = event("device.ttv.open");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTINGUISHER_SPRAY = event("device.extinguisher.spray");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTINGUISHER_REFILL =
            event("device.extinguisher.refill");
    public static final DeferredHolder<SoundEvent, SoundEvent> EXTINGUISHER_EMPTY = event("device.extinguisher.empty");

    // The wrench turning or removing a part (CONT-035)
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH = event("device.wrench.use");

    private AtmosSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> event(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ZasAtmosMod.id(name)));
    }

    /**
     * An event heard further than 16 blocks. A server only sends a sound out to its event's range,
     * so this must match the {@code attenuation_distance} in {@code sounds.json} (CLIENT-020).
     */
    private static DeferredHolder<SoundEvent, SoundEvent> event(String name, float range) {
        return SOUNDS.register(name, () -> SoundEvent.createFixedRangeEvent(ZasAtmosMod.id(name), range));
    }

    /**
     * Plays an event at a block for everyone in its range, on the Blocks slider, as {@code sounds.json}
     * sets its volume and pitch. On the server: a client's call plays nothing.
     *
     * @param level the level
     * @param pos the block
     * @param event the event
     */
    public static void play(Level level, BlockPos pos, Supplier<SoundEvent> event) {
        level.playSound(null, pos, event.get(), SoundSource.BLOCKS, 1f, 1f);
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
