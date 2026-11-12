package io.github.west3436.zas.atmos.device;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.controller.CentralClaims;
import io.github.west3436.zas.atmos.gas.GasCodecs;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.persistence.SavedWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Unit;
import org.junit.jupiter.api.Test;

/**
 * The content mod's part of a world saved by each release reads with this code (DATA-012): its block
 * entities, the gas in them, the data components on items, the central controllers' claims and the
 * primed TTV. The world is the framework's fixture, which {@link SavedWorld} describes; its GameTest
 * {@code SavedWorldTests} loads the same world in a running game.
 *
 * <p>0.1.0's world is S-09's station, built by console commands: a block entity of every kind, pipe
 * networks across chunk borders, and a chest of items with every data component.
 */
class SavedWorldDataTest {
    private static final SavedWorld RELEASE_0_1_0 = SavedWorld.of("0.1.0");
    private static final String MOD = "zas_atmos:";
    private static final ResourceLocation CO2 = ResourceLocation.fromNamespaceAndPath("zas", "carbon_dioxide");
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");

    /** Every kind of block entity the content mod had at 0.1.0. */
    private static final Set<String> KINDS_0_1_0 = Set.of(
            "pipe",
            "pipe_cap",
            "connector_port",
            "canister",
            "vent",
            "scrubber",
            "passive_vent",
            "area_air_controller",
            "central_atmos_controller",
            "fire_alarm",
            "generator",
            "firelock",
            "airlock",
            "airlock_controller",
            "air_sensor",
            "firelock_door",
            "heater",
            "air_conditioner",
            "portable_scrubber",
            "portable_pump",
            "recharger",
            "igniter",
            "pressure_pump",
            "volume_pump",
            "filter",
            "mixer",
            "valve",
            "electrolyzer",
            "radiator_pipe",
            "radiator_adaptor",
            "heat_exchanger",
            "tank_transfer_valve",
            "tank_holder");

    /** The area air controller in room R, and the central controller that claimed it. */
    private static final BlockPos CONTROLLER = new BlockPos(643, -59, 2);

    private static final BlockPos CONSOLE = new BlockPos(653, -60, 3);

    /**
     * Each block entity as 0.1.0 wrote it to the region file, by position, without the {@code
     * keepPacked} the game adds to every block entity in a chunk.
     */
    private static Map<String, CompoundTag> saved() {
        Map<String, CompoundTag> out = new TreeMap<>();
        for (JsonElement c : RELEASE_0_1_0.expected().getAsJsonArray("chunks")) {
            CompoundTag chunk = RELEASE_0_1_0.chunk(
                    c.getAsJsonArray().get(0).getAsInt(),
                    c.getAsJsonArray().get(1).getAsInt());
            for (Tag t : chunk.getList("block_entities", Tag.TAG_COMPOUND)) {
                CompoundTag tag = (CompoundTag) t;
                assertTrue(tag.contains("keepPacked"), "a block entity saved with its chunk");
                tag.remove("keepPacked");
                out.put(tag.getInt("x") + " " + tag.getInt("y") + " " + tag.getInt("z"), tag);
            }
        }
        return out;
    }

    /** Each block entity as 0.1.0's console showed it just before it stopped, by position. */
    private static Map<String, CompoundTag> told() throws CommandSyntaxException {
        Map<String, CompoundTag> out = new TreeMap<>();
        JsonObject blocks = RELEASE_0_1_0.expected().getAsJsonObject("block_entities");
        for (Map.Entry<String, JsonElement> e : blocks.entrySet()) {
            out.put(e.getKey(), TagParser.parseTag(e.getValue().getAsString()));
        }
        return out;
    }

    private static <A> A read(Codec<A> format, Tag saved) {
        return ItemData.versioned(format).parse(NbtOps.INSTANCE, saved).getOrThrow();
    }

    /** A tank's gas, from its saved stack. */
    private static GasParcel gas(CompoundTag stack) {
        return read(GasCodecs.PARCEL, stack.getCompound("components").get(MOD + "gas"));
    }

    /** The stacks of a saved container component, each with its slot. */
    private static List<CompoundTag> contents(CompoundTag components, String component) {
        CompoundTag saved = components.getCompound(component);
        assertEquals(1, saved.getInt(DeviceData.VERSION_KEY), component);
        List<CompoundTag> out = new ArrayList<>();
        ListTag slots = saved.getList(ItemData.VALUE_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < slots.size(); i++) {
            assertEquals(i, slots.getCompound(i).getInt("slot"), component);
            out.add(slots.getCompound(i).getCompound("item"));
        }
        return out;
    }

    private static void holds(GasParcel gas, double kelvin, Map<ResourceLocation, Double> moles, String what) {
        assertEquals(moles, gas.moles(), what);
        assertEquals(kelvin, gas.temperature(), what);
    }

    /**
     * No format has changed since 0.1.0, so a block entity brought forward is as it was saved. A step
     * added to {@link DeviceData} changes that: say here what it does to these.
     */
    @Test
    void data_012_block_entities_saved_by_0_1_0_are_in_the_current_format() throws Exception {
        Map<String, CompoundTag> saved = saved();
        Map<String, CompoundTag> told = told();
        assertEquals(told.keySet(), saved.keySet(), "the block entities in the station's chunks");
        Set<String> kinds = new TreeSet<>();
        for (Map.Entry<String, CompoundTag> e : saved.entrySet()) {
            CompoundTag tag = e.getValue();
            assertEquals(told.get(e.getKey()), tag, "at " + e.getKey() + ", the file against the console");
            String id = tag.getString("id");
            if (!id.startsWith(MOD)) {
                continue;
            }
            kinds.add(id.substring(MOD.length()));
            assertEquals(1, tag.getInt(DeviceData.VERSION_KEY), "at " + e.getKey());
            CompoundTag forward = tag.copy();
            DeviceData.migrate(forward);
            assertEquals(DeviceData.VERSION, forward.getInt(DeviceData.VERSION_KEY), "at " + e.getKey());
            assertEquals(tag, forward, "at " + e.getKey() + ", brought forward");
        }
        assertEquals(new TreeSet<>(KINDS_0_1_0), kinds);
    }

    /** Every store of gas a block saved reads as the amounts the console showed, to the last digit. */
    @Test
    void data_012_gas_saved_in_blocks_by_0_1_0_reads_as_it_was() throws Exception {
        Map<String, CompoundTag> saved = saved();
        int stores = 0;
        for (Map.Entry<String, CompoundTag> e : told().entrySet()) {
            List<CompoundTag> told = new ArrayList<>();
            List<CompoundTag> read = new ArrayList<>();
            for (CompoundTag tag : List.of(e.getValue(), saved.get(e.getKey()))) {
                List<CompoundTag> into = tag == e.getValue() ? told : read;
                if (tag.contains("Gas")) {
                    into.add(tag.getCompound("Gas"));
                }
                tag.getList("Ports", Tag.TAG_COMPOUND).forEach(p -> into.add((CompoundTag) p));
            }
            assertEquals(told.size(), read.size(), "at " + e.getKey());
            for (int i = 0; i < told.size(); i++) {
                String what = "at " + e.getKey() + ", store " + i;
                CompoundTag moles = told.get(i).getCompound("Moles");
                GasParcel gas = GasStore.parcel(read.get(i));
                assertEquals(moles.getAllKeys().size(), gas.moles().size(), what);
                for (String id : moles.getAllKeys()) {
                    assertEquals(moles.getDouble(id), gas.moles(ResourceLocation.parse(id)), what + ", " + id);
                }
                if (!moles.isEmpty()) {
                    assertEquals(told.get(i).getDouble("T"), gas.temperature(), what);
                    stores++;
                }
            }
        }
        assertEquals(51, stores, "the stores that hold gas");

        // N1, with its canister on a port, was given 60 mol of N2 and 15 of O2 in a pipe and 500 of N2
        // and 12.5 of CO2 in the canister. The valve then took the place of one of the row's ten
        // nodes, and a tenth of the pipe's gas left with that node. It crosses from chunk 40 to 41
        // between x = 655 and 656.
        List<String> n1 = new ArrayList<>(List.of("652 -59 10", "652 -58 10"));
        for (int x = 651; x <= 660; x++) {
            n1.add(x + " -60 10");
        }
        Map<ResourceLocation, Double> held = new TreeMap<>();
        double west = 0;
        for (String at : n1) {
            GasParcel share = GasStore.parcel(saved.get(at).getCompound("Gas"));
            share.moles().forEach((gas, n) -> held.merge(gas, n, Double::sum));
            if (Integer.parseInt(at.substring(0, 3)) <= 655) {
                west += share.totalMoles();
            }
        }
        assertEquals(554, held.get(StandardAir.NITROGEN), 1e-9);
        assertEquals(13.5, held.get(StandardAir.OXYGEN), 1e-9);
        assertEquals(12.5, held.get(CO2), 1e-9);
        assertTrue(west > 0 && west < 580, "N1's gas isn't saved on both sides of the border: " + west);

        // N2 was given 20 mol of CO2. It crosses from chunk 41 to 42 between x = 671 and 672.
        double total = 0;
        double east = 0;
        for (int x = 668; x <= 676; x++) {
            double share =
                    GasStore.parcel(saved.get(x + " -60 12").getCompound("Gas")).moles(CO2);
            total += share;
            east += x >= 672 ? share : 0;
        }
        assertEquals(20, total, 1e-9);
        assertTrue(east > 0 && east < 20, "N2's gas isn't saved on both sides of the border: " + east);
    }

    /** The chest's items, the tank holder's tanks and the placed TTV: every data component reads. */
    @Test
    void data_012_item_data_saved_by_0_1_0_reads() {
        Map<String, CompoundTag> saved = saved();
        ListTag items = saved.get("642 -60 8").getList("Items", Tag.TAG_COMPOUND);
        assertEquals(8, items.size());
        List<CompoundTag> components = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            assertEquals(i, items.getCompound(i).getByte("Slot"));
            components.add(items.getCompound(i).getCompound("components"));
        }

        holds(gas(items.getCompound(0)), 293, Map.of(StandardAir.OXYGEN, 5.5, StandardAir.NITROGEN, 0.125), "a tank");
        assertEquals(24.5, read(Codec.DOUBLE, components.get(0).get(MOD + "distribution_pressure")));
        assertEquals(
                Unit.INSTANCE, read(Codec.unit(Unit.INSTANCE), components.get(0).get(MOD + "tank_low")));

        List<CompoundTag> valve = contents(components.get(1), MOD + "ttv_tanks");
        assertEquals(2, valve.size());
        holds(gas(valve.get(0)), 290, Map.of(StandardAir.OXYGEN, 3.125), "the TTV's first tank");
        holds(gas(valve.get(1)), 293, Map.of(H2, 2.25), "the TTV's second tank");
        assertEquals(450, read(ExtraCodecs.NON_NEGATIVE_INT, components.get(1).get(MOD + "ttv_timer")));
        assertEquals(false, read(Codec.BOOL, components.get(1).get(MOD + "ttv_open")));

        List<CompoundTag> suit = contents(components.get(2), MOD + "suit_tank");
        assertEquals(1, suit.size());
        holds(gas(suit.get(0)), 291, Map.of(StandardAir.OXYGEN, 2.375), "the suit's tank");
        assertEquals(0.25f, read(Codec.FLOAT, components.get(2).get(MOD + "leaking")));

        holds(gas(items.getCompound(3)), 287, Map.of(StandardAir.NITROGEN, 40.0, CO2, 3.5), "a canister as an item");
        assertEquals(2468, read(ExtraCodecs.NON_NEGATIVE_INT, components.get(4).get(MOD + "energy")));
        assertEquals(true, read(Codec.BOOL, components.get(5).get(MOD + "magnetized")));
        assertEquals(7, read(ExtraCodecs.NON_NEGATIVE_INT, components.get(6).get(MOD + "charges")));

        // The multitool's selection, which it keeps in the stack's custom data.
        CompoundTag selection = components.get(7).getCompound("minecraft:custom_data");
        assertEquals(1, selection.getInt(DeviceData.VERSION_KEY));
        assertEquals(CONTROLLER, BlockPos.of(selection.getLong("Controller")));
        assertEquals("minecraft:overworld", selection.getString("Dimension"));

        ListTag tanks = saved.get("657 -60 6").getCompound("Tanks").getList("Items", Tag.TAG_COMPOUND);
        assertEquals(4, tanks.size());
        holds(gas(tanks.getCompound(0)), 289, Map.of(StandardAir.OXYGEN, 4.25), "the holder's first tank");
        holds(
                gas(tanks.getCompound(1)),
                294,
                Map.of(StandardAir.NITROGEN, 3.0, StandardAir.OXYGEN, 0.8125),
                "the holder's second tank");
        holds(gas(tanks.getCompound(2)), 301, Map.of(CO2, 0.5, StandardAir.NITROGEN, 0.375), "the holder's third tank");
        holds(gas(tanks.getCompound(3)), 285, Map.of(StandardAir.OXYGEN, 0.625), "the holder's fourth tank");

        CompoundTag placed = saved.get("650 -60 8").getCompound("components");
        List<CompoundTag> placedTanks = contents(placed, MOD + "ttv_tanks");
        holds(gas(placedTanks.get(0)), 290, Map.of(StandardAir.OXYGEN, 3.125), "the placed TTV's first tank");
        holds(gas(placedTanks.get(1)), 293, Map.of(H2, 2.25), "the placed TTV's second tank");
        assertEquals(120, read(ExtraCodecs.NON_NEGATIVE_INT, placed.get(MOD + "ttv_timer")));
        assertEquals(false, read(Codec.BOOL, placed.get(MOD + "ttv_open")));
    }

    /** The central controller's one claim, on room R's area air controller, never read while unpowered. */
    @Test
    void data_012_the_claims_saved_by_0_1_0_read() {
        CompoundTag saved = RELEASE_0_1_0.data(CentralClaims.NAME);
        assertEquals(1, saved.getInt(DeviceData.VERSION_KEY));
        CentralClaims claims = CentralClaims.load(saved.copy(), null);
        assertEquals(Optional.of(CONSOLE), claims.centralOf(CONTROLLER));
        assertEquals(List.of(CONTROLLER), claims.claimedBy(CONSOLE));

        // The claim's last readings are NaN, which no tag equals, so the text is compared.
        CompoundTag again = claims.save(new CompoundTag(), null);
        assertEquals(saved.toString(), again.toString(), "saved again");
    }

    /** The primed TTV, frozen with 11,000 ticks of fuse left, is in the file as the console showed it. */
    @Test
    void data_012_the_primed_valve_saved_by_0_1_0_is_as_it_was() throws Exception {
        ListTag entities = RELEASE_0_1_0.entities(40, 0).getList("Entities", Tag.TAG_COMPOUND);
        assertEquals(1, entities.size());
        CompoundTag saved = entities.getCompound(0).copy();
        assertEquals(MOD + "primed_tank_transfer_valve", saved.getString("id"));
        saved.remove("id");
        assertEquals(
                TagParser.parseTag(RELEASE_0_1_0.expected().get("primed_ttv").getAsString()), saved);

        assertEquals(1, saved.getInt(DeviceData.VERSION_KEY));
        assertEquals(11_000, saved.getInt("Fuse"));
        assertEquals(600, saved.getInt("Timer"));
        holds(gas(saved.getCompound("First")), 290, Map.of(StandardAir.OXYGEN, 1.5), "its first tank");
        holds(gas(saved.getCompound("Second")), 293, Map.of(StandardAir.NITROGEN, 1.25), "its second tank");
    }
}
