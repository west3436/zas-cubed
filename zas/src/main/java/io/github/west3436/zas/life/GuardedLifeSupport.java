package io.github.west3436.zas.life;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Another mod's life support behind NFR-013's guard: the one place ZAS³ looks up an {@link
 * ILifeSupport} capability or calls into one, or into an {@link IInternalSupply}, whether the life
 * support holds it or the item gives it by itself ({@link IInternalSupply#ITEM}). A call
 * that throws, or answers null where it can't, is logged at most once a minute for each item or
 * entity type, and is skipped:
 *
 * <ul>
 *   <li>A lookup that throws counts as no capability.
 *   <li>A part of the life support that throws gives what the interface's default gives, which is
 *       nothing. Its other parts still count.
 *   <li>A supply's {@code contents} that throws reads as empty and its {@code distributionPressure}
 *       as 0, so it delivers nothing and the entity breathes the atmosphere (LIFE-046). A {@code
 *       draw} that throws gives no gas, so nothing is exhaled.
 * </ul>
 *
 * <p>Nothing is kept, so each call asks again and counts as soon as it stops throwing. Server thread.
 */
final class GuardedLifeSupport implements ILifeSupport {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String LIFE_SUPPORT = "Life support";
    private static final String SUPPLY = "Internal supply";

    /** What a supply reads as when its {@code contents} throws. */
    private static final GasMixture NOTHING =
            new GasMixture(Map.of(), StandardAir.TEMPERATURE, StandardAir.CELL_VOLUME);

    private final ILifeSupport delegate;
    private final Owner owner;

    /**
     * What a life support belongs to, which is the source a fault is logged under.
     *
     * @param kind {@code item} or {@code entity}
     * @param id its item's or entity type's id
     */
    private record Owner(String kind, ResourceLocation id) {
        static Owner of(ItemStack stack) {
            return new Owner("item", BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }

        static Owner of(Entity entity) {
            return new Owner("entity", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        }

        void failed(String what, Throwable error) {
            String source = what + " of " + kind + " " + id;
            if (ThirdPartyFaults.shouldLog(source)) {
                LOGGER.error("{} threw; skipped for this call (logged at most once a minute)", source, error);
            }
        }
    }

    private GuardedLifeSupport(ILifeSupport delegate, Owner owner) {
        this.delegate = delegate;
        this.owner = owner;
    }

    /** An item's capability, guarded, or null if it has none or the lookup throws. */
    static @Nullable ILifeSupport of(ItemStack stack) {
        try {
            ILifeSupport support = stack.getCapability(ILifeSupport.ITEM);
            return support == null ? null : new GuardedLifeSupport(support, Owner.of(stack));
        } catch (Exception | LinkageError e) {
            Owner.of(stack).failed(LIFE_SUPPORT, e);
            return null;
        }
    }

    /**
     * The supply of an item that only holds gas ({@link IInternalSupply#ITEM}), guarded, or empty if
     * it has none or the lookup throws.
     */
    static Optional<IInternalSupply> supplyOf(ItemStack stack) {
        try {
            IInternalSupply supply = stack.getCapability(IInternalSupply.ITEM);
            return supply == null ? Optional.empty() : Optional.of(new GuardedSupply(supply, Owner.of(stack)));
        } catch (Exception | LinkageError e) {
            Owner.of(stack).failed(SUPPLY, e);
            return Optional.empty();
        }
    }

    /** An entity's own capability, guarded, or null if it has none or the lookup throws. */
    static @Nullable ILifeSupport of(Entity entity) {
        try {
            ILifeSupport support = entity.getCapability(ILifeSupport.ENTITY);
            return support == null ? null : new GuardedLifeSupport(support, Owner.of(entity));
        } catch (Exception | LinkageError e) {
            Owner.of(entity).failed(LIFE_SUPPORT, e);
            return null;
        }
    }

    @Override
    public double minPressure() {
        try {
            return delegate.minPressure();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.minPressure();
        }
    }

    @Override
    public double maxPressure() {
        try {
            return delegate.maxPressure();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.maxPressure();
        }
    }

    @Override
    public double minTemperature() {
        try {
            return delegate.minTemperature();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.minTemperature();
        }
    }

    @Override
    public double maxTemperature() {
        try {
            return delegate.maxTemperature();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.maxTemperature();
        }
    }

    @Override
    public boolean sealedBreathing() {
        try {
            return delegate.sealedBreathing();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.sealedBreathing();
        }
    }

    @Override
    public boolean gasFiltering() {
        try {
            return delegate.gasFiltering();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.gasFiltering();
        }
    }

    @Override
    public double airflowResistance() {
        try {
            return delegate.airflowResistance();
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.airflowResistance();
        }
    }

    @Override
    public Optional<IInternalSupply> internalSupply() {
        try {
            return delegate.internalSupply().map(supply -> new GuardedSupply(supply, owner));
        } catch (Exception | LinkageError e) {
            owner.failed(LIFE_SUPPORT, e);
            return ILifeSupport.super.internalSupply();
        }
    }

    /** A supply behind the same guard. */
    private record GuardedSupply(IInternalSupply delegate, Owner owner) implements IInternalSupply {
        @Override
        public GasMixture contents() {
            try {
                return Objects.requireNonNull(delegate.contents(), "contents");
            } catch (Exception | LinkageError e) {
                owner.failed(SUPPLY, e);
                return NOTHING;
            }
        }

        @Override
        public double distributionPressure() {
            try {
                return delegate.distributionPressure();
            } catch (Exception | LinkageError e) {
                owner.failed(SUPPLY, e);
                return 0;
            }
        }

        @Override
        public GasParcel draw(double moles) {
            try {
                return Objects.requireNonNull(delegate.draw(moles), "draw");
            } catch (Exception | LinkageError e) {
                owner.failed(SUPPLY, e);
                return GasParcel.EMPTY;
            }
        }
    }
}
