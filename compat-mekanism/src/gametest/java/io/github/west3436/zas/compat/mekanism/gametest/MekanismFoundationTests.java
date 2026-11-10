package io.github.west3436.zas.compat.mekanism.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import io.github.west3436.zas.api.gas.GasTags;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.State;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.compat.mekanism.ChemicalGases;
import io.github.west3436.zas.compat.mekanism.ChemicalMapping;
import io.github.west3436.zas.compat.mekanism.ChemicalUnits;
import io.github.west3436.zas.compat.mekanism.MekanismCompat;
import io.github.west3436.zas.compat.mekanism.MekanismSettings;
import io.github.west3436.zas.compat.mekanism.MekanismVersions;
import io.github.west3436.zas.compat.mekanism.mixin.MekanismMixinPlugin;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import mekanism.api.MekanismAPI;
import mekanism.api.MekanismAPITags;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The adapter's foundation on a server that has Mekanism and Mekanism Generators (MEK-001, MEK-002,
 * MEK-010): it is on, its mixin config applies, its data and settings load, and its chemical map
 * answers from Mekanism's own registry.
 *
 * <p>The build runs these with the newest Mekanism and with the oldest the adapter supports
 * (MEK-003), and tells the tests which it asked for. With a Mekanism older than that it runs {@link
 * MekanismUnsupportedTests} instead.
 */
@GameTestHolder(MekanismFoundationTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class MekanismFoundationTests {
    /** The namespace of the adapter's GameTests, and of the data they add. */
    public static final String NAMESPACE = "zas_mekanism";

    /** The mod the adapter ships in. */
    private static final String ZAS = "zas";

    /** The chemicals the adapter maps by default, each with the one gas it is. */
    private static final Map<String, String> DEFAULTS = new TreeMap<>(Map.of(
            "mekanism:oxygen", "zas:oxygen",
            "mekanism:hydrogen", "zas:hydrogen",
            "mekanism:water_vapor", "zas:water_vapor",
            "mekanism:steam", "zas:water_vapor",
            "mekanism:chlorine", "zas:chlorine",
            "mekanism:hydrogen_chloride", "zas:hydrogen_chloride",
            "mekanism:sulfur_dioxide", "zas:sulfur_dioxide",
            "mekanism:ethene", "zas:ethene",
            "mekanismgenerators:deuterium", "zas:deuterium",
            "mekanismgenerators:tritium", "zas:tritium"));

    private static final String FUSION_FUEL = "mekanismgenerators:fusion_fuel";

    /** A mapping these tests' own data adds, to a chemical the defaults leave out. */
    private static final String ADDED = "mekanism:sulfur_trioxide";

    /** The gases the adapter's data pack defines. */
    private static final List<String> PACK_GASES = List.of(
            "zas:chlorine",
            "zas:hydrogen_chloride",
            "zas:sulfur_dioxide",
            "zas:ethene",
            "zas:deuterium",
            "zas:tritium");

    private MekanismFoundationTests() {}

    private static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }

    private static Holder<Chemical> chemical(GameTestHelper h, String name) {
        Optional<Holder.Reference<Chemical>> holder = MekanismAPI.CHEMICAL_REGISTRY.getHolder(
                ResourceKey.create(MekanismAPI.CHEMICAL_REGISTRY_NAME, id(name)));
        h.assertTrue(holder.isPresent(), "Mekanism registers no chemical " + name);
        return holder.orElseThrow();
    }

    private static Optional<String> chemicalFor(String gas, double temperature) {
        return ChemicalGases.chemical(id(gas), temperature)
                .map(holder -> holder.unwrapKey().orElseThrow().location().toString());
    }

    private static void assertClose(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + " and got " + actual);
    }

    /**
     * With Mekanism installed in a supported version, {@code zas} starts the adapter: its row is
     * active, with the range the adapter itself states, and the Mekanism on the server is the one
     * the build asked for.
     */
    @GameTest(template = "empty")
    public static void mek_001_the_adapter_is_on_with_a_supported_mekanism(GameTestHelper h) {
        String installed = Adapters.installedVersion(MekanismVersions.MOD_ID).orElse(null);
        h.assertTrue(installed != null, "Mekanism isn't installed on this server");
        h.assertTrue(ModList.get().isLoaded(MekanismVersions.GENERATORS_MOD_ID), "Mekanism Generators isn't installed");
        // A published build is <minecraft>-<mod version>.<build number>.
        String asked = System.getProperty("zas.mekanism.runVersion");
        h.assertTrue(
                asked != null && asked.startsWith("1.21.1-" + installed + "."),
                "the build asked for Mekanism " + asked + ", and " + installed + " is installed");
        h.assertTrue(MekanismVersions.supports(installed), installed + " is outside " + MekanismVersions.SUPPORTED);

        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(MekanismVersions.MOD_ID))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Mekanism");
        h.assertValueEqual(status.row().range(), MekanismVersions.SUPPORTED, "the row's range");
        h.assertValueEqual(status.row().entryClass(), MekanismCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(Adapters.isActive(MekanismVersions.MOD_ID), "zas says the adapter is off");
        h.assertTrue(MekanismCompat.isActive(), "the adapter says it never started");
        h.succeed();
    }

    /**
     * The mixin config was handed to Mixin, because Mekanism is installed, and its plugin lets its
     * mixins apply, because the version is supported. A hook added to the config is then covered by
     * a GameTest of its own (MEK-003).
     */
    @GameTest(template = "empty")
    public static void mek_001_the_mixin_config_applies_to_a_supported_mekanism(GameTestHelper h) {
        h.assertTrue(MekanismMixinPlugin.loaded(), "Mixin never loaded zas.mekanism.mixins.json");
        h.assertTrue(MekanismMixinPlugin.applies(), "the plugin keeps the mixins off a supported Mekanism");
        h.succeed();
    }

    /**
     * What the adapter adds arrives with it: its data pack is on, the gases the pack defines exist,
     * the toxic ones are tagged, and its settings file is a server config of {@code zas}.
     */
    @GameTest(template = "empty")
    public static void mek_001_the_adapters_data_and_settings_load_with_mekanism(GameTestHelper h) {
        String pack = MekanismCompat.dataPackId(ZAS);
        h.assertValueEqual(pack, "mod/zas:datapacks/mekanism", "the pack's id");
        h.assertTrue(
                h.getLevel().getServer().getPackRepository().getSelectedIds().contains(pack), pack + " isn't on");
        for (String gas : PACK_GASES) {
            h.assertTrue(GasProperties.isDefined(id(gas)), "the server defines no gas " + gas);
        }
        for (String gas : List.of("zas:chlorine", "zas:hydrogen_chloride", "zas:sulfur_dioxide")) {
            h.assertTrue(GasTags.contains(GasTags.TOXIC, id(gas)), gas + " isn't toxic");
        }
        // The pack adds to the tag; ZAS³'s own toxic gas is still in it.
        h.assertTrue(GasTags.contains(GasTags.TOXIC, id("zas:carbon_monoxide")), "the pack replaced #zas:toxic");

        ModConfig config = ModConfigs.getFileMap().get(MekanismSettings.FILE);
        h.assertTrue(config != null, MekanismSettings.FILE + " isn't registered");
        h.assertValueEqual(config.getType(), ModConfig.Type.SERVER, "the config's type");
        h.assertValueEqual(config.getModId(), ZAS, "the config's mod");
        h.assertTrue(MekanismSettings.SPEC.isLoaded(), "the server config isn't loaded on a running server");
        h.succeed();
    }

    /**
     * The unit factor is one server setting (Q-19): 1 mB is 1 L of gas at 101.325 kPa and 293.15 K
     * by default, and a change applies to the next conversion, in both directions, with no restart.
     */
    @GameTest(template = "empty")
    public static void mek_002_the_unit_factor_is_one_server_setting_read_on_use(GameTestHelper h) {
        double standard = ChemicalUnits.molesPerMillibucket(1.0);
        h.assertValueEqual(MekanismSettings.litresPerMillibucket(), 1.0, "the default factor");
        assertClose(h, ChemicalGases.molesPerMillibucket(), standard, "mol per mB");
        assertClose(h, ChemicalGases.moles(1000), 1000 * standard, "1,000 mB");
        ChemicalStack oxygen = new ChemicalStack(chemical(h, "mekanism:oxygen"), 1000);
        assertClose(
                h, ChemicalGases.gas(oxygen, 293.15).moles(id("zas:oxygen")), 1000 * standard, "1,000 mB of oxygen");
        h.assertValueEqual(ChemicalGases.wholeMillibuckets(1000 * standard), 1000L, "1,000 mB, there and back");

        // No test runs between the change and its undoing, since both are in this one call.
        try {
            MekanismSettings.LITRES_PER_MILLIBUCKET.set(2.0);
            assertClose(h, ChemicalGases.molesPerMillibucket(), 2 * standard, "mol per mB at 2 L");
            assertClose(
                    h,
                    ChemicalGases.gas(oxygen, 293.15).moles(id("zas:oxygen")),
                    2000 * standard,
                    "1,000 mB of oxygen at 2 L per mB");
            h.assertValueEqual(ChemicalGases.wholeMillibuckets(1000 * standard), 500L, "the same gas at 2 L per mB");
        } finally {
            MekanismSettings.LITRES_PER_MILLIBUCKET.set(1.0);
        }
        assertClose(h, ChemicalGases.molesPerMillibucket(), standard, "mol per mB, set back");
        h.succeed();
    }

    /**
     * Every default mapping finds its chemical in Mekanism's registry, in this version, and its gas
     * on this server: the lookup answers for each, both ways.
     */
    @GameTest(template = "empty")
    public static void mek_010_the_default_map_resolves_against_mekanisms_chemicals(GameTestHelper h) {
        DEFAULTS.forEach((name, gas) -> {
            Holder<Chemical> chemical = chemical(h, name);
            ChemicalMapping mapping = ChemicalGases.mapping(chemical).orElse(null);
            h.assertTrue(mapping != null, name + " isn't mapped");
            h.assertValueEqual(mapping.gases(), Map.of(id(gas), 1.0), "what " + name + " is");
            h.assertTrue(ChemicalGases.mapping(chemical.value()).isPresent(), name + " by its chemical");
            h.assertTrue(ChemicalGases.mapping(new ChemicalStack(chemical, 1)).isPresent(), name + " by a stack");
            h.assertFalse(chemical.value().isRadioactive(), name + " is radioactive");
        });
        ChemicalMapping fuel = ChemicalGases.mapping(chemical(h, FUSION_FUEL)).orElse(null);
        h.assertTrue(fuel != null, FUSION_FUEL + " isn't mapped");
        h.assertValueEqual(fuel.gases(), Map.of(id("zas:deuterium"), 0.5, id("zas:tritium"), 0.5), "fusion fuel");

        // The whole map on this server: the defaults, and the one these tests' data adds.
        Set<String> expected = new TreeSet<>(DEFAULTS.keySet());
        expected.add(FUSION_FUEL);
        expected.add(ADDED);
        h.assertValueEqual(
                new TreeSet<>(ChemicalGases.map().chemicals().stream()
                        .map(ResourceLocation::toString)
                        .toList()),
                expected,
                "the chemicals that are gases");

        // And back: each gas leaves as its chemical, water vapour by its temperature.
        h.assertValueEqual(chemicalFor("zas:oxygen", 293.15), Optional.of("mekanism:oxygen"), "oxygen");
        h.assertValueEqual(chemicalFor("zas:hydrogen", 293.15), Optional.of("mekanism:hydrogen"), "hydrogen");
        h.assertValueEqual(chemicalFor("zas:chlorine", 293.15), Optional.of("mekanism:chlorine"), "chlorine");
        h.assertValueEqual(
                chemicalFor("zas:hydrogen_chloride", 293.15), Optional.of("mekanism:hydrogen_chloride"), "HCl");
        h.assertValueEqual(
                chemicalFor("zas:sulfur_dioxide", 293.15), Optional.of("mekanism:sulfur_dioxide"), "sulfur dioxide");
        h.assertValueEqual(chemicalFor("zas:ethene", 293.15), Optional.of("mekanism:ethene"), "ethene");
        h.assertValueEqual(
                chemicalFor("zas:deuterium", 293.15), Optional.of("mekanismgenerators:deuterium"), "deuterium");
        h.assertValueEqual(chemicalFor("zas:tritium", 293.15), Optional.of("mekanismgenerators:tritium"), "tritium");
        // Gases Mekanism has no chemical for don't cross.
        h.assertValueEqual(chemicalFor("zas:nitrogen", 293.15), Optional.empty(), "nitrogen");
        h.assertValueEqual(chemicalFor("zas:carbon_dioxide", 293.15), Optional.empty(), "carbon dioxide");
        h.succeed();
    }

    /**
     * Steam arrives at 373.15 K or hotter and water vapour at its source's temperature; on the way
     * out the gas's temperature picks between them. Fusion fuel arrives as half deuterium and half
     * tritium, and neither comes back as fuel.
     */
    @GameTest(template = "empty")
    public static void mek_010_steam_arrives_hot_and_fusion_fuel_splits(GameTestHelper h) {
        double factor = ChemicalGases.molesPerMillibucket();
        ResourceLocation water = id("zas:water_vapor");

        GasParcel steam = ChemicalGases.gas(new ChemicalStack(chemical(h, "mekanism:steam"), 400), 293.15);
        assertClose(h, steam.temperature(), 373.15, "steam from a cold source");
        assertClose(h, steam.moles(water), 400 * factor, "400 mB of steam");
        assertClose(h, steam.totalMoles(), 400 * factor, "steam is water vapour only");
        GasParcel hotSteam = ChemicalGases.gas(new ChemicalStack(chemical(h, "mekanism:steam"), 400), 600);
        assertClose(h, hotSteam.temperature(), 600, "steam from a hotter source");
        GasParcel vapour = ChemicalGases.gas(new ChemicalStack(chemical(h, "mekanism:water_vapor"), 400), 293.15);
        assertClose(h, vapour.temperature(), 293.15, "water vapour keeps its source's temperature");
        assertClose(h, vapour.moles(water), 400 * factor, "400 mB of water vapour");

        h.assertValueEqual(chemicalFor("zas:water_vapor", 293.15), Optional.of("mekanism:water_vapor"), "cool");
        h.assertValueEqual(chemicalFor("zas:water_vapor", 373.14), Optional.of("mekanism:water_vapor"), "just below");
        h.assertValueEqual(chemicalFor("zas:water_vapor", 373.15), Optional.of("mekanism:steam"), "at boiling");
        h.assertValueEqual(chemicalFor("zas:water_vapor", 900), Optional.of("mekanism:steam"), "hot");

        GasParcel fuel = ChemicalGases.gas(new ChemicalStack(chemical(h, FUSION_FUEL), 1000), 293.15);
        assertClose(h, fuel.moles(id("zas:deuterium")), 500 * factor, "deuterium in 1,000 mB of fuel");
        assertClose(h, fuel.moles(id("zas:tritium")), 500 * factor, "tritium in 1,000 mB of fuel");
        assertClose(h, fuel.totalMoles(), 1000 * factor, "1,000 mB of fuel");
        h.assertValueEqual(
                chemicalFor("zas:deuterium", 293.15), Optional.of("mekanismgenerators:deuterium"), "deuterium");
        h.succeed();
    }

    /**
     * The map is the allow-list, against every chemical Mekanism registers: a chemical without a
     * mapping is no gas, and neither is a radioactive one, an infuse type or a slurry.
     */
    @GameTest(template = "empty")
    public static void mek_010_no_other_chemical_is_a_gas(GameTestHelper h) {
        Set<String> mapped = new TreeSet<>(DEFAULTS.keySet());
        mapped.add(FUSION_FUEL);
        mapped.add(ADDED);
        List<TagKey<Chemical>> neverGases = List.of(
                MekanismAPITags.Chemicals.CARBON,
                MekanismAPITags.Chemicals.REDSTONE,
                MekanismAPITags.Chemicals.DIAMOND,
                MekanismAPITags.Chemicals.REFINED_OBSIDIAN,
                MekanismAPITags.Chemicals.BIO,
                MekanismAPITags.Chemicals.FUNGI,
                MekanismAPITags.Chemicals.GOLD,
                MekanismAPITags.Chemicals.TIN,
                MekanismAPITags.Chemicals.DIRTY,
                MekanismAPITags.Chemicals.CLEAN);
        int others = 0;
        int radioactive = 0;
        int tagged = 0;
        for (Holder.Reference<Chemical> chemical :
                MekanismAPI.CHEMICAL_REGISTRY.holders().toList()) {
            String name = chemical.key().location().toString();
            boolean isMapped = ChemicalGases.mapping(chemical).isPresent();
            h.assertValueEqual(isMapped, mapped.contains(name), "whether " + name + " is a gas");
            ChemicalStack stack = new ChemicalStack(chemical, 1000);
            if (!isMapped) {
                others++;
                h.assertValueEqual(ChemicalGases.gas(stack, 293.15), GasParcel.EMPTY, "the gas " + name + " makes");
            }
            if (chemical.value().isRadioactive()) {
                radioactive++;
                h.assertFalse(isMapped, name + " is radioactive, and a gas");
            }
            if (neverGases.stream().anyMatch(chemical::is)) {
                tagged++;
                h.assertFalse(isMapped, name + " is an infuse type or a slurry, and a gas");
            }
        }
        // The registry really holds the kinds this is about, so the loop above checked something.
        h.assertTrue(others > 30, "only " + others + " unmapped chemicals: Mekanism's registry looks wrong");
        h.assertTrue(radioactive >= 4, "only " + radioactive + " radioactive chemicals");
        h.assertTrue(tagged >= 20, "only " + tagged + " infuse types and slurries");
        h.assertValueEqual(
                ChemicalGases.gas(ChemicalStack.EMPTY, 293.15), GasParcel.EMPTY, "the gas an empty stack makes");
        h.succeed();
    }

    /**
     * What a datapack can and can't do, with the files these tests add: map another chemical, one
     * way; leave a file out by a failed condition, which is how a shipped mapping is removed; and
     * not map a radioactive chemical, a gas nobody defines, a chemical nobody registers or a file
     * that isn't a mapping.
     */
    @GameTest(template = "empty")
    public static void mek_010_a_datapack_changes_the_map(GameTestHelper h) {
        // Added: sulfur trioxide becomes sulfur dioxide, which still leaves as sulfur dioxide.
        ChemicalMapping added = ChemicalGases.mapping(chemical(h, ADDED)).orElse(null);
        h.assertTrue(added != null, ADDED + " isn't mapped by the tests' data");
        h.assertTrue(added.oneWay(), ADDED + " isn't one-way");
        h.assertValueEqual(
                chemicalFor("zas:sulfur_dioxide", 293.15), Optional.of("mekanism:sulfur_dioxide"), "sulfur dioxide");

        // A failed condition: the file isn't even loaded.
        ResourceLocation conditional = id("mekanism:hydrofluoric_acid");
        h.assertTrue(ChemicalGases.loadedMap().ofChemical(conditional).isEmpty(), conditional + " was loaded");
        h.assertTrue(ChemicalGases.mapping(chemical(h, conditional.toString())).isEmpty(), conditional + " is mapped");

        // Loaded, and then refused: a radioactive chemical, and a gas the server doesn't define.
        for (String refused : List.of("mekanism:nuclear_waste", "mekanism:uranium_hexafluoride")) {
            h.assertTrue(ChemicalGases.loadedMap().ofChemical(id(refused)).isPresent(), refused + " wasn't loaded");
            h.assertTrue(ChemicalGases.mapping(chemical(h, refused)).isEmpty(), refused + " is mapped");
        }
        h.assertTrue(chemical(h, "mekanism:nuclear_waste").value().isRadioactive(), "nuclear waste isn't radioactive");
        h.assertValueEqual(chemicalFor("zas:carbon_dioxide", 293.15), Optional.empty(), "carbon dioxide's chemical");

        // Loaded and never applied: a chemical no mod registers.
        ResourceLocation unregistered = id(NAMESPACE + ":no_such_chemical");
        h.assertTrue(ChemicalGases.loadedMap().ofChemical(unregistered).isPresent(), unregistered + " wasn't loaded");
        h.assertFalse(ChemicalGases.map().chemicals().contains(unregistered), unregistered + " applies");
        h.assertValueEqual(chemicalFor("zas:oxygen", 293.15), Optional.of("mekanism:oxygen"), "oxygen's chemical");

        // Skipped: fractions that don't add up to 1.
        ResourceLocation broken = id(NAMESPACE + ":broken");
        h.assertTrue(ChemicalGases.loadedMap().ofChemical(broken).isEmpty(), "an invalid file was loaded");
        h.succeed();
    }
}
