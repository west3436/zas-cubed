package io.github.west3436.zas.compat.viewer;

import io.github.west3436.zas.engine.fire.FireSettings;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionConditions;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.engine.reaction.ReactionRate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the gases and reactions a client holds into what the viewers show (COMPAT-060, COMPAT-061).
 * Pure: it reads only what it is given, apart from the player's display units.
 */
public final class ViewerEntries {
    /**
     * The fields of a reaction file that end up on a page. The one that doesn't is {@code
     * parameters}, which never leaves the server. A test holds this against the format's own list,
     * so a field added to the format can't be forgotten here.
     */
    public static final Set<String> SHOWN_FIELDS = Set.of(
            "type",
            "reactants",
            "products",
            "enthalpy",
            "min_temperature",
            "max_temperature",
            "concentrations",
            "catalysts",
            "requires_ignition",
            "flammability",
            "rate",
            "incomplete_combustion");

    /** The tint of a gas with no colour of its own. */
    public static final int GREY = 0xB0B0B0;

    /**
     * The built-in gases' colours, as the content mod's windows draw them. A gas's definition has no
     * colour unless the gas is visible in the world, so these are repeated here.
     */
    private static final Map<String, Integer> COLOURS = Map.of(
            "zas:oxygen", 0x2A79C8,
            "zas:nitrogen", 0xD92626,
            "zas:carbon_dioxide", 0x7A7A7A,
            "zas:nitrous_oxide", 0xE7A0A0,
            "zas:carbon_monoxide", 0x8A4FD0,
            "zas:hydrogen", 0xF26C0D,
            "zas:methane", 0xA96A3C,
            "zas:water_vapor", 0xE8E8E8);

    private static final Map<String, Icon> TAG_ICONS =
            Map.of("zas:oxidizer", Icon.OXIDIZER, "zas:flammable", Icon.FLAME, "zas:toxic", Icon.TOXIC);

    private ViewerEntries() {}

    /** Every gas in a registry, in the registry's order. */
    public static List<GasEntry> gases(GasRegistry registry) {
        List<GasEntry> out = new ArrayList<>(registry.size());
        for (int i = 0; i < registry.size(); i++) {
            out.add(gas(registry, i));
        }
        return List.copyOf(out);
    }

    /** The gas that stands for all of them, as the gas list's icon: oxygen, or else the first. */
    public static GasEntry emblem(List<GasEntry> gases) {
        return gases.stream()
                .filter(gas -> gas.id().equals(FireSettings.OXYGEN))
                .findFirst()
                .orElseGet(() -> gases.get(0));
    }

    /** A gas by its id, if the registry has it. */
    public static Optional<GasEntry> find(GasRegistry registry, String id) {
        int index = registry.indexOf(id);
        return index < 0 ? Optional.empty() : Optional.of(gas(registry, index));
    }

    /**
     * Every reaction, in the order given, each followed by its incomplete form if it has one.
     *
     * @param registry the gases the reactions name
     * @param definitions the reactions
     * @param fire the fire oxygen threshold, which every reaction with flammability limits is under
     */
    public static List<ReactionEntry> reactions(
            GasRegistry registry, List<ReactionDefinition> definitions, FireSettings fire) {
        List<ReactionEntry> out = new ArrayList<>();
        for (ReactionDefinition def : definitions) {
            out.add(reaction(registry, def, fire, false));
            if (def.incomplete().isPresent()) {
                out.add(reaction(registry, def, fire, true));
            }
        }
        return List.copyOf(out);
    }

    /**
     * One form of a reaction.
     *
     * @param incomplete whether to show the reaction's incomplete form, which it must have
     */
    public static ReactionEntry reaction(
            GasRegistry registry, ReactionDefinition def, FireSettings fire, boolean incomplete) {
        if (!incomplete) {
            return new ReactionEntry(
                    def.id(),
                    false,
                    stacks(registry, def.reactants()),
                    stacks(registry, def.products()),
                    catalysts(registry, def),
                    chips(registry, def, fire, def.enthalpy(), false));
        }
        ReactionDefinition.Stoichiometry form = def.incomplete().orElseThrow();
        return new ReactionEntry(
                def.id(),
                true,
                stacks(registry, form.reactants()),
                stacks(registry, form.products()),
                catalysts(registry, def),
                chips(registry, def, fire, form.enthalpy(), true));
    }

    /** A gas's own properties: its molar mass, its heat capacity, then its tags (COMPAT-061). */
    public static List<Chip> properties(GasEntry gas) {
        List<Chip> out = new ArrayList<>();
        if (gas.known()) {
            out.add(new Chip(
                    Icon.MASS,
                    Line.of("viewer.zas.g_per_mol", ViewerNumbers.grams(gas.molarMass())),
                    Line.of("viewer.zas.hint.molar_mass")));
            out.add(new Chip(
                    Icon.CAPACITY,
                    Line.of("viewer.zas.j_per_mol_k", ViewerNumbers.number(gas.molarHeatCapacity(), 2)),
                    Line.of("viewer.zas.hint.heat_capacity")));
        }
        for (String tag : gas.tags()) {
            out.add(new Chip(TAG_ICONS.getOrDefault(tag, Icon.TAG), tagName(tag), Line.plain("#" + tag)));
        }
        return List.copyOf(out);
    }

    /** A tag's name: its translation, {@code gas_tag.<namespace>.<path>}, or its id. */
    public static Line tagName(String tag) {
        int colon = tag.indexOf(':');
        return Line.keyOr("gas_tag." + tag.substring(0, colon) + "." + tag.substring(colon + 1), "#" + tag);
    }

    private static GasEntry gas(GasRegistry registry, int index) {
        GasDefinition def = registry.definition(index);
        int colour = def.visibility().map(v -> v.rgb()).orElseGet(() -> COLOURS.getOrDefault(def.id(), GREY));
        return new GasEntry(
                def.id(),
                def.nameKey(),
                def.formula(),
                def.molarMass(),
                def.molarHeatCapacity(),
                colour,
                registry.tagsOf(index));
    }

    /** A gas by id. One the registry lacks still gets an entry, so no reaction loses a gas. */
    private static GasEntry gas(GasRegistry registry, String id) {
        int index = registry.indexOf(id);
        if (index >= 0) {
            return gas(registry, index);
        }
        int colon = id.indexOf(':');
        String key = "gas." + id.substring(0, colon) + "." + id.substring(colon + 1);
        return new GasEntry(id, key, id, Double.NaN, Double.NaN, GREY, List.of());
    }

    private static List<GasStack> stacks(GasRegistry registry, Map<String, Double> coefficients) {
        List<GasStack> out = new ArrayList<>(coefficients.size());
        coefficients.forEach((id, moles) -> out.add(new GasStack(gas(registry, id), moles)));
        return out;
    }

    private static List<GasStack> catalysts(GasRegistry registry, ReactionDefinition def) {
        List<GasStack> out = new ArrayList<>();
        def.conditions().catalysts().forEach(c -> out.add(GasStack.of(gas(registry, c.gas()))));
        return out;
    }

    private static List<Chip> chips(
            GasRegistry registry, ReactionDefinition def, FireSettings fire, double enthalpy, boolean incomplete) {
        ReactionConditions cond = def.conditions();
        List<Chip> out = new ArrayList<>();
        if (!def.type().equals(ReactionDefinition.STANDARD)) {
            out.add(new Chip(Icon.GEAR, Line.plain(def.type()), Line.of("viewer.zas.hint.code")));
        }
        if (cond.requiresIgnition()) {
            out.add(new Chip(Icon.SPARK, Line.NONE, Line.of("viewer.zas.hint.ignition")));
        }
        Optional<ReactionConditions.FlammabilityLimits> limits = cond.flammability();
        limits.filter(l -> Double.isFinite(l.autoignition()))
                .ifPresent(l -> out.add(new Chip(
                        Icon.FLAME,
                        Line.plain(ViewerNumbers.temperature(l.autoignition())),
                        Line.of("viewer.zas.hint.autoignition"))));
        temperature(cond).ifPresent(text -> out.add(new Chip(Icon.THERMOMETER, text, hint("temperature"))));
        limits.ifPresent(l -> out.add(new Chip(
                Icon.SHARE,
                share(registry, l.fuel(), ViewerNumbers.percentRange(l.lower(), l.upper())),
                hint("flammable_range"))));
        if (limits.isPresent()) {
            // Every reaction with flammability limits is a fire, and a fire needs this much oxygen.
            out.add(new Chip(
                    Icon.SHARE,
                    oxygen(registry, "≥" + ViewerNumbers.percent(fire.minOxygenFraction())),
                    hint("fire_oxygen")));
            out.add(new Chip(
                    Icon.GAUGE,
                    oxygen(registry, "≥" + ViewerNumbers.pressure(fire.minOxygenKpa())),
                    hint("fire_oxygen")));
        }
        if (incomplete) {
            out.add(new Chip(
                    Icon.SHARE,
                    oxygen(registry, "<" + ViewerNumbers.percent(fire.incompleteBelowFraction())),
                    hint("incomplete")));
        }
        for (ReactionConditions.Concentration c : cond.concentrations()) {
            out.add(new Chip(Icon.SHARE, share(registry, c.gas(), range(c)), hint("concentration")));
        }
        for (ReactionConditions.Concentration c : cond.catalysts()) {
            if (c.min() > 0) {
                out.add(new Chip(
                        Icon.SHARE, share(registry, c.gas(), "≥" + ViewerNumbers.percent(c.min())), hint("catalyst")));
            }
        }
        def.rate().ifPresent(rate -> rate(rate, out));
        if (enthalpy != 0) {
            out.add(new Chip(
                    enthalpy < 0 ? Icon.HEAT_OUT : Icon.HEAT_IN,
                    Line.of("viewer.zas.kj_per_mol", ViewerNumbers.kilojoules(enthalpy)),
                    hint(enthalpy < 0 ? "heat_released" : "heat_absorbed")));
        }
        return out;
    }

    private static void rate(ReactionRate rate, List<Chip> out) {
        String share = ViewerNumbers.shareInOneSecond(rate.perSecond());
        boolean changes = rate.activationEnergy() != 0;
        out.add(new Chip(
                Icon.CLOCK,
                changes
                        ? Line.of(
                                "viewer.zas.percent_per_second_at",
                                share,
                                ViewerNumbers.temperature(rate.referenceTemperature()))
                        : Line.of("viewer.zas.percent_per_second", share),
                hint("rate")));
        if (changes) {
            out.add(new Chip(
                    Icon.BARRIER,
                    Line.of("viewer.zas.kj_per_mol", ViewerNumbers.kilojoules(rate.activationEnergy())),
                    hint("activation_energy")));
        }
    }

    private static Optional<Line> temperature(ReactionConditions cond) {
        boolean lower = cond.minTemperature() > 0;
        boolean upper = Double.isFinite(cond.maxTemperature());
        if (lower && upper) {
            return Optional.of(
                    Line.plain(ViewerNumbers.temperatureRange(cond.minTemperature(), cond.maxTemperature())));
        }
        if (lower) {
            return Optional.of(Line.plain("≥" + ViewerNumbers.temperature(cond.minTemperature())));
        }
        if (upper) {
            return Optional.of(Line.plain("≤" + ViewerNumbers.temperature(cond.maxTemperature())));
        }
        return Optional.empty();
    }

    private static String range(ReactionConditions.Concentration c) {
        if (c.min() > 0 && c.max() < 1) {
            return ViewerNumbers.percentRange(c.min(), c.max());
        }
        if (c.max() < 1) {
            return "≤" + ViewerNumbers.percent(c.max());
        }
        if (c.min() > 0) {
            return "≥" + ViewerNumbers.percent(c.min());
        }
        return ViewerNumbers.percentRange(0, 1);
    }

    /** A gas's formula, then a value: what share or pressure of that gas. */
    private static Line share(GasRegistry registry, String gas, String value) {
        return labelled(gas(registry, gas).formula(), value);
    }

    /**
     * Oxygen's formula, then a value. On a server whose datapacks define no oxygen the value stands
     * alone, under the chip's own name.
     */
    private static Line oxygen(GasRegistry registry, String value) {
        return find(registry, FireSettings.OXYGEN)
                .map(gas -> labelled(gas.formula(), value))
                .orElseGet(() -> Line.plain(value));
    }

    private static Line labelled(String formula, String value) {
        return Line.plain(String.format(Locale.ROOT, "%s %s", formula, value));
    }

    private static Line hint(String name) {
        return Line.of("viewer.zas.hint." + name);
    }
}
