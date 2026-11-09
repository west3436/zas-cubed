package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * A data pack that adds {@code zas_atmos:test_fe_item} ({@link TestParts#feItem}) to the {@code
 * zas_atmos:batteries} tag, as a pack would add another mod's battery, for {@link BatteryTests}
 * (CONT-112). It lives in the GameTest source set at {@code testpacks/battery_tag} and is added only
 * on the GameTest server.
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class BatteryTestPack {
    private BatteryTestPack() {}

    @SubscribeEvent
    static void addPack(AddPackFindersEvent event) {
        if (!GameTestHooks.isGametestServer() || !ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(ZasAtmosMod.MOD_ID, "testpacks/battery_tag"),
                PackType.SERVER_DATA,
                Component.literal("ZAS³ battery tag test"),
                PackSource.BUILT_IN,
                true,
                Pack.Position.TOP);
    }
}
