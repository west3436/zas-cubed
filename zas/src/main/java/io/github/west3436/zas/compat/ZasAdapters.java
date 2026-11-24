package io.github.west3436.zas.compat;

import io.github.west3436.ss13.compat.Adapters;
import io.github.west3436.ss13.compat.Adapters.Row;
import java.util.ArrayList;
import java.util.List;

/**
 * ZAS³'s adapters for other mods (COMPAT-002, SPACE-003, MEK-001, NFR-022): its table of them,
 * which SS13 Framework's loader starts.
 *
 * <p>An adapter is code that names another mod's classes, so it may only be loaded when that mod
 * is installed, in a version the adapter was written for. Each has a {@link Row} below, and {@code
 * ZasMod} hands the table to the loader ({@link Adapters#load(net.neoforged.bus.api.IEventBus,
 * net.neoforged.fml.ModContainer)}) last in its constructor: a target that isn't installed loads
 * nothing and logs nothing above debug, a version outside the row's range leaves the adapter off
 * with one warning, and otherwise the entry class's {@code public static void init(IEventBus modBus,
 * ModContainer container)} is called with {@code zas}'s mod bus and container. An entry class that
 * can't be loaded, or whose {@code init} throws, is logged once and that adapter is off (NFR-013).
 * What it had already registered stays registered, so an {@code init} should do anything that can
 * fail first.
 *
 * <p>No {@code neoforge.mods.toml} names a target, so the target may be constructed after {@code
 * zas}. An {@code init} therefore only registers: listeners, deferred registers, capabilities. It
 * calls into the target later, from those.
 */
public final class ZasAdapters {
    /** The table, which {@code ZasMod} loads; ask it whether an adapter is on. */
    public static final Adapters ADAPTERS = Adapters.of(rows());

    private ZasAdapters() {}

    /**
     * The rows. An adapter adds one line here, and nothing else in {@code zas} names it.
     *
     * <p>A row is the target's mod id, the versions of it the adapter supports (written as in {@code
     * neoforge.mods.toml}; see {@link io.github.west3436.ss13.compat.VersionGate}) and the full name
     * of the adapter's entry class, as text so that nothing here loads the class. The entry class
     * ships in this jar; it is public and has a {@code public static void init(IEventBus modBus,
     * ModContainer container)}. A target has one row.
     */
    private static List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        // One line per adapter, like this one:
        // rows.add(new Row("examplemod", "[1.2,2)", "io.github.west3436.zas.compat.examplemod.ExampleModCompat"));

        // Ad Astra (SPACE-003): 1.16.26 is the build the adapter is tested with, and 1.17 may change its API.
        rows.add(new Row("ad_astra", "[1.16.26,1.17)", "io.github.west3436.zas.compat.adastra.AdAstraCompat"));

        // Mekanism (MEK-001): its API has what the adapter uses from 10.7.15 on. No upper bound: the
        // adapter's GameTests run against the newest build every night (MEK-003).
        rows.add(new Row("mekanism", "[10.7.15,)", "io.github.west3436.zas.compat.mekanism.MekanismCompat"));

        // CC: Tweaked (COMPAT-020 to COMPAT-022): 1.113.0 is its first build for this Minecraft, and
        // the peripheral API the adapter uses is the same in its newest. The adapter's classes are
        // part of zas_atmos in this jar, since the peripherals are of the content's devices.
        rows.add(new Row("computercraft", "[1.113.0,)", "io.github.west3436.zas.atmos.compat.cc.CcCompat"));

        // Curios (COMPAT-040): 9.2.0 is its oldest build for this Minecraft, and has what the adapter
        // uses. Accessories (COMPAT-041): 1.1.0-beta.3 is its first build made for this Minecraft;
        // the 1.0.0 builds before it were made for 1.21 and the adapter was never run with one. No
        // upper bounds: the adapters' GameTests run with the newest builds too, every night
        // (COMPAT-004).
        rows.add(new Row("curios", "[9.2.0,)", "io.github.west3436.zas.compat.curios.CuriosCompat"));
        rows.add(new Row(
                "accessories", "[1.1.0-beta.3,)", "io.github.west3436.zas.compat.accessories.AccessoriesCompat"));

        // Applied Energistics 2 (COMPAT-070 to COMPAT-074): 19.0.24 is its first release for this
        // Minecraft not marked alpha or beta, and the API the adapter uses is the same in its newest.
        // No upper bound: the adapter's GameTests run with the newest build every night (COMPAT-004).
        rows.add(new Row("ae2", "[19.0.24,)", "io.github.west3436.zas.compat.ae2.Ae2Compat"));

        // PneumaticCraft: Repressurized (COMPAT-010 to COMPAT-013): 8.0.3 is its first build for this
        // Minecraft, and the API the adapter uses is the same in its newest. No upper bound: the
        // adapter's GameTests run with the newest build too, every night (COMPAT-004).
        rows.add(new Row(
                "pneumaticcraft", "[8.0.3,)", "io.github.west3436.zas.compat.pneumaticcraft.PneumaticCraftCompat"));

        // NuclearCraft: Neoteric (COMPAT-150 to COMPAT-155): 1.3.2 is its first release after the 1.3.0
        // betas and release candidates, and the fields of its fission controller that the adapter
        // reads are the same in its newest. No upper bound: the adapter's GameTests run with the
        // newest build too, every night (COMPAT-004).
        rows.add(new Row("nuclearcraft", "[1.3.2,)", "io.github.west3436.zas.compat.nuclearcraft.NuclearCraftCompat"));

        // Modern Industrialization (COMPAT-090 to COMPAT-093): 2.2.27 is its first build whose gas
        // fluids carry the c: tags of their names, which the adapter's data relies on. No upper
        // bound: the adapter's GameTests run with the newest build too, every night (COMPAT-004).
        rows.add(new Row(
                "modern_industrialization",
                "[2.2.27,)",
                "io.github.west3436.zas.compat.mi.ModernIndustrializationCompat"));

        // Create Aeronautics (COMPAT-080 to COMPAT-083): 1.3.0 is its first build on Sable 2, which
        // every later one needs, and the adapter's one hook into Sable is the same in all of them. No
        // upper bound: the adapter's GameTests run with the newest build too, every night
        // (COMPAT-004). The range is AeronauticsVersions.SUPPORTED, which its mixin plugin reads.
        rows.add(new Row("aeronautics", "[1.3.0,)", "io.github.west3436.zas.compat.aeronautics.AeronauticsCompat"));

        // Sable (COMPAT-100 to COMPAT-102): 2.0.0 is its first build of Sable 2, and what the
        // adapter uses is the same in its newest. No upper bound: the adapter's GameTests run with
        // the newest build every night (COMPAT-004).
        rows.add(new Row("sable", "[2.0.0,)", "io.github.west3436.zas.compat.sable.SableCompat"));

        // Create Cosmonautics (SPACE-012): 26.08.291 is its first build whose mod metadata gives its
        // version; the ones before call themselves 0.0NONE, which no range can place. The adapter
        // names none of its classes, only its breathing check in NeoForge's event. No upper bound:
        // the adapter's GameTests run with the newest build too, every night (COMPAT-004).
        rows.add(new Row(
                "rocketnautics", "[26.08.291,)", "io.github.west3436.zas.compat.cosmonautics.CosmonauticsCompat"));

        // Create: Northstar – Redux (SPACE-011): 0.6.6 is the first build with the oxygen and
        // temperature providers and planet data the adapter uses as they are now. No upper bound:
        // the adapter's GameTests run with the newest build too, every night (COMPAT-004). The range
        // is NorthstarVersions.SUPPORTED, which its mixin plugin reads.
        rows.add(new Row("northstar", "[0.6.6,)", "io.github.west3436.zas.compat.northstar.NorthstarCompat"));

        return rows;
    }
}
