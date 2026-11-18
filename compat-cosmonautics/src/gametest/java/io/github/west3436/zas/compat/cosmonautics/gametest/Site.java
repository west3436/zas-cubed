package io.github.west3436.zas.compat.cosmonautics.gametest;

import com.mojang.authlib.GameProfile;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.StandardAir;
import io.github.west3436.zas.api.mutation.GasMutations;
import io.github.west3436.zas.api.mutation.SourceId;
import io.github.west3436.zas.simulation.AtmosService;
import java.util.ArrayList;
import java.util.HashMap;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * A test's rooms and entities, on Cosmonautics' Moon or on the Overworld. Each test has an area of
 * its own, far from the others, and drives every atmos tick itself within one game tick.
 *
 * <p>A room is a hollow stone box with a 3 × 3 × 3 inside, from area x {@code x0} to {@code x0 +
 * 4}. The Moon's level is made as the server starts ({@link Dimensions}).
 */
final class Site implements AutoCloseable {
    static final ResourceLocation O2 = StandardAir.OXYGEN;
    static final ResourceLocation N2 = StandardAir.NITROGEN;

    /** Standard air's partial pressures, in kPa. */
    static final Map<ResourceLocation, Double> AIR = Map.of(
            N2, StandardAir.PRESSURE * StandardAir.NITROGEN_FRACTION,
            O2, StandardAir.PRESSURE * StandardAir.OXYGEN_FRACTION);

    /** Cosmonautics' Moon, where its own check finds no air at any height. */
    static final ResourceKey<Level> MOON =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("rocketnautics", "moon"));

    private static final SourceId SOURCE = SourceId.of("zas", "cosmonautics_gametest");
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState NOTHING = Blocks.AIR.defaultBlockState();

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

    /** An area on the Overworld, where Cosmonautics finds air this low. */
    static Site onTheOverworld(GameTestHelper h, int index) {
        return new Site(h, h.getLevel(), index);
    }

    /**
     * An area on Cosmonautics' Moon. Two atmos ticks there take the ambient seeded from the Moon's
     * {@code dimension_physics} entry, a vacuum.
     */
    static Site onTheMoon(GameTestHelper h, int index) {
        ServerLevel moon = h.getLevel().getServer().getLevel(MOON);
        h.assertTrue(moon != null, "the server has no Moon: " + Dimensions.class.getSimpleName() + " made none");
        Site site = new Site(h, moon, index);
        site.ticks(2);
        h.assertTrue(
                Atmospheres.at(site.level, site.abs(0, 20, 0)).mixture().isEmpty(),
                "the Moon's open air should be a vacuum");
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
        Vec3 middle = point(x0 + 2, 2, 2);
        settle(GasMutations.remove(level, middle, Map.of(N2, 1e6, O2, 1e6), SOURCE));
        if (!kPa.isEmpty()) {
            Map<ResourceLocation, Double> moles = new HashMap<>();
            kPa.forEach((gas, p) -> moles.put(gas, p * 27_000 / (GasMixture.GAS_CONSTANT * StandardAir.TEMPERATURE)));
            settle(GasMutations.add(level, middle, new GasParcel(moles, StandardAir.TEMPERATURE), SOURCE));
        }
        h.assertTrue(Atmospheres.zoneAt(level, middle).isPresent(), "the room from " + x0 + " has no zone");
    }

    /** Where something standing in the middle of the room from x0 stands. */
    Vec3 floor(int x0) {
        return Vec3.atBottomCenterOf(abs(x0 + 2, 1, 2));
    }

    /** Where something in the open stands: 10 blocks above the area's floor, at x. */
    Vec3 open(int x) {
        return Vec3.atBottomCenterOf(abs(x, 10, 2));
    }

    /** A mob with its AI off, so it stays where it is put. */
    <T extends Mob> T mob(EntityType<T> type, Vec3 at) {
        T mob = type.create(level);
        h.assertTrue(mob != null, "couldn't create a " + type);
        mob.moveTo(at);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        entities.add(mob);
        return mob;
    }

    /** A survival player whom damage reaches as it would a real player. It isn't added to the level. */
    FakePlayer player(Vec3 at) {
        FakePlayer player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "zas_cosmonautics")) {
            @Override
            public boolean isInvulnerableTo(DamageSource source) {
                return false;
            }
        };
        player.moveTo(at);
        entities.add(player);
        return player;
    }

    /** One of the content's items, by path. */
    static ItemStack content(String path) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("zas_atmos", path));
        if (item == Items.AIR) {
            throw new GameTestAssertException("the content has no item " + path);
        }
        return new ItemStack(item);
    }

    @Override
    public void close() {
        for (Entity entity : entities) {
            entity.discard();
        }
    }
}
