package io.github.west3436.zas.compat.mekanism.mixin;

import io.github.west3436.zas.compat.mekanism.MekanismAmbient;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import mekanism.api.heat.IMekanismHeatHandler;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Gives a zone the heat a Mekanism heat block loses to its environment through a side that faces
 * it (MEK-041), while the server's {@code [heat] ambient_from_rooms} is on.
 *
 * <p>Changes the heat that {@code ITileHeatHandler#simulateEnvironment} hands the block for each
 * side, through {@link MekanismAmbient#environment}. A loss is handed on as it is, and the zone on
 * that side is given it at the adapter's next step. A gain, which a block colder than its zone
 * would make, becomes 0: the zone is asked for the heat, and the block gets what the zone
 * delivers. Through a side that faces no zone the heat is as Mekanism has it.
 *
 * <p><b>Which blocks.</b> A Mekanism block entity that is no part of a multiblock, and a
 * Thermodynamic Conductor, whose heat handler is its transmitter. A multiblock's port or valve
 * runs this method too, for heat that is its multiblock's; those are left alone, as is anything
 * else that implements the interface.
 *
 * <p>No public hook serves: the loss is worked out and taken from the block inside this one
 * default method, and nothing is told of it; Mekanism's API has no event or callback for heat
 * that leaves to the environment.
 */
@Mixin(value = ITileHeatHandler.class, remap = false)
interface RoomLossMixin {
    @ModifyArg(
            method = "simulateEnvironment",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lmekanism/common/capabilities/heat/ITileHeatHandler;handleHeat(DLnet/minecraft/core/Direction;)V"),
            index = 0)
    private double zas$toTheRoom(double heat, Direction side) {
        if (heat == 0 || !MekanismSettings.ambientFromRooms()) {
            return heat;
        }
        Object self = this;
        BlockEntity owner;
        if (self instanceof TileEntityMekanism block) {
            if (block instanceof TileEntityMultiblock<?>) {
                return heat;
            }
            owner = block;
        } else if (self instanceof Transmitter<?, ?, ?> transmitter) {
            owner = transmitter.getTransmitterTile();
        } else {
            return heat;
        }
        if (owner != null
                && !owner.isRemoved()
                && owner.getLevel() instanceof ServerLevel level
                && level.getServer().isSameThread()) {
            return MekanismAmbient.environment(level, owner, side, heat, (IMekanismHeatHandler) self);
        }
        return heat;
    }
}
