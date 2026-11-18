package io.github.west3436.zas.compat.northstar.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A test's rooms and entities, on one of Northstar's planets or on the Overworld. Each test has an
 * area of its own, far from the others, and drives every atmos tick itself within one game tick.
 *
 * <p>A room is a hollow stone box with a 3 × 3 × 3 inside, from area x {@code x0} to {@code x0 +
 * 4}.
 */
final class Site implements AutoCloseable {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** Standard air's partial pressures, in kPa. */
    static final Map<ResourceLocation, Double> AIR = Map.of(
            N2, StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
            O2, StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION);

    /** The inside of a room, in litres. */
    static final double ROOM_LITRES = 27_000;

    static final ResourceKey<Level> MOON = northstar("moon");
    static final ResourceKey<Level> MARS = northstar("mars");

    private static final SourceId SOURCE = SourceId.of("zas", "northstar_gametest");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState NOTHING = Blocks.AIR.defaultBlockState();

    /** The damage types each tracked entity was dealt, in order. */
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

    private Site(GameTestHelper h, ServerLevel level, int index) {
        this.h = h;
        this.level = level;
        this.origin = new BlockPos(4096 + 256 * index, 100, 8192);
        level.getChunk(origin);
    }

    static ResourceKey<Level> northstar(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("northstar", path));
    }

    /** An area on the Overworld. */
    static Site onTheOverworld(GameTestHelper h, int index) {
        return new Site(h, h.getLevel(), index);
    }

    /** An area in one of Northstar's dimensions, after two atmos ticks there. */
    static Site on(GameTestHelper h, ResourceKey<Level> dimension, int index) {
        ServerLevel level = h.getLevel().getServer().getLevel(dimension);
        h.assertTrue(level != null, "the server has no " + dimension.location());
        Site site = new Site(h, level, index);
        site.ticks(2);
        return site;
    }

    /** An area on Northstar's Moon, whose open air the adapter seeds as a vacuum. */
    static Site onTheMoon(GameTestHelper h, int index) {
        Site site = on(h, MOON, index);
        h.assertTrue(
                Atmospheres.at(site.level, site.open(0)).mixture().isEmpty(), "the Moon's open air should be a vacuum");
        return site;
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
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x0 + 4, 6, 4))) {
            level.setBlockAndUpdate(p, NOTHING);
        }
        for (BlockPos p : BlockPos.betweenClosed(abs(x0, 0, 0), abs(x0 + 4, 4, 4))) {
            int x = p.getX() - origin.getX() - x0;
            int y = p.getY() - origin.getY();
            int z = p.getZ() - origin.getZ();
            boolean wall = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
            level.setBlockAndUpdate(p, wall ? STONE : NOTHING);
        }
        Vec3 middle = middle(x0);
        settle(GasMutations.remove(level, middle, Map.of(N2, 1e6, O2, 1e6), SOURCE));
        if (!kPa.isEmpty()) {
            Map<ResourceLocation, Double> moles = new HashMap<>();
            kPa.forEach((gas, p) -> moles.put(gas, moles(p)));
            settle(GasMutations.add(level, middle, new GasParcel(moles, StandardAir.TEMPERATURE), SOURCE));
        }
        h.assertTrue(Atmospheres.zoneAt(level, middle).isPresent(), "the room from " + x0 + " has no zone");
    }

    /** The moles of a gas at this partial pressure in a room, at 293.15 K. */
    static double moles(double kPa) {
        return kPa * ROOM_LITRES / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE);
    }

    /** The middle of the room from x0. */
    Vec3 middle(int x0) {
        return point(x0 + 2, 2, 2);
    }

    /** Where something standing in the middle of the room from x0 stands. */
    Vec3 floor(int x0) {
        return Vec3.atBottomCenterOf(abs(x0 + 2, 1, 2));
    }

    /** Where something in the open stands: 10 blocks above the area's floor, at x. */
    Vec3 open(int x) {
        return Vec3.atBottomCenterOf(abs(x, 10, 2));
    }

    /** A mob with its AI off, so it stays where it is put, whose damage is tracked. */
    <T extends Mob> T mob(EntityType<T> type, Vec3 at) {
        T mob = type.create(level);
        h.assertTrue(mob != null, "couldn't create a " + type);
        mob.moveTo(at);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        entities.add(mob);
        DEALT.put(mob, new ArrayList<>());
        return mob;
    }

    /** A survival player whom damage reaches as it would a real player. It isn't added to the level. */
    FakePlayer player(Vec3 at) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_northstar")) {
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

    /** The damage types an entity made here was dealt. */
    static List<ResourceLocation> dealt(Entity entity) {
        return DEALT.getOrDefault(entity, List.of());
    }

    /** Runs ZAS³'s breathing and hazard check on an entity n times, an interval apart. */
    static void check(LivingEntity entity, int n) {
        long now = entity.level().getGameTime();
        int interval = BreathingConfig.checkIntervalTicks();
        for (int i = 1; i <= n; i++) {
            entity.invulnerableTime = 0;
            Breathers.checkAt(entity, now + (long) i * interval);
        }
    }

    /** One of Northstar's items, by path. */
    static ItemStack northstarItem(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("northstar", path));
        if (item == Items.AIR) {
            throw new GameTestAssertException("Northstar has no item " + path);
        }
        return new ItemStack(item);
    }

    @Override
    public void close() {
        for (Entity entity : entities) {
            DEALT.remove(entity);
            entity.discard();
        }
    }
}
