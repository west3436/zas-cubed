package io.github.west3436.zas.life;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.data.DatapackErrors;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Protection an item gets from data rather than its own capability (LIFE-026): one file of {@code
 * data/<namespace>/zas/protection/}, naming the items and item tags it applies to and the parts of
 * {@link ILifeSupport} they give. A part left out gives nothing.
 *
 * @param items the item ids and, with a leading {@code #}, item tags it applies to
 * @param minPressure the lowest pressure protected against, in kPa, 0 to 1 atm
 * @param maxPressure the highest pressure protected against, in kPa, at least 1 atm
 * @param minTemperature the lowest temperature protected against, in K, 0 to 293.15 K
 * @param maxTemperature the highest temperature protected against, in K, at least 293.15 K
 * @param sealedBreathing whether it seals the breathing, in the head slot
 * @param gasFiltering whether it filters the breath, in the head slot
 * @param airflowResistance its airflow resistance, 0 to 1
 */
public record ProtectionData(
        List<Entry> items,
        double minPressure,
        double maxPressure,
        double minTemperature,
        double maxTemperature,
        boolean sealedBreathing,
        boolean gasFiltering,
        double airflowResistance)
        implements ILifeSupport {

    /**
     * An item or item tag a protection file names.
     *
     * @param id the item's or tag's id
     * @param tag whether it names a tag
     */
    public record Entry(ResourceLocation id, boolean tag) {
        static final Codec<Entry> CODEC = Codec.STRING.comapFlatMap(Entry::parse, Entry::toString);

        static DataResult<Entry> parse(String s) {
            boolean tag = s.startsWith("#");
            String id = tag ? s.substring(1) : s;
            return ResourceLocation.read(id).map(rl -> new Entry(rl, tag));
        }

        @Override
        public String toString() {
            return (tag ? "#" : "") + id;
        }
    }

    /** The file format. */
    public static final Codec<ProtectionData> CODEC = RecordCodecBuilder.create(i -> i.group(
                    DatapackErrors.field(Entry.CODEC.listOf(1, Integer.MAX_VALUE), "items")
                            .forGetter(ProtectionData::items),
                    DatapackErrors.optionalField(
                                    range(0, StandardAir.PRESSURE), "min_pressure_kpa", StandardAir.PRESSURE)
                            .forGetter(ProtectionData::minPressure),
                    DatapackErrors.optionalField(
                                    atLeast(StandardAir.PRESSURE), "max_pressure_kpa", StandardAir.PRESSURE)
                            .forGetter(ProtectionData::maxPressure),
                    DatapackErrors.optionalField(
                                    range(0, StandardAir.TEMPERATURE), "min_temperature_k", StandardAir.TEMPERATURE)
                            .forGetter(ProtectionData::minTemperature),
                    DatapackErrors.optionalField(
                                    atLeast(StandardAir.TEMPERATURE), "max_temperature_k", StandardAir.TEMPERATURE)
                            .forGetter(ProtectionData::maxTemperature),
                    DatapackErrors.optionalField(Codec.BOOL, "sealed_breathing", false)
                            .forGetter(ProtectionData::sealedBreathing),
                    DatapackErrors.optionalField(Codec.BOOL, "gas_filtering", false)
                            .forGetter(ProtectionData::gasFiltering),
                    DatapackErrors.optionalField(range(0, 1), "airflow_resistance", 0.0)
                            .forGetter(ProtectionData::airflowResistance))
            .apply(i, ProtectionData::new));

    private static Codec<Double> range(double min, double max) {
        return Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= min && v <= max
                ? DataResult.success(v)
                : DataResult.error(() -> "must be from " + min + " to " + max + ", got " + v));
    }

    private static Codec<Double> atLeast(double min) {
        return Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= min
                ? DataResult.success(v)
                : DataResult.error(() -> "must be finite and at least " + min + ", got " + v));
    }
}
