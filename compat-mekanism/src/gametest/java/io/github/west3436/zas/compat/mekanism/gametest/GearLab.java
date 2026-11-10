package io.github.west3436.zas.compat.mekanism.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.compat.mekanism.MekanismGear;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.life.Breathers;
import io.github.west3436.zas.simulation.AtmosService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.gear.IModule;
import mekanism.api.gear.IModuleHelper;
import mekanism.api.gear.ModuleData;
import mekanism.common.content.gear.ModuleHelper;
import mekanism.common.util.StorageUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A gear test's rooms, players and Mekanism items. Each test has an area of its own, far from the
 * other tests' and from the tests' structures, and drives every tick itself within one game tick,
 * so the tests don't see each other.
 *
 * <p>A room is a hollow stone box with a 3 × 3 × 3 inside, from area x {@code x0} to {@code x0 + 4}
 * and area z {@code z0} to {@code z0 + 4}. Nothing forces a chunk: building loads the area for the
 * game tick the test runs in, and everything is built in the area's first chunk.
 *
 * <p>Items are made by id and filled through Mekanism's capabilities, as the adapter reads them.
 * Charging a piece and installing a module have no public way in, so those two go through
 * Mekanism's own helpers.
 */
final class GearLab implements AutoCloseable {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** Standard air's partial pressures, in kPa. */
    static final Map<ResourceLocation, Double> AIR = Map.of(
            N2, StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
            O2, StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION);

    /** Nitrogen at one atmosphere: nothing to breathe, and nothing else wrong with it. */
    static final Map<ResourceLocation, Double> NITROGEN = Map.of(N2, StandardAir.PRESSURE);

    /** A room's inside, in litres. */
    private static final double ROOM_LITRES = 27_000;

    private static final SourceId SOURCE = SourceId.of("zas", "mekanism_gear_gametest");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState NOTHING = Blocks.AIR.defaultBlockState();

    /** Mekanism's chemical capability on items, by name, as the adapter finds it. */
    private static final ItemCapability<IChemicalHandler, Void> CHEMICAL = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    /**
     * Damage that reached an entity.
     *
     * @param type its damage type
     * @param amount the health it took
     */
    record Hit(ResourceLocation type, float amount) {}

    /** The damage that reached each watched entity, in order. */
    private static final Map<Entity, List<Hit>> TAKEN = new IdentityHashMap<>();

    /** The game time of each watched entity's next breathing check. */
    private static final Map<Entity, Long> NEXT_CHECK = new IdentityHashMap<>();

    static {
        NeoForge.EVENT_BUS.addListener(LivingDamageEvent.Post.class, event -> {
            List<Hit> taken = TAKEN.get(event.getEntity());
            if (taken != null) {
                ResourceLocation type =
                        event.getSource().typeHolder().unwrapKey().orElseThrow().location();
                taken.add(new Hit(type, event.getNewDamage()));
            }
        });
    }

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;
    private final List<Entity> entities = new ArrayList<>();
    private final List<Runnable> restore = new ArrayList<>();

    /**
     * An area on the Overworld.
     *
     * @param index the area's number, which no other gear test uses
     */
    GearLab(GameTestHelper h, int index) {
        this.h = h;
        this.level = h.getLevel();
        // A row of its own, so that no other part of the adapter's tests builds here.
        this.origin = new BlockPos(4096 + 256 * index, 100, 16384);
        level.getChunk(origin);
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    AtmosphereSample sample(int x, int y, int z) {
        return Atmospheres.at(level, abs(x, y, z));
    }

    private void tick() {
        AtmosService service = AtmosService.current();
        if (service == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        service.tickNow(level);
    }

    /** Runs {@code n} atmos ticks, each a second of the simulation, within this game tick. */
    void atmosTicks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Runs atmos ticks until a mutation completes; classifying a new room may take a few. */
    private <T> T settle(CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /**
     * Builds a sealed room and fills it with these partial pressures, in kPa, at a temperature. An
     * empty map leaves it a vacuum.
     */
    void room(int x0, int z0, Map<ResourceLocation, Double> kPa, double kelvin) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, z0), abs(x0 + 4, 6, z0 + 4))) {
            level.setBlockAndUpdate(p, NOTHING);
        }
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, z0), abs(x0 + 4, 4, z0 + 4))) {
            int x = p.getX() - origin.getX() - x0;
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ() - z0;
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : NOTHING);
        }
        settle(GasMutations.remove(level, middle(x0, z0), Map.of(N2, 1e6, O2, 1e6), SOURCE));
        if (!kPa.isEmpty()) {
            add(x0, z0, kPa, kelvin);
            h.assertTrue(
                    Atmospheres.zoneAt(level, middle(x0, z0)).isPresent(),
                    "the room from " + x0 + ", " + z0 + " has no zone");
        }
    }

    /** As {@link #room(int, int, Map, double)}, at area z 0 and standard air's temperature. */
    void room(int x0, Map<ResourceLocation, Double> kPa) {
        room(x0, 0, kPa, StandardAir.TEMPERATURE);
    }

    /** Adds gas to a room: these partial pressures, in kPa, at a temperature. */
    void add(int x0, int z0, Map<ResourceLocation, Double> kPa, double kelvin) {
        Map<ResourceLocation, Double> moles = new HashMap<>();
        kPa.forEach((gas, p) -> moles.put(gas, p * ROOM_LITRES / (GasMixture.GAS_CONSTANT * kelvin)));
        settle(GasMutations.add(level, middle(x0, z0), new GasParcel(moles, kelvin), SOURCE));
    }

    /** Puts water where a player standing in the room has its legs and head. */
    void flood(int x0, int z0) {
        level.setBlockAndUpdate(abs(x0 + 2, 1, z0 + 2), Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(abs(x0 + 2, 2, z0 + 2), Blocks.WATER.defaultBlockState());
    }

    private Vec3 middle(int x0, int z0) {
        return Vec3.atCenterOf(abs(x0 + 2, 2, z0 + 2));
    }

    /** The atmosphere in the middle of a room. */
    AtmosphereSample air(int x0, int z0) {
        return sample(x0 + 2, 2, z0 + 2);
    }

    /** Where something standing in the middle of a room stands. */
    Vec3 floor(int x0, int z0) {
        return Vec3.atBottomCenterOf(abs(x0 + 2, 1, z0 + 2));
    }

    /** Runs {@code undo} when the test ends, after anything registered later. */
    void afterwards(Runnable undo) {
        restore.add(undo);
    }

    /**
     * A survival player with 1,000 health, whom damage reaches as it would a real player, standing
     * at a position. It isn't added to the level: a test ticks it itself ({@link #live}).
     */
    FakePlayer player(Vec3 at) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_mekanism")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        try {
            Field spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("can't end the test player's spawn protection: " + e);
        }
        player.moveTo(at);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        player.setHealth(1000);
        entities.add(player);
        TAKEN.put(player, new ArrayList<>());
        return player;
    }

    // ---- Time -----------------------------------------------------------------------------------

    /**
     * Runs ZAS³'s breathing check on an entity {@code n} times, each one check interval of game time
     * after its last, without waiting for the time to pass. A check is where ZAS³ judges what the
     * entity breathes and deals pressure, temperature and toxic gas harm, by the seconds since the
     * check before; an entity's first counts none.
     */
    static void check(LivingEntity entity, int n) {
        int interval = BreathingConfig.checkIntervalTicks();
        long at = NEXT_CHECK.getOrDefault(entity, entity.level().getGameTime());
        for (int i = 0; i < n; i++) {
            entity.invulnerableTime = 0;
            Breathers.checkAt(entity, at);
            at += interval;
        }
        NEXT_CHECK.put(entity, at);
    }

    /**
     * Ticks a player as the server does, {@code n} times: the whole of its tick, with every event
     * that fires in one. That is where ZAS³ drains or refills the air meter, by the verdict of the
     * last check, where Mekanism's modules tick, and where, at the end, Mekanism feeds the meter
     * from a scuba tank.
     */
    static void live(FakePlayer player, int n) {
        for (int i = 0; i < n; i++) {
            player.tickCount++;
            player.doTick();
        }
    }

    /** A check, so that the verdict is of the player as it is now, and then {@code n} ticks of it. */
    static void breathe(FakePlayer player, int n) {
        check(player, 1);
        live(player, n);
    }

    /** The damage that has reached an entity since it was made, in order. */
    static List<Hit> taken(Entity entity) {
        return List.copyOf(TAKEN.get(entity));
    }

    /** The health an entity has lost to one damage type since it was made. */
    static double taken(Entity entity, ResourceKey<?> type) {
        return TAKEN.get(entity).stream()
                .filter(hit -> hit.type().equals(type.location()))
                .mapToDouble(Hit::amount)
                .sum();
    }

    // ---- Mekanism's items ------------------------------------------------------------------

    /** One of Mekanism's items, by path. */
    static ItemStack mekanism(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("mekanism", path));
        if (item == Items.AIR) {
            throw new GameTestAssertException("Mekanism has no item " + path);
        }
        return new ItemStack(item);
    }

    /**
     * A scuba tank holding this much oxygen, in mB, put there through Mekanism's chemical capability,
     * and switched on or off through its data component.
     */
    @SuppressWarnings("unchecked")
    static ItemStack scubaTank(long oxygen, boolean on) {
        ItemStack tank = mekanism("scuba_tank");
        IChemicalHandler store = tank.getCapability(CHEMICAL);
        if (store == null) {
            throw new GameTestAssertException("a scuba tank has no chemical capability");
        }
        Holder<Chemical> chemical = MekanismAPI.CHEMICAL_REGISTRY
                .getHolder(ResourceLocation.fromNamespaceAndPath("mekanism", "oxygen"))
                .orElseThrow();
        long left = oxygen;
        while (left > 0) {
            // The capability takes a limited amount at a time.
            long refused = store.insertChemical(new ChemicalStack(chemical, left), Action.EXECUTE)
                    .getAmount();
            if (refused >= left) {
                throw new GameTestAssertException(
                        "a scuba tank took only " + (oxygen - left) + " of " + oxygen + " mB");
            }
            left = refused;
        }
        DataComponentType<Boolean> mode =
                (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(MekanismGear.SCUBA_TANK_MODE);
        if (mode == null) {
            throw new GameTestAssertException("Mekanism has no data component " + MekanismGear.SCUBA_TANK_MODE);
        }
        tank.set(mode, on);
        return tank;
    }

    /** The oxygen in a scuba tank, in mB, as Mekanism's chemical capability reports it. */
    static long oxygenIn(ItemStack tank) {
        IChemicalHandler store = tank.getCapability(CHEMICAL);
        if (store == null) {
            throw new GameTestAssertException(tank + " has no chemical capability");
        }
        return store.getChemicalInTank(0).getAmount();
    }

    /** Puts a scuba mask and a scuba tank on an entity; the tank holds this much oxygen, in mB. */
    static void wearScuba(LivingEntity entity, long oxygen, boolean on) {
        entity.setItemSlot(EquipmentSlot.HEAD, mekanism("scuba_mask"));
        entity.setItemSlot(EquipmentSlot.CHEST, scubaTank(oxygen, on));
    }

    /** One of the MekaSuit's pieces, holding all the energy it can. */
    static ItemStack charged(String path) {
        ItemStack piece = StorageUtils.getFilledEnergyVariant(mekanism(path));
        if (energy(piece) <= 0) {
            throw new GameTestAssertException("couldn't charge " + path);
        }
        return piece;
    }

    /** The energy an item holds, in Mekanism's joules. */
    static long energy(ItemStack stack) {
        return StorageUtils.getStoredEnergyFromAttachment(stack);
    }

    /** Dresses an entity in a whole MekaSuit, every piece charged. */
    static void wearMekaSuit(LivingEntity entity) {
        entity.setItemSlot(EquipmentSlot.HEAD, charged("mekasuit_helmet"));
        entity.setItemSlot(EquipmentSlot.CHEST, charged("mekasuit_bodyarmor"));
        entity.setItemSlot(EquipmentSlot.LEGS, charged("mekasuit_pants"));
        entity.setItemSlot(EquipmentSlot.FEET, charged("mekasuit_boots"));
    }

    /** One of Mekanism's modules, by path. */
    static Holder<ModuleData<?>> module(String path) {
        return MekanismAPI.MODULE_REGISTRY
                .getHolder(ResourceLocation.fromNamespaceAndPath("mekanism", path))
                .orElseThrow(() -> new GameTestAssertException("Mekanism has no module " + path));
    }

    /** Installs one of a module in a piece of the MekaSuit, enabled as a new module is. */
    void install(ItemStack piece, String path) {
        int installed =
                ModuleHelper.get().getModuleContainer(piece).addModule(level.registryAccess(), piece, module(path), 1);
        h.assertValueEqual(installed, 1, "modules installed: " + path);
        h.assertTrue(IModuleHelper.INSTANCE.isEnabled(piece, module(path)), path + " isn't enabled once installed");
    }

    /** Switches a module of a worn piece off or back on, as its wearer does. */
    static void toggle(FakePlayer wearer, ItemStack piece, String path) {
        IModule<?> module = IModuleHelper.INSTANCE.getModuleContainer(piece).get(module(path));
        module.toggleEnabled(IModuleHelper.INSTANCE.getModuleContainer(piece), piece, wearer, Component.empty());
    }

    @Override
    public void close() {
        for (Entity entity : entities) {
            TAKEN.remove(entity);
            NEXT_CHECK.remove(entity);
            entity.discard();
        }
        for (int i = restore.size() - 1; i >= 0; i--) {
            restore.get(i).run();
        }
    }
}
