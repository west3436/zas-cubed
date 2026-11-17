package io.github.west3436.zas.compat.sable.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import io.github.west3436.zas.compat.sable.PlotAir;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps each plot chunk's atmosphere in the tag Sable saves the chunk in (COMPAT-100), under the key
 * a world chunk keeps it under, so that the load event Sable fires with that tag hands it back to
 * ZAS³ unchanged.
 *
 * <p>No public hook serves. Sable saves a sub-level's plot with the sub-level, in its own {@code
 * sublevels} files, whenever the level saves and whenever the sub-level unloads; it fires no {@code
 * ChunkDataEvent.Save} for its chunks, and its API has no event before a save. The one place to
 * write per chunk without one is {@code ServerLevelPlot.save}'s result, the same in every Sable 2
 * build. COMPAT-100's GameTests fail without it.
 */
@Mixin(value = ServerLevelPlot.class, remap = false)
abstract class PlotSaveMixin {
    @ModifyReturnValue(method = "save", at = @At("RETURN"))
    private CompoundTag zas$saveTheAir(CompoundTag saved) {
        return PlotAir.saving((ServerLevelPlot) (Object) this, saved);
    }
}
