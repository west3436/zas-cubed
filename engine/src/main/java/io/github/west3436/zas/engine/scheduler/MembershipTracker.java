package io.github.west3436.zas.engine.scheduler;

import io.github.west3436.zas.engine.SectionKeys;
import io.github.west3436.zas.engine.zone.MembershipListener;
import io.github.west3436.zas.engine.zone.Zone;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongToDoubleFunction;

/**
 * The worker's zone membership (architecture §4.4), kept up to date change by change as the zone
 * graph reports them ({@link MembershipListener}), and published as immutable {@link Membership}
 * tables.
 *
 * <ul>
 *   <li>A cell joining or leaving a zone (creation, joins, leaves, fills, splits, dissolution,
 *       loading and eviction) changes one position and one palette entry of its section: O(1).
 *   <li>A merge renames the absorbed zone's palette entries in its sections: O(its sections), with
 *       no pass over its cells.
 *   <li>{@link #publish} freezes the sections changed since the last table and copies the two
 *       reference arrays, patching in the changed entries: O(changed sections + sections). Sections
 *       and zones that didn't change are shared between tables (copy-on-write; a frozen section is
 *       copied before its next change).
 * </ul>
 *
 * <p>Worker thread only. Hash maps are used for lookups only; tables list sections and zones in
 * ascending order (architecture §3.5).
 */
final class MembershipTracker implements MembershipListener {
    private final CellNaming naming;
    /** The graph's volume of a zoned cell, for a position's first cell when a second one joins it. */
    private final LongToDoubleFunction cellVolume;

    private final Map<Long, SectionMembers> sections = new HashMap<>();
    private final TreeMap<Long, Membership.ZoneSections> zones = new TreeMap<>();

    private final ArrayList<SectionMembers> dirty = new ArrayList<>();
    private final Set<Long> dirtyZones = new HashSet<>();
    private boolean sectionSetChanged;
    private boolean zoneSetChanged;
    private Membership published = Membership.EMPTY;

    private long cachedKey;
    private SectionMembers cached;

    MembershipTracker(CellNaming naming, LongToDoubleFunction cellVolume) {
        this.naming = naming;
        this.cellVolume = cellVolume;
    }

    // ---- Live queries --------------------------------------------------------------------

    /** Whether a section holds zoned cells now. */
    boolean hasSection(long key) {
        return sections.containsKey(key);
    }

    /** The chunks a zone has cells in now, ascending; empty if it has none. Not a copy. */
    long[] chunks(long zoneId) {
        Membership.ZoneSections z = zones.get(zoneId);
        return z == null ? new long[0] : z.chunks();
    }

    // ---- Publishing ----------------------------------------------------------------------

    /**
     * The membership as of now, as an immutable table. Returns the last table when nothing has
     * changed since.
     */
    Membership publish() {
        if (dirty.isEmpty() && dirtyZones.isEmpty() && !sectionSetChanged && !zoneSetChanged) {
            return published;
        }
        long[] keys;
        SectionMembers[] values;
        if (sectionSetChanged) {
            keys = new long[sections.size()];
            int n = 0;
            for (long k : sections.keySet()) {
                keys[n++] = k;
            }
            Arrays.sort(keys);
            values = new SectionMembers[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = sections.get(keys[i]);
            }
        } else {
            keys = published.sectionKeys;
            values = published.sections.clone();
            for (SectionMembers s : dirty) {
                values[Arrays.binarySearch(keys, s.key)] = s;
            }
        }
        for (SectionMembers s : dirty) {
            s.frozen = true;
            s.dirty = false;
        }
        dirty.clear();

        long[] ids;
        Membership.ZoneSections[] records;
        if (zoneSetChanged) {
            ids = new long[zones.size()];
            records = new Membership.ZoneSections[ids.length];
            int n = 0;
            for (Map.Entry<Long, Membership.ZoneSections> e : zones.entrySet()) {
                ids[n] = e.getKey();
                records[n++] = e.getValue();
            }
        } else {
            ids = published.zoneIds;
            records = published.zones;
            if (!dirtyZones.isEmpty()) {
                records = records.clone();
                for (long id : dirtyZones) {
                    records[Arrays.binarySearch(ids, id)] = zones.get(id);
                }
            }
        }
        dirtyZones.clear();
        sectionSetChanged = false;
        zoneSetChanged = false;
        published = new Membership(naming, keys, values, ids, records);
        return published;
    }

    // ---- The graph's changes (MembershipListener) -------------------------------------------

    @Override
    public void cellAdded(long cell, Zone zone, double volume) {
        long block = naming.position(cell);
        long key = SectionKeys.sectionOfBlockKey(block);
        int index = index(block);
        int sub = naming.subIndex(cell);
        SectionMembers s = writable(key, true);
        long id = zone.id();
        int e = s.findEntry(id);
        if (e == 0) {
            e = s.newEntry(id);
            addZoneSection(id, key);
        }
        s.addToEntry(e, volume);
        int l = s.local(index);
        if (l == 0) {
            if (sub == 0) {
                s.setLocal(index, e);
            } else {
                s.setLocal(index, SectionMembers.MULTI);
                s.sideInsert(index, cell, e, volume);
            }
        } else if (l == SectionMembers.MULTI) {
            if (s.sideFind(index, cell) >= 0) {
                throw outOfStep("added twice", cell);
            }
            s.sideInsert(index, cell, e, volume);
        } else {
            // The position's first cell is zoned alone: move it to the side table with the new one.
            long first = naming.cell(block, 0);
            if (first == cell) {
                throw outOfStep("added twice", cell);
            }
            s.setLocal(index, SectionMembers.MULTI);
            s.sideInsert(index, first, l, cellVolume.applyAsDouble(first));
            s.sideInsert(index, cell, e, volume);
        }
    }

    @Override
    public void cellRemoved(long cell, Zone zone, double volume) {
        long block = naming.position(cell);
        long key = SectionKeys.sectionOfBlockKey(block);
        int index = index(block);
        SectionMembers s = writable(key, false);
        if (s == null) {
            throw outOfStep("removed but not zoned", cell);
        }
        int l = s.local(index);
        int e;
        if (l == SectionMembers.MULTI) {
            int i = s.sideFind(index, cell);
            if (i < 0) {
                throw outOfStep("removed but not zoned", cell);
            }
            e = s.sideEntry(i);
            s.sideRemove(i);
            int left = s.sideCount(index);
            if (left == 0) {
                s.setLocal(index, 0);
            } else if (left == 1) {
                int only = s.sideFirst(index);
                if (naming.subIndex(s.sideCell(only)) == 0) {
                    s.setLocal(index, s.sideEntry(only));
                    s.sideRemove(only);
                }
            }
        } else {
            if (l == 0 || naming.subIndex(cell) != 0) {
                throw outOfStep("removed but not zoned", cell);
            }
            e = l;
            s.setLocal(index, 0);
        }
        long id = s.zoneOfEntry(e);
        if (id != zone.id()) {
            throw outOfStep("removed from zone " + zone.id() + " but held for " + id, cell);
        }
        if (s.removeFromEntry(e, volume) && s.findEntry(id) == 0) {
            removeZoneSection(id, key);
        }
        if (s.positions() == 0) {
            sections.remove(key);
            sectionSetChanged = true;
            cached = null;
        }
    }

    @Override
    public void cellResized(long cell, Zone zone, double oldVolume, double newVolume) {
        long block = naming.position(cell);
        int index = index(block);
        SectionMembers s = writable(SectionKeys.sectionOfBlockKey(block), false);
        if (s == null) {
            throw outOfStep("resized but not zoned", cell);
        }
        int e;
        if (s.local(index) == SectionMembers.MULTI) {
            int i = s.sideFind(index, cell);
            if (i < 0) {
                throw outOfStep("resized but not zoned", cell);
            }
            e = s.sideEntry(i);
            s.setSideVolume(i, newVolume);
        } else {
            e = s.entryOf(index, cell, naming.subIndex(cell));
            if (e == 0) {
                throw outOfStep("resized but not zoned", cell);
            }
        }
        s.resizeEntry(e, newVolume - oldVolume);
    }

    @Override
    public void zoneMerged(Zone from, Zone into) {
        Membership.ZoneSections absorbed = zones.remove(from.id());
        if (absorbed == null) {
            return;
        }
        zoneSetChanged = true;
        dirtyZones.remove(from.id());
        for (long key : absorbed.sections()) {
            writable(key, false).renameZone(from.id(), into.id());
        }
        Membership.ZoneSections kept = zones.get(into.id());
        zones.put(into.id(), kept == null ? absorbed : kept.union(absorbed));
        dirtyZones.add(into.id());
    }

    // ---- Helpers ---------------------------------------------------------------------------

    /** The section to change, copied first if it was published; created if asked to. */
    private SectionMembers writable(long key, boolean create) {
        SectionMembers s = cached != null && cachedKey == key ? cached : sections.get(key);
        if (s == null) {
            if (!create) {
                return null;
            }
            s = new SectionMembers(key);
            sections.put(key, s);
            sectionSetChanged = true;
        } else if (s.frozen) {
            s = s.copy();
            sections.put(key, s);
        }
        if (!s.dirty) {
            s.dirty = true;
            dirty.add(s);
        }
        cachedKey = key;
        cached = s;
        return s;
    }

    private void addZoneSection(long id, long key) {
        Membership.ZoneSections z = zones.get(id);
        if (z == null) {
            zones.put(id, Membership.ZoneSections.of(new long[] {key}));
            zoneSetChanged = true;
        } else {
            zones.put(id, z.with(key));
            dirtyZones.add(id);
        }
    }

    private void removeZoneSection(long id, long key) {
        Membership.ZoneSections z = zones.get(id).without(key);
        if (z.sections().length == 0) {
            zones.remove(id);
            dirtyZones.remove(id);
            zoneSetChanged = true;
        } else {
            zones.put(id, z);
            dirtyZones.add(id);
        }
    }

    private static int index(long block) {
        return SectionKeys.index(
                SectionKeys.blockX(block) & 15, SectionKeys.blockY(block) & 15, SectionKeys.blockZ(block) & 15);
    }

    private IllegalStateException outOfStep(String what, long cell) {
        return new IllegalStateException("zone membership out of step with the graph: cell " + cell + " ("
                + naming.position(cell) + ") " + what);
    }
}
