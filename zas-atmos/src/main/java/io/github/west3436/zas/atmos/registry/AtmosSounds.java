package io.github.west3436.zas.atmos.registry;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
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
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_FAR = event("blast.far");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLAST_SMALL_FAR = event("blast.small_far");

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

    private AtmosSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> event(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ZasAtmosMod.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
