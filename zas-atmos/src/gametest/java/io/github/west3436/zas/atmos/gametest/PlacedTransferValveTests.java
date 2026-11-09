package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.device.DeviceTicker;
import io.github.west3436.zas.atmos.failure.ContainerFailure;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosComponents;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlock;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.ttv.TransferValveMenu;
import io.github.west3436.zas.config.AtmospherePreset;
import io.github.west3436.zas.config.ZasServerConfig;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A placed tank transfer valve (CONT-103, CONT-104, D-86): sneak-using the item places it, breaking it
 * gives the item back as it was, a tank used on it fits, an empty hand opens its screen, and Start
 * primes it where it stands. Its gas reacts and fails as a held TTV's does, and its screen moves each
 * tank once, however many players have it open. Each test counts every tank after each step. The
 * areas are 951 and up.
 *
 * <p>The block goes at (2, 1, 4), with a player at (2, 1, 2) facing it, to the south. A fake player
 * opens no menus, so the tests that need the game to open one use a real {@link ServerPlayer} on a
 * connection that keeps what it is sent.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacedTransferValveTests {
    private static final String BATCH = "zas_atmos_ttv_placed";

    /** For the test that drives atmos ticks itself. */
    private static final String FAILURE_BATCH = "zas_atmos_ttv_placed_failure";

    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;
    private static final ResourceLocation H2 = ResourceLocation.fromNamespaceAndPath("zas", "hydrogen");
    private static final ResourceLocation H2O = ResourceLocation.fromNamespaceAndPath("zas", "water_vapor");
    private static final double T = StandardAir.TEMPERATURE;

    private static final int FIRST = TransferValve.FIRST;
    private static final int SECOND = TransferValve.SECOND;

    /** The moles in the two tanks most tests use. */
    private static final double O2_MOLES = 5.0;

    private static final double H2_MOLES = 1.5;

    /** The menu's slots: the two tanks, three rows of the inventory, the hotbar. */
    private static final int ROWS_START = 2;

    private static final int HOTBAR_START = ROWS_START + 27;

    /** Where the block goes. */
    private static final int X = 2;

    private static final int Y = 1;
    private static final int Z = 4;

    private PlacedTransferValveTests() {}

    // ---- Helpers ---------------------------------------------------------------------------

    /** A fake player that keeps what it drops, at (2, 1, 2), facing south. */
    private static final class TestPlayer extends FakePlayer {
        final List<ItemStack> drops = new ArrayList<>();

        TestPlayer(Area a) {
            super(a.level, new GameProfile(UUID.randomUUID(), "zas_ttv_placed"));
            Vec3 at = Vec3.atBottomCenterOf(a.abs(2, 1, 2));
            moveTo(at.x, at.y, at.z, 0, 0);
        }

        @Override
        public ItemEntity drop(ItemStack stack, boolean dropAround, boolean includeThrowerName) {
            if (!stack.isEmpty()) {
                drops.add(stack.copy());
            }
            return null;
        }
    }

    /** A player as a server has one, whose menus open, at (2, 1, 2), facing south. */
    private static ServerPlayer realPlayer(Area a, List<Packet<?>> sent) {
        ServerPlayer p = new ServerPlayer(
                a.level.getServer(),
                a.level,
                new GameProfile(UUID.randomUUID(), "zas_ttv_placed_real"),
                ClientInformation.createDefault());
        new ListeningPlayer.Recording(a.level, p, sent);
        Vec3 at = Vec3.atBottomCenterOf(a.abs(2, 1, 2));
        p.moveTo(at.x, at.y, at.z, 0, 0);
        return p;
    }

    /** A stone room around the player and the block, x 0 to 4 and z 0 to 6. */
    private static void room(Area a) {
        a.box(0, 0, 4, 6);
    }

    private static void clear(Area a) {
        a.clear(-1, -1, 5, 7);
    }

    private static BlockPos pos(Area a) {
        return a.abs(X, Y, Z);
    }

    /** A hit on the top of the floor where the block goes. */
    private static BlockHitResult floor(Area a) {
        BlockPos floor = a.abs(X, Y - 1, Z);
        return new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false);
    }

    /** A hit on the block, from the player's side. */
    private static BlockHitResult on(Area a) {
        return new BlockHitResult(Vec3.atCenterOf(pos(a)), Direction.NORTH, pos(a), false);
    }

    /** Right-clicks with a stack in the main hand, through the game's own path. */
    private static InteractionResult use(Area a, ServerPlayer p, ItemStack held, BlockHitResult hit) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return p.gameMode.useItemOn(p, a.level, held, InteractionHand.MAIN_HAND, hit);
    }

    /** Places a TTV from a stack as sneak-using it on the floor does. */
    private static TransferValveBlockEntity sneakPlace(GameTestHelper h, Area a, ServerPlayer p, ItemStack ttv) {
        p.setShiftKeyDown(true);
        InteractionResult result = use(a, p, ttv, floor(a));
        p.setShiftKeyDown(false);
        h.assertTrue(result.consumesAction(), "sneak-using it on the floor gave " + result);
        h.assertTrue(
                a.get(X, Y, Z).is(AtmosBlocks.TANK_TRANSFER_VALVE.get()), "sneak-using it placed " + a.get(X, Y, Z));
        return a.entity(X, Y, Z);
    }

    /** A TTV put at a place with a stack's tanks, valve and timer, as placing gives them. */
    private static TransferValveBlockEntity placed(Area a, int x, int y, int z, ItemStack ttv, Direction facing) {
        a.place(
                x,
                y,
                z,
                AtmosBlocks.TANK_TRANSFER_VALVE
                        .get()
                        .defaultBlockState()
                        .setValue(TransferValveBlock.FACING, facing)
                        .setValue(TransferValveBlock.TANKS, TransferValveItem.tankCount(ttv))
                        .setValue(
                                TransferValveBlock.OPEN,
                                TransferValveItem.valve(ttv).isOpen()));
        TransferValveBlockEntity be = a.entity(x, y, z);
        be.applyComponentsFromItemStack(ttv);
        return be;
    }

    private static TransferValveBlockEntity placed(Area a, ItemStack ttv) {
        return placed(a, X, Y, Z, ttv, Direction.NORTH);
    }

    /** The screen of a placed TTV. */
    private static TransferValveMenu open(TestPlayer p, TransferValveBlockEntity ttv) {
        TransferValveMenu menu = new TransferValveMenu(1, p.getInventory(), ttv);
        p.containerMenu = menu;
        return menu;
    }

    /** A plain 70 L tank holding a gas. */
    private static ItemStack tank(Map<ResourceLocation, Double> moles, double temperature) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, new GasParcel(moles, temperature));
        return tank;
    }

    private static ItemStack oxygen() {
        return tank(Map.of(O2, O2_MOLES), T);
    }

    private static ItemStack hydrogen() {
        return tank(Map.of(H2, H2_MOLES), T);
    }

    /** A plain 70 L tank of N2 at a pressure. */
    private static ItemStack nitrogen(double atm) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, GasTankItem.fill(Map.of(N2, 1.0), atm * ContainerFailure.ATM_KPA, 70));
        return tank;
    }

    /** A TTV stack with these tanks, either of which may be empty, its timer as given. */
    private static ItemStack ttv(ItemStack first, ItemStack second, int timer) {
        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(stack, new TransferValve(first, second, false));
        TransferValveItem.setTimer(stack, timer);
        return stack;
    }

    /** A TTV with the tanks most tests use. */
    private static ItemStack loaded(int timer) {
        return ttv(oxygen(), hydrogen(), timer);
    }

    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(
                Math.abs(actual - expected) <= Area.REL * Math.max(1, Math.abs(expected)),
                what + ": expected " + expected + ", got " + actual);
    }

    /** Checks a TTV holds what a stack holds: its tanks, each with every component, its valve and its timer. */
    private static void same(GameTestHelper h, ItemStack actual, ItemStack expected, String what) {
        for (DataComponentType<?> type : List.of(
                AtmosComponents.TTV_TANKS.get(), AtmosComponents.TTV_OPEN.get(), AtmosComponents.TTV_TIMER.get())) {
            h.assertTrue(
                    Objects.equals(actual.get(type), expected.get(type)),
                    what + ": " + type + " is " + actual.get(type) + ", not " + expected.get(type));
        }
    }

    /** Every tank in these stacks, a TTV's included. */
    private static List<ItemStack> tanks(List<ItemStack> stacks) {
        List<ItemStack> found = new ArrayList<>();
        stacks.forEach(s -> collect(found, s));
        return found;
    }

    private static void collect(List<ItemStack> found, ItemStack stack) {
        if (stack.getItem() instanceof GasTankItem) {
            for (int i = 0; i < stack.getCount(); i++) {
                found.add(stack);
            }
        } else if (stack.getItem() instanceof TransferValveItem) {
            collect(found, TransferValveItem.tank(stack, FIRST));
            collect(found, TransferValveItem.tank(stack, SECOND));
        }
    }

    /** What a player has: its inventory, its cursor and what it dropped. */
    private static List<ItemStack> has(TestPlayer p) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            out.add(p.getInventory().getItem(i));
        }
        out.add(p.containerMenu.getCarried());
        out.addAll(p.drops);
        return out;
    }

    /** Checks the usual two tanks are among these stacks exactly once each, with their gas. */
    private static void bothOnce(GameTestHelper h, List<ItemStack> stacks, String when) {
        List<ItemStack> found = tanks(stacks);
        h.assertValueEqual(found.size(), 2, "tanks " + when);
        close(h, found.stream().mapToDouble(t -> GasTankItem.gas(t).moles(O2)).sum(), O2_MOLES, "O2 " + when);
        close(h, found.stream().mapToDouble(t -> GasTankItem.gas(t).moles(H2)).sum(), H2_MOLES, "H2 " + when);
    }

    /** The menu's slot for a place in the inventory: the hotbar is 0 to 8, the rows 9 to 35. */
    private static int slotOf(int inventoryIndex) {
        return inventoryIndex < 9 ? HOTBAR_START + inventoryIndex : ROWS_START + inventoryIndex - 9;
    }

    /** The item entities and primed TTVs that joined a level since {@link #watch}. */
    private static final List<ItemEntity> DROPPED = new ArrayList<>();

    private static final List<PrimedTransferValve> PRIMED = new ArrayList<>();

    private static boolean watching;

    /**
     * Starts catching dropped items and primed TTVs afresh. A level only finds entities in a chunk that
     * is ticking them, so these tests see each one as it joins. Each runs within one call, so nothing
     * else adds to these in the meantime.
     */
    private static void watch() {
        if (!watching) {
            watching = true;
            NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent e) -> {
                if (e.getEntity() instanceof ItemEntity item) {
                    DROPPED.add(item);
                } else if (e.getEntity() instanceof PrimedTransferValve primed) {
                    PRIMED.add(primed);
                }
            });
        }
        DROPPED.clear();
        PRIMED.clear();
    }

    /** The TTVs dropped near the block since {@link #watch}; every item's entity is removed. */
    private static List<ItemStack> droppedTtvs(Area a) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemEntity item : DROPPED) {
            if (item.getItem().getItem() instanceof TransferValveItem
                    && item.position().distanceTo(a.point(X, Y, Z)) < 16) {
                out.add(item.getItem().copy());
            }
            item.discard();
        }
        DROPPED.clear();
        return out;
    }

    /** The primed TTVs since {@link #watch}, which are removed from the level. */
    private static List<PrimedTransferValve> primed() {
        List<PrimedTransferValve> out = new ArrayList<>(PRIMED);
        PRIMED.clear();
        out.forEach(PrimedTransferValve::discard);
        return out;
    }

    private static void withPreset(AtmospherePreset preset, Runnable checks) {
        AtmospherePreset before = ZasServerConfig.preset();
        try {
            ZasServerConfig.setPreset(preset);
            checks.run();
        } finally {
            ZasServerConfig.setPreset(before);
        }
    }

    // ---- Placing and picking up ------------------------------------------------------------

    /**
     * Sneak-using the item on a block places it, its timer toward the player, with the item's tanks,
     * valve and timer, and the hand is empty. Plain use places nothing, and goes on to the item's own
     * use, which opens its screen.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_sneak_use_places_it_and_plain_use_opens_its_screen(GameTestHelper h) {
        Area a = new Area(h, 951);
        room(a);
        ServerPlayer p = realPlayer(a, new ArrayList<>());
        ItemStack stack = loaded(37);
        ItemStack before = stack.copy();

        h.assertFalse(use(a, p, stack, floor(a)).consumesAction(), "plain use on the floor stopped there");
        h.assertTrue(a.get(X, Y, Z).isAir(), "plain use placed " + a.get(X, Y, Z));
        p.gameMode.useItem(p, a.level, stack, InteractionHand.MAIN_HAND);
        h.assertTrue(
                p.containerMenu instanceof TransferValveMenu menu && !menu.isPlaced(),
                "plain use opened " + p.containerMenu);
        p.closeContainer();
        h.assertTrue(ItemStack.matches(p.getMainHandItem(), before), "plain use changed the TTV in hand");

        TransferValveBlockEntity ttv = sneakPlace(h, a, p, stack);
        h.assertTrue(p.getMainHandItem().isEmpty(), "the hand kept " + p.getMainHandItem());
        BlockState state = a.get(X, Y, Z);
        h.assertValueEqual(state.getValue(TransferValveBlock.FACING), Direction.NORTH, "the way its timer faces");
        h.assertValueEqual(state.getValue(TransferValveBlock.TANKS), 2, "the tanks it shows");
        h.assertFalse(state.getValue(TransferValveBlock.OPEN), "it shows its valve open");
        same(h, ttv.item(), before, "what it holds");
        clear(a);
        h.succeed();
    }

    /**
     * Broken by hand it goes at once, and drops as the item it was placed from, down to the last
     * component: its tanks with their gas, its valve, its timer and its name. Saved and read back, it
     * holds the same (DATA-016). Broken in creative, it drops nothing, as any block does.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_broken_it_drops_as_the_item_it_was_placed_from(GameTestHelper h) throws IOException {
        Area a = new Area(h, 952);
        room(a);
        TestPlayer p = new TestPlayer(a);
        HolderLookup.Provider registries = a.level.registryAccess();
        ItemStack named = loaded(37);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Ballast"));
        ItemStack open = loaded(0);
        TransferValveItem.toggle(open);
        ItemStack lone = ttv(ItemStack.EMPTY, hydrogen(), TransferValve.MAX_TIMER);
        ItemStack bare = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());

        for (ItemStack stack : List.of(named, open, lone, bare)) {
            ItemStack before = stack.copy();
            String what = before.getComponentsPatch().toString();
            TransferValveBlockEntity ttv = sneakPlace(h, a, p, stack);
            BlockPos pos = pos(a);
            BlockState state = a.get(X, Y, Z);
            h.assertTrue(state.getDestroyProgress(p, a.level, pos) >= 1, "it doesn't break at once by hand");
            h.assertValueEqual(
                    state.getValue(TransferValveBlock.TANKS),
                    TransferValveItem.tankCount(before),
                    "tanks shown, " + what);
            h.assertValueEqual(
                    state.getValue(TransferValveBlock.OPEN),
                    TransferValveItem.valve(before).isOpen(),
                    "valve shown, " + what);

            CompoundTag saved = ttv.saveWithFullMetadata(registries);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(saved, bytes);
            CompoundTag read =
                    NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            BlockEntity loaded = BlockEntity.loadStatic(pos, state, read, registries);
            h.assertTrue(loaded instanceof TransferValveBlockEntity, "it read back as " + loaded);
            same(h, ((TransferValveBlockEntity) loaded).item(), before, "read back, " + what);

            watch();
            h.assertTrue(p.gameMode.destroyBlock(pos), "breaking it failed");
            h.assertTrue(a.get(X, Y, Z).isAir(), "breaking it left " + a.get(X, Y, Z));
            List<ItemStack> drops = droppedTtvs(a);
            h.assertValueEqual(drops.size(), 1, "TTVs dropped, " + what);
            h.assertTrue(
                    ItemStack.matches(drops.get(0), before),
                    "it dropped " + drops.get(0).getComponentsPatch() + ", not " + what);
        }

        p.setGameMode(GameType.CREATIVE);
        sneakPlace(h, a, p, loaded(10));
        watch();
        h.assertTrue(p.gameMode.destroyBlock(pos(a)), "breaking it in creative failed");
        h.assertTrue(droppedTtvs(a).isEmpty(), "it dropped in creative");
        clear(a);
        h.succeed();
    }

    /**
     * A blast from elsewhere breaks it, and it drops whole, its tanks with their gas, even where a blast
     * drops only some of what it breaks.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_blast_from_elsewhere_drops_it_whole(GameTestHelper h) {
        Area a = new Area(h, 957);
        room(a);
        ItemStack stack = loaded(37);
        placed(a, stack.copy());
        watch();
        Vec3 at = a.point(X + 1, Y, Z);
        a.level.explode(null, at.x, at.y, at.z, 2.0f, Level.ExplosionInteraction.BLOCK);
        h.assertTrue(a.get(X, Y, Z).isAir(), "the blast left it");
        List<ItemStack> drops = droppedTtvs(a);
        h.assertValueEqual(drops.size(), 1, "TTVs dropped");
        h.assertTrue(
                ItemStack.matches(drops.get(0), stack),
                "it dropped " + drops.get(0).getComponentsPatch());
        clear(a);
        h.succeed();
    }

    // ---- Using it where it stands ----------------------------------------------------------

    /**
     * A tank used on it fits in the first free place, with the bundle's sound from the block, and leaves
     * the hand, in creative too. A third tank stays in hand and changes nothing. An empty hand opens its
     * screen; a stick does nothing, and a TTV in hand opens its own.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_tank_used_on_it_fits_and_an_empty_hand_opens_its_screen(GameTestHelper h) {
        Area a = new Area(h, 953);
        room(a);
        SoundLog.listen();
        ServerPlayer p = realPlayer(a, new ArrayList<>());
        TransferValveBlockEntity ttv = sneakPlace(h, a, p, new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get()));
        SoundLog.take(a.point(X, Y, Z));

        ItemStack o2 = oxygen();
        h.assertTrue(use(a, p, o2.copy(), on(a)).consumesAction(), "using a tank on it did nothing");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the tank stayed in hand");
        h.assertValueEqual(a.get(X, Y, Z).getValue(TransferValveBlock.TANKS), 1, "the tanks it shows");
        h.assertTrue(ItemStack.matches(TransferValveItem.tank(ttv.item(), FIRST), o2), "the first place holds another");
        h.assertTrue(
                SoundLog.take(a.point(X, Y, Z)).stream()
                        .anyMatch(s -> s.id().equals(SoundEvents.BUNDLE_INSERT.getLocation())
                                && s.channel() == SoundSource.BLOCKS),
                "fitting a tank made no bundle sound at the block");

        p.setGameMode(GameType.CREATIVE);
        ItemStack h2 = hydrogen();
        h.assertTrue(use(a, p, h2.copy(), on(a)).consumesAction(), "using a tank in creative did nothing");
        h.assertTrue(p.getMainHandItem().isEmpty(), "a creative player kept a copy");
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(
                ItemStack.matches(TransferValveItem.tank(ttv.item(), SECOND), h2), "the second place holds another");
        h.assertValueEqual(a.get(X, Y, Z).getValue(TransferValveBlock.TANKS), 2, "the tanks it shows with two");

        ItemStack third = nitrogen(1);
        ItemStack full = ttv.item();
        h.assertTrue(use(a, p, third.copy(), on(a)).consumesAction(), "a third tank's click went on");
        h.assertTrue(ItemStack.matches(p.getMainHandItem(), third), "the third tank in hand changed");
        same(h, ttv.item(), full, "with a third tank offered");

        h.assertTrue(use(a, p, ItemStack.EMPTY, on(a)).consumesAction(), "an empty hand did nothing");
        h.assertTrue(
                p.containerMenu instanceof TransferValveMenu menu && menu.isPlaced(),
                "an empty hand opened " + p.containerMenu);
        h.assertTrue(ItemStack.matches(p.containerMenu.getSlot(FIRST).getItem(), o2), "the screen's first slot");
        p.closeContainer();

        h.assertFalse(use(a, p, new ItemStack(Items.STICK), on(a)).consumesAction(), "a stick did something");
        h.assertTrue(p.containerMenu == p.inventoryMenu, "a stick opened " + p.containerMenu);

        ItemStack held = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        h.assertFalse(use(a, p, held, on(a)).consumesAction(), "a TTV in hand stopped at the placed one");
        p.gameMode.useItem(p, a.level, held, InteractionHand.MAIN_HAND);
        h.assertTrue(
                p.containerMenu instanceof TransferValveMenu menu && !menu.isPlaced(),
                "a TTV in hand opened " + p.containerMenu);
        p.closeContainer();
        same(h, ttv.item(), full, "after it was used");
        clear(a);
        h.succeed();
    }

    /**
     * Start primes it where it stands: the block goes, and a primed TTV stands in its place, still,
     * with its tanks, its timer, and its timer facing the same way. Its screen stops. Start waits for a
     * tank on the cursor, and an open valve won't start.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_start_primes_it_where_it_stands(GameTestHelper h) {
        Area a = new Area(h, 954);
        room(a);
        TestPlayer p = new TestPlayer(a);
        TransferValveBlockEntity ttv = placed(a, X, Y, Z, loaded(10), Direction.EAST);
        TransferValveMenu menu = open(p, ttv);
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.UP_10), "the timer didn't move");
        ItemStack before = ttv.item();
        h.assertValueEqual(TransferValveItem.timer(before), 20, "the timer after +10");

        watch();
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.START), "it started with a tank on the cursor");
        menu.clicked(FIRST, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().isEmpty(), "the tank didn't go back");
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.START), "Start did nothing");
        h.assertTrue(a.get(X, Y, Z).isAir(), "the block stayed as " + a.get(X, Y, Z));
        h.assertFalse(menu.stillValid(p), "its screen outlived it");
        List<PrimedTransferValve> primed = primed();
        h.assertValueEqual(primed.size(), 1, "primed TTVs");
        PrimedTransferValve one = primed.get(0);
        h.assertValueEqual(one.position(), Vec3.atBottomCenterOf(pos(a)), "where the primed TTV is");
        h.assertValueEqual(one.getDeltaMovement(), Vec3.ZERO, "how it moves");
        h.assertValueEqual(one.getYRot(), Direction.WEST.toYRot(), "its turn, its timer to the east");
        h.assertValueEqual(one.timer(), 20, "its timer");
        h.assertFalse(one.valve().isOpen(), "its valve is open");
        h.assertTrue(
                ItemStack.matches(one.valve().first(), TransferValveItem.tank(before, FIRST))
                        && ItemStack.matches(one.valve().second(), TransferValveItem.tank(before, SECOND)),
                "its tanks aren't the placed TTV's");
        h.assertTrue(droppedTtvs(a).isEmpty(), "priming it dropped a TTV");

        ItemStack open = loaded(10);
        TransferValveItem.toggle(open);
        TransferValveBlockEntity opened = placed(a, open);
        menu = open(p, opened);
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.START), "an open valve started");
        h.assertTrue(a.get(X, Y, Z).is(AtmosBlocks.TANK_TRANSFER_VALVE.get()), "an open valve's block went");
        h.assertTrue(primed().isEmpty(), "an open valve primed");
        clear(a);
        h.succeed();
    }

    /** Redstone does nothing to it: powered, it holds what it held, and its timer never starts. */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_104_redstone_does_nothing_to_it(GameTestHelper h) {
        Area a = new Area(h, 961);
        room(a);
        ItemStack stack = loaded(0); // started, it would open at once
        TransferValveBlockEntity ttv = placed(a, stack);
        BlockState before = a.get(X, Y, Z);
        watch();
        a.set(X + 1, Y, Z, Blocks.REDSTONE_BLOCK.defaultBlockState());
        h.assertTrue(a.level.hasNeighborSignal(pos(a)), "no signal reaches it");
        ttv.step();
        h.assertValueEqual(a.get(X, Y, Z), before, "its block, powered");
        same(h, ttv.item(), stack, "what it holds, powered");
        h.assertTrue(primed().isEmpty(), "a signal primed it");
        h.assertFalse(before.hasAnalogOutputSignal(), "it gives a comparator a signal");
        a.set(X + 1, Y, Z, Blocks.AIR.defaultBlockState());
        h.assertValueEqual(a.get(X, Y, Z), before, "its block, unpowered");
        clear(a);
        h.succeed();
    }

    // ---- Its gas --------------------------------------------------------------------------

    /**
     * Its tanks' gas reacts each atmos tick (CONT-100): it is stepped with the devices, and a hot
     * mixture behind its open valve burns, as in a held TTV, and stays standing. The same gases cold
     * don't.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_gas_reacts_in_a_placed_ttv(GameTestHelper h) {
        Area a = new Area(h, 955);
        room(a);
        ItemStack hot = ttv(tank(Map.of(O2, O2_MOLES), 1500), hydrogen(), 10);
        h.assertTrue(TransferValveItem.toggle(hot), "the hot valve refused to open");
        TransferValveBlockEntity ttv = placed(a, hot);
        h.assertTrue(DeviceTicker.isScheduled(ttv), "it isn't stepped with the devices");
        ttv.step();
        GasParcel burnt = GasTankItem.gas(TransferValveItem.tank(ttv.item(), SECOND));
        h.assertValueEqual(burnt.moles(H2), 0.0, "H2 left after the burn");
        close(h, burnt.moles(H2O), H2_MOLES, "water after the burn");
        h.assertTrue(a.get(X, Y, Z).is(AtmosBlocks.TANK_TRANSFER_VALVE.get()), "the burn took the block");
        h.assertTrue(a.get(X, Y, Z).getValue(TransferValveBlock.OPEN), "it shows its valve shut");

        ItemStack cold = ttv(oxygen(), hydrogen(), 10);
        h.assertTrue(TransferValveItem.toggle(cold), "the cold valve refused to open");
        a.set(X, Y, Z, Blocks.AIR.defaultBlockState());
        TransferValveBlockEntity unlit = placed(a, cold);
        unlit.step();
        GasParcel kept = GasTankItem.gas(TransferValveItem.tank(unlit.item(), SECOND));
        h.assertValueEqual(kept.moles(H2O), 0.0, "water from the cold mixture");
        close(h, kept.moles(H2), H2_MOLES, "H2 in the cold mixture");
        clear(a);
        h.succeed();
    }

    /**
     * Its tanks fail as a held TTV's do (CONT-038), at the block's middle: a leaking tank stays on, a
     * tank that ruptures leaves its place empty with no blast, and one that explodes takes the block
     * with it, with nothing dropped, the other tank's gas going into the room. Under Hardcore, where the
     * blast breaks the block itself, it still drops nothing.
     */
    @GameTest(template = "empty", batch = FAILURE_BATCH)
    public static void cont_103_a_placed_ttvs_tanks_fail_as_a_held_ones_do(GameTestHelper h) {
        Blasts.listen();
        Area a = new Area(h, 956);
        a.box(0, 0, 4, 4);
        TransferValveBlockEntity ttv =
                placed(a, 2, 1, 2, new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get()), Direction.NORTH);
        a.fill(2, 2, 2, Map.of(N2, 1.0));
        double[] room = {a.zone(2, 2, 2).mixture().totalMoles()};
        Runnable inRoom = () -> {
            double now = a.zone(2, 2, 2).mixture().totalMoles();
            h.assertTrue(Math.abs(now - room[0]) < 1e-6, "the gas in the room: expected " + room[0] + ", got " + now);
        };

        ItemStack leaky = nitrogen(35);
        double held = GasTankItem.gas(leaky).totalMoles();
        ttv.applyComponentsFromItemStack(ttv(oxygen(), leaky, 10));
        ttv.step();
        a.tick();
        double left =
                GasTankItem.gas(TransferValveItem.tank(ttv.item(), SECOND)).totalMoles();
        h.assertTrue(left < held, "the leaking tank didn't lose its gas: " + left + " of " + held);
        room[0] += held - left;
        inRoom.run();
        h.assertValueEqual(a.get(2, 1, 2).getValue(TransferValveBlock.TANKS), 2, "the tanks it shows after a leak");

        for (boolean firstFails : new boolean[] {true, false}) {
            ItemStack burst = nitrogen(45);
            room[0] += GasTankItem.gas(burst).totalMoles();
            ttv.applyComponentsFromItemStack(firstFails ? ttv(burst, oxygen(), 10) : ttv(oxygen(), burst, 10));
            ttv.step();
            a.tick();
            inRoom.run();
            ItemStack now = ttv.item();
            h.assertTrue(
                    TransferValveItem.tank(now, firstFails ? FIRST : SECOND).isEmpty(), "the burst tank is on");
            close(
                    h,
                    GasTankItem.gas(TransferValveItem.tank(now, firstFails ? SECOND : FIRST))
                            .moles(O2),
                    O2_MOLES,
                    "the other tank's O2");
            h.assertValueEqual(
                    a.get(2, 1, 2).getValue(TransferValveBlock.TANKS), 1, "the tanks it shows after a rupture");
        }
        h.assertTrue(Blasts.take(a.point(2, 1, 2)).isEmpty(), "a rupture exploded");

        for (boolean firstFails : new boolean[] {true, false}) {
            ItemStack burst = nitrogen(60);
            room[0] += GasTankItem.gas(burst).totalMoles() + O2_MOLES;
            TransferValveBlockEntity bomb = placed(
                    a, 2, 1, 2, firstFails ? ttv(burst, oxygen(), 10) : ttv(oxygen(), burst, 10), Direction.NORTH);
            watch();
            bomb.step();
            a.tick();
            h.assertTrue(a.get(2, 1, 2).isAir(), "the block outlasted its tank");
            h.assertTrue(bomb.isRemoved(), "its block entity outlasted its tank");
            h.assertTrue(droppedTtvsAt(a, 2, 1, 2).isEmpty(), "it dropped as its tank exploded");
            List<Explosion> blasts = Blasts.take(a.point(2, 1, 2));
            h.assertValueEqual(blasts.size(), 1, "explosions");
            h.assertTrue(
                    Math.abs(blasts.get(0).radius() - 1) < 1e-4,
                    "the power at 60 atm: " + blasts.get(0).radius());
            inRoom.run();
        }

        withPreset(AtmospherePreset.HARDCORE, () -> {
            TransferValveBlockEntity bomb = placed(a, 2, 1, 2, ttv(nitrogen(60), oxygen(), 10), Direction.NORTH);
            watch();
            bomb.step();
            h.assertTrue(a.get(2, 1, 2).isAir(), "the block outlasted its tank under Hardcore");
            h.assertTrue(droppedTtvsAt(a, 2, 1, 2).isEmpty(), "it dropped as its tank exploded under Hardcore");
            h.assertValueEqual(Blasts.take(a.point(2, 1, 2)).size(), 1, "explosions under Hardcore");
        });
        a.clear(-1, -1, 5, 5);
        h.succeed();
    }

    /** The TTVs dropped near a block since {@link #watch}; every item's entity is removed. */
    private static List<ItemStack> droppedTtvsAt(Area a, int x, int y, int z) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemEntity item : DROPPED) {
            if (item.getItem().getItem() instanceof TransferValveItem
                    && item.position().distanceTo(a.point(x, y, z)) < 16) {
                out.add(item.getItem().copy());
            }
            item.discard();
        }
        DROPPED.clear();
        return out;
    }

    // ---- Its screen ------------------------------------------------------------------------

    /**
     * Two screens on one TTV share its tanks: what one takes, the other no longer has. Every way of
     * taking a tank moves it once; nothing reaches the hidden slot, and a creative clone copies nothing.
     * Broken with a tank on a cursor, it drops with the other, the screens stop, and closing puts the
     * cursor's tank in the inventory. A screen also stops out of reach, for a spectator, and on a TTV
     * put in its place.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_its_screen_moves_each_tank_once(GameTestHelper h) {
        Area a = new Area(h, 958);
        room(a);
        TransferValveBlockEntity ttv = placed(a, loaded(10));
        TestPlayer one = new TestPlayer(a);
        TestPlayer two = new TestPlayer(a);
        TransferValveMenu first = open(one, ttv);
        TransferValveMenu second = open(two, ttv);

        first.clicked(FIRST, 0, ClickType.PICKUP, one);
        h.assertFalse(first.getCarried().isEmpty(), "the first screen took nothing");
        h.assertTrue(second.getSlot(FIRST).getItem().isEmpty(), "the second screen still shows the tank");
        second.clicked(FIRST, 0, ClickType.PICKUP, two);
        h.assertTrue(second.getCarried().isEmpty(), "the second screen took the same tank");
        bothOnce(h, all(ttv, one, two), "after both took the first tank");
        h.assertValueEqual(a.get(X, Y, Z).getValue(TransferValveBlock.TANKS), 1, "the tanks it shows");

        second.clicked(SECOND, 0, ClickType.QUICK_MOVE, two);
        bothOnce(h, all(ttv, one, two), "after a shift-click");
        h.assertValueEqual(a.get(X, Y, Z).getValue(TransferValveBlock.TANKS), 0, "the tanks it shows with none");
        first.clicked(FIRST, 0, ClickType.PICKUP, one);
        second.clicked(slotOf(tankIn(two)), 0, ClickType.QUICK_MOVE, two);
        bothOnce(h, all(ttv, one, two), "after both went back");
        h.assertValueEqual(a.get(X, Y, Z).getValue(TransferValveBlock.TANKS), 2, "the tanks it shows with both");

        two.getInventory().setItem(1, ItemStack.EMPTY);
        second.clicked(SECOND, 1, ClickType.SWAP, two);
        bothOnce(h, all(ttv, one, two), "after a number key");
        h.assertTrue(two.getInventory().getItem(1).getItem() instanceof GasTankItem, "the number key moved nothing");
        second.clicked(SECOND, 1, ClickType.SWAP, two);
        bothOnce(h, all(ttv, one, two), "after a number key back");
        first.clicked(FIRST, 0, ClickType.THROW, one);
        h.assertValueEqual(one.drops.size(), 1, "tanks thrown");
        bothOnce(h, all(ttv, one, two), "after a throw");
        first.setCarried(one.drops.remove(0));
        first.clicked(FIRST, 0, ClickType.PICKUP, one);
        h.assertValueEqual(TransferValveItem.tankCount(ttv.item()), 2, "tanks on after the throw");

        ItemStack before = ttv.item();
        one.setGameMode(GameType.CREATIVE);
        for (ClickType type :
                List.of(ClickType.PICKUP, ClickType.QUICK_MOVE, ClickType.SWAP, ClickType.CLONE, ClickType.THROW)) {
            for (int button : new int[] {0, 1}) {
                first.clicked(first.valveSlot(), button, type, one);
            }
        }
        first.clicked(FIRST, 2, ClickType.CLONE, one);
        one.setGameMode(GameType.SURVIVAL);
        h.assertTrue(first.getCarried().isEmpty(), "a click copied " + first.getCarried());
        h.assertTrue(ItemStack.matches(ttv.item(), before), "a click on the hidden slot changed it");
        bothOnce(h, all(ttv, one, two), "after clicks on the hidden slot");

        first.clicked(FIRST, 0, ClickType.PICKUP, one);
        watch();
        a.level.destroyBlock(pos(a), true);
        List<ItemStack> dropped = droppedTtvs(a);
        h.assertValueEqual(dropped.size(), 1, "TTVs dropped");
        h.assertFalse(first.stillValid(one) || second.stillValid(two), "a screen outlived its TTV");
        second.clicked(SECOND, 0, ClickType.PICKUP, two);
        h.assertTrue(second.getCarried().isEmpty(), "a stopped screen took a tank");
        one.closeContainer();
        two.closeContainer();
        List<ItemStack> everything = new ArrayList<>(dropped);
        everything.addAll(has(one));
        everything.addAll(has(two));
        bothOnce(h, everything, "after it was broken");

        TransferValveBlockEntity again = placed(a, loaded(10));
        TransferValveMenu menu = open(one, again);
        h.assertTrue(menu.stillValid(one), "a screen in reach stopped");
        Vec3 was = one.position();
        one.moveTo(was.add(0, 0, -12));
        h.assertFalse(menu.stillValid(one), "a screen out of reach goes on");
        one.moveTo(was);
        one.setGameMode(GameType.SPECTATOR);
        h.assertFalse(menu.stillValid(one), "a spectator's screen goes on");
        one.setGameMode(GameType.SURVIVAL);
        h.assertTrue(menu.stillValid(one), "a screen back in reach stopped");
        a.set(X, Y, Z, Blocks.AIR.defaultBlockState());
        placed(a, loaded(10));
        h.assertFalse(menu.stillValid(one), "a screen on a TTV put in its place goes on");
        clear(a);
        h.succeed();
    }

    /** Everything the usual two tanks could be in: the placed TTV, if it stands, and both players. */
    private static List<ItemStack> all(TransferValveBlockEntity ttv, TestPlayer one, TestPlayer two) {
        List<ItemStack> out = new ArrayList<>();
        if (!ttv.isRemoved()) {
            out.add(ttv.item());
        }
        out.addAll(has(one));
        out.addAll(has(two));
        return out;
    }

    /** The inventory index of a player's tank. */
    private static int tankIn(TestPlayer p) {
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).getItem() instanceof GasTankItem) {
                return i;
            }
        }
        throw new IllegalStateException("no tank in the inventory");
    }

    /**
     * A client's menu on a placed TTV predicts each click with the same code, on its copy of the
     * stack, which a hidden slot sends. Both end up agreeing, and a button's change reaches the client
     * the same way.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_a_clients_screen_on_it_agrees_with_the_servers(GameTestHelper h) {
        Area a = new Area(h, 959);
        room(a);
        TransferValveBlockEntity ttv = placed(a, new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get()));
        TestPlayer server = new TestPlayer(a);
        TestPlayer client = new TestPlayer(a);
        TransferValveMenu serverMenu = open(server, ttv);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), a.level.registryAccess());
        buf.writeVarInt(TransferValveMenu.PLACED);
        TransferValveMenu clientMenu = new TransferValveMenu(1, client.getInventory(), buf);
        client.containerMenu = clientMenu;
        h.assertTrue(clientMenu.isPlaced() && serverMenu.isPlaced(), "a menu isn't on a placed TTV");
        h.assertValueEqual(clientMenu.slots.size(), serverMenu.slots.size(), "slots on each side");
        int hidden = serverMenu.valveSlot();
        h.assertValueEqual(clientMenu.valveSlot(), hidden, "the hidden slot on each side");
        h.assertFalse(clientMenu.getSlot(hidden).isActive(), "the hidden slot is shown");
        clientMenu.setItem(
                hidden,
                clientMenu.getStateId(),
                serverMenu.getSlot(hidden).getItem().copy());

        record Click(int slot, int button, ClickType type) {}
        for (TestPlayer p : List.of(server, client)) {
            p.getInventory().setItem(9, oxygen());
            p.getInventory().setItem(10, hydrogen());
            p.getInventory().setItem(1, ItemStack.EMPTY);
        }
        for (Click click : List.of(
                new Click(slotOf(9), 0, ClickType.PICKUP), // the first tank onto the cursor
                new Click(FIRST, 0, ClickType.PICKUP), // and into its slot
                new Click(slotOf(10), 0, ClickType.QUICK_MOVE), // the second by a shift-click
                new Click(SECOND, 1, ClickType.SWAP), // out by a number key
                new Click(SECOND, 1, ClickType.SWAP), // and back
                new Click(hidden, 0, ClickType.QUICK_MOVE), // refused
                new Click(hidden, 1, ClickType.PICKUP), // refused, though a held TTV's right-click works
                new Click(FIRST, 0, ClickType.PICKUP))) { // the first tank onto the cursor
            serverMenu.clicked(click.slot, click.button, click.type, server);
            clientMenu.clicked(click.slot, click.button, click.type, client);
            ItemStack shown = clientMenu.getSlot(hidden).getItem();
            h.assertTrue(
                    ItemStack.matches(ttv.item(), shown),
                    click + ": the server has " + ttv.item().getComponentsPatch() + ", the client "
                            + shown.getComponentsPatch());
            h.assertTrue(
                    ItemStack.matches(serverMenu.getCarried(), clientMenu.getCarried()),
                    click + ": the cursors " + serverMenu.getCarried() + ", " + clientMenu.getCarried());
        }

        h.assertTrue(serverMenu.clickMenuButton(server, TransferValveMenu.UP_10), "the timer didn't move");
        clientMenu.setItem(
                hidden,
                clientMenu.getStateId(),
                serverMenu.getSlot(hidden).getItem().copy());
        h.assertValueEqual(clientMenu.timer(), TransferValve.DEFAULT_TIMER + 10, "the client's timer after an update");
        h.assertTrue(clientMenu.stillValid(client), "the client's menu after an update");
        h.assertTrue(
                ItemStack.matches(clientMenu.getSlot(SECOND).getItem(), TransferValveItem.tank(ttv.item(), SECOND)),
                "the client's second slot after an update");
        clear(a);
        h.succeed();
    }

    private static void click(ServerPlayer p, int slot, int button, ClickType type) {
        AbstractContainerMenu menu = p.containerMenu;
        p.connection.handleContainerClick(new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                slot,
                button,
                type,
                ItemStack.EMPTY,
                new Int2ObjectOpenHashMap<>()));
    }

    /**
     * Through the game's own path: an empty hand opens its screen, clicks and buttons reach it, the
     * client is told the TTV by its hidden slot, and the block shows the change. Broken while the
     * screen is open, the player's next tick closes it and tells the client, and the tank on the cursor
     * goes back to the inventory.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_103_its_screen_opens_and_syncs_through_the_game(GameTestHelper h) {
        Area a = new Area(h, 960);
        room(a);
        List<Packet<?>> sent = new ArrayList<>();
        ServerPlayer p = realPlayer(a, sent);
        TransferValveBlockEntity ttv = placed(a, new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get()));
        p.getInventory().setItem(9, oxygen());
        p.getInventory().setItem(10, hydrogen());

        h.assertTrue(use(a, p, ItemStack.EMPTY, on(a)).consumesAction(), "an empty hand did nothing");
        h.assertTrue(p.containerMenu instanceof TransferValveMenu, "an empty hand opened " + p.containerMenu);
        TransferValveMenu menu = (TransferValveMenu) p.containerMenu;
        h.assertTrue(menu.isPlaced(), "the screen isn't on the placed TTV");
        p.tick();
        h.assertTrue(p.containerMenu == menu, "the screen closed after being opened");

        click(p, slotOf(9), 0, ClickType.PICKUP);
        click(p, FIRST, 0, ClickType.PICKUP);
        h.assertTrue(menu.getCarried().isEmpty(), "the click put nothing on the TTV");
        h.assertValueEqual(TransferValveItem.tankCount(ttv.item()), 1, "tanks on");
        h.assertValueEqual(a.get(X, Y, Z).getValue(TransferValveBlock.TANKS), 1, "the tanks it shows");
        boolean told = sent.stream()
                .filter(ClientboundContainerSetSlotPacket.class::isInstance)
                .map(ClientboundContainerSetSlotPacket.class::cast)
                .anyMatch(s -> s.getSlot() == menu.valveSlot()
                        && s.getContainerId() == menu.containerId
                        && TransferValveItem.tankCount(s.getItem()) == 1);
        h.assertTrue(told, "the client wasn't told the TTV");

        p.connection.handleContainerButtonClick(
                new ServerboundContainerButtonClickPacket(menu.containerId, TransferValveMenu.UP_10));
        h.assertValueEqual(TransferValveItem.timer(ttv.item()), TransferValve.DEFAULT_TIMER + 10, "the timer");

        click(p, FIRST, 0, ClickType.PICKUP);
        h.assertFalse(menu.getCarried().isEmpty(), "the tank isn't on the cursor");
        watch();
        a.level.destroyBlock(pos(a), true);
        List<ItemStack> dropped = droppedTtvs(a);
        h.assertValueEqual(dropped.size(), 1, "TTVs dropped");
        click(p, slotOf(10), 0, ClickType.PICKUP);
        p.tick();
        h.assertTrue(p.containerMenu == p.inventoryMenu, "the player's tick left the screen open");
        h.assertTrue(
                sent.stream().anyMatch(ClientboundContainerClosePacket.class::isInstance),
                "the client wasn't told to close");
        int o2 = 0;
        int h2 = 0;
        for (ItemStack s : tanks(dropped)) {
            o2 += GasTankItem.gas(s).moles(O2) == O2_MOLES ? 1 : 0;
        }
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof GasTankItem) {
                o2 += GasTankItem.gas(s).moles(O2) == O2_MOLES ? 1 : 0;
                h2 += GasTankItem.gas(s).moles(H2) == H2_MOLES ? 1 : 0;
            }
        }
        h.assertValueEqual(o2, 1, "the O2 tanks");
        h.assertValueEqual(h2, 1, "the H2 tanks");
        clear(a);
        h.succeed();
    }
}
