package io.github.west3436.zas.compat.adastra;

import earth.terrarium.adastra.api.systems.OxygenApi;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.fire.Fires;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Torches, lanterns and the other blocks of {@code #ad_astra:destroyed_in_space} can be placed in a
 * zone whose air lets fire burn (SPACE-015, D-49).
 *
 * <p>Ad Astra cancels the placement of such a block where it finds no oxygen at the position, and
 * it doesn't know a zone's air. So for the length of the placement event the adapter tells Ad Astra
 * there is oxygen at that one position, through Ad Astra's API, and takes it back once every
 * listener has run. Nothing is ever un-cancelled, so another mod's refusal of the placement
 * stands.
 *
 * <p>Ad Astra saves a position whose oxygen differs from its planet's, so what is lent must always
 * come back. If a placement event never reaches its end, because a listener threw, the end of the
 * server tick takes it back instead.
 */
final class Torches {
    private static final TagKey<Block> DESTROYED_IN_SPACE =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("ad_astra", "destroyed_in_space"));

    /** The positions that have Ad Astra's oxygen only until their placement event ends. Server thread. */
    private static final Set<GlobalPos> LENT = new HashSet<>();

    private Torches() {}

    /**
     * Registers both ends: before every other listener, and after them all, cancelled or not. And the
     * end of each server tick, for an event that didn't get that far.
     */
    static void register(IEventBus gameBus) {
        gameBus.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, Torches::before);
        gameBus.addListener(EventPriority.LOWEST, true, BlockEvent.EntityPlaceEvent.class, Torches::after);
        gameBus.addListener(ServerTickEvent.Post.class, Torches::endOfTick);
    }

    private static void before(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) {
            return;
        }
        BlockState placed = event.getPlacedBlock();
        // What Ad Astra refuses: blocks of the tag that don't tick randomly. The ones that do, it
        // places and leaves to its environment tick (SPACE-014).
        if (!placed.is(DESTROYED_IN_SPACE) || placed.isRandomlyTicking()) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        if (OxygenApi.API.hasOxygen(level, pos)) {
            return;
        }
        AtmosphereSample air = Atmospheres.at(level, pos);
        boolean zoned = air.state() == AtmosphereState.ZONED || air.state() == AtmosphereState.PAUSED;
        if (zoned && Fires.canBurnIn(air.mixture()) && LENT.add(GlobalPos.of(level.dimension(), pos))) {
            OxygenApi.API.setOxygen(level, pos, true);
        }
    }

    private static void after(BlockEvent.EntityPlaceEvent event) {
        if (LENT.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos().immutable();
        if (LENT.remove(GlobalPos.of(level.dimension(), pos))) {
            OxygenApi.API.setOxygen(level, pos, false);
        }
    }

    /** Takes back what a placement event that never ended left lent. Nearly always one empty check. */
    private static void endOfTick(ServerTickEvent.Post event) {
        if (LENT.isEmpty()) {
            return;
        }
        for (GlobalPos lent : LENT) {
            ServerLevel level = event.getServer().getLevel(lent.dimension());
            if (level != null) {
                OxygenApi.API.setOxygen(level, lent.pos(), false);
            }
        }
        LENT.clear();
    }
}
