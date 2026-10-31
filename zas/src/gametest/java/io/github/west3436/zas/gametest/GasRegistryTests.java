package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.engine.gas.GasDefinition;
import io.github.west3436.zas.engine.gas.GasRegistry;
import io.github.west3436.zas.gas.ClientGasRegistry;
import io.github.west3436.zas.gas.GasDefinitionsPayload;
import io.github.west3436.zas.gas.ServerGasRegistry;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** Gas definitions from datapacks, using the gases in this source set's {@code data/zas_gametest/zas/gas}. */
@GameTestHolder(ZasMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasRegistryTests {
    private GasRegistryTests() {}

    /** A datapack gas loads with every field (CORE-010). */
    @GameTest(template = "empty")
    public static void core_010_datapack_gas_loads(GameTestHelper h) {
        GasRegistry registry = ServerGasRegistry.current();
        checkTestGas(h, registry, "server");
        GasDefinition plain = registry.find("zas_gametest:plain_gas").orElse(null);
        h.assertTrue(plain != null, "zas_gametest:plain_gas did not load");
        h.assertTrue(plain.visibility().isEmpty(), "visibility is optional");
        h.assertValueEqual(plain.molarHeatCapacity(), 12.5, "plain_gas molar heat capacity");
        // The standard-air gases ship with zas, under the ids the API's StandardAir uses.
        h.assertTrue(registry.find("zas:nitrogen").isPresent(), "zas:nitrogen did not load");
        h.assertTrue(registry.find("zas:oxygen").isPresent(), "zas:oxygen did not load");
        h.succeed();
    }

    /**
     * The sync payload carries every definition to the client-side registry
     * unchanged (CORE-014). The payload goes through its network codec, as it
     * does on the wire; a GameTest server has no client to receive it.
     */
    @GameTest(template = "empty")
    public static void core_014_gas_definitions_sync_to_clients(GameTestHelper h) {
        GasRegistry server = ServerGasRegistry.current();
        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            GasDefinitionsPayload.STREAM_CODEC.encode(buf, GasDefinitionsPayload.of(server));
            GasDefinitionsPayload received = GasDefinitionsPayload.STREAM_CODEC.decode(buf);
            h.assertTrue(buf.readableBytes() == 0, "payload left unread bytes");

            ClientGasRegistry.accept(received);
            GasRegistry client = ClientGasRegistry.current();
            checkTestGas(h, client, "client");
            h.assertValueEqual(client, server, "client registry");
            h.assertValueEqual(
                    client.indexOf("zas_gametest:test_gas"), server.indexOf("zas_gametest:test_gas"), "index");
        } finally {
            buf.release();
            ClientGasRegistry.accept(GasDefinitionsPayload.of(GasRegistry.EMPTY));
        }
        h.succeed();
    }

    private static void checkTestGas(GameTestHelper h, GasRegistry registry, String side) {
        GasDefinition gas = registry.find("zas_gametest:test_gas").orElse(null);
        h.assertTrue(gas != null, side + " registry lacks zas_gametest:test_gas: " + registry);
        h.assertValueEqual(gas.nameKey(), "gas.zas_gametest.test_gas", side + " name");
        h.assertValueEqual(gas.formula(), "Tg₂", side + " formula");
        h.assertValueEqual(gas.molarMass(), 0.05, side + " molar mass");
        h.assertValueEqual(gas.molarHeatCapacity(), 25.5, side + " molar heat capacity");
        h.assertTrue(gas.visibility().isPresent(), side + " visibility missing");
        h.assertValueEqual(gas.visibility().get().rgb(), 0x80FF20, side + " colour");
        h.assertValueEqual(gas.visibility().get().densityThreshold(), 0.01, side + " density threshold");
    }
}
