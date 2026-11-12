package io.github.west3436.zas.gametest;

import io.github.west3436.zas.ZasMod;
import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlotVisitor;
import io.github.west3436.zas.api.life.ExtraSlots;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Extra equipment slots for GameTests, standing in for a mod that adds them (COMPAT-040 to
 * COMPAT-042): a registered {@link io.github.west3436.zas.api.life.ExtraSlotProvider} that gives an
 * entity the stacks a test put in its slots, in the order it put them there. It knows only the
 * entities a test gave it, so the other tests running beside it see no slots. Closing it
 * unregisters it.
 */
final class TestExtraSlots implements AutoCloseable {
    private record Slot(ItemStack stack, ExtraSlotKind kind) {}

    private final ResourceLocation id;
    private final Map<LivingEntity, List<Slot>> worn = new IdentityHashMap<>();

    /** How many times it has been asked about an entity it knows. */
    int asked;

    /** What it throws when asked about an entity it knows, after giving that entity's first item; null for nothing. */
    Throwable failing;

    /**
     * Registers it.
     *
     * @param path the path of its id, which decides when it is asked among the registered providers
     */
    TestExtraSlots(String path) {
        this.id = ResourceLocation.fromNamespaceAndPath(ZasMod.MOD_ID, path);
        ExtraSlots.register(id, this::items);
    }

    /** Its id. */
    ResourceLocation id() {
        return id;
    }

    /** Puts a stack in a new slot of an entity's, after the ones it has. */
    void wear(LivingEntity entity, ExtraSlotKind kind, ItemStack stack) {
        worn.computeIfAbsent(entity, e -> new ArrayList<>()).add(new Slot(stack, kind));
    }

    /** Empties an entity's slots. */
    void clear(LivingEntity entity) {
        worn.remove(entity);
    }

    private void items(LivingEntity entity, ExtraSlotVisitor visitor) {
        List<Slot> slots = worn.get(entity);
        if (slots == null) {
            return;
        }
        asked++;
        for (int i = 0; i < slots.size(); i++) {
            visitor.visit(slots.get(i).stack(), slots.get(i).kind());
            if (i == 0 && failing != null) {
                sneak(failing);
            }
        }
        if (slots.isEmpty() && failing != null) {
            sneak(failing);
        }
    }

    /** Throws anything, an error included, as a faulty provider might. */
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void sneak(Throwable t) throws T {
        throw (T) t;
    }

    @Override
    public void close() {
        ExtraSlots.unregister(id);
    }
}
