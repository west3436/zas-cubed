package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.gas.GasStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A block that provides the gas-handler capability, as another mod's would, for GameTests of API-021,
 * API-025 and API-026. A test can make it refuse, take part of what it's offered, say it took more
 * than it was offered, offer the capability on one side or none, or do something else from inside a
 * fill.
 *
 * <p>It is registered from the GameTest source set as {@code zas_atmos:test_gas_handler}, so it exists
 * in dev runs and GameTest servers but never in the mod jar. It has no item or model.
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestGasHandler {
    public static final ResourceLocation ID = ZasAtmosMod.id("test_gas_handler");

    /** The volume of its tank, in L: a pipe's. */
    public static final double VOLUME = 70;

    private static Block block;
    private static BlockEntityType<Entity> type;

    private TestGasHandler() {}

    /** The test handler's block. */
    public static Block block() {
        return block;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, helper -> {
            block = new HandlerBlock(BlockBehaviour.Properties.of().strength(1.0f));
            helper.register(ID, block);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            type = BlockEntityType.Builder.of(Entity::new, block).build(null);
            helper.register(ID, type);
        });
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(IGasHandler.BLOCK, type, (be, side) -> be.on(side));
    }

    private static final class HandlerBlock extends Block implements EntityBlock {
        HandlerBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Entity(pos, state);
        }
    }

    /** The test handler's block entity: a tank that records how it was called. */
    public static final class Entity extends BlockEntity implements IGasHandler {
        /** What it holds. */
        public final GasStore tank = new GasStore(VOLUME);
        /** Whether it provides the capability at all. */
        public boolean exposed = true;
        /** The one side it provides the capability on, or null for all of them. */
        @Nullable
        public Direction onlySide;
        /** Whether it takes no gas. */
        public boolean refusesFill;
        /** Whether it gives no gas. */
        public boolean refusesDrain;
        /** The most moles it holds. */
        public double capacity = Double.MAX_VALUE;
        /** Moles of each gas it says it took beyond what it did. */
        public double overstates;
        /** Whether looking up the capability throws. */
        public boolean failsLookup;
        /** Run each time it's asked for its contents. */
        public Runnable duringContents = () -> {};
        /** Run at the start of an executed fill, before it takes anything. */
        public Runnable duringFill = () -> {};
        /** Run at the start of an executed drain, before it gives anything. */
        public Runnable duringDrain = () -> {};
        /** The executed fills it has been asked for, refused or not. */
        public int fills;
        /** The executed drains it has been asked for, refused or not. */
        public int drains;
        /** The thread of each call it has had. */
        public final List<Thread> threads = new ArrayList<>();

        Entity(BlockPos pos, BlockState state) {
            super(type, pos, state);
        }

        @Nullable
        private IGasHandler on(@Nullable Direction side) {
            if (failsLookup) {
                throw new IllegalStateException("the test handler's capability failing");
            }
            return exposed && (onlySide == null || onlySide == side) ? this : null;
        }

        @Override
        public GasMixture contents() {
            threads.add(Thread.currentThread());
            duringContents.run();
            return tank.mixture();
        }

        @Override
        public GasParcel fill(GasParcel gas, Action action) {
            threads.add(Thread.currentThread());
            if (action == Action.EXECUTE) {
                fills++;
                duringFill.run();
            }
            if (refusesFill) {
                return GasParcel.EMPTY;
            }
            double part = Math.min(1, Math.max(0, capacity - tank.totalMoles()) / gas.totalMoles());
            TreeMap<ResourceLocation, Double> took = new TreeMap<>();
            gas.moles().forEach((id, n) -> took.put(id, n * part));
            if (action == Action.EXECUTE) {
                tank.add(took, gas.temperature());
            }
            if (overstates > 0) {
                took.replaceAll((id, n) -> n + overstates);
            }
            return new GasParcel(took, gas.temperature());
        }

        @Override
        public GasParcel drain(Map<ResourceLocation, Double> moles, Action action) {
            threads.add(Thread.currentThread());
            if (action == Action.EXECUTE) {
                drains++;
                duringDrain.run();
            }
            if (refusesDrain) {
                return GasParcel.EMPTY;
            }
            if (action == Action.EXECUTE) {
                return tank.take(moles);
            }
            TreeMap<ResourceLocation, Double> would = new TreeMap<>();
            moles.forEach((id, n) -> would.put(id, Math.min(n, tank.moles(id))));
            return new GasParcel(would, tank.temperature());
        }
    }
}
