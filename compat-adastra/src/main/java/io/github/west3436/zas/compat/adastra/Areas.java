package io.github.west3436.zas.compat.adastra;

import earth.terrarium.adastra.api.systems.OxygenApi;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.life.LocalAtmospheres;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ad Astra's own breathable areas, the ones its oxygen distributors keep, as ZAS³'s breathing rules
 * take them in Cooperative mode (SPACE-017): outside zones, living things there are in standard air
 * at Ad Astra's 22 °C. Atmosphere queries still report the planet's ambient there.
 */
final class Areas {
    /** The id the adapter's provider is registered under. */
    static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "ad_astra_distributors");

    /** Ad Astra's distributor temperature, 22 °C, in kelvin. */
    static final double TEMPERATURE = 295.15;

    /** One cell of standard air, 101.325 kPa of 79 % N₂ and 21 % O₂, at the distributor's temperature. */
    static final GasMixture AIR = standardAir(TEMPERATURE);

    private Areas() {}

    /** Registers the areas with ZAS³'s breathing rules. Once, at common setup. */
    static void register() {
        LocalAtmospheres.register(ID, Areas::air);
    }

    /**
     * Whether a position is in a distributor's area: where Ad Astra finds oxygen at a position in a
     * dimension it finds none in. Two map lookups.
     */
    static boolean distributed(Level level, BlockPos pos) {
        return !OxygenApi.API.hasOxygen(level) && OxygenApi.API.hasOxygen(level, pos);
    }

    /** The air at a point outside zones: a distributor's, in Cooperative mode, or null. */
    static @Nullable GasMixture air(ServerLevel level, Vec3 point) {
        return Mode.current() == Mode.COOPERATIVE && distributed(level, BlockPos.containing(point)) ? AIR : null;
    }

    private static GasMixture standardAir(double temperature) {
        double moles = StandardAir.PRESSURE * StandardAir.CELL_VOLUME / (GasMixture.GAS_CONSTANT * temperature);
        return new GasMixture(
                Map.of(
                        StandardAir.NITROGEN, moles * StandardAir.NITROGEN_FRACTION,
                        StandardAir.OXYGEN, moles * StandardAir.OXYGEN_FRACTION),
                temperature,
                StandardAir.CELL_VOLUME);
    }
}
