package io.github.west3436.zas.atmos.compat.jade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.WailaCommonRegistration;

/**
 * What Jade's server answers for a block, as far as the adapter is concerned: the tests ask Jade
 * itself which providers it holds for the block, as Jade does when a client asks about one, and run
 * the adapter's among them.
 *
 * <p>It uses two of Jade's own classes that aren't in its API: the registration, to see what Jade
 * really holds once it has loaded the plugin, and the builder of the accessor a provider is handed.
 * Both read the same in every Jade the adapter supports. Only the tests use them; the adapter
 * itself stays within the API (COMPAT-003).
 */
final class JadeProbe {
    /** The most bytes a reading may be: a few of these a second go to each player looking. */
    static final int MOST_BYTES = 200;

    /** The path of the id of Jade's own provider of a block's name. */
    private static final String NAME_PROVIDER = "object_name";

    private JadeProbe() {}

    /** Whether Jade holds the adapter's server part for a block. */
    static boolean registered(BlockState state, @Nullable BlockEntity be) {
        for (IServerDataProvider<BlockAccessor> provider :
                WailaCommonRegistration.instance().getBlockNBTProviders(state.getBlock(), be)) {
            if (provider.getUid().equals(ReadoutData.UID)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a client looking at a block would ask the server about it for the adapter. */
    static boolean asks(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        return registered(state, be) && ReadoutData.INSTANCE.shouldRequestData(accessor(level, pos, state, be));
    }

    /** What the adapter adds to Jade's answer for a block: empty if Jade doesn't hold it for the block. */
    static CompoundTag data(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag data = new CompoundTag();
        BlockAccessor accessor = accessor(level, pos, state, be);
        for (IServerDataProvider<BlockAccessor> provider :
                WailaCommonRegistration.instance().getBlockNBTProviders(state.getBlock(), be)) {
            if (provider.getUid().equals(ReadoutData.UID)) {
                provider.appendServerData(data, accessor);
            }
        }
        return data;
    }

    /**
     * The reading the server sends for a block.
     *
     * @throws GameTestAssertException if it sends none, or bytes that aren't a reading, or too many
     */
    static Reading reading(ServerLevel level, BlockPos pos) {
        CompoundTag data = data(level, pos);
        String what = level.getBlockState(pos).getBlock().getName().getString() + " at " + pos.toShortString();
        if (!data.contains(ReadoutData.KEY, Tag.TAG_BYTE_ARRAY)) {
            throw new GameTestAssertException("no reading for the " + what);
        }
        byte[] bytes = data.getByteArray(ReadoutData.KEY);
        if (bytes.length > MOST_BYTES || data.size() != 1) {
            throw new GameTestAssertException(bytes.length + " bytes in " + data.size() + " tags for the " + what);
        }
        return ReadingCodec.decode(bytes)
                .orElseThrow(() -> new GameTestAssertException("the bytes for the " + what + " aren't a reading"));
    }

    /**
     * What Jade's server sends for its own name line of a block: the answer of whichever of its
     * providers names things, which the adapter has no part in. Empty if it sends none, and Jade
     * shows the block's own name.
     */
    static CompoundTag name(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);
        CompoundTag data = new CompoundTag();
        BlockAccessor accessor = accessor(level, pos, state, be);
        boolean found = false;
        for (IServerDataProvider<BlockAccessor> provider :
                WailaCommonRegistration.instance().getBlockNBTProviders(state.getBlock(), be)) {
            if (provider.getUid().getPath().equals(NAME_PROVIDER)) {
                found = true;
                if (provider.shouldRequestData(accessor)) {
                    provider.appendServerData(data, accessor);
                }
            }
        }
        if (!found) {
            throw new GameTestAssertException("Jade holds no name provider for the block at " + pos.toShortString());
        }
        // Older versions write their answer through the accessor, into a copy of the data it holds.
        return data.merge(accessor.getServerData());
    }

    /** Whether the server sends nothing for a block. */
    static boolean silent(ServerLevel level, BlockPos pos) {
        return data(level, pos).isEmpty();
    }

    private static BlockAccessor accessor(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
        return new BlockAccessorImpl.Builder()
                .level(level)
                .player(FakePlayerFactory.getMinecraft(level))
                .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                .blockState(state)
                .blockEntity(() -> be)
                .build();
    }
}
