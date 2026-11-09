package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.ScrubberControl;
import io.github.west3436.zas.atmos.controller.ScrubberOrder;
import io.github.west3436.zas.atmos.controller.VentControl;
import io.github.west3436.zas.atmos.controller.VentOrder;
import io.github.west3436.zas.atmos.device.AtmosDevice;
import io.github.west3436.zas.atmos.power.DevicePower;
import io.github.west3436.zas.atmos.power.EnergyStore;
import io.github.west3436.zas.atmos.power.PowerPriority;
import io.github.west3436.zas.atmos.power.PoweredDevice;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.energy.ComponentEnergyStorage;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Stand-ins for the parts an area air controller covers, until the real vent (#48) and scrubber
 * exist, and a test FE block (CONT-062). Registered from the GameTest source set only, so never in
 * the mod jar, and without items or models:
 *
 * <ul>
 *   <li>{@code zas_atmos:test_vent}: life support, 10 FE/t, takes vent orders;
 *   <li>{@code zas_atmos:test_scrubber}: life support, 15 FE/t, takes scrubber orders;
 *   <li>{@code zas_atmos:test_pump}: gas handling, 20 FE/t;
 *   <li>{@code zas_atmos:test_heater}: auxiliary, 60 FE/t;
 *   <li>{@code zas_atmos:test_battery}: an FE block holding 100,000 FE that gives it to any side;
 *   <li>{@code zas_atmos:test_fe_item} and {@code zas_atmos:test_untagged_fe_item}: FE items holding
 *       up to 50,000 FE, another mod's battery as far as ZAS³ can tell. The first is added to the
 *       {@code zas_atmos:batteries} tag by {@link BatteryTestPack}; the second never is (CONT-112).
 * </ul>
 *
 * The {@code test_heater} stand-in predates the heater running on a battery (D-73): it is kept as a
 * generic auxiliary part for CONT-064's priority tests.
 *
 * Each part has no collision, so its block is a cell like air, and its air point is the middle of
 * its own block. The battery is a full block, so it can stand in a wall.
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID)
public final class TestParts {
    /** One kind of stand-in part. */
    public enum Kind {
        VENT("test_vent", PowerPriority.LIFE_SUPPORT, 10),
        SCRUBBER("test_scrubber", PowerPriority.LIFE_SUPPORT, 15),
        PUMP("test_pump", PowerPriority.GAS_HANDLING, 20),
        HEATER("test_heater", PowerPriority.AUXILIARY, 60);

        final String id;
        final PowerPriority priority;
        final int draw;
        Block block;
        BlockEntityType<Part> type;

        Kind(String id, PowerPriority priority, int draw) {
            this.id = id;
            this.priority = priority;
            this.draw = draw;
        }

        public Block block() {
            return block;
        }
    }

    public static final int BATTERY_FE = 100_000;

    /** What each test FE item holds. */
    public static final int FE_ITEM_CAPACITY = 50_000;

    private static Item feItem;
    private static Item untaggedFeItem;

    /** An FE item the test pack adds to the battery tag. */
    public static Item feItem() {
        return feItem;
    }

    /** An FE item in no tag. */
    public static Item untaggedFeItem() {
        return untaggedFeItem;
    }

    private static Block battery;
    private static BlockEntityType<Battery> batteryType;

    private TestParts() {}

    public static Block battery() {
        return battery;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.register(Registries.BLOCK, helper -> {
            for (Kind k : Kind.values()) {
                k.block = new PartBlock(k);
                helper.register(ZasAtmosMod.id(k.id), k.block);
            }
            battery = new BatteryBlock();
            helper.register(ZasAtmosMod.id("test_battery"), battery);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            for (Kind k : Kind.values()) {
                k.type = BlockEntityType.Builder.of((p, s) -> create(k, p, s), k.block)
                        .build(null);
                helper.register(ZasAtmosMod.id(k.id), k.type);
            }
            batteryType = BlockEntityType.Builder.of(Battery::new, battery).build(null);
            helper.register(ZasAtmosMod.id("test_battery"), batteryType);
        });
        event.register(Registries.ITEM, helper -> {
            feItem = new Item(new Item.Properties().stacksTo(1));
            helper.register(ZasAtmosMod.id("test_fe_item"), feItem);
            untaggedFeItem = new Item(new Item.Properties().stacksTo(1));
            helper.register(ZasAtmosMod.id("test_untagged_fe_item"), untaggedFeItem);
        });
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, batteryType, (be, side) -> be.store);
        event.registerItem(
                Capabilities.EnergyStorage.ITEM,
                (stack, context) -> new ComponentEnergyStorage(
                        stack, AtmosComponents.ENERGY.get(), FE_ITEM_CAPACITY, FE_ITEM_CAPACITY, FE_ITEM_CAPACITY),
                feItem,
                untaggedFeItem);
    }

    private static Part create(Kind k, BlockPos pos, BlockState state) {
        return switch (k) {
            case VENT -> new Vent(k, pos, state);
            case SCRUBBER -> new Scrubber(k, pos, state);
            default -> new Part(k, pos, state);
        };
    }

    private static final class PartBlock extends Block implements EntityBlock {
        private final Kind kind;

        PartBlock(Kind kind) {
            super(BlockBehaviour.Properties.of().strength(1.0f).noCollission().noOcclusion());
            this.kind = kind;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return create(kind, pos, state);
        }
    }

    /** A stand-in part: it counts the atmos ticks it ran with power. */
    public static class Part extends AtmosDevice implements PoweredDevice {
        public final Kind kind;
        /** Atmos ticks it stepped with power. */
        public int poweredSteps;
        /** What it draws, in FE/t. */
        public int draw;

        private final DevicePower power;

        Part(Kind kind, BlockPos pos, BlockState state) {
            super(kind.type, pos, state);
            this.kind = kind;
            this.draw = kind.draw;
            this.power = new DevicePower(kind.priority, this::currentDraw, this::now);
        }

        /** What it draws now, in FE/t. */
        protected int currentDraw() {
            return draw;
        }

        @Override
        protected void atmosTick(ServerLevel level) {
            if (power.isPowered()) {
                poweredSteps++;
            }
        }

        @Override
        public DevicePower power() {
            return power;
        }

        @Override
        public Vec3 airPoint() {
            return Vec3.atCenterOf(getBlockPos());
        }
    }

    /**
     * A stand-in vent: it records the orders it is given. With {@link #drawsOnlyWhenOn} it draws
     * nothing until it is ordered on, as a real vent draws nothing while off.
     */
    public static final class Vent extends Part implements VentControl {
        public final List<VentOrder> orders = new ArrayList<>();
        public boolean drawsOnlyWhenOn;

        Vent(Kind kind, BlockPos pos, BlockState state) {
            super(kind, pos, state);
        }

        @Override
        public void ventOrder(VentOrder order) {
            orders.add(order);
        }

        public VentOrder last() {
            return orders.isEmpty() ? null : orders.get(orders.size() - 1);
        }

        @Override
        protected int currentDraw() {
            if (!drawsOnlyWhenOn) {
                return draw;
            }
            VentOrder last = last();
            return last != null && last.on() ? draw : 0;
        }
    }

    /** A stand-in scrubber: it records the orders it is given. */
    public static final class Scrubber extends Part implements ScrubberControl {
        public final List<ScrubberOrder> orders = new ArrayList<>();

        Scrubber(Kind kind, BlockPos pos, BlockState state) {
            super(kind, pos, state);
        }

        @Override
        public void scrubberOrder(ScrubberOrder order) {
            orders.add(order);
        }

        public ScrubberOrder last() {
            return orders.isEmpty() ? null : orders.get(orders.size() - 1);
        }
    }

    private static final class BatteryBlock extends Block implements EntityBlock {
        BatteryBlock() {
            super(BlockBehaviour.Properties.of().strength(1.0f));
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Battery(pos, state);
        }
    }

    /** The test FE block's entity. */
    public static final class Battery extends BlockEntity {
        public final EnergyStore store = new EnergyStore(() -> BATTERY_FE, true, true, () -> {});

        Battery(BlockPos pos, BlockState state) {
            super(batteryType, pos, state);
            store.set(BATTERY_FE);
        }
    }
}
