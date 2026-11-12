package io.github.west3436.zas.compat.slots.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.capability.IInternalSupply;
import io.github.west3436.zas.api.life.ExtraSlotKind;
import io.github.west3436.zas.api.life.ExtraSlots;
import io.github.west3436.zas.compat.Adapters;
import io.github.west3436.zas.compat.Adapters.Status;
import io.github.west3436.zas.life.Internals;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.slf4j.Logger;

/**
 * What the tests of the Curios and Accessories adapters share: what the run says it loaded, the
 * framework's view of an adapter, a player, and what the registered providers give for an entity.
 * It names nothing of either target, so every test class can use it in every run.
 */
final class Slots {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Curios' mod id. */
    static final String CURIOS = "curios";

    /** Accessories' mod id. */
    static final String ACCESSORIES = "accessories";

    private Slots() {}

    /** The version of a target the build loaded for this run, or null if it loaded none. */
    static String asked(String modId) {
        String version = System.getProperty("zas.slots." + modId, "");
        return version.isEmpty() ? null : version;
    }

    /** Whether the run has the content mod's items: it does unless the build switched the content off. */
    static boolean content() {
        return !"off".equals(System.getProperty("zas.slots.content"));
    }

    /** Checks that the target the build asked for is the one installed, and returns zas's row for it. */
    static Status status(GameTestHelper h, String modId) {
        String installed = Adapters.installedVersion(modId).orElse(null);
        h.assertTrue(installed != null, modId + " isn't installed on this server");
        h.assertValueEqual(installed, asked(modId), "the " + modId + " installed");
        Status status = Adapters.statuses().stream()
                .filter(s -> s.row().modId().equals(modId))
                .findFirst()
                .orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for " + modId);
        h.assertValueEqual(status.version(), installed, "the version the loader saw");
        return status;
    }

    /** Whether a provider with this id is registered. */
    static boolean registered(ResourceLocation id) {
        return ExtraSlots.registered().stream().anyMatch(r -> r.id().equals(id));
    }

    /** A survival fake player standing in the test's structure. */
    static FakePlayer player(GameTestHelper h, String name) {
        FakePlayer p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
        p.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
        return p;
    }

    /** An item tag. */
    static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    /** Whether any item is in a tag. */
    static boolean tagged(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.getTag(tag).map(items -> items.size() > 0).orElse(false);
    }

    /**
     * What the registered providers give for an entity, in order.
     *
     * @param stacks the stacks
     * @param kinds each one's kind
     */
    record Worn(List<ItemStack> stacks, List<ExtraSlotKind> kinds) {}

    /** What an entity wears in extra slots, as the framework is given it. */
    static Worn worn(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>();
        List<ExtraSlotKind> kinds = new ArrayList<>();
        ExtraSlots.forEach(entity, (stack, kind) -> {
            stacks.add(stack);
            kinds.add(kind);
        });
        return new Worn(stacks, kinds);
    }

    /** Takes the tanks an entity has one after another, each the first found, by their distribution pressures. */
    static void order(GameTestHelper h, LivingEntity entity, double[] expected, Runnable[] remove) {
        for (int i = 0; i < expected.length; i++) {
            Optional<IInternalSupply> found = Internals.find(entity);
            h.assertTrue(found.isPresent(), "no tank found, step " + i);
            close(h, found.get().distributionPressure(), expected[i], 1e-9, "the tank found, step " + i);
            remove[i].run();
        }
        h.assertTrue(Internals.find(entity).isEmpty(), "a tank was left");
    }

    /**
     * What {@code /zas life} tells an entity about itself, a line each. The lines go to the server's
     * log too, as an operator would read them on the console.
     */
    static List<String> life(LivingEntity entity) {
        List<String> said = new ArrayList<>();
        CommandSource listener = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        ServerLevel level = (ServerLevel) entity.level();
        CommandSourceStack source = new CommandSourceStack(
                listener,
                entity.position(),
                Vec2.ZERO,
                level,
                4,
                "zas_slots",
                Component.literal("zas_slots"),
                level.getServer(),
                entity);
        try {
            level.getServer().getCommands().getDispatcher().execute("zas life", source);
        } catch (CommandSyntaxException e) {
            throw new GameTestAssertException("/zas life failed: " + e.getMessage());
        }
        said.forEach(line -> LOGGER.info("/zas life: {}", line));
        return said;
    }

    /** The action bar's text while internals breathe from a tank in an extra slot. */
    static String onFromWorn() {
        return Component.translatable(
                        "message.zas.internals.on", Component.translatable("message.zas.internals.place.worn"))
                .getString();
    }

    static void close(GameTestHelper h, double actual, double expected, double tolerance, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= tolerance,
                what + ": expected " + expected + " ± " + tolerance + ", got " + actual);
    }
}
