package io.github.west3436.zas.compat.jei.gametest;

import com.mojang.serialization.JsonOps;
import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import io.github.west3436.zas.compat.jei.GasIngredients;
import io.github.west3436.zas.compat.jei.ZasJeiPlugin;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.GasStack;
import io.github.west3436.zas.compat.viewer.ReactionEntry;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerGate;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.engine.reaction.ReactionDefinition;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.gas.GasDefinitionsPayload;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.github.west3436.zas.reaction.ClientReactions;
import io.github.west3436.zas.reaction.ReactionDefinitionsPayload;
import io.github.west3436.zas.reaction.ServerReactions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The JEI plugin on a server with JEI really installed, from the release jar (COMPAT-004). JEI
 * starts its plugins only on a client, so what a server can show is everything up to that point:
 * the jar holds the plugin where JEI looks for it, the plugin accepts this JEI, the pages it would
 * register are made from what the server sends, and behind a closed gate it registers nothing.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class JeiPluginTests {
    private JeiPluginTests() {}

    /**
     * The release jar holds the plugin with the annotation JEI finds plugins by, the JEI this run
     * installs is inside the plugin's range, and the plugin can be made where there is no client.
     */
    @GameTest(template = "empty")
    public static void compat_061_the_jar_holds_the_plugin_and_it_accepts_this_jei(GameTestHelper h) {
        String plugin = ZasJeiPlugin.class.getName();
        boolean annotated = ModList.get().getAllScanData().stream()
                .flatMap(data -> data.getAnnotations().stream())
                .anyMatch(a -> a.annotationType().getClassName().equals("mezz.jei.api.JeiPlugin")
                        && a.clazz().getClassName().equals(plugin));
        h.assertTrue(annotated, "no @JeiPlugin on " + plugin + " in the mods' scan data");

        String installed = ModList.get()
                .getModContainerById(ZasJeiPlugin.JEI)
                .orElseThrow(() -> new GameTestAssertException("JEI isn't installed in this run"))
                .getModInfo()
                .getVersion()
                .toString();
        h.assertValueEqual(VersionGate.check(installed, ZasJeiPlugin.SUPPORTED), Verdict.SUPPORTED, "JEI " + installed);
        ViewerGate gate = new ViewerGate(ZasJeiPlugin.JEI, ZasJeiPlugin.SUPPORTED);
        h.assertTrue(gate.on(), "the plugin's gate is closed to JEI " + installed);
        h.assertValueEqual(gate.warnings(), 0, "warnings");

        h.assertValueEqual(
                new ZasJeiPlugin().getPluginUid(), ResourceLocation.fromNamespaceAndPath("zas", "viewer"), "the uid");
        h.succeed();
    }

    /**
     * What the server sends a client becomes the pages the plugin registers: every gas the server
     * defines, and every reaction it runs with its incomplete form. A gas is told apart by its id
     * whatever its amount, and is saved and loaded by its id.
     */
    @GameTest(template = "empty")
    public static void compat_060_the_pages_are_every_gas_and_reaction_the_server_sends(GameTestHelper h) {
        GasRegistry gases = ServerGasRegistry.current();
        List<ReactionDefinition> reactions = ServerReactions.current().definitions();
        h.assertTrue(gases.size() >= 8, "the server has " + gases.size() + " gases");
        h.assertTrue(reactions.size() >= 4, "the server has " + reactions.size() + " reactions");
        ByteBuf buf = Unpooled.buffer();
        try {
            GasDefinitionsPayload.STREAM_CODEC.encode(buf, GasDefinitionsPayload.of(gases));
            ClientGasRegistry.accept(GasDefinitionsPayload.STREAM_CODEC.decode(buf));
            ReactionDefinitionsPayload.STREAM_CODEC.encode(buf, new ReactionDefinitionsPayload(reactions));
            ClientReactions.accept(ReactionDefinitionsPayload.STREAM_CODEC.decode(buf));
            h.assertTrue(buf.readableBytes() == 0, "the payloads left unread bytes");

            List<GasEntry> listed = ViewerData.gases();
            h.assertValueEqual(
                    listed.stream().map(GasEntry::id).toList(),
                    gases.definitions().stream().map(def -> def.id()).toList(),
                    "the gases listed");
            for (int i = 0; i < gases.size(); i++) {
                GasEntry gas = listed.get(i);
                h.assertValueEqual(gas.molarMass(), gases.definition(i).molarMass(), gas.id() + " molar mass");
                h.assertValueEqual(
                        gas.molarHeatCapacity(), gases.definition(i).molarHeatCapacity(), gas.id() + " heat capacity");
                h.assertValueEqual(gas.tags(), gases.tagsOf(i), gas.id() + " tags");
            }

            List<ReactionEntry> pages = ViewerData.reactions().stream()
                    .map(ViewerData.Reaction::now)
                    .toList();
            List<String> expected = new ArrayList<>();
            for (ReactionDefinition def : reactions) {
                expected.add(def.id());
                ReactionEntry page = page(pages, def.id(), false);
                h.assertValueEqual(amounts(page.reactants()), def.reactants(), def.id() + " takes");
                h.assertValueEqual(amounts(page.products()), def.products(), def.id() + " gives");
                def.incomplete().ifPresent(form -> {
                    expected.add(def.id() + "/incomplete");
                    ReactionEntry other = page(pages, def.id(), true);
                    h.assertValueEqual(amounts(other.reactants()), form.reactants(), def.id() + " takes, incomplete");
                    h.assertValueEqual(amounts(other.products()), form.products(), def.id() + " gives, incomplete");
                });
            }
            h.assertValueEqual(
                    pages.stream().map(p -> p.namespace() + ":" + p.path()).toList(), expected, "the reactions listed");

            GasIngredients.Helper helper = new GasIngredients.Helper();
            for (GasEntry gas : listed) {
                GasStack one = GasStack.of(gas);
                GasStack half = new GasStack(gas, 0.5);
                h.assertValueEqual(helper.getUid(one, UidContext.Ingredient), gas.id(), "the uid");
                h.assertValueEqual(
                        helper.getUid(half, UidContext.Recipe), helper.getUid(one, UidContext.Recipe), "one gas");
                h.assertValueEqual(helper.normalizeIngredient(half), one, "a gas listed on its own");
                h.assertValueEqual(helper.getResourceLocation(one), ResourceLocation.parse(gas.id()), "the id");
                var saved =
                        GasIngredients.CODEC.encodeStart(JsonOps.INSTANCE, half).getOrThrow();
                h.assertValueEqual(saved.getAsString(), gas.id(), "a saved gas");
                h.assertValueEqual(
                        GasIngredients.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow(), one, "a loaded gas");
            }
            h.assertTrue(
                    GasIngredients.CODEC
                            .parse(JsonOps.INSTANCE, new com.google.gson.JsonPrimitive("zas:no_such_gas"))
                            .isError(),
                    "a saved gas the server doesn't define loaded");
        } finally {
            buf.release();
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
            ClientReactions.accept(new ReactionDefinitionsPayload(List.of()));
        }
        h.succeed();
    }

    /**
     * Behind a closed gate the plugin registers nothing, whatever JEI asks of it and however often,
     * and the gate warns once (COMPAT-002). The gate is closed with a range this run's JEI is
     * outside; the registrations fail the test if the plugin touches them.
     */
    @GameTest(template = "empty")
    public static void compat_002_outside_its_range_the_plugin_registers_nothing_and_warns_once(GameTestHelper h) {
        ViewerGate closed = new ViewerGate(ZasJeiPlugin.JEI, "[1,2)");
        ZasJeiPlugin plugin = new ZasJeiPlugin(closed);
        ClientGasRegistry.accept(GasDefinitionsPayload.of(ServerGasRegistry.current()));
        try {
            h.assertFalse(ViewerData.gases().isEmpty(), "there are gases to register, were the gate open");
            for (int reload = 0; reload < 3; reload++) {
                plugin.registerIngredients(untouched(IModIngredientRegistration.class));
                plugin.registerCategories(untouched(IRecipeCategoryRegistration.class));
                plugin.registerRecipes(untouched(IRecipeRegistration.class));
            }
        } finally {
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
        }
        h.assertFalse(closed.on(), "the gate is open");
        h.assertValueEqual(closed.warnings(), 1, "warnings");
        h.succeed();
    }

    /** A registration that fails the test if anything is asked of it. */
    private static <T> T untouched(Class<T> type) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            throw new GameTestAssertException("the plugin called " + type.getSimpleName() + "." + method.getName());
        });
        return type.cast(proxy);
    }

    private static ReactionEntry page(List<ReactionEntry> pages, String id, boolean incomplete) {
        return pages.stream()
                .filter(p -> p.id().equals(id) && p.incomplete() == incomplete)
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no page for " + id));
    }

    private static Map<String, Double> amounts(List<GasStack> stacks) {
        Map<String, Double> out = new TreeMap<>();
        stacks.forEach(stack -> out.put(stack.gas().id(), stack.amount()));
        return out;
    }
}
