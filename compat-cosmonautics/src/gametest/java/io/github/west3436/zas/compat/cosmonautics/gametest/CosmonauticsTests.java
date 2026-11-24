package io.github.west3436.zas.compat.cosmonautics.gametest;

import io.github.west3436.ss13.compat.Adapters;
import io.github.west3436.ss13.compat.Adapters.State;
import io.github.west3436.ss13.compat.Adapters.Status;
import io.github.west3436.zas.api.life.BreathReason;
import io.github.west3436.zas.api.life.BreathSource;
import io.github.west3436.zas.api.life.Breathing;
import io.github.west3436.zas.compat.ZasAdapters;
import io.github.west3436.zas.compat.cosmonautics.CosmonauticsCompat;
import io.github.west3436.zas.config.BreathingConfig;
import io.github.west3436.zas.life.Breathers;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Breathing with Create Cosmonautics (SPACE-012, S-06): on its Moon, where Cosmonautics finds no
 * air anywhere, a sealed and pressurized ZAS³ room is breathable, and so are ZAS³'s internals in
 * the open; ZAS³'s verdict decides there, and vanilla's rule under water. In the open with nothing
 * of ZAS³'s, Cosmonautics' answer stands.
 */
@GameTestHolder(CosmonauticsTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class CosmonauticsTests {
    /** The namespace of the adapter's GameTests. */
    public static final String NAMESPACE = "zas_cosmonautics";

    private CosmonauticsTests() {}

    /**
     * What NeoForge's breathing hook would be told on the entity's tick: vanilla's answer, then
     * every listener, Cosmonautics', the adapter's and ZAS³'s among them.
     */
    static boolean breathes(LivingEntity entity) {
        LivingBreatheEvent event =
                new LivingBreatheEvent(entity, Breathing.vanilla(entity).canBreathe(), 1, 4);
        NeoForge.EVENT_BUS.post(event);
        return event.canBreathe();
    }

    /** What Cosmonautics' own listener makes of vanilla's answer, with no other listener. */
    static boolean cosmonautics(LivingEntity entity) {
        LivingBreatheEvent event =
                new LivingBreatheEvent(entity, Breathing.vanilla(entity).canBreathe(), 1, 4);
        try {
            Method own = Class.forName("dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler")
                    .getMethod("onLivingBreathe", LivingBreatheEvent.class);
            own.invoke(null, event);
        } catch (InvocationTargetException e) {
            throw new GameTestAssertException("Cosmonautics' listener threw: " + e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new GameTestAssertException("can't find Cosmonautics' breathing listener: " + e);
        }
        return event.canBreathe();
    }

    /** Runs ZAS³'s breathing check on an entity now, so its verdict is the one it is held to. */
    static void check(LivingEntity entity) {
        Breathers.checkAt(entity, entity.level().getGameTime() + BreathingConfig.checkIntervalTicks());
    }

    /**
     * With Cosmonautics installed, {@code zas} starts the adapter, and the Cosmonautics on the
     * server is the one the build asked for.
     */
    @GameTest(template = "empty")
    public static void compat_002_the_adapter_is_on_with_cosmonautics(GameTestHelper h) {
        String installed = Adapters.installedVersion(CosmonauticsCompat.MOD_ID).orElse(null);
        h.assertValueEqual(installed, System.getProperty("zas.cosmonautics.version"), "the Cosmonautics installed");
        Status status = ZasAdapters.ADAPTERS.status(CosmonauticsCompat.MOD_ID).orElse(null);
        h.assertTrue(status != null, "zas has no adapter row for Cosmonautics");
        h.assertValueEqual(status.row().entryClass(), CosmonauticsCompat.class.getName(), "the row's entry class");
        h.assertValueEqual(status.state(), State.ACTIVE, "the adapter's state");
        h.assertTrue(CosmonauticsCompat.isActive(), "the adapter says it never started");
        h.succeed();
    }

    /**
     * On the Moon, in a sealed room of ZAS³ air: Cosmonautics by itself says a player and a pig
     * there can't breathe, and with the adapter both can, before and after ZAS³'s check finds them
     * breathing (S-06).
     */
    @GameTest(template = "empty")
    public static void space_012_a_sealed_room_on_the_moon_is_breathable(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 1)) {
            site.room(0, Site.AIR);
            Pig pig = site.mob(EntityType.PIG, site.floor(0));
            FakePlayer player = site.player(site.floor(0));
            h.assertFalse(cosmonautics(pig), "Cosmonautics by itself let a pig breathe on the Moon");
            h.assertFalse(cosmonautics(player), "Cosmonautics by itself let a player breathe on the Moon");
            h.assertTrue(breathes(pig), "a pig in the room can't breathe before ZAS³'s check");
            check(pig);
            check(player);
            h.assertValueEqual(Breathing.of(pig).reason(), BreathReason.BREATHABLE, "ZAS³'s verdict for the pig");
            h.assertTrue(breathes(pig), "a pig in the room can't breathe");
            h.assertTrue(breathes(player), "a player in the room can't breathe");
        }
        h.succeed();
    }

    /**
     * On the Moon, ZAS³'s verdict decides inside a zone: a room of air too thin in O₂, and a vacuum
     * room, give no breath, before ZAS³'s check (by the room's O₂) and after it (by its verdict).
     */
    @GameTest(template = "empty")
    public static void space_012_a_thin_or_empty_room_gives_no_breath(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 2)) {
            site.room(0, Map.of(Site.N2, 90.0, Site.O2, 10.0));
            site.room(8, Map.of());
            for (int x0 : new int[] {0, 8}) {
                Pig pig = site.mob(EntityType.PIG, site.floor(x0));
                h.assertFalse(breathes(pig), "a pig in the room from " + x0 + " breathes before ZAS³'s check");
                check(pig);
                h.assertValueEqual(
                        Breathing.of(pig).reason(), BreathReason.LOW_OXYGEN, "ZAS³'s verdict in the room from " + x0);
                h.assertFalse(breathes(pig), "a pig in the room from " + x0 + " breathes");
            }
        }
        h.succeed();
    }

    /**
     * In the open on the Moon, Cosmonautics' answer stands for anything ZAS³ only knows the ambient
     * of, before ZAS³'s check and after it. (Internals in the open are {@link
     * CosmonauticsContentTests}'.)
     */
    @GameTest(template = "empty")
    public static void space_012_the_open_moon_gives_no_breath(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 3)) {
            Pig pig = site.mob(EntityType.PIG, site.open(4));
            h.assertFalse(breathes(pig), "a pig in the open on the Moon breathes before ZAS³'s check");
            check(pig);
            h.assertFalse(breathes(pig), "a pig in the open on the Moon breathes");
            FakePlayer player = site.player(site.open(8));
            check(player);
            h.assertFalse(breathes(player), "a player in the open on the Moon breathes");
        }
        h.succeed();
    }

    /**
     * Under water inside a room on the Moon, vanilla's rule stands: a pig whose eyes are in water
     * can't breathe, as it couldn't anywhere.
     */
    @GameTest(template = "empty")
    public static void space_012_under_water_in_a_room_vanilla_decides(GameTestHelper h) {
        try (Site site = Site.onTheMoon(h, 4)) {
            site.room(0, Site.AIR);
            site.set(2, 1, 2, Blocks.WATER.defaultBlockState());
            site.set(2, 2, 2, Blocks.WATER.defaultBlockState());
            site.ticks(2);
            Pig pig = site.mob(EntityType.PIG, site.floor(0));
            pig.baseTick();
            h.assertValueEqual(Breathing.vanilla(pig).source(), BreathSource.FLUID, "where the pig's eyes are");
            h.assertFalse(breathes(pig), "a pig with its eyes in water breathes");
        }
        h.succeed();
    }

    /** Low on the Overworld Cosmonautics finds air, and the adapter changes nothing there. */
    @GameTest(template = "empty")
    public static void space_012_low_on_the_overworld_nothing_changes(GameTestHelper h) {
        try (Site site = Site.onTheOverworld(h, 5)) {
            Pig pig = site.mob(EntityType.PIG, site.open(4));
            h.assertTrue(cosmonautics(pig), "Cosmonautics by itself found no air on the Overworld");
            h.assertTrue(breathes(pig), "a pig in the open on the Overworld can't breathe");
        }
        h.succeed();
    }
}
