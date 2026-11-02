package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.capability.ILifeSupport;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
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
 *       every part: pressure from 0 to 1,000 kPa, temperature from 0 to 1,000 K, sealed
 *       breathing, gas filtering and airflow resistance 0.5;
 *   <li>on a zombie with the entity tag {@link #ENTITY_TAG}, {@link ILifeSupport#ENTITY} giving
 *       low-pressure protection to vacuum, sealed breathing and airflow resistance 0.25.
 * </ul>
 */
@EventBusSubscriber(modid = ZasMod.MOD_ID)
public final class TestLifeSupport {
    /** The test item's id. */
    public static final ResourceLocation ITEM_ID =
            ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, "test_suit_piece");

    /** The entity tag ({@code Entity#addTag}) that gives a zombie its own life support. */
    public static final String ENTITY_TAG = "zas_test_life_support";

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

    private static Item item;

    private TestLifeSupport() {}

    /** The test item. */
    public static Item item() {
        return item;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        event.register(Registries.ITEM, ITEM_ID, () -> {
            item = new Item(new Item.Properties());
            return item;
        });
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(ILifeSupport.ITEM, (stack, context) -> SUIT_PIECE, item);
        event.registerEntity(
                ILifeSupport.ENTITY,
                EntityType.ZOMBIE,
                (zombie, context) -> zombie.getTags().contains(ENTITY_TAG) ? OWN : null);
    }
}
