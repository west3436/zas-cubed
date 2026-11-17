package io.github.west3436.zas.compat.ae2;

import appeng.api.behaviors.ExternalStorageStrategy;
import appeng.api.behaviors.GenericSlotCapacities;
import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackImportStrategy;
import appeng.api.stacks.AEKeyType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Starts the Applied Energistics 2 adapter (COMPAT-070 to COMPAT-077). {@code zas}'s adapter loader
 * calls {@link #init} by name, and only with AE2 installed in a version its row supports (COMPAT-001,
 * COMPAT-002), so this class may name AE2's.
 *
 * <p>It adds gases as a type of stack ME storage holds ({@link GasKeyType}), the import, export and
 * storage buses for blocks that provide {@code IGasHandler} ({@link GasBuses}), and on a client the
 * way a gas is drawn in AE2's windows ({@link GasKeyRendering}), and the gas storage cells ({@link
 * GasCells}). It needs nothing of the content mod,
 * so it is the same with the content off; only fewer blocks offer gas.
 */
public final class Ae2Compat {
    /** The units of a gas one slot of an ME interface or pattern provider holds: 160 mol. */
    public static final long SLOT_CAPACITY = 160L * GasUnits.PER_MOLE;

    private static volatile boolean active;
    private static volatile boolean registered;

    private Ae2Compat() {}

    /**
     * The adapter loader's way in. It only registers: AE2 may not be constructed yet.
     *
     * @param modBus the mod bus of the mod the adapter ships in
     * @param container that mod's container, which holds the adapter's config file
     */
    public static void init(IEventBus modBus, ModContainer container) {
        if (active) {
            return;
        }
        Ae2Settings.register(container);
        modBus.addListener(Ae2Compat::registerType);
        modBus.addListener(GasCells::register);
        modBus.addListener(GasCells::creativeTab);
        modBus.addListener(Ae2Compat::setup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            GasKeyRendering.init(modBus);
            GasCellsClient.init(modBus);
        }
        active = true;
    }

    /** Whether the adapter started. False where AE2 is absent or unsupported. Any thread. */
    public static boolean isActive() {
        return active;
    }

    /** Whether the buses were registered: false until common setup. Any thread. */
    public static boolean registered() {
        return registered;
    }

    private static void registerType(RegisterEvent event) {
        event.register(AEKeyType.REGISTRY_KEY, helper -> helper.register(GasKeyType.ID, GasKeyType.TYPE));
    }

    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            StackImportStrategy.register(GasKeyType.TYPE, GasBuses::importing);
            StackExportStrategy.register(GasKeyType.TYPE, GasBuses::exporting);
            ExternalStorageStrategy.register(GasKeyType.TYPE, GasBuses::external);
            GenericSlotCapacities.register(GasKeyType.TYPE, SLOT_CAPACITY);
            GasCells.upgrades();
            registered = true;
        });
    }
}
