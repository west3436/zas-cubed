package io.github.west3436.zas.compat.ae2.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Another mod's nitrogen stored as a fluid: {@code zas:ae2_test_nitrogen}, in the tag {@code
 * #zas:ae2_test_nitrogen}, which the tests' data maps to nitrogen. With it, every gas store holding
 * nitrogen also offers a fluid view, as it does with NuclearCraft installed. Registered from the
 * GameTest source set, so it is never in the mod jar. It has no bucket, block, item or model.
 */
@EventBusSubscriber(modid = "zas", bus = EventBusSubscriber.Bus.MOD)
public final class TestGasFluid {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("zas", "ae2_test_nitrogen");

    private static FluidType type;
    private static BaseFlowingFluid.Source source;
    private static BaseFlowingFluid.Flowing flowing;

    private TestGasFluid() {}

    public static Fluid fluid() {
        return source;
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        // The event comes once for each registry; a fluid made and not registered breaks loading.
        if (type == null) {
            type = new FluidType(FluidType.Properties.create()
                    .temperature(250)
                    .density(-1000)
                    .descriptionId("fluid_type.zas.ae2_test_nitrogen"));
            BaseFlowingFluid.Properties properties =
                    new BaseFlowingFluid.Properties(() -> type, () -> source, () -> flowing);
            source = new BaseFlowingFluid.Source(properties);
            flowing = new BaseFlowingFluid.Flowing(properties);
        }
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> helper.register(ID, type));
        event.register(Registries.FLUID, helper -> {
            helper.register(ID, source);
            helper.register(ResourceLocation.fromNamespaceAndPath("zas", "flowing_ae2_test_nitrogen"), flowing);
        });
    }
}
