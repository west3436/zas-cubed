package io.github.west3436.zas.atmos.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.ss13.testkit.ListeningPlayer;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.config.AtmosServerConfig;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosEntities;
import io.github.west3436.zas.atmos.registry.AtmosItems;
import io.github.west3436.zas.atmos.ttv.PrimedTransferValve;
import io.github.west3436.zas.atmos.ttv.ProximitySensorItem;
import io.github.west3436.zas.atmos.ttv.RemoteSignalerItem;
import io.github.west3436.zas.atmos.ttv.RemoteSignalerMenu;
import io.github.west3436.zas.atmos.ttv.Signal;
import io.github.west3436.zas.atmos.ttv.TransferValve;
import io.github.west3436.zas.atmos.ttv.TransferValveBlock;
import io.github.west3436.zas.atmos.ttv.TransferValveBlockEntity;
import io.github.west3436.zas.atmos.ttv.TransferValveItem;
import io.github.west3436.zas.atmos.ttv.TransferValveMenu;
import io.github.west3436.zas.atmos.ttv.TtvTrigger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A TTV's triggers (CONT-184 to CONT-187, CONT-189): a remote signaler or proximity sensor fitted in
 * its timer's place, which goes with it wherever it goes. The areas are 5930 and up. The tests in which
 * a primed TTV must find other entities, or a signal must find primed TTVs, force their chunks loaded
 * and wait for them to tick entities, as the game only finds entities there.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TtvTriggerTests {
    private static final String BATCH = "zas_atmos_ttv_triggers";

    private static final ResourceLocation O2 = StandardAir.OXYGEN;
    private static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** The TTV menu's first inventory slot, after its two tanks. */
    private static final int ROWS_START = 2;

    private TtvTriggerTests() {}

    // ---- Helpers ---------------------------------------------------------------------------

    /** A fake player at (2, 1, 2), facing south. */
    private static FakePlayer player(Area a, String name) {
        FakePlayer p = new FakePlayer(a.level, new GameProfile(UUID.randomUUID(), name));
        Vec3 at = Vec3.atBottomCenterOf(a.abs(2, 1, 2));
        p.moveTo(at.x, at.y, at.z, 0, 0);
        return p;
    }

    /** A plain tank holding a gas. */
    private static ItemStack tank(ResourceLocation gas, double moles) {
        ItemStack tank = new ItemStack(AtmosItems.EMPTY_TANK.get());
        GasTankItem.setGas(tank, new GasParcel(Map.of(gas, moles), StandardAir.TEMPERATURE));
        return tank;
    }

    /** A TTV with an O2 tank and an N2 tank, its timer as given. */
    private static ItemStack ttv(int timer) {
        ItemStack stack = new ItemStack(AtmosItems.TANK_TRANSFER_VALVE.get());
        TransferValveItem.save(stack, new TransferValve(tank(O2, 3), tank(N2, 3), false));
        TransferValveItem.setTimer(stack, timer);
        return stack;
    }

    private static ItemStack signaler(Signal signal) {
        ItemStack stack = new ItemStack(AtmosItems.REMOTE_SIGNALER.get());
        RemoteSignalerItem.setSignal(stack, signal);
        return stack;
    }

    private static ItemStack sensor(int range) {
        ItemStack stack = new ItemStack(AtmosItems.PROXIMITY_SENSOR.get());
        ProximitySensorItem.setRange(stack, range);
        return stack;
    }

    /** A TTV fitted with a trigger. */
    private static ItemStack fitted(ItemStack trigger) {
        ItemStack stack = ttv(30);
        if (!TransferValveItem.fitTrigger(stack, trigger)) {
            throw new IllegalStateException("couldn't fit " + trigger);
        }
        return stack;
    }

    /** A TTV block at a place, facing north, holding what a stack holds, as placing it gives. */
    private static TransferValveBlockEntity placed(Area a, int x, int y, int z, ItemStack ttv) {
        a.place(x, y, z, AtmosBlocks.TANK_TRANSFER_VALVE.get().defaultBlockState());
        TransferValveBlockEntity be = a.entity(x, y, z);
        be.applyComponentsFromItemStack(ttv);
        be.step();
        return be;
    }

    /** A floor of stone under x 0 to 8, z 0 to 12, cleared above. */
    private static void floor(Area a) {
        a.clear(-1, -1, 9, 13);
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 12; z++) {
                a.set(x, 0, z, Area.STONE);
            }
        }
    }

    private static PrimedTransferValve prime(Area a, int x, int z, ItemStack ttv) {
        return PrimedTransferValve.prime(a.level, a.abs(x, 1, z), Direction.NORTH, ttv);
    }

    private static void same(GameTestHelper h, ItemStack actual, ItemStack expected, String what) {
        h.assertTrue(
                ItemStack.matches(actual, expected),
                what + ": " + actual + " " + actual.getComponentsPatch() + ", not " + expected + " "
                        + expected.getComponentsPatch());
    }

    // ---- Fitting and taking off ------------------------------------------------------------

    /**
     * CONT-184: a remote signaler or a proximity sensor goes in a TTV's timer's place, one at a time,
     * through the window's slot, by shift-click, carried onto the TTV in the inventory, or used on a
     * placed TTV. A tank doesn't go there. Taken off, it comes back as it went on, and the TTV has its
     * timer again, set as it was.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_184_each_trigger_fits_in_the_timers_place_and_comes_off(GameTestHelper h) {
        Area a = new Area(h, 5930);
        floor(a);
        FakePlayer p = player(a, "zas_ttv_triggers_fit");
        ItemStack held = ttv(42);
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, held);
        TransferValveMenu menu = new TransferValveMenu(1, p.getInventory(), InteractionHand.MAIN_HAND);
        p.containerMenu = menu;
        int slot = menu.triggerSlot();

        // The window's slot.
        ItemStack signal = signaler(new Signal(1499, 7));
        menu.setCarried(signal.copy());
        menu.clicked(slot, 0, ClickType.PICKUP, p);
        h.assertTrue(menu.getCarried().isEmpty(), "the signaler stayed on the cursor");
        same(h, TransferValveItem.trigger(held), signal, "the fitted signaler");
        h.assertValueEqual(TtvTrigger.ofTtv(held), TtvTrigger.SIGNALER, "its trigger");
        menu.setCarried(tank(O2, 1));
        menu.clicked(slot, 0, ClickType.PICKUP, p);
        same(h, TransferValveItem.trigger(held), signal, "the trigger after a tank was put on it");
        h.assertTrue(menu.getCarried().getItem() instanceof GasTankItem, "the tank left the cursor");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(slot, 0, ClickType.PICKUP, p);
        same(h, menu.getCarried(), signal, "the signaler taken off");
        h.assertValueEqual(TtvTrigger.ofTtv(held), TtvTrigger.TIMER, "its trigger once taken off");
        h.assertValueEqual(TransferValveItem.timer(held), 42, "its timer once taken off");
        menu.setCarried(ItemStack.EMPTY);

        // Shift-click from the inventory, and back.
        ItemStack sensor = sensor(3);
        p.getInventory().setItem(9, sensor.copy());
        menu.quickMoveStack(p, ROWS_START);
        h.assertTrue(p.getInventory().getItem(9).isEmpty(), "the sensor stayed in the inventory");
        same(h, TransferValveItem.trigger(held), sensor, "the sensor shift-clicked on");
        p.getInventory().setItem(10, signal.copy());
        menu.quickMoveStack(p, ROWS_START + 1);
        same(h, p.getInventory().getItem(10), signal, "a second trigger, shift-clicked");
        same(h, TransferValveItem.trigger(held), sensor, "the sensor after a second trigger");
        menu.quickMoveStack(p, slot);
        h.assertValueEqual(TtvTrigger.ofTtv(held), TtvTrigger.TIMER, "its trigger, shift-clicked off");
        int back = -1;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof ProximitySensorItem) {
                back = i;
            }
        }
        h.assertTrue(back >= 0, "the sensor shift-clicked off went nowhere");
        same(h, p.getInventory().getItem(back), sensor, "the sensor shift-clicked off");
        p.containerMenu = p.inventoryMenu;

        // Carried onto the TTV in the inventory, as a tank is.
        InventoryMenu inventory = p.inventoryMenu;
        inventory.setCarried(p.getInventory().removeItemNoUpdate(back));
        inventory.clicked(InventoryMenu.USE_ROW_SLOT_START, 1, ClickType.PICKUP, p);
        h.assertTrue(inventory.getCarried().isEmpty(), "the sensor carried onto it stayed on the cursor");
        same(h, TransferValveItem.trigger(p.getInventory().getItem(0)), sensor, "the sensor carried onto it");

        // Used on a placed TTV.
        TransferValveBlockEntity block = placed(a, 2, 1, 4, ttv(15));
        BlockPos pos = a.abs(2, 1, 4);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        ItemStack inHand = signal.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        InteractionResult used = p.gameMode.useItemOn(p, a.level, inHand, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(used.consumesAction(), "using a signaler on it gave " + used);
        h.assertTrue(p.getMainHandItem().isEmpty(), "the hand kept " + p.getMainHandItem());
        same(h, TransferValveItem.trigger(block.item()), signal, "the signaler used on it");
        h.assertValueEqual(a.get(2, 1, 4).getValue(TransferValveBlock.TRIGGER), TtvTrigger.SIGNALER, "its block");
        ItemStack second = sensor(2);
        p.setItemInHand(InteractionHand.MAIN_HAND, second);
        p.gameMode.useItemOn(p, a.level, second, InteractionHand.MAIN_HAND, hit);
        h.assertFalse(p.getMainHandItem().isEmpty(), "a second trigger went on");
        same(h, TransferValveItem.trigger(block.item()), signal, "the trigger after a second was used");

        // Taken off through a placed TTV's window, its block shows its timer.
        TransferValveMenu window = new TransferValveMenu(2, p.getInventory(), block);
        p.containerMenu = window;
        window.clicked(window.triggerSlot(), 0, ClickType.PICKUP, p);
        same(h, window.getCarried(), signal, "the signaler taken off the placed TTV");
        h.assertValueEqual(a.get(2, 1, 4).getValue(TransferValveBlock.TRIGGER), TtvTrigger.TIMER, "its block after");
        h.assertValueEqual(TransferValveItem.timer(block.item()), 15, "the placed TTV's timer after");
        window.setCarried(ItemStack.EMPTY);
        p.containerMenu = p.inventoryMenu;
        a.clear(-1, -1, 9, 13);
        h.succeed();
    }

    /**
     * CONT-187: a fitted proximity sensor's range is set in the TTV's window, 1 to {@code
     * [ttv_triggers] proximity_max_range} blocks, and the sensor keeps it when taken off. Without a
     * sensor, the range buttons do nothing.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_187_its_range_is_set_in_the_ttvs_window(GameTestHelper h) {
        Area a = new Area(h, 5931);
        FakePlayer p = player(a, "zas_ttv_triggers_range");
        ItemStack held = ttv(10);
        p.getInventory().selected = 0;
        p.getInventory().setItem(0, held);
        TransferValveMenu menu = new TransferValveMenu(1, p.getInventory(), InteractionHand.MAIN_HAND);
        p.containerMenu = menu;
        h.assertFalse(menu.clickMenuButton(p, TransferValveMenu.RANGE_UP), "a range was set with no sensor");
        TransferValveItem.fitTrigger(held, sensor(ProximitySensorItem.DEFAULT_RANGE));
        h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.RANGE_UP), "+1 was refused");
        h.assertValueEqual(
                ProximitySensorItem.range(menu.trigger()), ProximitySensorItem.DEFAULT_RANGE + 1, "its range after +1");
        int most = ProximitySensorItem.maxRange();
        for (int i = 0; i < most + 2; i++) {
            menu.clickMenuButton(p, TransferValveMenu.RANGE_UP);
        }
        h.assertValueEqual(ProximitySensorItem.range(menu.trigger()), most, "its range past its widest");
        for (int i = 0; i < most + 2; i++) {
            menu.clickMenuButton(p, TransferValveMenu.RANGE_DOWN);
        }
        h.assertValueEqual(ProximitySensorItem.range(menu.trigger()), ProximitySensorItem.MIN_RANGE, "its narrowest");
        menu.clickMenuButton(p, TransferValveMenu.RANGE_UP);
        ItemStack off = TransferValveItem.takeTrigger(held);
        h.assertValueEqual(ProximitySensorItem.range(off), ProximitySensorItem.MIN_RANGE + 1, "its range taken off");
        p.containerMenu = p.inventoryMenu;
        h.succeed();
    }

    // ---- The signaler ------------------------------------------------------------------------

    /**
     * CONT-185, CONT-186: a remote signaler's window sets its frequency and code, and Send sends. A
     * primed TTV fitted with a signaler set the same opens its valve; one set to another code or
     * frequency, one with a sensor or a timer, a placed TTV and one out of range don't. Then, opened, it
     * lies a while and drops as a TTV with its signaler still fitted (CONT-189). A signaler that has just
     * sent waits a moment before it sends again.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_186_a_signal_opens_only_ttvs_set_the_same(GameTestHelper h) {
        Area a = new Area(h, 5932);
        a.forceLoad(true);
        floor(a);
        Signal right = new Signal(1475, 12);
        List<PrimedTransferValve> primed = new ArrayList<>();
        List<Packet<?>> sent = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        a.level.isPositionEntityTicking(a.abs(2, 1, 2)), "the area doesn't tick entities yet"))
                .thenExecute(() -> {
                    primed.add(prime(a, 1, 2, fitted(signaler(right))));
                    primed.add(prime(a, 3, 2, fitted(signaler(right.withCode(13)))));
                    primed.add(prime(a, 5, 2, fitted(signaler(right.withFrequency(1471)))));
                    primed.add(prime(a, 7, 2, fitted(sensor(1))));
                    primed.add(prime(a, 1, 6, ttv(60)));
                    placed(a, 3, 1, 6, fitted(signaler(right)));
                    for (PrimedTransferValve one : primed) {
                        h.assertFalse(one.valveOpen(), "a TTV opened as it was primed");
                    }
                    h.assertValueEqual(primed.get(0).kind(), TtvTrigger.SIGNALER, "the first TTV's trigger");
                    h.assertValueEqual(primed.get(0).fuse(), 0, "a signaler's fuse");

                    int range = AtmosServerConfig.get(AtmosServerConfig.SIGNALER_RANGE);
                    Vec3 far = a.point(2, 1, 2).add(range + 20, 0, 0);
                    h.assertValueEqual(RemoteSignalerItem.send(a.level, far, right), 0, "TTVs opened out of range");
                    h.assertValueEqual(
                            RemoteSignalerItem.send(a.level, a.point(2, 1, 2), right.withCode(11)),
                            0,
                            "TTVs opened by another code");

                    ServerPlayer p = new ServerPlayer(
                            a.level.getServer(),
                            a.level,
                            new GameProfile(UUID.randomUUID(), "zas_ttv_signaler"),
                            ClientInformation.createDefault());
                    new ListeningPlayer.Recording(a.level, p, sent);
                    p.moveTo(Vec3.atBottomCenterOf(a.abs(2, 1, 9)));
                    ItemStack hand = signaler(Signal.DEFAULT);
                    p.setItemInHand(InteractionHand.MAIN_HAND, hand);
                    p.gameMode.useItem(p, a.level, hand, InteractionHand.MAIN_HAND);
                    h.assertTrue(p.containerMenu instanceof RemoteSignalerMenu, "its window is " + p.containerMenu);
                    RemoteSignalerMenu menu = (RemoteSignalerMenu) p.containerMenu;
                    h.assertTrue(menu.clickMenuButton(p, RemoteSignalerMenu.DEFAULT), "Default was refused");
                    for (int i = 0; i < (right.frequency() - Signal.DEFAULT_FREQUENCY) / 10; i++) {
                        menu.clickMenuButton(p, RemoteSignalerMenu.FREQUENCY_DOWN_10 + 3);
                    }
                    for (int i = 0; i < (right.frequency() - Signal.DEFAULT_FREQUENCY) % 10; i++) {
                        menu.clickMenuButton(p, RemoteSignalerMenu.FREQUENCY_DOWN_10 + 2);
                    }
                    for (int i = 0; i < Signal.DEFAULT_CODE - right.code(); i++) {
                        menu.clickMenuButton(p, RemoteSignalerMenu.CODE_DOWN_10 + 1);
                    }
                    h.assertValueEqual(RemoteSignalerItem.signal(hand), right, "its setting");
                    h.assertValueEqual(menu.signal(), right, "its window's setting");
                    h.assertTrue(menu.clickMenuButton(p, RemoteSignalerMenu.SEND), "Send was refused");
                    h.assertTrue(primed.get(0).valveOpen(), "the TTV set the same didn't open");
                    for (int i = 1; i < primed.size(); i++) {
                        h.assertFalse(primed.get(i).valveOpen(), "TTV " + i + " opened");
                    }
                    h.assertTrue(a.get(3, 1, 6).is(AtmosBlocks.TANK_TRANSFER_VALVE.get()), "the placed TTV was primed");
                    h.assertValueEqual(RemoteSignalerItem.send(p, hand), -1, "it sent again at once");
                    p.closeContainer();
                })
                .thenWaitUntil(() -> h.assertFalse(primed.get(0).isAlive(), "the opened TTV hasn't dropped yet"))
                .thenExecute(() -> {
                    List<ItemEntity> drops = a.level.getEntitiesOfClass(
                            ItemEntity.class,
                            new AABB(a.abs(0, 0, 0)).inflate(4),
                            e -> e.getItem().getItem() instanceof TransferValveItem);
                    h.assertValueEqual(drops.size(), 1, "TTVs dropped");
                    ItemStack dropped = drops.get(0).getItem();
                    h.assertTrue(TransferValveItem.valve(dropped).isOpen(), "it dropped with its valve shut");
                    same(h, TransferValveItem.trigger(dropped), signaler(right), "the dropped TTV's trigger");
                    drops.forEach(ItemEntity::discard);
                    primed.forEach(PrimedTransferValve::discard);
                    a.clear(-1, -1, 9, 13);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    // ---- The proximity sensor ---------------------------------------------------------------

    /**
     * CONT-187: armed, a TTV with a proximity sensor waits out its arming delay, opening for nothing,
     * though a pig stands next to it, and then opens. Another, whose pig is out of its range, stays
     * shut once watching until its pig walks into range. An armor stand doesn't set one off.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = Area.CHUNK_WAIT_TICKS)
    public static void cont_187_a_proximity_sensor_opens_once_armed_when_something_living_comes_near(GameTestHelper h) {
        Area a = new Area(h, 5933);
        a.forceLoad(true);
        floor(a);
        int delay = AtmosServerConfig.get(AtmosServerConfig.PROXIMITY_ARMING_DELAY) * 20;
        List<PrimedTransferValve> primed = new ArrayList<>();
        List<Pig> pigs = new ArrayList<>();
        List<ArmorStand> stands = new ArrayList<>();
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(
                        a.level.isPositionEntityTicking(a.abs(2, 1, 2)), "the area doesn't tick entities yet"))
                .thenExecute(() -> {
                    pigs.add(pig(a, 2, 3));
                    pigs.add(pig(a, 6, 11));
                    ArmorStand stand = EntityType.ARMOR_STAND.create(a.level);
                    stand.moveTo(Vec3.atBottomCenterOf(a.abs(6, 1, 5)));
                    a.level.addFreshEntity(stand);
                    stands.add(stand);
                    // Armed through a placed TTV's window, as a player arms one.
                    TransferValveBlockEntity block = placed(a, 2, 1, 2, fitted(sensor(1)));
                    FakePlayer p = player(a, "zas_ttv_triggers_arm");
                    TransferValveMenu menu = new TransferValveMenu(1, p.getInventory(), block);
                    h.assertTrue(menu.clickMenuButton(p, TransferValveMenu.START), "Arm was refused");
                    primed.addAll(
                            a.level.getEntitiesOfClass(PrimedTransferValve.class, new AABB(a.abs(2, 1, 2)).inflate(1)));
                    h.assertValueEqual(primed.size(), 1, "TTVs armed at (2, 1, 2)");
                    primed.add(prime(a, 6, 6, fitted(sensor(2))));
                    for (PrimedTransferValve one : primed) {
                        h.assertValueEqual(one.kind(), TtvTrigger.PROXIMITY, "an armed TTV's trigger");
                        h.assertValueEqual(one.fuse(), delay, "an armed TTV's delay");
                    }
                })
                .thenExecuteFor(delay - 10, () -> {
                    h.assertFalse(primed.get(0).valveOpen(), "it opened before its delay was out");
                    h.assertFalse(primed.get(0).watching(), "it watched before its delay was out");
                })
                .thenWaitUntil(() -> h.assertTrue(primed.get(0).valveOpen(), "it didn't open for the pig"))
                .thenExecuteFor(40, () -> {
                    h.assertTrue(primed.get(1).watching(), "the second isn't watching");
                    h.assertFalse(primed.get(1).valveOpen(), "the second opened with nothing in range");
                })
                .thenExecute(() -> {
                    Vec3 near = Vec3.atBottomCenterOf(a.abs(6, 1, 8));
                    pigs.get(1).teleportTo(near.x, near.y, near.z);
                })
                .thenWaitUntil(() -> h.assertTrue(primed.get(1).valveOpen(), "it didn't open when the pig came"))
                .thenExecute(() -> {
                    primed.forEach(PrimedTransferValve::discard);
                    pigs.forEach(Pig::discard);
                    stands.forEach(ArmorStand::discard);
                    a.clear(-1, -1, 9, 13);
                    a.forceLoad(false);
                })
                .thenSucceed();
    }

    private static Pig pig(Area a, int x, int z) {
        Pig pig = EntityType.PIG.create(a.level);
        pig.setNoAi(true);
        pig.moveTo(Vec3.atBottomCenterOf(a.abs(x, 1, z)));
        a.level.addFreshEntity(pig);
        return pig;
    }

    // ---- Where it goes, it goes too ----------------------------------------------------------

    /**
     * CONT-189: a fitted trigger goes with its TTV: placed from the item, into the block and its save;
     * broken, into the TTV it drops; armed, into the primed TTV and its save; and started from the
     * hand. A signaler's primed TTV waits for its signal however long it lies.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_189_a_fitted_trigger_goes_with_its_ttv(GameTestHelper h) {
        Area a = new Area(h, 5934);
        floor(a);
        FakePlayer p = player(a, "zas_ttv_triggers_travel");
        ItemStack sensor = sensor(4);
        ItemStack stack = fitted(sensor);

        // Placed from the item, as sneak-using it does.
        BlockPos floor = a.abs(2, 0, 4);
        p.setShiftKeyDown(true);
        ItemStack inHand = stack.copy();
        p.setItemInHand(InteractionHand.MAIN_HAND, inHand);
        p.gameMode.useItemOn(
                p,
                a.level,
                inHand,
                InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false));
        p.setShiftKeyDown(false);
        BlockState state = a.get(2, 1, 4);
        h.assertTrue(state.is(AtmosBlocks.TANK_TRANSFER_VALVE.get()), "sneak-using it placed " + state);
        h.assertValueEqual(state.getValue(TransferValveBlock.TRIGGER), TtvTrigger.PROXIMITY, "its block");
        TransferValveBlockEntity block = a.entity(2, 1, 4);
        same(h, TransferValveItem.trigger(block.item()), sensor, "the placed TTV's trigger");

        // Saved and read back.
        BlockPos pos = a.abs(2, 1, 4);
        CompoundTag saved = block.saveWithFullMetadata(a.level.registryAccess());
        BlockEntity read = BlockEntity.loadStatic(pos, state, saved, a.level.registryAccess());
        h.assertTrue(read instanceof TransferValveBlockEntity, "it read back as " + read);
        same(h, TransferValveItem.trigger(((TransferValveBlockEntity) read).item()), sensor, "read back");

        // Broken, it drops the TTV with its sensor.
        List<ItemStack> drops = Block.getDrops(state, a.level, pos, block);
        h.assertValueEqual(drops.size(), 1, "what it drops");
        same(h, drops.get(0), stack, "what it drops");

        // Armed, the primed TTV has it, and keeps it when saved.
        PrimedTransferValve armed = block.prime();
        h.assertTrue(armed != null, "it didn't arm");
        h.assertValueEqual(armed.kind(), TtvTrigger.PROXIMITY, "the armed TTV's trigger");
        same(h, armed.trigger(), sensor, "the armed TTV's sensor");
        CompoundTag tag = new CompoundTag();
        armed.saveWithoutId(tag);
        PrimedTransferValve copy =
                AtmosEntities.PRIMED_TANK_TRANSFER_VALVE.get().create(a.level);
        copy.load(tag);
        h.assertValueEqual(copy.kind(), TtvTrigger.PROXIMITY, "the armed TTV's trigger read back");
        same(h, copy.trigger(), sensor, "the armed TTV's sensor read back");
        h.assertValueEqual(copy.fuse(), armed.fuse(), "the armed TTV's delay read back");
        armed.discard();

        // Started from the hand, a signaler's TTV is armed, and waits.
        ItemStack signal = signaler(new Signal(1333, 44));
        p.setItemInHand(InteractionHand.MAIN_HAND, fitted(signal));
        PrimedTransferValve started = TransferValveItem.start(p, InteractionHand.MAIN_HAND);
        h.assertTrue(started != null, "Arm from the hand refused");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the hand kept " + p.getMainHandItem());
        h.assertValueEqual(started.kind(), TtvTrigger.SIGNALER, "the started TTV's trigger");
        same(h, started.trigger(), signal, "the started TTV's signaler");
        for (int i = 0; i < 40 * 20; i++) {
            started.tick();
        }
        h.assertFalse(started.valveOpen(), "a signaler's TTV opened by itself");
        h.assertTrue(started.isAlive(), "a signaler's TTV dropped by itself");
        started.discard();
        a.clear(-1, -1, 9, 13);
        h.succeed();
    }

    /**
     * CONT-104, CONT-184: redstone does nothing to a TTV with a trigger fitted, as it does nothing to
     * one with its timer: powered, it holds what it held, and isn't armed.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_184_redstone_does_nothing_to_a_ttv_with_a_trigger(GameTestHelper h) {
        Area a = new Area(h, 5935);
        floor(a);
        for (ItemStack trigger : List.of(signaler(Signal.DEFAULT), sensor(1))) {
            ItemStack stack = fitted(trigger);
            TransferValveBlockEntity ttv = placed(a, 2, 1, 4, stack);
            BlockState before = a.get(2, 1, 4);
            a.set(3, 1, 4, Blocks.REDSTONE_BLOCK.defaultBlockState());
            h.assertTrue(a.level.hasNeighborSignal(a.abs(2, 1, 4)), "no signal reaches it");
            ttv.step();
            h.assertValueEqual(a.get(2, 1, 4), before, "its block, powered");
            same(h, ttv.item(), stack, "what it holds, powered");
            h.assertTrue(
                    a.level
                            .getEntitiesOfClass(PrimedTransferValve.class, new AABB(a.abs(2, 1, 4)).inflate(2))
                            .isEmpty(),
                    "a signal armed it");
            a.set(3, 1, 4, Blocks.AIR.defaultBlockState());
            a.set(2, 1, 4, Blocks.AIR.defaultBlockState());
        }
        a.clear(-1, -1, 9, 13);
        h.succeed();
    }
}
