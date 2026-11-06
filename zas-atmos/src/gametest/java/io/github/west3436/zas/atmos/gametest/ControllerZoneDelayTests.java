package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.api.atmosphere.AtmosphereState;
import io.github.west3436.zas.api.atmosphere.Atmospheres;
import io.github.west3436.zas.api.atmosphere.ZoneInfo;
import io.github.west3436.zas.atmos.ZasAtmosMod;
import io.github.west3436.zas.atmos.controller.AreaAirControllerBlockEntity;
import io.github.west3436.zas.config.ZasServerConfig;
import io.github.west3436.zas.engine.Ambient;
import io.github.west3436.zas.environment.Ambients;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A controller's zone in a sealed room outlasts CORE-052's delay (CONT-110), where an unpinned zone
 * holding the same air dissolves. Each test lowers the delay to 3 s, 3 atmos ticks at the default
 * period, and restores it within its own call, so no other test's atmos tick sees it.
 */
@GameTestHolder(ZasAtmosMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ControllerZoneDelayTests {
    private static final String BATCH = "zas_atmos_controller_zone_delay";
    private static final double DELAY = 3.0;
    private static final ResourceKey<Level> VACUUM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("zas_gametest", "vacuum"));

    private ControllerZoneDelayTests() {}

    /**
     * In a sealed room at {@code s}, a controller's zone lasts well past the delay, and is unbreached;
     * removing the controller unpins it, so it dissolves the delay after that, and the room's cells
     * read as the ambient.
     */
    private static void outlastsTheDelay(GameTestHelper h, Site s) {
        double configured = ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.get();
        try {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(DELAY);
            s.clear();
            s.room(0, 4);
            s.ticks(2);
            AreaAirControllerBlockEntity c = s.controller(2);
            s.cycle(c);
            ZoneInfo zone = s.zone(2, 2, 2);
            h.assertValueEqual(s.pins(zone.id()), List.of(c.owner().toString()), "the zone's pins");

            s.cycles(12, c);
            ZoneInfo later = Atmospheres.zone(s.level, zone.id())
                    .orElseThrow(() -> new GameTestAssertException("the controller's zone was dissolved"));
            h.assertFalse(later.breached(), "the room is sealed");

            s.set(2, 2, 1, Site.AIR); // remove the controller
            s.tick();
            h.assertTrue(Atmospheres.zone(s.level, zone.id()).isPresent(), "the zone went before the delay");
            s.ticks(12);
            h.assertTrue(Atmospheres.zone(s.level, zone.id()).isEmpty(), "the unpinned zone stayed");
            h.assertValueEqual(
                    Atmospheres.at(s.level, s.abs(2, 2, 2)).state(), AtmosphereState.AMBIENT, "the room's cells");
        } finally {
            ZasServerConfig.SEALED_ZONE_DISSOLVE_SECONDS.set(configured);
            s.clear();
        }
        h.succeed();
    }

    /** In a sealed Overworld room, whose zone holds the ambient's air (CONT-110). */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_110_a_controllers_zone_outlasts_the_delay(GameTestHelper h) {
        outlastsTheDelay(h, new Site(h, 970));
    }

    /**
     * In a sealed vacuum room, whose zone holds nothing (CONT-110). The GameTest server has no datapack
     * dimensions, so the End borrows the test datapack's vacuum ambient within this call.
     */
    @GameTest(template = "empty", batch = BATCH)
    public static void cont_110_a_controllers_zone_outlasts_the_delay_in_a_vacuum(GameTestHelper h) {
        ServerLevel end = h.getLevel().getServer().getLevel(Level.END);
        if (end == null) {
            throw new GameTestAssertException("no End");
        }
        Ambient vacuum = Ambients.ambient(VACUUM);
        h.assertTrue(vacuum.isVacuum(), "zas_gametest:vacuum isn't a vacuum");
        Ambients.overrideForTest(Level.END, vacuum);
        try {
            outlastsTheDelay(h, new Site(h, end, 971));
        } finally {
            Ambients.overrideForTest(Level.END, null);
        }
    }
}
