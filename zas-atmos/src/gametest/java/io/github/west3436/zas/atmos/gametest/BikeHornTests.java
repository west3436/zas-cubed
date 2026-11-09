package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.registry.AtmosSounds;
import io.github.west3436.zas.atmos.tool.BikeHornItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The bike horn (CONT-154). Each test puts its players on the server's lists only while it runs,
 * within one game tick, so the sounds a server broadcasts reach them and nothing else sees them. A
 * test uses the horn as a right click or a hit does, through the player's game mode or its attack.
 * Nothing ticks the players, so a test ticks a player's cooldowns itself, one game tick at a time.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BikeHornTests {
    /** Rolls of each chest table: about 50 horns each at 5 %. The seeds are fixed, so the counts are too. */
    private static final int ROLLS = 1000;

    private BikeHornTests() {}

    /** A player on the server's lists at a block of the test's area, holding a horn if asked. */
    private static ListeningPlayer join(GameTestHelper h, int x, boolean horn) {
        ListeningPlayer l = ListeningPlayer.client(
                h.getLevel(), Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x, 2, 1))), false);
        if (horn) {
            l.player().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AtmosItems.BIKE_HORN.get()));
        }
        l.join();
        return l;
    }

    /** The honks a listener was sent: each at volume 1 and pitch 1, on the Players slider. */
    private static List<ClientboundSoundPacket> honks(GameTestHelper h, ListeningPlayer l) {
        List<ClientboundSoundPacket> honks = l.sounds().stream()
                .filter(s -> s.getSound().value() == AtmosSounds.BIKE_HORN.get())
                .toList();
        for (ClientboundSoundPacket s : honks) {
            h.assertValueEqual(s.getSource(), SoundSource.PLAYERS, "the honk's slider");
            h.assertValueEqual(s.getVolume(), 1f, "the honk's volume");
            h.assertValueEqual(s.getPitch(), 1f, "the honk's pitch");
        }
        return honks;
    }

    /** Uses the horn in the main hand, as a right click into the air does. */
    private static InteractionResult use(ServerPlayer p) {
        return p.gameMode.useItem(p, p.serverLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
    }

    private static boolean cooling(ServerPlayer p) {
        return p.getCooldowns().isOnCooldown(AtmosItems.BIKE_HORN.get());
    }

    private static void tick(ServerPlayer p, int ticks) {
        for (int i = 0; i < ticks; i++) {
            p.getCooldowns().tick();
        }
    }

    /**
     * Using it honks once where its user stands, for its user and for a player 6 blocks away, and
     * it can't be used again for 40 ticks. It stacks to one.
     */
    @GameTest(template = "empty")
    public static void cont_154_using_it_honks_for_everyone_and_starts_a_2_s_cooldown(GameTestHelper h) {
        SoundLog.listen();
        ListeningPlayer user = join(h, 1, true);
        ListeningPlayer near = join(h, 7, false);
        try {
            ServerPlayer p = user.player();
            h.assertValueEqual(p.getMainHandItem().getMaxStackSize(), 1, "a stack's size");
            h.assertTrue(use(p).consumesAction(), "using it did nothing");
            h.assertValueEqual(honks(h, user).size(), 1, "honks its user heard");
            h.assertValueEqual(honks(h, near).size(), 1, "honks a player 6 blocks away heard");
            // Where the server played it, from its own event: a packet's position is a float, too coarse
            // this far out.
            h.assertTrue(
                    SoundLog.take(p.position()).stream().anyMatch(s -> s.id().equals(AtmosSounds.BIKE_HORN.getId())),
                    "it didn't honk where its user stands");

            tick(p, BikeHornItem.COOLDOWN - 1);
            h.assertTrue(cooling(p), "the cooldown ended before 40 ticks");
            tick(p, 1);
            h.assertFalse(cooling(p), "the cooldown lasts past 40 ticks");
        } finally {
            user.leave();
            near.leave();
        }
        h.succeed();
    }

    /**
     * A use 20 ticks into the cooldown, by a right click or by a direct call, plays nothing and
     * doesn't restart it: it still ends 40 ticks after the first use, and the horn honks again then.
     */
    @GameTest(template = "empty")
    public static void cont_154_a_use_during_the_cooldown_does_nothing(GameTestHelper h) {
        ListeningPlayer user = join(h, 1, true);
        try {
            ServerPlayer p = user.player();
            use(p);
            h.assertValueEqual(honks(h, user).size(), 1, "honks after the first use");
            tick(p, 20);
            h.assertFalse(use(p).consumesAction(), "a right click during the cooldown was used");
            ItemStack stack = p.getMainHandItem();
            h.assertFalse(
                    stack.use(p.serverLevel(), p, InteractionHand.MAIN_HAND)
                            .getResult()
                            .consumesAction(),
                    "a direct use during the cooldown was used");
            h.assertValueEqual(honks(h, user).size(), 1, "honks after two uses during the cooldown");
            tick(p, BikeHornItem.COOLDOWN - 21);
            h.assertTrue(cooling(p), "the cooldown ended early");
            tick(p, 1);
            h.assertFalse(cooling(p), "a use during the cooldown restarted it");
            h.assertTrue(use(p).consumesAction(), "it can't be used after the cooldown");
            h.assertValueEqual(honks(h, user).size(), 2, "honks after the cooldown");
        } finally {
            user.leave();
        }
        h.succeed();
    }

    /**
     * A hit on a mob honks once, for its user and for a player 4 blocks away, and starts no cooldown.
     * A hit during a use's cooldown honks too. A hit with an empty hand doesn't.
     */
    @GameTest(template = "empty")
    public static void cont_154_hitting_a_mob_honks(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ListeningPlayer user = join(h, 1, true);
        ListeningPlayer near = join(h, 5, false);
        Pig pig = EntityType.PIG.create(level);
        pig.setNoAi(true);
        pig.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2, 2, 1))));
        level.addFreshEntity(pig);
        try {
            ServerPlayer p = user.player();
            float health = pig.getHealth();
            p.attack(pig);
            h.assertTrue(pig.getHealth() < health, "the hit didn't land");
            h.assertValueEqual(honks(h, user).size(), 1, "honks its user heard");
            h.assertValueEqual(honks(h, near).size(), 1, "honks a player 4 blocks away heard");
            h.assertFalse(cooling(p), "a hit started the cooldown");

            use(p);
            h.assertTrue(cooling(p), "the use started no cooldown");
            pig.invulnerableTime = 0;
            p.attack(pig);
            h.assertValueEqual(honks(h, user).size(), 3, "honks after a use and a hit during its cooldown");

            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            pig.invulnerableTime = 0;
            health = pig.getHealth();
            p.attack(pig);
            h.assertTrue(pig.getHealth() < health, "the empty hand's hit didn't land");
            h.assertValueEqual(honks(h, user).size(), 3, "honks after a hit with an empty hand");
        } finally {
            pig.discard();
            user.leave();
            near.leave();
        }
        h.succeed();
    }

    /**
     * The global loot modifier adds the horn to dungeon chests and the three stronghold chests,
     * one at a time, at about 5 % a chest: 30 to 75 in 1,000 rolls of each, against the 50 expected.
     * A desert pyramid's chest never has one.
     */
    @GameTest(template = "empty")
    public static void cont_154_dungeon_and_stronghold_chests_sometimes_hold_one(GameTestHelper h) {
        for (ResourceKey<LootTable> chest : List.of(
                BuiltInLootTables.SIMPLE_DUNGEON,
                BuiltInLootTables.STRONGHOLD_CORRIDOR,
                BuiltInLootTables.STRONGHOLD_CROSSING,
                BuiltInLootTables.STRONGHOLD_LIBRARY)) {
            int horns = horns(h, chest);
            h.assertTrue(
                    horns >= 30 && horns <= 75,
                    chest.location() + " held " + horns + " horns in " + ROLLS + " rolls, not about 50");
        }
        h.assertValueEqual(horns(h, BuiltInLootTables.DESERT_PYRAMID), 0, "horns in desert pyramid chests");
        h.succeed();
    }

    /** The horns in {@link #ROLLS} rolls of a chest table, with seeds 1 to ROLLS; each stack holds one. */
    private static int horns(GameTestHelper h, ResourceKey<LootTable> chest) {
        ServerLevel level = h.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(chest);
        h.assertTrue(table != LootTable.EMPTY, chest.location() + " didn't load");
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(h.absolutePos(BlockPos.ZERO)))
                .create(LootContextParamSets.CHEST);
        int horns = 0;
        for (long seed = 1; seed <= ROLLS; seed++) {
            for (ItemStack stack : table.getRandomItems(params, seed)) {
                if (stack.is(AtmosItems.BIKE_HORN.get())) {
                    h.assertValueEqual(stack.getCount(), 1, "a horn stack's count");
                    horns++;
                }
            }
        }
        return horns;
    }
}
