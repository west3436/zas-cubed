package io.github.west3436.zas.atmos.compat.jade;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.holder.TankHolderBlockEntity;
import io.github.west3436.zas.atmos.tank.LargeTankBlock;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * The Jade adapter's entry point (COMPAT-030 to COMPAT-032). Jade finds it by its annotation and
 * loads it once every mod is set up; nothing in ZAS³ names this class or any other in its package,
 * so without Jade none of them is loaded (COMPAT-001, NFR-022).
 *
 * <p>It registers one readout: a server part, which reads the block entity looked at, and a client
 * part, which draws what the server sent. Jade asks the server for a block only while the player
 * looks at it, a few times a second, and carries the answer in its own packet. Only the content
 * mod's own block entities have a server part, so plain air and every other block get no reading
 * (COMPAT-032).
 */
@WailaPlugin
public final class ZasJadePlugin implements IWailaPlugin {
    static {
        // For the check that nothing here runs without Jade.
        JadeGate.notePluginStarted();
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        if (!on()) {
            return;
        }
        // Every device but the tank holder is an AtmosDevice. Those with nothing to read are never
        // asked about: see ReadoutData.shouldRequestData.
        registration.registerBlockDataProvider(ReadoutData.INSTANCE, AtmosDevice.class);
        registration.registerBlockDataProvider(ReadoutData.INSTANCE, TankHolderBlockEntity.class);
        // A large tank's upper half has no block entity, so it is found by its block.
        registration.registerBlockDataProvider(ReadoutData.INSTANCE, LargeTankBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        if (!on()) {
            return;
        }
        // On every block: it draws only where the server sent a reading, so it needs no list of
        // the devices' block classes.
        registration.registerBlockComponent(ReadoutTooltip.INSTANCE, Block.class);
    }

    /** Whether there is anything to read, and a Jade the adapter supports to show it. */
    private static boolean on() {
        return ZasAtmosMod.contentEnabled() && JadeGate.open();
    }
}
