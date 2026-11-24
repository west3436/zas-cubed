package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.ss13.api.power.AreaPower;
import io.github.west3436.ss13.api.power.EnergyStore;
import io.github.west3436.ss13.api.power.PowerFeed;
import io.github.west3436.ss13.api.power.PowerProvider;
import io.github.west3436.ss13.api.power.PoweredDevice;
import io.github.west3436.ss13.device.DeviceBlockEntity;
import io.github.west3436.ss13.power.AreaDevices;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A stand-in for NT Engineering's APC, for the GameTests of SS13 Framework's power contract in ZAS³
 * ({@link PowerProviderTests}): {@code zas_atmos:test_apc}, a provider of {@link PowerProvider#MAINS}
 * precedence that powers the powered devices of every mod in its area, from a buffer of its own, as
 * an APC on a grid does. It finds them as an APC would, through SS13 Framework's {@link AreaDevices},
 * holding its area through {@link AreaPower}, and serves them with {@link PowerFeed#serve}. It orders
 * nothing: an area air controller covering the same devices still controls them.
 *
 * <p>Registered from the GameTest source set only, so never in the mod jar, and without an item or a
 * model.
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID)
public final class TestApc {
    /** Its buffer, set full when it is placed. */
    public static final int CAPACITY = 1_000_000;

    private static Block block;
    private static BlockEntityType<Apc> type;

    private TestApc() {}

    /** The stand-in's block. */
    public static Block block() {
        return block;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.register(Registries.BLOCK, helper -> {
            block = new ApcBlock();
            helper.register(ZasAtmosMod.id("test_apc"), block);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            type = BlockEntityType.Builder.of(Apc::new, block).build(null);
            helper.register(ZasAtmosMod.id("test_apc"), type);
        });
    }

    private static final class ApcBlock extends Block implements EntityBlock {
        ApcBlock() {
            super(BlockBehaviour.Properties.of().strength(1.0f));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Apc(pos, state);
        }
    }

    /** The stand-in: it powers the devices in its {@link #area} at each of its steps. */
    public static final class Apc extends DeviceBlockEntity implements PowerProvider {
        /** Its buffer, full when placed. Nothing fills it: a test watches it fall. */
        public final EnergyStore store = new EnergyStore(() -> CAPACITY, true, false, this::setChanged);

        /** The area it powers, or null for none. */
        public @Nullable ResourceLocation area;

        /** The devices it covered at its last step. */
        public List<PoweredDevice> covered = List.of();

        /** What became of each at its last step. */
        public List<PowerFeed.Outcome> outcomes = List.of();

        Apc(BlockPos pos, BlockState state) {
            super(type, pos, state);
            store.set(CAPACITY);
        }

        @Override
        protected void deviceTick(ServerLevel level) {
            List<PoweredDevice> devices = new ArrayList<>();
            if (area != null && AreaPower.claim(level, area, this)) {
                devices.addAll(AreaDevices.in(level, area));
            }
            PowerFeed.Served served = PowerFeed.serve(this, devices, store.getEnergyStored());
            store.take(served.spent());
            covered = List.copyOf(devices);
            outcomes = served.outcomes();
        }

        /**
         * What became of a device at its last step.
         *
         * @param device the device
         * @return the outcome, or null if it didn't cover it
         */
        public PowerFeed.@Nullable Outcome outcome(PoweredDevice device) {
            int i = covered.indexOf(device);
            return i < 0 ? null : outcomes.get(i);
        }

        @Override
        public int precedence() {
            return MAINS;
        }

        @Override
        public boolean providing() {
            return !isRemoved();
        }
    }
}
