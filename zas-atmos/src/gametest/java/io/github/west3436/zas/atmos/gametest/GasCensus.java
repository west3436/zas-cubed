package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.pipe.PipeHost;
import io.github.west3436.zas.atmos.pipe.PipeNetwork;
import io.github.west3436.zas.atmos.pipe.PipeNode;
import io.github.west3436.zas.atmos.portable.PortableGasDevice;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.conservation.ConservationReport;
import io.github.west3436.zas.engine.scheduler.AtmosSnapshot;
import io.github.west3436.zas.engine.scheduler.ZoneView;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.stream.LongStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/**
 * Counts one gas across some chunks, apart from the invariant checker (CORE-073), which compares only
 * zones and so says nothing about gas in pipes (NFR-012). A test takes a total, does things, and takes
 * another.
 *
 * <p>It counts zones as of the last atmos tick, pipe networks, nodes in no network, device ports,
 * portables' stores and the tanks they hold. A zone or network with any part in a named chunk counts
 * whole, and once. It can't see:
 *
 * <ul>
 *   <li>gas on its way between a zone and a device, so it fails if a device it counts has some;
 *   <li>gas a removed node has released, until the next atmos tick;
 *   <li>gas on any other item, such as a transfer valve's tanks: add what {@link #on} finds on it;
 *   <li>an unloaded chunk's gas, which is in the world's files.
 * </ul>
 *
 * <p>Use a gas the ambient doesn't hold, or a sealed room: the ambient is a source by design.
 */
final class GasCensus {
    private GasCensus() {}

    /** The moles of a gas in the named chunks. */
    static double total(Area a, ResourceLocation gas, ChunkPos... chunks) {
        Set<Long> named = new HashSet<>();
        for (ChunkPos chunk : chunks) {
            named.add(SectionKeys.chunk(chunk.x, chunk.z));
        }
        return inZones(a, gas, named) + inDevices(a, gas, named);
    }

    /** The moles of a gas on an item: a tank's or a mined canister's. */
    static double on(ItemStack item, ResourceLocation gas) {
        GasParcel held = item.get(AtmosComponents.GAS.get());
        return held == null ? 0 : held.moles(gas);
    }

    private static double inZones(Area a, ResourceLocation gas, Set<Long> named) {
        AtmosSnapshot table = a.service().dimension(a.level).published();
        int index = table.registry().indexOf(gas.toString());
        double total = 0;
        for (ZoneView zone : table.zones()) {
            if (LongStream.of(table.zoneChunks(zone.id())).anyMatch(named::contains)) {
                total += zone.moles(index);
            }
        }
        return total;
    }

    private static double inDevices(Area a, ResourceLocation gas, Set<Long> named) {
        Set<Object> counted = Collections.newSetFromMap(new IdentityHashMap<>());
        double total = 0;
        for (AtmosDevice device : DeviceTicker.loaded(a.level)) {
            BlockPos pos = device.getBlockPos();
            if (!named.contains(SectionKeys.chunk(pos.getX() >> 4, pos.getZ() >> 4))) {
                continue;
            }
            a.h.assertTrue(device.readyToStep(), "gas is on its way at " + pos);
            if (device instanceof PortableGasDevice portable) {
                total += portable.store().moles(gas) + on(portable.tank().tank(), gas);
            }
            if (device instanceof PipeHost host) {
                for (Direction face : Direction.values()) {
                    PipeNode node = host.nodeOn(face);
                    total += node == null ? 0 : inNode(node, gas, counted);
                }
            }
        }
        return total;
    }

    private static double inNode(PipeNode node, ResourceLocation gas, Set<Object> counted) {
        PipeNetwork network = node.currentNetwork();
        if (network != null) {
            return counted.add(network) ? network.gas().moles(gas) : 0;
        }
        return counted.add(node) ? node.stored().moles(gas) : 0;
    }

    /**
     * What the conservation ledger says became of a gas that moved: a test's total plus {@link #net()}
     * should stay what it was. Call {@link #read()} each game tick with the checker on. The ledger
     * covers the whole dimension, so a test using this has a batch to itself.
     */
    static final class Ledger {
        private final Area a;
        private final int gas;
        private long read;
        private double net;
        private String unexplained;

        /** Starts from now: a report already made isn't counted. */
        Ledger(Area a, ResourceLocation gas) {
            this.a = a;
            this.gas = a.service().dimension(a.level).registry().indexOf(gas.toString());
            ConservationReport last = a.service().dimension(a.level).lastConservation();
            this.read = last == null ? Long.MIN_VALUE : last.tick();
        }

        void read() {
            ConservationReport report = a.service().dimension(a.level).lastConservation();
            if (report == null || report.tick() == read) {
                return;
            }
            read = report.tick();
            net += report.voided(gas) - report.supplied(gas);
            if (unexplained == null && !report.conserved()) {
                unexplained = "unexplained: " + report.violations();
            }
        }

        /** The moles voided at paused zones (GEO-036) less those supplied by them (GEO-038). */
        double net() {
            return net;
        }

        /** Fails if an atmos tick so far reported a change nothing explains. */
        void assertExplained() {
            a.h.assertTrue(unexplained == null, String.valueOf(unexplained));
        }
    }
}
