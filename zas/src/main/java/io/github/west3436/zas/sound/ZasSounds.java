package io.github.west3436.zas.sound;

import io.github.west3436.zas.ZasMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The framework's sound events, named for what happens. The files, pitches and volumes behind them
 * are in {@code assets/zas/sounds.json}, so a sound can be swapped with no code change; which vg
 * file each one is and why are in the archived sounds note:
 * https://github.com/west3436/zas-cubed/blob/128e69fe9056e122f5cc8b095d8cca84e51592e4/docs/research/vg-sounds.md
 */
public final class ZasSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, ZasMod.MOD_ID);

    /** The hiss at an opening with air moving through it (CLIENT-010). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRFLOW_HISS = event("airflow.hiss");

    /** Wind at an opening whose pressure difference moves light things (FLOW-011). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRFLOW_LIGHT = event("airflow.light");

    /** Wind at an opening whose pressure difference moves mobs and players (FLOW-011). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRFLOW_MOB = event("airflow.mob");

    /** Wind at an opening whose pressure difference moves heavy things (FLOW-011). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRFLOW_HEAVY = event("airflow.heavy");

    /** A gas fire burning: a loop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_LOOP = event("fire.loop");

    /** Gas catching fire: the whoomp. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FIRE_IGNITE = event("fire.ignite");

    private ZasSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> event(String name) {
        return SOUNDS.register(
                name,
                () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
