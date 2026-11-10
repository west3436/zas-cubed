package io.github.west3436.zas.compat.mekanism.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.west3436.zas.compat.mekanism.MekanismAmbient;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import java.util.function.Supplier;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Has a Mekanism heat block read the temperature of what stands round it, not of its biome
 * (MEK-041), while the server's {@code [heat] ambient_from_rooms} is on.
 *
 * <p>Changes what {@code CachedAmbientTemperature#getTemperature} returns for a side: {@link
 * MekanismAmbient#temperature} answers with the temperature of the zone that side faces, or of the
 * open air there as ZAS³ has it, and with Mekanism's own value for any other side. Mekanism's
 * value is always worked out first and is what is returned with the setting off, on a client, and
 * for the reading that names no side, which is the block's own starting temperature and the cold
 * end of a Heat Generator.
 *
 * <p><b>Not for a multiblock's blocks.</b> A reactor's port, a boiler's casing and an evaporation
 * plant's valve read through this class too, for heat that is their multiblock's. Those keep
 * Mekanism's own value: a multiblock's loss to its environment is worked out in each multiblock's
 * own code, which the adapter doesn't touch. Whether the block is one is looked up once.
 *
 * <p>No public hook serves: Mekanism reads the biome's temperature through a static helper, once
 * for each side, and keeps it in this class's private array; its API has no ambient temperature a
 * mod can supply.
 */
@Mixin(value = CachedAmbientTemperature.class, remap = false)
abstract class RoomAmbientMixin {
    @Shadow
    @Final
    private Supplier<Level> worldSupplier;

    @Shadow
    @Final
    private Supplier<BlockPos> positionSupplier;

    /** Whether the block this reads for is one of a multiblock's: 0 not looked up yet, 1 no, 2 yes. */
    @Unique
    private byte zas$ofAMultiblock;

    @ModifyReturnValue(method = "getTemperature", at = @At("RETURN"))
    private double zas$roomTemperature(double mekanisms, @Nullable Direction side) {
        if (side == null || !MekanismSettings.ambientFromRooms()) {
            return mekanisms;
        }
        if (!(worldSupplier.get() instanceof ServerLevel level)
                || !level.getServer().isSameThread()) {
            return mekanisms;
        }
        BlockPos pos = positionSupplier.get();
        if (zas$ofAMultiblock == 0) {
            BlockEntity block = level.isLoaded(pos) ? level.getBlockEntity(pos) : null;
            if (block == null) {
                return mekanisms;
            }
            zas$ofAMultiblock = block instanceof TileEntityMultiblock<?> ? (byte) 2 : 1;
        }
        return zas$ofAMultiblock == 2 ? mekanisms : MekanismAmbient.temperature(level, pos, side, mekanisms);
    }
}
