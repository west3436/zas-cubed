package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceData;
import io.github.west3436.zas.atmos.gas.GasStore;
import io.github.west3436.zas.atmos.pipe.PipeBlockEntity;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The format version of what this mod's block entities save (DATA-003, DATA-012). These build block
 * entities without placing them, as the save format doesn't depend on the world.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeviceDataTests {
    private DeviceDataTests() {}

    /**
     * Every block entity of this mod saves the version, and one saved with no version, as all were
     * before versions were added, loads and saves the same fields.
     */
    @GameTest(template = "empty")
    public static void data_012_every_block_entity_saves_its_version(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        Set<BlockEntityType<?>> seen = new HashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(ZasAtmosMod.MOD_ID)
                    || !(block instanceof EntityBlock maker)) {
                continue;
            }
            BlockEntity entity = maker.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (entity == null) {
                continue;
            }
            seen.add(entity.getType());
            String name = BuiltInRegistries.BLOCK.getKey(block).toString();
            CompoundTag saved = entity.saveCustomOnly(registries);
            h.assertValueEqual(saved.getInt(DeviceData.VERSION_KEY), DeviceData.VERSION, name + "'s version");

            CompoundTag old = saved.copy();
            old.remove(DeviceData.VERSION_KEY);
            BlockEntity reloaded = maker.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            reloaded.loadCustomOnly(old, registries);
            h.assertValueEqual(reloaded.saveCustomOnly(registries), saved, name + " saved again");
        }
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (id.getNamespace().equals(ZasAtmosMod.MOD_ID)) {
                h.assertTrue(seen.contains(type), "no block made " + id);
            }
        }
        h.succeed();
    }

    /** A pipe saved with no version keeps the gas it held, to DATA-016's 1e-9 (DATA-012). */
    @GameTest(template = "empty")
    public static void data_012_a_pipe_saved_with_no_version_keeps_its_gas(GameTestHelper h) {
        HolderLookup.Provider registries = h.getLevel().registryAccess();
        CompoundTag old = new CompoundTag();
        old.put("Gas", GasStore.save(new GasParcel(Map.of(Area.N2, 12.5), Area.T)));

        PipeBlockEntity pipe =
                new PipeBlockEntity(BlockPos.ZERO, AtmosBlocks.PIPE.get().defaultBlockState());
        pipe.loadCustomOnly(old, registries);
        double moles = pipe.share().moles(Area.N2);
        h.assertTrue(Math.abs(moles - 12.5) <= 12.5e-9, "the pipe's nitrogen: " + moles);

        CompoundTag again = pipe.saveCustomOnly(registries);
        h.assertValueEqual(again.getInt(DeviceData.VERSION_KEY), DeviceData.VERSION, "the version it saves");
        h.assertValueEqual(again.getCompound("Gas"), old.getCompound("Gas"), "the gas it saves");
        h.succeed();
    }
}
