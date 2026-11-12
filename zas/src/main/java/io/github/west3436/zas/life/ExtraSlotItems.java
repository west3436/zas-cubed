package io.github.west3436.zas.life;

import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.capability.ILifeSupport;
import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlotVisitor;
import io.github.west3436.zas.api.life.ExtraSlots;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * What an entity wears in the equipment slots other mods add (COMPAT-040 to COMPAT-042): the items
 * the registered {@link io.github.west3436.zas.api.life.ExtraSlotProvider}s give, in the providers'
 * order and then each provider's own. This is the one place ZAS³ asks them, behind NFR-013's guard:
 * a provider that throws is logged at most once a minute and gives nothing more for that call.
 *
 * <p>{@link Internals} looks here for a supply after the armor slots and before the offhand, and
 * {@link WornProtection} for a head piece that seals the breathing or filters the breath. Nothing
 * else of an item counts from an extra slot.
 *
 * <p>With no provider registered every question here is one read of an empty list, so a game
 * without such a mod does what it did before. With one, a question walks the slots once and keeps
 * nothing. The thread is the entity's own side's, which for ZAS³'s own questions is the server's.
 */
public final class ExtraSlotItems {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ExtraSlotItems() {}

    /** Whether any provider is registered. */
    public static boolean any() {
        return !ExtraSlots.registered().isEmpty();
    }

    /** Gives a visitor each item the entity wears in an extra slot. */
    public static void forEach(LivingEntity entity, ExtraSlotVisitor visitor) {
        List<ExtraSlots.Registration> providers = ExtraSlots.registered();
        for (int i = 0; i < providers.size(); i++) {
            ExtraSlots.Registration provider = providers.get(i);
            try {
                provider.provider().items(entity, visitor);
            } catch (Exception | LinkageError e) {
                String source = "Extra slot provider " + provider.id();
                if (ThirdPartyFaults.shouldLog(source)) {
                    LOGGER.error("{} threw; skipped for this check (logged at most once a minute)", source, e);
                }
            }
        }
    }

    /**
     * The first internal supply in an extra slot that passes a test, or null.
     *
     * @param supplyOf the supply a stack gives, as {@link Internals} reads it
     * @param wanted the test
     */
    static @Nullable IInternalSupply firstSupply(
            LivingEntity entity,
            Function<ItemStack, Optional<IInternalSupply>> supplyOf,
            Predicate<IInternalSupply> wanted) {
        if (!any()) {
            return null;
        }
        SupplySearch search = new SupplySearch(supplyOf, wanted);
        forEach(entity, search);
        return search.found;
    }

    /** Whether an item in an extra head slot has life support that passes a test (COMPAT-042). */
    static boolean headPiece(LivingEntity entity, Predicate<ILifeSupport> gives) {
        if (!any()) {
            return false;
        }
        HeadSearch search = new HeadSearch(gives);
        forEach(entity, search);
        return search.found;
    }

    private static final class SupplySearch implements ExtraSlotVisitor {
        private final Function<ItemStack, Optional<IInternalSupply>> supplyOf;
        private final Predicate<IInternalSupply> wanted;

        @Nullable
        IInternalSupply found;

        SupplySearch(Function<ItemStack, Optional<IInternalSupply>> supplyOf, Predicate<IInternalSupply> wanted) {
            this.supplyOf = supplyOf;
            this.wanted = wanted;
        }

        @Override
        public void visit(ItemStack stack, ExtraSlotKind kind) {
            if (found == null && !stack.isEmpty()) {
                found = supplyOf.apply(stack).filter(wanted).orElse(null);
            }
        }
    }

    private static final class HeadSearch implements ExtraSlotVisitor {
        private final Predicate<ILifeSupport> gives;
        boolean found;

        HeadSearch(Predicate<ILifeSupport> gives) {
            this.gives = gives;
        }

        @Override
        public void visit(ItemStack stack, ExtraSlotKind kind) {
            if (!found && kind == ExtraSlotKind.HEAD && !stack.isEmpty()) {
                ILifeSupport support = Protections.of(stack);
                found = support != null && gives.test(support);
            }
        }
    }
}
