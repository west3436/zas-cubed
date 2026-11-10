package io.github.west3436.zas.compat.emi.gametest;

import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiRegistry;
import io.github.west3436.zas.compat.VersionGate;
import io.github.west3436.zas.compat.VersionGate.Verdict;
import io.github.west3436.zas.compat.emi.ZasEmiPlugin;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The EMI plugin on a server with EMI really installed, from the release jar (COMPAT-004). EMI
 * starts its plugins only on a client, so what a server can show is everything up to that point:
 * the jar holds the plugin where EMI looks for it, the plugin accepts this EMI, the pages it would
 * register are made from what the server sends, and behind a closed gate it registers nothing.
 */
@GameTestHolder("zas")
@PrefixGameTestTemplate(false)
public final class EmiPluginTests {
    private EmiPluginTests() {}

    /**
     * The release jar holds the plugin with the annotation EMI finds plugins by, in the mod file
     * whose first mod is {@code zas}, and the EMI this run installs is inside the plugin's range.
     * EMI writes its version with the game's after it, which the range has to take.
     */
    @GameTest(template = "empty")
    public static void compat_062_the_jar_holds_the_plugin_and_it_accepts_this_emi(GameTestHelper h) {
        String plugin = ZasEmiPlugin.class.getName();
        var file = ModList.get().getAllScanData().stream()
                .filter(data -> data.getAnnotations().stream()
                        .anyMatch(a -> a.annotationType().getClassName().equals("dev.emi.emi.api.EmiEntrypoint")
                                && a.clazz().getClassName().equals(plugin)))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("no @EmiEntrypoint on " + plugin + " in the scan data"));
        // EMI credits a plugin to its file's first mod, and leaves that mod's JEI plugin out.
        h.assertValueEqual(file.getIModInfoData().get(0).getMods().get(0).getModId(), "zas", "the plugin's mod");

        String installed = ModList.get()
                .getModContainerById(ZasEmiPlugin.EMI)
                .orElseThrow(() -> new GameTestAssertException("EMI isn't installed in this run"))
                .getModInfo()
                .getVersion()
                .toString();
        h.assertTrue(installed.contains("+"), "EMI's version is written without the game's: " + installed);
        h.assertValueEqual(VersionGate.check(installed, ZasEmiPlugin.SUPPORTED), Verdict.SUPPORTED, "EMI " + installed);
        ViewerGate gate = new ViewerGate(ZasEmiPlugin.EMI, ZasEmiPlugin.SUPPORTED);
        h.assertTrue(gate.on(), "the plugin's gate is closed to EMI " + installed);
        h.assertValueEqual(gate.warnings(), 0, "warnings");
        h.succeed();
    }

    /**
     * What the server sends a client becomes the pages the plugin registers: every gas the server
     * defines, and every reaction it runs with its incomplete form.
     */
    @GameTest(template = "empty")
    public static void compat_062_the_pages_are_every_gas_and_reaction_the_server_sends(GameTestHelper h) {
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
                h.assertValueEqual(ViewerData.gas(gas.id()).orElseThrow(), gas, "a gas found by its id");
            }
            h.assertTrue(ViewerData.gas("zas:no_such_gas").isEmpty(), "a gas the server doesn't define was found");

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
        } finally {
            buf.release();
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
            ClientReactions.accept(new ReactionDefinitionsPayload(List.of()));
        }
        h.succeed();
    }

    /**
     * Behind a closed gate the plugin registers nothing, however often EMI reloads, and the gate
     * warns once (COMPAT-002). The gate is closed with a range this run's EMI is outside; the
     * registries fail the test if the plugin touches them.
     */
    @GameTest(template = "empty")
    public static void compat_002_outside_its_range_the_plugin_registers_nothing_and_warns_once(GameTestHelper h) {
        ViewerGate closed = new ViewerGate(ZasEmiPlugin.EMI, "[2,3)");
        ZasEmiPlugin plugin = new ZasEmiPlugin(closed);
        ClientGasRegistry.accept(GasDefinitionsPayload.of(ServerGasRegistry.current()));
        try {
            h.assertFalse(ViewerData.gases().isEmpty(), "there are gases to register, were the gate open");
            for (int reload = 0; reload < 3; reload++) {
                plugin.initialize(untouched(EmiInitRegistry.class));
                plugin.register(untouched(EmiRegistry.class));
            }
        } finally {
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
        }
        h.assertFalse(closed.on(), "the gate is open");
        h.assertValueEqual(closed.warnings(), 1, "warnings");
        h.succeed();
    }

    /** A registry that fails the test if anything is asked of it. */
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
