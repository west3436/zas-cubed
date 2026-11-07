package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.device.DeviceData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Which central atmospherics controller each area air controller of a level belongs to (CONT-116 to
 * CONT-121), saved with the level as {@code data/zas_atmos_central_claims.dat}.
 *
 * <p>One table per level, keyed by the area air controller, holds every claim, so an area air
 * controller belongs to at most one central controller even while either is unloaded, and two central
 * controllers never fight over one. Each claim keeps its zone power priority, when it was made, for
 * the order within a priority, and the area air controller's last readings, which the central
 * controller's GUI shows while it is out of contact (CONT-123).
 *
 * <p>Saved with a {@code FormatVersion}, which {@link DeviceData#migrate(CompoundTag, List)} brings
 * forward (DATA-012). Server thread only.
 */
public final class CentralClaims extends SavedData {
    /** The file's name in the level's {@code data} folder. */
    public static final String NAME = "zas_atmos_central_claims";

    /**
     * What brings a saved table forward, oldest first. Version 0, a table with no version, has
     * version 1's fields.
     */
    private static final List<Consumer<CompoundTag>> FORMAT_STEPS = List.of(tag -> {});

    private static final Factory<CentralClaims> FACTORY = new Factory<>(CentralClaims::new, CentralClaims::load, null);

    /**
     * An area air controller's last readings, as its central controller last saw them.
     *
     * @param pressure its zone's pressure in kPa, or NaN before its first reading
     * @param temperature its zone's temperature in K, or NaN before its first reading
     * @param danger its danger level, 0 to 2
     * @param powered whether it paid its own draw at its last step
     */
    public record Readings(double pressure, double temperature, int danger, boolean powered) {
        /** Nothing read yet. */
        public static final Readings NONE = new Readings(Double.NaN, Double.NaN, 0, false);
    }

    /** One claim. */
    private static final class Claim {
        BlockPos central;
        ZonePriority priority = ZonePriority.NORMAL;
        long order;
        Readings last = Readings.NONE;
    }

    private final Map<BlockPos, Claim> claims = new LinkedHashMap<>();
    private long nextOrder;

    private CentralClaims() {}

    /** A level's table, made empty the first time it's asked for. */
    public static CentralClaims of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    /** The central controller an area air controller belongs to, if any. */
    public Optional<BlockPos> centralOf(BlockPos controller) {
        Claim c = claims.get(controller);
        return c == null ? Optional.empty() : Optional.of(c.central);
    }

    /** The area air controllers a central controller has claimed, in the order it claimed them. */
    public List<BlockPos> claimedBy(BlockPos central) {
        List<Map.Entry<BlockPos, Claim>> mine = new ArrayList<>();
        for (Map.Entry<BlockPos, Claim> e : claims.entrySet()) {
            if (e.getValue().central.equals(central)) {
                mine.add(e);
            }
        }
        mine.sort(Comparator.comparingLong(e -> e.getValue().order));
        List<BlockPos> out = new ArrayList<>(mine.size());
        mine.forEach(e -> out.add(e.getKey()));
        return out;
    }

    /**
     * Claims an area air controller for a central controller, if no other has it (CONT-116).
     *
     * @return whether the central controller has it now
     */
    public boolean claim(BlockPos controller, BlockPos central) {
        Claim c = claims.get(controller);
        if (c != null) {
            return c.central.equals(central);
        }
        put(controller, central);
        return true;
    }

    /**
     * Gives an area air controller to a central controller, whichever had it (CONT-118). It joins
     * at the end of the new one's order, at Normal priority. Giving it to the one it belongs to
     * changes nothing.
     */
    public void move(BlockPos controller, BlockPos central) {
        Claim c = claims.get(controller);
        if (c == null || !c.central.equals(central)) {
            put(controller, central);
        }
    }

    private void put(BlockPos controller, BlockPos central) {
        Claim c = new Claim();
        c.central = central.immutable();
        c.order = nextOrder++;
        claims.put(controller.immutable(), c);
        setDirty();
    }

    /** Releases an area air controller from its central controller (CONT-117, CONT-119). */
    public void release(BlockPos controller) {
        if (claims.remove(controller) != null) {
            setDirty();
        }
    }

    /** Releases every area air controller a central controller has (CONT-119). */
    public void releaseAll(BlockPos central) {
        if (claims.values().removeIf(c -> c.central.equals(central))) {
            setDirty();
        }
    }

    /** A claimed area air controller's zone power priority; Normal if it isn't claimed (CONT-121). */
    public ZonePriority priority(BlockPos controller) {
        Claim c = claims.get(controller);
        return c == null ? ZonePriority.NORMAL : c.priority;
    }

    /** Sets a claimed area air controller's zone power priority; does nothing if it isn't claimed. */
    public void setPriority(BlockPos controller, ZonePriority priority) {
        Claim c = claims.get(controller);
        if (c != null && c.priority != priority) {
            c.priority = priority;
            setDirty();
        }
    }

    /** A claimed area air controller's last readings; {@link Readings#NONE} if it has none. */
    public Readings last(BlockPos controller) {
        Claim c = claims.get(controller);
        return c == null ? Readings.NONE : c.last;
    }

    /** Records a claimed area air controller's readings; does nothing if it isn't claimed. */
    public void record(BlockPos controller, Readings readings) {
        Claim c = claims.get(controller);
        if (c != null && !c.last.equals(readings)) {
            c.last = readings;
            setDirty();
        }
    }

    // ---- Saving ---------------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(DeviceData.VERSION_KEY, FORMAT_STEPS.size());
        tag.putLong("NextOrder", nextOrder);
        ListTag list = new ListTag();
        claims.forEach((controller, c) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("Controller", controller.asLong());
            e.putLong("Central", c.central.asLong());
            e.putString("Priority", c.priority.key());
            e.putLong("Order", c.order);
            e.putDouble("Pressure", c.last.pressure());
            e.putDouble("Temperature", c.last.temperature());
            e.putInt("Danger", c.last.danger());
            e.putBoolean("Powered", c.last.powered());
            list.add(e);
        });
        tag.put("Claims", list);
        return tag;
    }

    /** Reads a saved table, brought forward first (DATA-012). Public for tests. */
    public static CentralClaims load(CompoundTag tag, HolderLookup.Provider registries) {
        DeviceData.migrate(tag, FORMAT_STEPS);
        CentralClaims out = new CentralClaims();
        out.nextOrder = tag.getLong("NextOrder");
        for (Tag t : tag.getList("Claims", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            Claim c = new Claim();
            c.central = BlockPos.of(e.getLong("Central"));
            c.priority = ZonePriority.byKey(e.getString("Priority"));
            c.order = e.getLong("Order");
            c.last = new Readings(
                    e.contains("Pressure") ? e.getDouble("Pressure") : Double.NaN,
                    e.contains("Temperature") ? e.getDouble("Temperature") : Double.NaN,
                    Math.max(0, Math.min(2, e.getInt("Danger"))),
                    e.getBoolean("Powered"));
            out.claims.put(BlockPos.of(e.getLong("Controller")), c);
            out.nextOrder = Math.max(out.nextOrder, c.order + 1);
        }
        return out;
    }
}
