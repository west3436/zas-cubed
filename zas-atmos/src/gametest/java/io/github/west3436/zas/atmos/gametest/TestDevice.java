package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * A powered device that only counts its steps, for GameTests of the device base and the power
 * contract (CONT-006, CONT-069), and for tests of providers before real devices exist.
 *
 * <p>It is registered from the GameTest source set as {@code zas_atmos:test_device}, so it exists
 * in dev runs and GameTest servers but never in the mod jar. It has no item or model.
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestDevice {
    public static final ResourceLocation ID = ZasAtmosMod.id("test_device");

    private static Block block;
    private static BlockEntityType<Entity> type;

    private TestDevice() {}

    /** The test device's block. */
    public static Block block() {
        return block;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.register(Registries.BLOCK, helper -> {
            block = new DeviceBlock(BlockBehaviour.Properties.of().strength(1.0f));
            helper.register(ID, block);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            type = BlockEntityType.Builder.of(Entity::new, block).build(null);
            helper.register(ID, type);
        });
    }

    private static final class DeviceBlock extends Block implements EntityBlock {
        DeviceBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Entity(pos, state);
        }
    }

    /** The test device's block entity: it records each step's game tick and thread. */
    public static final class Entity extends AtmosDevice implements PoweredDevice {
        /** The game ticks it stepped at. */
        public final List<Long> steps = new ArrayList<>();
        /** The game ticks it stepped at while powered. */
        public final List<Long> poweredSteps = new ArrayList<>();
        /** The threads it stepped on. */
        public final List<Thread> threads = new ArrayList<>();
        /** What it draws, in FE/t. */
        public int draw = 10;
        /** Whether it is ready to step. */
        public boolean ready = true;
        /** The moles it says it moves each step (DATA-017). */
        public double moves;
        /** Whether it says it has stopped at each step (DATA-017). */
        public boolean stopping;

        private final DevicePower power = new DevicePower(PowerPriority.LIFE_SUPPORT, () -> draw, this::now);

        Entity(BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Override
        protected void atmosTick(ServerLevel level) {
            steps.add(now());
            threads.add(Thread.currentThread());
            if (power.isPowered()) {
                poweredSteps.add(now());
            }
            reportMoved(moves);
            if (stopping) {
                reportStopped();
            }
        }

        @Override
        public boolean readyToStep() {
            return ready;
        }

        @Override
        public DevicePower power() {
            return power;
        }

        @Override
        public Vec3 airPoint() {
            return Vec3.atCenterOf(getBlockPos().above());
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            power.save(tag);
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            power.load(tag);
        }
    }
}
