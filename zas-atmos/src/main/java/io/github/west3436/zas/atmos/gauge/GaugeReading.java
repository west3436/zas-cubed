package io.github.west3436.zas.atmos.gauge;

import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBurst;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jetbrains.annotations.Nullable;

/**
 * What a gauge's dial shows: the pressure, the temperature, or one gas's share of the moles. A plain
 * use steps through them in this order, a sneaking use back. Each has its own face on the dial, and
 * its own scale, from no reading at the needle's rest to its top at the far end of the arc.
 *
 * <ul>
 *   <li><b>Pressure</b>, on the analyzer's bands for what the gauge is on: green to half the limit,
 *       amber to the limit, red to the top. On a pipe or a device that is a pipe network's burst
 *       pressure, 100 atm, with the top at 125 atm; on a canister or a large gas tank, its rating,
 *       9,120 kPa, with the top where it explodes, 13,680 kPa. Each band takes the same part of the
 *       arc on both, so the dial face is one.
 *   <li><b>Temperature</b>, 0 to 1,000 K, its arc in a heat exchanger's colours.
 *   <li><b>A gas's share</b> of the moles, 0 to 100 %, its arc in the gas's colour: each of the gases
 *       ZAS³ defines.
 * </ul>
 *
 * <p>No gas reads as nothing: the needle rests.
 */
public enum GaugeReading implements StringRepresentable {
    PRESSURE("pressure", null),
    TEMPERATURE("temperature", null),
    OXYGEN("oxygen", "oxygen"),
    NITROGEN("nitrogen", "nitrogen"),
    CARBON_DIOXIDE("carbon_dioxide", "carbon_dioxide"),
    NITROUS_OXIDE("nitrous_oxide", "nitrous_oxide"),
    CARBON_MONOXIDE("carbon_monoxide", "carbon_monoxide"),
    HYDROGEN("hydrogen", "hydrogen"),
    METHANE("methane", "methane"),
    WATER_VAPOR("water_vapor", "water_vapor");

    /** The block state's property: which reading the dial shows, and so which face it has. */
    public static final EnumProperty<GaugeReading> PROPERTY = EnumProperty.create("reading", GaugeReading.class);

    /** Where each pressure band ends along the arc, 0 to 1: green, amber, red. */
    static final double[] PRESSURE_FACE = {0.4, 0.8, 1.0};

    /** A pipe network's bands, kPa: half its burst pressure, the burst, and the top (CONT-108). */
    static final double[] NETWORK_STOPS = {
        PipeBurst.THRESHOLD_KPA / 2, PipeBurst.THRESHOLD_KPA, PipeBurst.THRESHOLD_KPA * 1.25
    };

    /** A canister's or large tank's bands, kPa: half its rating, the rating, and where it explodes. */
    static final double[] VESSEL_STOPS = {
        ContainerFailure.CANISTER_RATED_KPA / 2,
        ContainerFailure.CANISTER_RATED_KPA,
        ContainerFailure.CANISTER_EXPLODE_KPA
    };

    /** The temperature at the top of the scale, K. */
    public static final double TOP_KELVIN = 1000;

    private static final GaugeReading[] ALL = values();

    private final String name;
    private final @Nullable ResourceLocation gas;

    GaugeReading(String name, @Nullable String gas) {
        this.name = name;
        this.gas = gas == null ? null : ResourceLocation.fromNamespaceAndPath("zas", gas);
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** The gas whose share it reads, or null for the pressure and the temperature. */
    public @Nullable ResourceLocation gas() {
        return gas;
    }

    /** The next reading, after the last the first. */
    public GaugeReading next() {
        return ALL[(ordinal() + 1) % ALL.length];
    }

    /** The reading before, before the first the last. */
    public GaugeReading previous() {
        return ALL[(ordinal() + ALL.length - 1) % ALL.length];
    }

    /** Its name, as the action bar shows it when a gauge is turned to it. */
    public Component label() {
        return switch (this) {
            case PRESSURE -> Component.translatable("block.zas_atmos.gauge.pressure");
            case TEMPERATURE -> Component.translatable("block.zas_atmos.gauge.temperature");
            default -> Component.translatable("block.zas_atmos.gauge.share", GasCodecs.name(gas));
        };
    }

    /**
     * The value it reads: kPa, K, or a share from 0 to 1. No gas reads 0.
     *
     * @param gas the gas read
     * @return the value
     */
    public double value(GasStore gas) {
        if (gas.isEmpty()) {
            return 0;
        }
        return switch (this) {
            case PRESSURE -> gas.pressure();
            case TEMPERATURE -> gas.temperature();
            default -> gas.moles(this.gas) / gas.totalMoles();
        };
    }

    /**
     * Where the needle points for a value, along the arc: 0 at rest, 1 at the top, and no further.
     *
     * @param value what {@link #value} read
     * @param vessel whether the gauge is on a canister or a large tank, whose pressure bands it shows
     * @return 0 to 1
     */
    public double along(double value, boolean vessel) {
        if (!(value > 0)) {
            return 0; // nothing, below nothing, or no number
        }
        double along = switch (this) {
            case PRESSURE -> banded(value, vessel ? VESSEL_STOPS : NETWORK_STOPS);
            case TEMPERATURE -> value / TOP_KELVIN;
            default -> value;
        };
        return Double.isNaN(along) ? 0 : Math.max(0, Math.min(1, along));
    }

    /** A pressure's place on the arc: straight along each band, each band where the face has it. */
    private static double banded(double kpa, double[] stops) {
        double from = 0;
        double at = 0;
        for (int i = 0; i < stops.length; i++) {
            if (kpa <= stops[i]) {
                return at + (PRESSURE_FACE[i] - at) * (kpa - from) / (stops[i] - from);
            }
            from = stops[i];
            at = PRESSURE_FACE[i];
        }
        return 1;
    }
}
