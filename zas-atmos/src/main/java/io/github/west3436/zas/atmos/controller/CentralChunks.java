package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;

/**
 * The chunks a central atmospherics controller keeps loaded (CONT-122): its own and the 8 around it,
 * while {@code [central_controller] force_load} is true. They load and tick as {@code /forceload}'s
 * chunks do: block entities and entities tick, with no random ticks unless a player is near.
 *
 * <p>NeoForge keeps the tickets, owned by the central controller's position, and saves them with the
 * level. When the level loads, it asks {@link #validate} which to keep: none while the option is
 * false, and only those of a central controller that is still there.
 */
public final class CentralChunks {
    private static final TicketController TICKETS =
            new TicketController(ZasAtmosMod.id("central_atmos_controller"), CentralChunks::validate);

    private CentralChunks() {}

    /** Registers the ticket controller. Called once, from the mod constructor. */
    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterTicketControllersEvent e) -> e.register(TICKETS));
    }

    /** Whether central controllers keep their chunks loaded now: {@code [central_controller] force_load}. */
    public static boolean enabled() {
        return AtmosServerConfig.get(AtmosServerConfig.CENTRAL_FORCE_LOAD);
    }

    /**
     * Forces the 3 × 3 chunks around a central controller, or releases them. Either is idempotent.
     *
     * @param owner the central controller's position
     */
    static void hold(ServerLevel level, BlockPos owner, boolean on) {
        int cx = SectionPos.blockToSectionCoord(owner.getX());
        int cz = SectionPos.blockToSectionCoord(owner.getZ());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                TICKETS.forceChunk(level, owner, cx + dx, cz + dz, on, false);
            }
        }
    }

    private static void validate(ServerLevel level, TicketHelper tickets) {
        boolean on = enabled();
        for (BlockPos owner : tickets.getBlockTickets().keySet()) {
            if (!on || !(level.getBlockEntity(owner) instanceof CentralControllerBlockEntity)) {
                tickets.removeAllTickets(owner);
            }
        }
    }
}
