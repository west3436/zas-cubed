package io.github.west3436.zas.compat.ae2;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import io.github.west3436.zas.api.capability.GasHandlerView;
import io.github.west3436.zas.api.capability.IGasHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/**
 * AE2's import, export and storage buses on a block that provides {@link IGasHandler} (COMPAT-073):
 * each bus asks for the capability on the face it touches, so it works on any mod's block that offers
 * it there. ZAS³'s own pipes, pipe caps, connector ports and large tanks offer it on a face that meets
 * a block that isn't a pipe part, so a bus can touch any of them. A handler that is a {@link
 * GasHandlerView} of another mod's storage is left alone.
 *
 * <p>Every move goes through {@link GasHandlerStorage}, which rounds without making or losing gas
 * (COMPAT-074).
 */
final class GasBuses {
    private GasBuses() {}

    /** The storage on the face a bus touches, its handler looked up anew each time it is used. */
    private static GasHandlerStorage storage(
            ServerLevel level, BlockPos pos, Direction side, boolean extractableOnly, @Nullable Runnable changed) {
        Supplier<@Nullable IGasHandler> handler = own(level, pos, side);
        return new GasHandlerStorage(handler, level, pos, extractableOnly, changed);
    }

    /** A storage bus's view of the block. */
    static ExternalStorageStrategy external(ServerLevel level, BlockPos pos, Direction side) {
        Supplier<@Nullable IGasHandler> handler = own(level, pos, side);
        // The storage bus asks for a new wrapper when the block changes, and keeps none for a block
        // without the capability.
        return (extractableOnly, changed) ->
                handler.get() == null ? null : new GasHandlerStorage(handler, level, pos, extractableOnly, changed);
    }

    /**
     * The block's gas handler on a face, looked up anew each time, unless it is a view of another
     * mod's storage ({@link GasHandlerView}): a fluid tank or a Mekanism chemical tank as gas. AE2
     * reaches those through their own kind, its fluid buses or Applied Mekanistics', and through
     * the view too it would count the same gas twice.
     */
    static Supplier<@Nullable IGasHandler> own(ServerLevel level, BlockPos pos, Direction side) {
        BlockCapabilityCache<IGasHandler, @Nullable Direction> cache =
                BlockCapabilityCache.create(IGasHandler.BLOCK, level, pos, side);
        return () -> {
            IGasHandler handler = cache.getCapability();
            return handler instanceof GasHandlerView ? null : handler;
        };
    }

    /** An import bus: gas out of the block into ME storage, each gas on its own. */
    static StackImportStrategy importing(ServerLevel level, BlockPos pos, Direction side) {
        GasHandlerStorage block = storage(level, pos, side, false, null);
        return context -> {
            if (!context.isKeyTypeEnabled(GasKeyType.TYPE) || !block.present()) {
                return false;
            }
            MEStorage network = context.getInternalStorage().getInventory();
            IActionSource source = context.getActionSource();
            long room = (long) context.getOperationsRemaining() * GasKeyType.PER_OPERATION;
            for (GasKey key : held(block)) {
                if (room <= 0) {
                    break;
                }
                if (context.isInFilter(key) == context.isInverted()) {
                    continue;
                }
                long fits = network.insert(key, room, Actionable.SIMULATE, source);
                long taken = block.drain(key, fits, Actionable.MODULATE);
                if (taken <= 0) {
                    continue;
                }
                long stored = network.insert(key, taken, Actionable.MODULATE, source);
                if (stored < taken) {
                    // ME storage took less than it said it would: the rest goes back where it came from.
                    long back = block.fill(key, taken - stored, Actionable.MODULATE, block.temperature());
                    if (back < taken - stored) {
                        GasRelease.release(
                                level,
                                pos,
                                GasHandlerStorage.parcel(
                                        key.gas(), GasUnits.moles(taken - stored - back), block.temperature()),
                                "gas ME storage refused");
                    }
                }
                context.reduceOperationsRemaining(Math.max(1, stored / GasKeyType.PER_OPERATION));
                room -= stored;
            }
            return false;
        };
    }

    /** An export bus: the gas it is set to, out of ME storage into the block. */
    static StackExportStrategy exporting(ServerLevel level, BlockPos pos, Direction side) {
        GasHandlerStorage block = storage(level, pos, side, false, null);
        return new StackExportStrategy() {
            @Override
            public long transfer(StackTransferContext context, AEKey what, long amount) {
                if (!(what instanceof GasKey key) || !block.present()) {
                    return 0;
                }
                MEStorage network = context.getInternalStorage().getInventory();
                IActionSource source = context.getActionSource();
                long available = StorageHelper.poweredExtraction(
                        context.getEnergySource(), network, key, amount, source, Actionable.SIMULATE);
                long fits = block.insert(key, available, Actionable.SIMULATE, source);
                if (fits <= 0) {
                    return 0;
                }
                long taken = StorageHelper.poweredExtraction(
                        context.getEnergySource(), network, key, fits, source, Actionable.MODULATE);
                long given = block.insert(key, taken, Actionable.MODULATE, source);
                if (given < taken) {
                    // The block took less than it said it would: the rest goes back into ME storage.
                    long back = network.insert(key, taken - given, Actionable.MODULATE, source);
                    if (back < taken - given) {
                        GasRelease.release(
                                level,
                                pos,
                                GasHandlerStorage.parcel(
                                        key.gas(),
                                        GasUnits.moles(taken - given - back),
                                        Ae2Settings.leavingTemperature()),
                                "gas neither a block nor ME storage took");
                    }
                }
                return given;
            }

            @Override
            public long push(AEKey what, long amount, Actionable mode) {
                return what instanceof GasKey key ? block.insert(key, amount, mode, IActionSource.empty()) : 0;
            }
        };
    }

    /** The keys of the gases the block holds a whole unit of, in id order. */
    private static List<GasKey> held(GasHandlerStorage block) {
        KeyCounter counter = new KeyCounter();
        block.getAvailableStacks(counter);
        List<GasKey> keys = new ArrayList<>();
        for (var entry : counter) {
            if (entry.getKey() instanceof GasKey key) {
                keys.add(key);
            }
        }
        keys.sort((a, b) -> a.gas().compareTo(b.gas()));
        return keys;
    }
}
