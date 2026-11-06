package io.github.west3436.zas.geometry;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.ISealProvider;
import io.github.west3436.zas.api.capability.SealGeometry;
import io.github.west3436.zas.engine.Face;
import io.github.west3436.zas.engine.Geometry;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The blocks with an {@link ISealProvider} (API-023), and the one place ZAS³ calls one: {@link
 * #ask}.
 */
public final class SealProviders {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Set<Block> blocks = Set.of();

    private SealProviders() {}

    /** Registers the listener on the mod bus. */
    public static void register(IEventBus modBus) {
        // Lowest, so every mod's registrations are in.
        modBus.addListener(EventPriority.LOWEST, SealProviders::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        Set<Block> found = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (event.isBlockRegistered(ISealProvider.BLOCK, block)) {
                found.add(block);
            }
        }
        blocks = Set.copyOf(found);
    }

    /** The blocks with a provider. Any thread. */
    static Set<Block> blocks() {
        return blocks;
    }

    /**
     * Asks a block's provider for its geometry. Every call of third-party seal code goes through
     * here, on the server thread (API-028, D-30).
     *
     * @param blockEntity the block's block entity, or null if it has none
     * @return its answer; empty if it has no provider at this position or leaves the block's
     *     geometry as it is; null if it threw, which is no answer at all (NFR-013)
     */
    static @Nullable Optional<SealGeometry> ask(
            ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        try {
            ISealProvider provider = level.getCapability(ISealProvider.BLOCK, pos, state, blockEntity, null);
            return provider == null ? Optional.empty() : provider.geometry();
        } catch (Exception | LinkageError e) {
            String source = "Seal provider of " + BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (ThirdPartyFaults.shouldLog(source)) {
                LOGGER.error(
                        "{} threw at {}; skipped for the atmos tick (logged at most once a minute)", source, pos, e);
            }
            return null;
        }
    }

    /**
     * The engine's geometry for a provider's answer: a single cell (API-023), with the heat and fire
     * data of the block's own geometry in the table.
     */
    static Geometry geometry(SealGeometry seal, Geometry base) {
        Set<Face> sealed = EnumSet.noneOf(Face.class);
        for (Direction d : seal.sealedFaces()) {
            sealed.add(GeometryDerivation.face(d));
        }
        int flags = Geometry.FLAG_POSITIONAL;
        for (Direction d : seal.boundaryFaces()) {
            flags |= Geometry.boundaryFlag(GeometryDerivation.face(d));
        }
        return Geometry.declared(sealed, seal.volume(), flags).withDataOf(base);
    }
}
