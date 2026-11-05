package io.github.west3436.zas.atmos.client;

import io.github.west3436.zas.api.client.ClientSounds;
import io.github.west3436.zas.api.client.LoopRequest;
import io.github.west3436.zas.atmos.device.ClientDeviceActivity;
import io.github.west3436.zas.atmos.device.DeviceActivity;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Plays a device's loop while the server says it is working (DATA-018, CLIENT-015). A kind of device
 * gets its sound with one line, {@link #add}. Each client tick this asks for the loop of every working
 * device within {@value #RANGE} blocks whose block is still there, as loud as its level, and the
 * looping sounds' manager does the rest: a device that stops being asked for fades out by itself.
 *
 * <p>It also keeps the client's table to the current level, so it runs whether or not any sound has
 * been added. Client thread only.
 */
public final class DeviceLoops {
    /** How far a working device is heard, in blocks: the owner's decision of 2026-11-05. */
    public static final double RANGE = 8;

    private record Loop(Supplier<? extends Block> block, Supplier<? extends SoundEvent> sound) {}

    /** Added but not looked up yet: the registries aren't ready when a mod's constructor runs. */
    private static final List<Loop> ADDED = new ArrayList<>();

    private static final Map<Block, Supplier<? extends SoundEvent>> BY_BLOCK = new IdentityHashMap<>();

    private DeviceLoops() {}

    /**
     * Gives a kind of device its loop, from the client entry point.
     *
     * @param block its block, as the registry's holder
     * @param sound the loop, as the registry's holder
     */
    public static void add(Supplier<? extends Block> block, Supplier<? extends SoundEvent> sound) {
        ADDED.add(new Loop(block, sound));
    }

    /** Registers the listeners. Called once, from the client entry point. */
    static void register(IEventBus gameBus) {
        gameBus.addListener((ClientTickEvent.Post e) -> tick(Minecraft.getInstance()));
        gameBus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientDeviceActivity.LOCAL.clear());
    }

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        ClientDeviceActivity.LOCAL.enter(level);
        if (level == null || mc.isPaused()) {
            return;
        }
        for (Loop loop : ADDED) {
            BY_BLOCK.put(loop.block().get(), loop.sound());
        }
        ADDED.clear();
        if (BY_BLOCK.isEmpty()) {
            return;
        }
        Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        ClientDeviceActivity.LOCAL.forEach((pos, activity) -> {
            at.set(pos);
            double x = at.getX() + 0.5;
            double y = at.getY() + 0.5;
            double z = at.getZ() + 0.5;
            if (ear.distanceToSqr(x, y, z) > RANGE * RANGE
                    || !level.getChunkSource()
                            .hasChunk(
                                    SectionPos.blockToSectionCoord(at.getX()),
                                    SectionPos.blockToSectionCoord(at.getZ()))) {
                return;
            }
            Supplier<? extends SoundEvent> sound =
                    BY_BLOCK.get(level.getBlockState(at).getBlock());
            if (sound != null) {
                ClientSounds.loop(new LoopRequest(
                        at.immutable(),
                        sound.get(),
                        SoundSource.BLOCKS,
                        x,
                        y,
                        z,
                        DeviceActivity.volume(activity),
                        1f,
                        RANGE));
            }
        });
    }
}
