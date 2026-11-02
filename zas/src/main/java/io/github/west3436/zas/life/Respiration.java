package io.github.west3436.zas.life;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.MutationOutcome;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.provider.PointResolver;
import io.github.west3436.zas.simulation.AtmosService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Respiration (LIFE-005, LIFE-042): breathers inside a zone turn its O2 into the same moles of CO2.
 *
 * <p>Each breath check adds the O2 a breather used since its last check to its zone's draw. Once
 * per atmos period the draws are sent, one per zone: the O2 is removed from the zone, and when the
 * removal is applied, the moles actually removed come back as CO2 at the zone's temperature. So a
 * zone short of O2 gets only as much CO2 as it lost. Both changes are recorded under the ledger's
 * {@code LIFE} category, and neither ever forms a zone (CORE-051). Breathing outside a zone, in
 * the ambient, a paused zone or an unresolved region, changes nothing (CORE-038).
 *
 * <p>Server thread.
 */
public final class Respiration {
    /** Who changes the gas, in ledgers and logs. */
    public static final SourceId SOURCE = SourceId.of(ZasMod.MOD_ID, "respiration");

    /** CO2, which breathers exhale. */
    public static final ResourceLocation CARBON_DIOXIDE =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "carbon_dioxide");

    /** One zone's O2 draw since the last flush, at the last point a breather drew it from. */
    private static final class Draw {
        Vec3 point;
        double moles;
    }

    /** Per dimension, per zone id, in the order breathers drew. */
    private static final Map<ResourceKey<Level>, Map<Long, Draw>> draws = new LinkedHashMap<>();

    private Respiration() {}

    /**
     * Adds O2 a breather in a zone used.
     *
     * @param level its level
     * @param zone the id of the zone at its eyes
     * @param eyes its eye position
     * @param moles the O2 it used
     */
    static void breathe(ServerLevel level, long zone, Vec3 eyes, double moles) {
        if (!(moles > 0)) {
            return;
        }
        Draw d = draws.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>())
                .computeIfAbsent(zone, k -> new Draw());
        d.point = eyes;
        d.moles += moles;
    }

    /** Sends every dimension's draws, once per atmos period. */
    static void flush(MinecraftServer server) {
        if (draws.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            flush(level);
        }
        draws.clear();
    }

    /**
     * Sends one level's draws: they reach the zones with its next atmos tick, and the CO2 with the
     * tick after.
     *
     * @return the removals, one per zone, completing with the O2 removed
     */
    public static Map<Long, CompletableFuture<GasParcel>> flush(ServerLevel level) {
        Map<Long, Draw> drawn = draws.remove(level.dimension());
        AtmosService service = AtmosService.current();
        Map<Long, CompletableFuture<GasParcel>> sent = new LinkedHashMap<>();
        if (drawn == null || service == null) {
            return sent;
        }
        drawn.forEach((zone, d) -> {
            PointResolver.Cell cell = PointResolver.resolve(level, d.point);
            CompletableFuture<GasParcel> exhaled = service.removeForLife(
                            level, cell.pos(), cell.sub(), Map.of(StandardAir.OXYGEN, d.moles), SOURCE)
                    .thenApply(r -> {
                        double o2 = r.moved().moles().getOrDefault(StandardAir.OXYGEN, 0.0);
                        if (r.outcome() != MutationOutcome.ZONE || !(o2 > 0)) {
                            return GasParcel.EMPTY;
                        }
                        GasParcel co2 = new GasParcel(
                                Map.of(CARBON_DIOXIDE, o2), r.moved().temperature());
                        service.addForLife(level, cell.pos(), cell.sub(), co2, SOURCE);
                        return r.moved();
                    });
            sent.put(zone, exhaled);
        });
        return sent;
    }

    /** Forgets every draw, when the server stops. */
    static void clear() {
        draws.clear();
    }
}
