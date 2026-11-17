package io.github.west3436.zas.atmos.gametest;

import io.github.west3436.zas.atmos.ZasAtmosMod;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Another mod's gases stored as fluids, and its fluid tank, for the GameTests of COMPAT-140 to
 * COMPAT-145. Registered from the GameTest source set, so they exist in dev runs and GameTest
 * servers but never in the mod jar. They have no bucket, block, item or model.
 *
 * <ul>
 *   <li>{@code zas_atmos:test_oxygen}, in {@code #c:oxygen} as other mods' oxygen is, and in a
 *       second mapped tag, {@code #zas_atmos:test_oxygen_too};
 *   <li>{@code zas_atmos:test_steam} at 383 K and {@code zas_atmos:test_hot_steam} at 423 K, in
 *       {@code #c:steam}, as a mod's several steams are;
 *   <li>{@code zas_atmos:test_pack_gas}, in {@code #zas_atmos:test_pack_gas}, which only the
 *       GameTests' data maps, to nitrous oxide at 250 K;
 *   <li>{@code zas_atmos:test_liquid_oxygen}, in {@code #c:oxygen} and in {@code
 *       #zas_atmos:never_gas}, as a mod's liquefied gas is;
 *   <li>{@code zas_atmos:test_fluid_tank}, a block with one tank of {@link #CAPACITY} mB on every
 *       face.
 * </ul>
 */
@EventBusSubscriber(modid = ZasAtmosMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TestFluids {
    /** The test tank's size, in mB. */
    public static final int CAPACITY = 1000;

    public static final ResourceLocation TANK_ID = ZasAtmosMod.id("test_fluid_tank");

    private static BaseFlowingFluid.Source oxygen;
    private static BaseFlowingFluid.Source steam;
    private static BaseFlowingFluid.Source hotSteam;
    private static BaseFlowingFluid.Source packGas;
    private static BaseFlowingFluid.Source liquidOxygen;
    private static Block tankBlock;
    private static Kind[] kinds;
    private static BlockEntityType<Tank> tankType;

    private TestFluids() {}

    public static Fluid oxygen() {
        return oxygen;
    }

    public static Fluid steam() {
        return steam;
    }

    public static Fluid hotSteam() {
        return hotSteam;
    }

    public static Fluid packGas() {
        return packGas;
    }

    public static Fluid liquidOxygen() {
        return liquidOxygen;
    }

    public static Block tank() {
        return tankBlock;
    }

    /** One test fluid: its type, its source and its flowing form, each registered under the name. */
    private static final class Kind {
        final String name;
        final FluidType type;
        BaseFlowingFluid.Source source;
        BaseFlowingFluid.Flowing flowing;

        Kind(String name, int temperature) {
            this.name = name;
            this.type = new FluidType(FluidType.Properties.create()
                    .temperature(temperature)
                    .density(-1000)
                    .descriptionId("fluid_type.zas_atmos." + name));
            BaseFlowingFluid.Properties properties =
                    new BaseFlowingFluid.Properties(() -> type, () -> source, () -> flowing);
            this.source = new BaseFlowingFluid.Source(properties);
            this.flowing = new BaseFlowingFluid.Flowing(properties);
        }
    }

    @SubscribeEvent
    static void register(RegisterEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        // The event comes once for each registry; a fluid made and not registered breaks loading.
        if (kinds == null) {
            kinds = new Kind[] {
                new Kind("test_oxygen", 300),
                new Kind("test_steam", 383),
                new Kind("test_hot_steam", 423),
                new Kind("test_pack_gas", 300),
                new Kind("test_liquid_oxygen", 90),
            };
        }
        event.register(NeoForgeRegistries.Keys.FLUID_TYPES, helper -> {
            for (Kind kind : kinds) {
                helper.register(ZasAtmosMod.id(kind.name), kind.type);
            }
        });
        event.register(Registries.FLUID, helper -> {
            for (Kind kind : kinds) {
                helper.register(ZasAtmosMod.id(kind.name), kind.source);
                helper.register(ZasAtmosMod.id("flowing_" + kind.name), kind.flowing);
            }
            oxygen = kinds[0].source;
            steam = kinds[1].source;
            hotSteam = kinds[2].source;
            packGas = kinds[3].source;
            liquidOxygen = kinds[4].source;
        });
        event.register(Registries.BLOCK, helper -> {
            tankBlock = new TankBlock(BlockBehaviour.Properties.of().strength(1.0f));
            helper.register(TANK_ID, tankBlock);
        });
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            tankType = BlockEntityType.Builder.of(Tank::new, tankBlock).build(null);
            helper.register(TANK_ID, tankType);
        });
    }

    @SubscribeEvent
    static void capabilities(RegisterCapabilitiesEvent event) {
        if (!ZasAtmosMod.contentEnabled()) {
            return;
        }
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, tankType, (be, side) -> be.exposed ? be.tank : null);
    }

    private static final class TankBlock extends Block implements EntityBlock {
        TankBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Tank(pos, state);
        }
    }

    /** The test tank's block entity: one tank, with what it accepts set by the test. */
    public static final class Tank extends BlockEntity {
        /** What it accepts: anything, unless a test says otherwise. */
        public Predicate<FluidStack> accepts = stack -> true;

        /** Whether it takes nothing by a fill, as a machine's output tank, while it still reports valid fluids. */
        public boolean outputOnly;

        /** Whether it provides the capability at all. */
        public boolean exposed = true;

        /** Its one tank. */
        public final FluidTank tank = new FluidTank(CAPACITY, stack -> accepts.test(stack)) {
            @Override
            public int fill(FluidStack resource, IFluidHandler.FluidAction action) {
                return outputOnly ? 0 : super.fill(resource, action);
            }
        };

        Tank(BlockPos pos, BlockState state) {
            super(tankType, pos, state);
        }

        /** Puts a fluid in it, whatever it accepts. */
        public void hold(@Nullable Fluid fluid, int amount) {
            tank.setFluid(fluid == null || amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount));
        }

        /** The tank as a pipe beside it would see it. */
        @Nullable
        public IFluidHandler on(Direction side) {
            return level == null ? null : level.getCapability(Capabilities.FluidHandler.BLOCK, worldPosition, side);
        }
    }
}
