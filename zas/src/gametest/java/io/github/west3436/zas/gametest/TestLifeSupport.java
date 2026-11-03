package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Life support from the capability (API-022), registered from the GameTest source set only, so
 * never in the mod jar:
 *
 * <ul>
 *   <li>{@code zas:test_suit_piece}, an item without a model whose {@link ILifeSupport#ITEM} gives
 *       every part but an internal supply: pressure from 0 to 1,000 kPa, temperature from 0 to
 *       1,000 K, sealed breathing, gas filtering and airflow resistance 0.5;
 *   <li>{@code zas:test_tank}, an item without a model whose {@link ILifeSupport#ITEM} gives only an
 *       internal supply (LIFE-028): the gas and distribution pressure kept on the stack ({@link
 *       #tank});
 *   <li>on a zombie with the entity tag {@link #ENTITY_TAG}, {@link ILifeSupport#ENTITY} giving
 *       low-pressure protection to vacuum, sealed breathing and airflow resistance 0.25, and, with
 *       the tag {@link #OWN_TANK_TAG} too, a tank of its own: {@link #OWN_TANK}.
 * </ul>
 */
@EventBusSubscriber(modid = ZasMod.MOD_ID)
public final class TestLifeSupport {
    /** The test item's id. */
    public static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "test_suit_piece");

    /** The test tank's id. */
    public static final ResourceLocation TANK_ID = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "test_tank");

    /** The test tank's volume, L. */
    public static final double TANK_VOLUME = 10;

    /** The entity tag ({@code Entity#addTag}) that gives a zombie its own life support. */
    public static final String ENTITY_TAG = "zas_test_life_support";

    /** The entity tag that gives a zombie with {@link #ENTITY_TAG} a tank of its own too. */
    public static final String OWN_TANK_TAG = "zas_test_own_tank";

    /** What the test item gives. */
    public static final ILifeSupport SUIT_PIECE = new ILifeSupport() {
        @Override
        public double minPressure() {
            return 0;
        }

        @Override
        public double maxPressure() {
            return 1000;
        }

        @Override
        public double minTemperature() {
            return 0;
        }

        @Override
        public double maxTemperature() {
            return 1000;
        }

        @Override
        public boolean sealedBreathing() {
            return true;
        }

        @Override
        public boolean gasFiltering() {
            return true;
        }

        @Override
        public double airflowResistance() {
            return 0.5;
        }
    };

    /** What a tagged zombie gives itself. */
    public static final ILifeSupport OWN = new ILifeSupport() {
        @Override
        public double minPressure() {
            return 0;
        }

        @Override
        public boolean sealedBreathing() {
            return true;
        }

        @Override
        public double airflowResistance() {
            return 0.25;
        }
    };

    /** A zombie's own tank: 1 mol of O2 at 293.15 K in 10 L, delivered at 21.3 kPa; {@link TestSupply#refill}. */
    public static final TestSupply OWN_TANK = new TestSupply();

    /** What a zombie tagged for a tank of its own gives itself: as {@link #OWN}, with {@link #OWN_TANK}. */
    public static final ILifeSupport OWN_WITH_TANK = new ILifeSupport() {
        @Override
        public double minPressure() {
            return 0;
        }

        @Override
        public boolean sealedBreathing() {
            return true;
        }

        @Override
        public Optional<IInternalSupply> internalSupply() {
            return Optional.of(OWN_TANK);
        }
    };

    private static Item item;
    private static Item tank;

    private TestLifeSupport() {}

    /** The test item. */
    public static Item item() {
        return item;
    }

    /** The test tank item. */
    public static Item tankItem() {
        return tank;
    }

    // ---- The test tank -------------------------------------------------------------------------

    /**
     * A test tank holding gas at 293.15 K in 10 L, delivered at a distribution pressure.
     *
     * @param moles the moles per gas
     * @param distribution the distribution pressure, kPa
     */
    public static ItemStack tank(Map<ResourceLocation, Double> moles, double distribution) {
        ItemStack stack = new ItemStack(tank);
        CompoundTag root = new CompoundTag();
        root.put("Moles", tag(moles));
        root.putDouble("Dist", distribution);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    /** The gas a test tank holds now. */
    public static GasMixture contents(ItemStack stack) {
        CompoundTag tag = root(stack).getCompound("Moles");
        Map<ResourceLocation, Double> moles = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            moles.put(ResourceLocation.parse(key), tag.getDouble(key));
        }
        return new GasMixture(moles, StandardAir.TEMPERATURE, TANK_VOLUME);
    }

    private static CompoundTag root(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static CompoundTag tag(Map<ResourceLocation, Double> moles) {
        CompoundTag tag = new CompoundTag();
        moles.forEach((id, n) -> tag.putDouble(id.toString(), n));
        return tag;
    }

    private static IInternalSupply supply(ItemStack stack) {
        return new IInternalSupply() {
            @Override
            public GasMixture contents() {
                return TestLifeSupport.contents(stack);
            }

            @Override
            public double distributionPressure() {
                return root(stack).getDouble("Dist");
            }

            @Override
            public GasParcel draw(double moles) {
                Map<ResourceLocation, Double> left = new HashMap<>(contents().moles());
                GasParcel taken = take(left, moles);
                CompoundTag root = root(stack);
                root.put("Moles", tag(left));
                stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
                return taken;
            }
        };
    }

    /** Takes up to {@code want} moles from a map of moles, in proportion, at 293.15 K. */
    static GasParcel take(Map<ResourceLocation, Double> moles, double want) {
        double total = moles.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!(want > 0) || !(total > 0)) {
            return GasParcel.EMPTY;
        }
        double f = Math.min(1, want / total);
        Map<ResourceLocation, Double> out = new HashMap<>();
        moles.replaceAll((id, n) -> {
            out.put(id, n * f);
            return n * (1 - f);
        });
        moles.values().removeIf(n -> n <= 1e-12);
        return new GasParcel(out, StandardAir.TEMPERATURE);
    }

    /** An internal supply held in memory, for an entity's own. */
    public static final class TestSupply implements IInternalSupply {
        private final Map<ResourceLocation, Double> moles = new HashMap<>();

        TestSupply() {
            refill();
        }

        /** Back to 1 mol of O2. */
        public void refill() {
            moles.clear();
            moles.put(StandardAir.OXYGEN, 1.0);
        }

        @Override
        public GasMixture contents() {
            return new GasMixture(moles, StandardAir.TEMPERATURE, TANK_VOLUME);
        }

        @Override
        public double distributionPressure() {
            return 21.3;
        }

        @Override
        public GasParcel draw(double want) {
            return take(moles, want);
        }
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.ITEM, ITEM_ID, () -> {
            item = new Item(new Item.Properties());
            return item;
        });
        event.register(Registries.ITEM, TANK_ID, () -> {
            tank = new Item(new Item.Properties().stacksTo(1));
            return tank;
        });
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(ILifeSupport.ITEM, (stack, context) -> SUIT_PIECE, item);
        event.registerItem(
                ILifeSupport.ITEM,
                (stack, context) -> new ILifeSupport() {
                    @Override
                    public Optional<IInternalSupply> internalSupply() {
                        return Optional.of(supply(stack));
                    }
                },
                tank);
        event.registerEntity(ILifeSupport.ENTITY, EntityType.ZOMBIE, (zombie, context) -> {
            if (!zombie.getTags().contains(ENTITY_TAG)) {
                return null;
            }
            return zombie.getTags().contains(OWN_TANK_TAG) ? OWN_WITH_TANK : OWN;
        });
    }
}
