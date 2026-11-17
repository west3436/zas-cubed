package io.github.west3436.zas.compat.ae2.gametest;

import static io.github.west3436.zas.compat.ae2.gametest.Lab.CO2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.N2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.O2;
import static io.github.west3436.zas.compat.ae2.gametest.Lab.T;

import appeng.api.implementations.parts.IStorageMonitorPart;
import appeng.api.parts.IPart;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.IConfigInvHost;
import appeng.parts.reporting.AbstractMonitorPart;
import appeng.util.prioritylist.IPartitionList;
import io.github.west3436.zas.compat.ae2.GasKey;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Filters set to a gas match that gas however the gas's id was made (COMPAT-070, COMPAT-071,
 * COMPAT-073). AE2 compares keys by their primary key, by identity, and in play a filter's key comes
 * from saved data, a window or EMI, never from the same id object as the gas a tank reports. So the
 * filters here are set from ids parsed anew, or loaded from saved data, never from {@link Lab}'s
 * constants, which are the ones the tanks hold.
 */
@GameTestHolder(Ae2Tests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class Ae2KeyTests {
    private static final String BATCH = "zas_ae2_keys";
    private static final int WAIT = 600;

    private Ae2KeyTests() {}

    /** An id equal to a gas's, but a new object, as a saved filter or a packet gives. */
    private static ResourceLocation fresh(ResourceLocation gas) {
        return ResourceLocation.parse(gas.toString());
    }

    private static void filter(IPart bus, ResourceLocation gas) {
        ((IConfigInvHost) bus).getConfig().setStack(0, new GenericStack(GasKey.of(fresh(gas)), 1));
    }

    /**
     * There is one key for each gas: made from two equal ids, loaded from saved data or read from a
     * packet, it is the same object, and so is its primary key, so AE2's filter lists match.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void compat_070_a_gas_has_one_key_however_its_id_was_made(GameTestHelper h) {
        ResourceLocation id = fresh(O2);
        h.assertFalse(id == O2, "the test's id isn't a new object");
        GasKey key = GasKey.of(O2);
        h.assertTrue(GasKey.of(id) == key, "two keys of O2");
        h.assertTrue(GasKey.of(id).getPrimaryKey() == key.getPrimaryKey(), "two primary keys of O2");

        var registries = h.getLevel().registryAccess();
        CompoundTag saved = key.toTagGeneric(registries);
        h.assertTrue(AEKey.fromTagGeneric(registries, saved) == key, "O2's key saved and loaded");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        AEKey.writeKey(buffer, key);
        h.assertTrue(AEKey.readKey(buffer) == key, "O2's key sent and read");

        IPartitionList.Builder builder = IPartitionList.builder();
        builder.add(GasKey.of(fresh(O2)));
        IPartitionList list = builder.build();
        h.assertTrue(list.isListed(GasKey.of(fresh(O2))), "an O2 filter misses O2");
        h.assertFalse(list.isListed(GasKey.of(N2)), "an O2 filter lists N2");
        h.succeed();
    }

    /**
     * Each bus with a gas set from a new id: a storage bus shows only its gas, an import bus takes
     * only its gas, an export bus puts out only its gas, and a storage monitor counts its gas.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_073_buses_and_monitors_match_a_gas_set_from_a_new_id(GameTestHelper h) {
        Lab lab = new Lab(h, 13);
        lab.tank(2, 1, 0, 1000, T).with(O2, 6.0).with(N2, 1.0);
        TestTank target = lab.tank(0, 1, 0, 1000, T);
        TestTank source = lab.tank(0, 1, 2, 1000, T).with(CO2, 3.0).with(N2, 2.0);
        lab.cable(1, 1, 0);
        filter(lab.part(1, 1, 0, Direction.WEST, "export_bus"), N2);
        IPart store = lab.part(1, 1, 0, Direction.EAST, "storage_bus");
        filter(store, O2);
        IPart monitor = lab.part(1, 1, 0, Direction.UP, "storage_monitor");
        ((AbstractMonitorPart) monitor).setConfiguredItem(GasKey.of(fresh(O2)));
        lab.energy(1, 1, -1);
        lab.cable(1, 1, 1);
        lab.cable(1, 1, 2);
        filter(lab.part(1, 1, 2, Direction.WEST, "import_bus"), CO2);
        // The imported CO2 needs somewhere to go: a tank behind an unfiltered storage bus.
        TestTank spare = lab.tank(2, 1, 2, 1000, T);
        lab.part(1, 1, 2, Direction.EAST, "storage_bus");
        h.succeedWhen(() -> {
            KeyCounter stored = Lab.stored(store);
            h.assertValueEqual(stored.get(GasKey.of(fresh(O2))), 6000L, "the O2 the filtered storage bus shows");
            lab.close(spare.moles(CO2), 3.0, "the CO2 the import bus took");
            lab.close(source.moles(N2), 2.0, "the N2 the import bus left, which it isn't set to");
            h.assertValueEqual(stored.get(GasKey.of(N2)), 0L, "the N2 the O2 storage bus shows");
            lab.close(target.moles(N2), 0, "the N2 exported, which the network can't reach");
            h.assertValueEqual(((IStorageMonitorPart) monitor).getAmount(), 6000L, "the O2 the storage monitor counts");
            lab.done();
        });
    }

    /**
     * A storage bus's filter saved and loaded, as a world restart does, still matches its gas, and an
     * export bus's loaded filter still puts its gas out.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = WAIT)
    public static void compat_073_a_saved_filter_matches_its_gas(GameTestHelper h) {
        Lab lab = new Lab(h, 14);
        lab.tank(2, 1, 0, 1000, T).with(O2, 4.0).with(N2, 3.0);
        TestTank target = lab.tank(0, 1, 0, 1000, T);
        lab.cable(1, 1, 0);
        IPart store = lab.part(1, 1, 0, Direction.EAST, "storage_bus");
        IPart export = lab.part(1, 1, 0, Direction.WEST, "export_bus");
        lab.energy(1, 2, 0);
        var registries = h.getLevel().registryAccess();
        for (IPart part : new IPart[] {store, export}) {
            IConfigInvHost host = (IConfigInvHost) part;
            host.getConfig().setStack(0, new GenericStack(GasKey.of(fresh(O2)), 1));
            CompoundTag saved = new CompoundTag();
            host.getConfig().writeToChildTag(saved, "config", registries);
            host.getConfig().clear();
            host.getConfig().readFromChildTag(saved, "config", registries);
            h.assertTrue(host.getConfig().getKey(0) instanceof GasKey, "the loaded filter of " + part);
        }
        h.succeedWhen(() -> {
            lab.close(target.moles(O2), 4.0, "the O2 exported through the loaded filters");
            lab.close(target.moles(N2), 0, "the N2 exported, which the loaded filters leave out");
            lab.done();
        });
    }
}
