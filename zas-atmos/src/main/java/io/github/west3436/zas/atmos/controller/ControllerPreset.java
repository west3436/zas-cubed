package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasTag;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * A controller's thresholds (CONT-040, CONT-047): pressure, temperature, each listed gas's partial
 * pressure, and the other gases' summed partial pressure, each with its {@link Thresholds}.
 *
 * <p>The readings are numbered for the GUI: 0 is pressure, 1 temperature, then the listed gases in
 * order, then the other gases last. Nitrogen is listed with no bounds, so that the air's filler gas
 * isn't counted among the other gases. A preset also names the gases its controller's scrubbers
 * take in Scrubbing mode (CONT-041): CO₂ and {@code #zas:toxic}, the scrubber's default (CONT-015).
 *
 * <p>Immutable: each edit returns a new preset.
 */
public record ControllerPreset(
        String name,
        boolean edited,
        Thresholds pressure,
        Thresholds temperature,
        Map<ResourceLocation, Thresholds> gases,
        Thresholds others) {

    public static final ResourceLocation CO2 = zas("carbon_dioxide");
    public static final ResourceLocation N2O = zas("nitrous_oxide");
    public static final ResourceLocation CO = zas("carbon_monoxide");
    public static final ResourceLocation H2 = zas("hydrogen");
    public static final ResourceLocation CH4 = zas("methane");

    /** The gases scrubbed in Scrubbing mode, besides {@link #SCRUB_TAGS}. */
    public static final List<ResourceLocation> SCRUB_GASES = List.of(CO2);

    /** The gas tags scrubbed in Scrubbing mode. */
    public static final List<GasTag> SCRUB_TAGS = List.of(GasTags.TOXIC);

    /** The Human preset (CONT-047). */
    public static final ControllerPreset HUMAN = new ControllerPreset(
            "human",
            false,
            new Thresholds(81, 91, 111, 122),
            new Thresholds(243, 273, 313, 343),
            humanGases(),
            Thresholds.max(0.5, 1));

    /**
     * The Vacuum preset (CONT-047): Human's gas maxima, with pressure at {@code –/–/1/5} kPa. A room
     * meant to hold no air has no minimum O₂ and no temperature bounds, which would otherwise always
     * trip in it.
     */
    public static final ControllerPreset VACUUM = new ControllerPreset(
            "vacuum", false, Thresholds.max(1, 5), Thresholds.NONE, vacuumGases(), Thresholds.max(0.5, 1));

    /** The shipped presets, in the order the GUI offers them. */
    public static final List<ControllerPreset> SHIPPED = List.of(HUMAN, VACUUM);

    public ControllerPreset {
        gases = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(gases));
    }

    private static ResourceLocation zas(String path) {
        return ResourceLocation.fromNamespaceAndPath("zas", path);
    }

    private static Map<ResourceLocation, Thresholds> humanGases() {
        Map<ResourceLocation, Thresholds> g = new LinkedHashMap<>();
        g.put(StandardAir.OXYGEN, new Thresholds(16, 18, 135, 140));
        g.put(StandardAir.NITROGEN, Thresholds.NONE);
        g.put(CO2, Thresholds.max(5, 10));
        g.put(N2O, Thresholds.max(0.5, 1));
        g.put(CO, Thresholds.max(0.005, 0.12));
        g.put(H2, Thresholds.max(1, 2));
        g.put(CH4, Thresholds.max(1, 2.5));
        return g;
    }

    private static Map<ResourceLocation, Thresholds> vacuumGases() {
        Map<ResourceLocation, Thresholds> g = humanGases();
        g.put(StandardAir.OXYGEN, Thresholds.max(135, 140));
        return g;
    }

    /** How many readings there are: pressure, temperature, each listed gas and the others. */
    public int readings() {
        return gases.size() + 3;
    }

    /** Reading {@code i}'s bounds, numbered as in the class comment. */
    public Thresholds thresholds(int i) {
        if (i == 0) {
            return pressure;
        }
        if (i == 1) {
            return temperature;
        }
        if (i == readings() - 1) {
            return others;
        }
        return new ArrayList<>(gases.values()).get(i - 2);
    }

    /** Reading {@code i}'s gas, or null for pressure, temperature and the others. */
    public ResourceLocation gas(int i) {
        return i >= 2 && i < readings() - 1 ? new ArrayList<>(gases.keySet()).get(i - 2) : null;
    }

    /** This preset with reading {@code i}'s bounds replaced, marked edited. */
    public ControllerPreset with(int i, Thresholds t) {
        if (i < 0 || i >= readings()) {
            throw new IllegalArgumentException("no reading " + i);
        }
        if (i == 0) {
            return new ControllerPreset(name, true, t, temperature, gases, others);
        }
        if (i == 1) {
            return new ControllerPreset(name, true, pressure, t, gases, others);
        }
        if (i == readings() - 1) {
            return new ControllerPreset(name, true, pressure, temperature, gases, t);
        }
        Map<ResourceLocation, Thresholds> g = new LinkedHashMap<>(gases);
        g.put(gas(i), t);
        return new ControllerPreset(name, true, pressure, temperature, g, others);
    }

    /**
     * The pressure vents blow to in Scrubbing mode, and the target of Replacement and Fill
     * (CONT-041): the middle of the safe band, min1 to max1, a disabled min1 counting as 0. 101 kPa
     * for Human.
     */
    public double targetPressure() {
        double lo = pressure.min1() == Thresholds.OFF ? 0 : pressure.min1();
        double hi = pressure.max1() == Thresholds.OFF ? lo : pressure.max1();
        return (lo + hi) / 2;
    }

    /** Each reading's value in a mixture, numbered as in the class comment. */
    public double[] values(GasMixture air) {
        double[] v = new double[readings()];
        v[0] = air.pressure();
        v[1] = air.temperature();
        int i = 2;
        double others = 0;
        for (ResourceLocation gas : gases.keySet()) {
            v[i++] = air.partialPressure(gas);
        }
        for (Map.Entry<ResourceLocation, Double> e : air.partialPressures().entrySet()) {
            if (!gases.containsKey(e.getKey())) {
                others += e.getValue();
            }
        }
        v[i] = others;
        return v;
    }

    /** Each reading's danger level, 0 to 2, for values from {@link #values}. */
    public int[] rate(double[] values) {
        int[] r = new int[readings()];
        for (int i = 0; i < r.length; i++) {
            r[i] = thresholds(i).rate(values[i]);
        }
        return r;
    }

    /** The worst danger level of a mixture's readings (CONT-040). */
    public int danger(GasMixture air) {
        int worst = 0;
        for (int r : rate(values(air))) {
            worst = Math.max(worst, r);
        }
        return worst;
    }

    // ---- Saving --------------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putBoolean("Edited", edited);
        ListTag list = new ListTag();
        for (int i = 0; i < readings(); i++) {
            CompoundTag r = new CompoundTag();
            ResourceLocation gas = gas(i);
            r.putString(
                    "Reading", i == 0 ? "pressure" : i == 1 ? "temperature" : gas == null ? "others" : gas.toString());
            Thresholds t = thresholds(i);
            r.putDouble("Min2", t.min2());
            r.putDouble("Min1", t.min1());
            r.putDouble("Max1", t.max1());
            r.putDouble("Max2", t.max2());
            list.add(r);
        }
        tag.put("Readings", list);
        return tag;
    }

    /**
     * Loads what {@link #save} wrote. A reading the saved preset lacks keeps the shipped value, and
     * a saved reading that is invalid or unknown is skipped.
     */
    public static ControllerPreset load(CompoundTag tag) {
        String name = tag.getString("Name");
        ControllerPreset p =
                SHIPPED.stream().filter(s -> s.name.equals(name)).findFirst().orElse(HUMAN);
        boolean edited = tag.getBoolean("Edited");
        for (Tag t : tag.getList("Readings", Tag.TAG_COMPOUND)) {
            CompoundTag r = (CompoundTag) t;
            int i = p.indexOf(r.getString("Reading"));
            if (i < 0) {
                continue;
            }
            try {
                p = p.with(
                        i,
                        new Thresholds(
                                r.getDouble("Min2"), r.getDouble("Min1"), r.getDouble("Max1"), r.getDouble("Max2")));
            } catch (IllegalArgumentException ignored) {
                // An invalid saved reading keeps the shipped bounds.
            }
        }
        return new ControllerPreset(p.name, edited, p.pressure, p.temperature, p.gases, p.others);
    }

    private int indexOf(String reading) {
        switch (reading) {
            case "pressure":
                return 0;
            case "temperature":
                return 1;
            case "others":
                return readings() - 1;
            default:
                ResourceLocation id = ResourceLocation.tryParse(reading);
                int i = new ArrayList<>(gases.keySet()).indexOf(id);
                return i < 0 ? -1 : i + 2;
        }
    }
}
