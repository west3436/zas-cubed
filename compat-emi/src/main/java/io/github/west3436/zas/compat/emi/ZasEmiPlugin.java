package io.github.west3436.zas.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiInitRegistry;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import io.github.west3436.zas.compat.viewer.GasEntry;
import io.github.west3436.zas.compat.viewer.ViewerData;
import io.github.west3436.zas.compat.viewer.ViewerGate;
import java.util.List;

/**
 * Shows every gas and every reaction in EMI (COMPAT-062), as the JEI plugin does in JEI: the same
 * entries, laid out and drawn by the same code. A gas is an EMI stack of its own kind, so asking
 * EMI for a gas's recipes or uses finds the reactions that give or take it.
 *
 * <p>This is a plugin of EMI's own because EMI loads JEI plugins only when JEI is installed too.
 * With both installed EMI leaves a mod's JEI plugin out when the mod has an EMI plugin, so the
 * pages show once.
 *
 * <p>EMI makes one of these on a client that has EMI, and nothing else does. It builds its lists
 * when a client has joined and received the recipes and tags, and again after {@code /reload}; the
 * server sends the gases and reactions before both. Outside the supported versions it registers
 * nothing (COMPAT-002).
 */
@EmiEntrypoint
public final class ZasEmiPlugin implements EmiPlugin {
    /** The EMI versions the plugin supports: 1.1.13, which it is compiled against, up to EMI 2. */
    public static final String SUPPORTED = "[1.1.13,2)";

    /** EMI's mod id. */
    public static final String EMI = "emi";

    /** One gate for the whole game, so its warning is logged once however often EMI reloads. */
    private static final ViewerGate INSTALLED = new ViewerGate(EMI, SUPPORTED);

    private final ViewerGate gate;

    /** EMI makes the plugin with this. */
    public ZasEmiPlugin() {
        this(INSTALLED);
    }

    /** A plugin behind a gate of the caller's, for tests. */
    public ZasEmiPlugin(ViewerGate gate) {
        this.gate = gate;
    }

    @Override
    public void initialize(EmiInitRegistry registry) {
        if (gate.on()) {
            Pages.serializer(registry);
        }
    }

    @Override
    public void register(EmiRegistry registry) {
        // None on a server without ZAS³, which sends no gases; then there is nothing to show.
        List<GasEntry> gases = gate.on() ? ViewerData.gases() : List.of();
        if (!gases.isEmpty()) {
            Pages.register(registry, gases, ViewerData.reactions());
        }
    }
}
