package io.github.west3436.zas.compat.adastra.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.environment.AmbientDefinition;
import io.github.west3436.zas.api.environment.AmbientProviders;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.simulation.AtmosService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/**
 * A test's rooms and entities, on the Overworld or on Ad Astra's Moon. Each test has an area of its
 * own, far from the others and from the tests' structures, and drives every tick itself within one
 * game tick, so the tests don't see each other.
 *
 * <p>A room is a hollow stone box with a 3 × 3 × 3 inside, from area x {@code x0} to {@code x0 +
 * 4}. Nothing forces a chunk: building loads the area for the game tick the test runs in.
 */
final class Lab implements AutoCloseable {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** Standard air's partial pressures, in kPa. */
    static final Map<ResourceLocation, Double> AIR = Map.of(
            N2, StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
            O2, StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION);

    private static final SourceId SOURCE = SourceId.of("zas", "adastra_gametest");
    private static final ResourceLocation TEST_AMBIENT =
            ResourceLocation.fromNamespaceAndPath("zas", "adastra_gametest");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState NOTHING = Blocks.AIR.defaultBlockState();

    /** The damage each watched entity was dealt, by damage type, in order. */
    private static final Map<Entity, List<ResourceLocation>> DEALT = new IdentityHashMap<>();

    static {
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event -> {
            List<ResourceLocation> dealt = DEALT.get(event.getEntity());
            if (dealt != null) {
                dealt.add(
                        event.getSource().typeHolder().unwrapKey().orElseThrow().location());
            }
        });
    }

    final GameTestHelper h;
    final ServerLevel level;
    final BlockPos origin;
    private final List<Entity> entities = new ArrayList<>();

    private Lab(GameTestHelper h, ServerLevel level, int index) {
        this.h = h;
        this.level = level;
        this.origin = new BlockPos(4096 + 256 * index, 100, 8192);
        // Loads the area's first chunk, where everything but a second row of rooms goes.
        level.getChunk(origin);
    }

    /** An area on the Overworld, which Ad Astra counts as Earth: it has oxygen everywhere. */
    static Lab onTheOverworld(GameTestHelper h, int index) {
        return new Lab(h, h.getLevel(), index);
    }

    /**
     * An area on Ad Astra's Moon, where Ad Astra finds no oxygen and −173 °C. Until the adapter
     * seeds ambients from Ad Astra's planets (SPACE-020), the tests give the Moon its vacuum
     * themselves, through the public API.
     */
    static Lab onTheMoon(GameTestHelper h, int index) {
        ServerLevel moon = Moon.level(h.getLevel().getServer());
        Lab lab = new Lab(h, moon, index);
        if (AmbientProviders.registered(moon.dimension()).isEmpty()) {
            AmbientDefinition vacuum = AmbientDefinition.vacuum(Moon.TEMPERATURE);
            AmbientProviders.register(TEST_AMBIENT, moon.dimension(), asked -> vacuum);
        }
        lab.ticks(2);
        AtmosphereSample open = lab.sample(0, 20, 0);
        h.assertValueEqual(open.state(), AtmosphereState.AMBIENT, "the Moon's open air");
        h.assertTrue(open.mixture().isEmpty(), "the Moon's open air should be a vacuum, and is " + open);
        return lab;
    }

    BlockPos abs(int x, int y, int z) {
        return origin.offset(x, y, z);
    }

    Vec3 point(int x, int y, int z) {
        return Vec3.atCenterOf(abs(x, y, z));
    }

    void set(int x, int y, int z, BlockState state) {
        level.setBlockAndUpdate(abs(x, y, z), state);
    }

    AtmosphereSample sample(int x, int y, int z) {
        return Atmospheres.at(level, abs(x, y, z));
    }

    void tick() {
        AtmosService service = AtmosService.current();
        if (service == null) {
            throw new GameTestAssertException("the atmos service isn't running");
        }
        service.tickNow(level);
    }

    void ticks(int n) {
        for (int i = 0; i < n; i++) {
            tick();
        }
    }

    /** Runs atmos ticks until a mutation completes; classifying a new room may take a few. */
    <T> T settle(CompletableFuture<T> future) {
        for (int i = 0; i < 8 && !future.isDone(); i++) {
            tick();
        }
        h.assertTrue(future.isDone(), "the mutation didn't complete within 8 atmos ticks");
        return future.join();
    }

    /**
     * Builds a sealed room from x0 and fills it with these partial pressures, in kPa at 293.15 K. An
     * empty map leaves it a vacuum.
     */
    void room(int x0, Map<ResourceLocation, Double> kPa) {
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x0 + 5, 6, 6))) {
            level.setBlockAndUpdate(p, NOTHING);
        }
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x0 + 4, 4, 4))) {
            int x = p.getX() - origin.getX() - x0;
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : NOTHING);
        }
        Vec3 middle = point(x0 + 2, 2, 2);
        settle(GasMutations.remove(level, middle, Map.of(N2, 1e6, O2, 1e6), SOURCE));
        if (!kPa.isEmpty()) {
            Map<ResourceLocation, Double> moles = new HashMap<>();
            kPa.forEach((gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
            settle(GasMutations.add(level, middle, new GasParcel(moles, StandardAir.TEMPERATURE), SOURCE));
            h.assertTrue(Atmospheres.zoneAt(level, middle).isPresent(), "the room from " + x0 + " has no zone");
        }
    }

    /** Where something standing in the middle of the room from x0 stands. */
    Vec3 floor(int x0) {
        return Vec3.atBottomCenterOf(abs(x0 + 2, 1, 2));
    }

    /** Where something in the open stands: 10 blocks above the area's floor, at x. */
    Vec3 open(int x) {
        return Vec3.atBottomCenterOf(abs(x, 10, 2));
    }

    /** A mob with its AI off, so it stays where it is put, whose damage is recorded. */
    <T extends Mob> T mob(EntityType<T> type, Vec3 at) {
        T mob = type.create(level);
        h.assertTrue(mob != null, "couldn't create a " + type);
        mob.moveTo(at);
        mob.setNoAi(true);
        return add(mob);
    }

    /** Adds an entity to the level, to be discarded when the test ends, and records its damage. */
    <T extends Entity> T add(T entity) {
        level.addFreshEntity(entity);
        entities.add(entity);
        DEALT.put(entity, new ArrayList<>());
        return entity;
    }

    /**
     * A survival player whom damage reaches as it would a real player. It isn't added to the level,
     * and its own tick does nothing, as a fake player's doesn't.
     */
    FakePlayer player(Vec3 at) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_adastra")) {
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
        entities.add(player);
        DEALT.put(player, new ArrayList<>());
        return player;
    }

    /** One of Ad Astra's items, by path. */
    static ItemStack adAstra(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ad_astra", path));
        if (item == Items.AIR) {
            throw new GameTestAssertException("Ad Astra has no item " + path);
        }
        return new ItemStack(item);
    }

    /** Ad Astra's oxygen, the fluid its suits hold. */
    static Fluid oxygen() {
        return BuiltInRegistries.FLUID.get(ResourceLocation.fromNamespaceAndPath("ad_astra", "oxygen"));
    }

    /**
     * One of Ad Astra's suit chest pieces holding this much oxygen, in mB, put there through
     * NeoForge's fluid item capability. The capability works on a copy: what it filled is its
     * container, not the stack it was asked for.
     */
    static ItemStack suitHolding(String path, int oxygen) {
        IFluidHandlerItem store = adAstra(path).getCapability(Capabilities.FluidHandler.ITEM);
        if (store == null || store.fill(new FluidStack(oxygen(), oxygen), FluidAction.EXECUTE) != oxygen) {
            throw new GameTestAssertException("couldn't put " + oxygen + " mB of oxygen in " + path);
        }
        return store.getContainer();
    }

    /** The oxygen in a suit chest piece, in mB, as NeoForge's fluid item capability reports it. */
    static int oxygenIn(ItemStack suit) {
        IFluidHandlerItem store = suit.getCapability(Capabilities.FluidHandler.ITEM);
        if (store == null) {
            throw new GameTestAssertException(suit + " has no fluid item capability");
        }
        return store.getFluidInTank(0).getAmount();
    }

    /** Dresses an entity in a whole suit of Ad Astra's: these four items, head to feet. */
    static void dress(LivingEntity entity, ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
        entity.setItemSlot(EquipmentSlot.HEAD, head);
        entity.setItemSlot(EquipmentSlot.CHEST, chest);
        entity.setItemSlot(EquipmentSlot.LEGS, legs);
        entity.setItemSlot(EquipmentSlot.FEET, feet);
    }

    /**
     * Ticks a player's inventory {@code n} times, as {@code n} ticks of the player would: this is
     * where Ad Astra's suit drains its oxygen, every 12th tick.
     */
    static void wear(FakePlayer player, int n) {
        for (int i = 0; i < n; i++) {
            player.tickCount++;
            player.getInventory().tick();
        }
    }

    /**
     * Ticks an entity as the level would, {@code n} times: the whole of its tick, which is where Ad
     * Astra hooks its oxygen and temperature checks, every 20th.
     */
    static void live(LivingEntity entity, int n) {
        for (int i = 0; i < n; i++) {
            entity.tickCount++;
            entity.tick();
        }
    }

    /** The damage types an entity has been dealt since it was made, in order. */
    static List<ResourceLocation> dealt(Entity entity) {
        return List.copyOf(DEALT.get(entity));
    }

    @Override
    public void close() {
        for (Entity entity : entities) {
            DEALT.remove(entity);
            entity.discard();
        }
    }
}
