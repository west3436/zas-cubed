package io.github.west3436.zas.atmos.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.device.ItemData;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.tool.MultitoolItem;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The format version of what this mod saves on items, and on the primed TTV (DATA-012). What an old
 * save holds is written out here by hand, as it was saved before versions were added, so these don't
 * depend on the code that writes it now.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemDataTests {
    /** Moles no float can hold, so a lost digit shows. */
    private static final double MOLES = 12.345678901234567;

    private static final GasParcel GAS = new GasParcel(Map.of(StandardAir.OXYGEN, MOLES), StandardAir.TEMPERATURE);

    private static final String OLD_GAS = "{moles:{\"zas:oxygen\":12.345678901234567d},temperature:293.15d}";
    private static final String OLD_TANK =
            "{id:\"zas_atmos:empty_tank\",count:1,components:{\"zas_atmos:gas\":" + OLD_GAS + "}}";

    private ItemDataTests() {}

    private static CompoundTag snbt(String text) {
        try {
            return TagParser.parseTag(text);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("bad test data: " + e.getMessage());
        }
    }

    private static ItemStack tank() {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, GAS);
        return tank;
    }

    /** Each kind of item that holds this mod's components, as it was saved and as it's made now. */
    private static Map<String, ItemStack> oldItems() {
        Map<String, ItemStack> items = new LinkedHashMap<>();

        ItemStack oxygenTank = new ItemStack(AtmosItems.OXYGEN_TANK.get());
        GasTankItem.setGas(oxygenTank, GAS);
        oxygenTank.set(AtmosComponents.DISTRIBUTION_PRESSURE.get(), 50.5);
        oxygenTank.set(AtmosComponents.TANK_LOW.get(), Unit.INSTANCE);
        oxygenTank.set(AtmosComponents.LEAKING.get(), 1.5f);
        items.put(
                "{id:\"zas_atmos:oxygen_tank\",count:1,components:{\"zas_atmos:gas\":" + OLD_GAS
                        + ",\"zas_atmos:distribution_pressure\":50.5d,\"zas_atmos:tank_low\":{}"
                        + ",\"zas_atmos:leaking\":1.5f}}",
                oxygenTank);

        ItemStack canister = new ItemStack(AtmosItems.CANISTER.get());
        canister.set(AtmosComponents.GAS.get(), GAS);
        items.put("{id:\"zas_atmos:canister\",count:1,components:{\"zas_atmos:gas\":" + OLD_GAS + "}}", canister);

        ItemStack battery = new ItemStack(AtmosItems.BATTERY.get());
        battery.set(AtmosComponents.ENERGY.get(), 1234);
        items.put("{id:\"zas_atmos:battery\",count:1,components:{\"zas_atmos:energy\":1234}}", battery);

        ItemStack extinguisher = new ItemStack(AtmosItems.FIRE_EXTINGUISHER.get());
        extinguisher.set(AtmosComponents.CHARGES.get(), 3);
        items.put("{id:\"zas_atmos:fire_extinguisher\",count:1,components:{\"zas_atmos:charges\":3}}", extinguisher);

        ItemStack suit = new ItemStack(AtmosItems.SOFTSUIT_CHESTPLATE.get());
        suit.set(AtmosComponents.SUIT_TANK.get(), ItemContainerContents.fromItems(List.of(tank())));
        items.put(
                "{id:\"zas_atmos:softsuit_chestplate\",count:1,components:{\"zas_atmos:suit_tank\":[{slot:0,item:"
                        + OLD_TANK + "}]}}",
                suit);

        ItemStack valve = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        valve.set(AtmosComponents.TTV_TANKS.get(), ItemContainerContents.fromItems(List.of(tank(), tank())));
        valve.set(AtmosComponents.TTV_OPEN.get(), true);
        valve.set(AtmosComponents.TTV_TIMER.get(), 45);
        items.put(
                "{id:\"zas_atmos:tank_transfer_valve\",count:1,components:{\"zas_atmos:ttv_tanks\":[{slot:0,item:"
                        + OLD_TANK + "},{slot:1,item:" + OLD_TANK
                        + "}],\"zas_atmos:ttv_open\":1b,\"zas_atmos:ttv_timer\":45}}",
                valve);
        return items;
    }

    /**
     * An item saved with no version reads as the first format with nothing lost, is equal to the same
     * item made now, and so stacks with it. Saved again, every component has a version, tanks inside
     * other items too, and it reads back the same.
     */
    @GameTest(template = "empty")
    public static void data_012_an_item_saved_with_no_version_loses_nothing(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        oldItems().forEach((old, made) -> {
            String name = made.getItem().toString();
            ItemStack read = ItemStack.parseOptional(registries, snbt(old));
            h.assertTrue(ItemStack.matches(read, made), name + " read as " + read.getComponents());
            h.assertTrue(ItemStack.isSameItemSameComponents(read, made), name + " won't stack with a new one");
            h.assertValueEqual(
                    ItemStack.hashItemAndComponents(read),
                    ItemStack.hashItemAndComponents(made),
                    name + "'s hash against a new one");

            CompoundTag current = (CompoundTag) read.save(registries);
            savedWithVersions(h, current, name);
            ItemStack again = ItemStack.parseOptional(registries, current);
            h.assertTrue(ItemStack.matches(again, made), name + " read back as " + again.getComponents());
        });
        h.succeed();
    }

    private static void savedWithVersions(GameTestHelper h, CompoundTag stack, String name) {
        CompoundTag components = stack.getCompound("components");
        h.assertFalse(components.isEmpty(), name + " saved nothing");
        for (String key : components.getAllKeys()) {
            if (key.startsWith(ZasAtmosMod.MOD_ID + ":")) {
                CompoundTag saved = components.getCompound(key);
                h.assertTrue(saved.getInt(DeviceData.VERSION_KEY) >= 1, name + "'s " + key + " saved no version");
                ListTag slots = saved.getList(ItemData.VALUE_KEY, Tag.TAG_COMPOUND);
                for (int i = 0; i < slots.size(); i++) {
                    savedWithVersions(h, slots.getCompound(i).getCompound("item"), name);
                }
            }
        }
    }

    /** A value of each component this mod registers. A new one fails this until it's added here. */
    private static Map<DataComponentType<?>, Object> samples() {
        Map<DataComponentType<?>, Object> samples = new HashMap<>();
        samples.put(AtmosComponents.GAS.get(), GAS);
        samples.put(AtmosComponents.DISTRIBUTION_PRESSURE.get(), 50.5);
        samples.put(AtmosComponents.TANK_LOW.get(), Unit.INSTANCE);
        samples.put(AtmosComponents.LEAKING.get(), 1.5f);
        samples.put(AtmosComponents.SUIT_TANK.get(), ItemContainerContents.fromItems(List.of(tank())));
        samples.put(AtmosComponents.MAGNETIZED.get(), true);
        samples.put(AtmosComponents.ENERGY.get(), 1234);
        samples.put(AtmosComponents.CHARGES.get(), 3);
        samples.put(AtmosComponents.TTV_TANKS.get(), ItemContainerContents.fromItems(List.of(tank(), tank())));
        samples.put(AtmosComponents.TTV_OPEN.get(), true);
        samples.put(AtmosComponents.TTV_TIMER.get(), 45);
        return samples;
    }

    /**
     * Every component {@code zas} and this mod register saves a version, reads the same with and without
     * it, and syncs none: what's sent to a client goes through its stream codec, with no version even
     * for a tank inside another item.
     */
    @GameTest(template = "empty")
    public static void data_012_every_component_saves_a_version_and_syncs_none(GameTestHelper h) {
        Map<DataComponentType<?>, Object> samples = samples();
        for (DataComponentType<?> type : BuiltInRegistries.DATA_COMPONENT_TYPE) {
            ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (id.getNamespace().equals(ZasAtmosMod.MOD_ID)
                    || id.getNamespace().equals("zas")) {
                h.assertTrue(samples.containsKey(type), "no value to check " + id + " with");
                check(h, id, type, samples.get(type));
            }
        }
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static <A> void check(GameTestHelper h, ResourceLocation id, DataComponentType<A> type, Object sample) {
        A value = (A) sample;
        RegistryOps<Tag> ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = type.codecOrThrow().encodeStart(ops, value).getOrThrow();
        h.assertTrue(saved instanceof CompoundTag, id + " saved as " + saved);
        CompoundTag tag = (CompoundTag) saved;
        h.assertTrue(tag.getInt(DeviceData.VERSION_KEY) >= 1, id + " saved no version: " + tag);
        h.assertValueEqual(type.codecOrThrow().parse(ops, tag).getOrThrow(), value, id + " saved as current");
        h.assertValueEqual(
                type.codecOrThrow().parse(ops, tag.get(ItemData.VALUE_KEY)).getOrThrow(),
                value,
                id + " saved with no version");

        RegistryFriendlyByteBuf buf =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), h.getLevel().registryAccess());
        type.streamCodec().encode(buf, value);
        h.assertFalse(buf.toString(StandardCharsets.ISO_8859_1).contains(DeviceData.VERSION_KEY), id + " syncs one");
        buf.release();
    }

    private static PrimedTransferValve valve(GameTestHelper h, CompoundTag saved) {
        PrimedTransferValve valve =
                new PrimedTransferValve(AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get(), h.getLevel());
        valve.load(saved);
        return valve;
    }

    /**
     * A primed TTV saved with no version reads as the first format, its time left and the moles in its
     * tanks exact. Saved again it has a version, and reads back the same.
     */
    @GameTest(template = "empty")
    public static void data_012_a_primed_valve_saved_with_no_version_loses_nothing(GameTestHelper h) {
        PrimedTransferValve old =
                valve(h, snbt("{First:" + OLD_TANK + ",Second:" + OLD_TANK + ",Open:1b,Timer:30,Fuse:123,Settle:77}"));
        h.assertValueEqual(old.timer(), 30, "the timer");
        h.assertValueEqual(old.fuse(), 123, "the fuse");
        h.assertTrue(old.valve().isOpen(), "the valve closed");
        h.assertValueEqual(GasTankItem.gas(old.valve().first()), GAS, "the first tank's gas");
        h.assertValueEqual(GasTankItem.gas(old.valve().second()), GAS, "the second tank's gas");
        h.assertValueEqual(GasTankItem.gas(old.secondTank()), GAS, "the second tank clients are told of");

        CompoundTag current = old.saveWithoutId(new CompoundTag());
        h.assertValueEqual(current.getInt(DeviceData.VERSION_KEY), 1, "the version it saves");
        h.assertValueEqual(current.getInt("Settle"), 77, "the settling time it saves");

        PrimedTransferValve again = valve(h, current.copy());
        h.assertValueEqual(GasTankItem.gas(again.valve().first()), GAS, "the first tank's gas, saved as current");
        h.assertValueEqual(again.saveWithoutId(new CompoundTag()), current, "the valve, saved as current");
        h.succeed();
    }

    /**
     * A multitool's selection is in vanilla's {@code custom_data}. One saved with no version still
     * reads, and one made now saves a version and reads back.
     */
    @GameTest(template = "empty")
    public static void data_012_a_multitool_selection_saved_with_no_version_still_reads(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        BlockPos controller = new BlockPos(12, 64, -34);
        Optional<BlockPos> selected = Optional.of(controller);

        ItemStack old = ItemStack.parseOptional(
                registries,
                snbt("{id:\"zas_atmos:multitool\",count:1,components:{\"minecraft:custom_data\":{Controller:"
                        + controller.asLong() + "L,Dimension:\""
                        + h.getLevel().dimension().location() + "\"}}}"));
        h.assertValueEqual(MultitoolItem.selected(old, h.getLevel()), selected, "the old selection");

        ItemStack made = new ItemStack(AtmosItems.MULTITOOL.get());
        MultitoolItem.select(made, h.getLevel(), controller);
        CompoundTag current = (CompoundTag) made.save(registries);
        h.assertValueEqual(
                current.getCompound("components")
                        .getCompound("minecraft:custom_data")
                        .getInt(DeviceData.VERSION_KEY),
                1,
                "the version it saves");
        h.assertValueEqual(
                MultitoolItem.selected(ItemStack.parseOptional(registries, current), h.getLevel()),
                selected,
                "the selection saved as current");
        h.succeed();
    }
}
