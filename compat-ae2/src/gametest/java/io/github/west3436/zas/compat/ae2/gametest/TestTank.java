package io.github.west3436.zas.compat.ae2.gametest;

import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Another mod's gas tank, as the tests need one with or without the content mod: a lodestone a test
 * has put a tank at provides {@link IGasHandler} on every face. It holds any gas up to a number of
 * moles, mixes heat as gas comes in, and can be made to misbehave: give more than it is asked for,
 * or take less than it said it would.
 *
 * <p>Registered from the GameTest source set, so it exists only in the tests' own runs.
 */
@EventBusSubscriber(modid = "zas", bus = EventBusSubscriber.Bus.MOD)
public final class TestTank implements IGasHandler {
    /** The tanks, by where they are. A lodestone with none provides nothing. */
    private static final Map<BlockPos, TestTank> TANKS = new ConcurrentHashMap<>();

    /** The block a tank stands in. */
    public static final BlockState BLOCK = Blocks.LODESTONE.defaultBlockState();

    private final TreeMap<ResourceLocation, Double> moles = new TreeMap<>();
    private double temperature;
    private final double capacity;

    /** Moles a drain gives on top of what it was asked for, of the same gas. */
    double drainBonus;

    /** The part of a fill it says, when simulated, that it would take and then doesn't. */
    double fillShortfall;

    TestTank(double capacity, double temperature) {
        this.capacity = capacity;
        this.temperature = temperature;
    }

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        event.registerBlock(IGasHandler.BLOCK, TestTank::at, Blocks.LODESTONE);
    }

    private static @Nullable IGasHandler at(
            Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable Direction side) {
        return level.isClientSide() ? null : TANKS.get(pos);
    }

    /** Puts a tank at a place; a lodestone there then provides it. */
    static TestTank put(BlockPos pos, double capacity, double temperature) {
        TestTank tank = new TestTank(capacity, temperature);
        TANKS.put(pos.immutable(), tank);
        return tank;
    }

    static void remove(BlockPos pos) {
        TANKS.remove(pos);
    }

    /** Adds gas as if it were made there, at the tank's temperature. */
    TestTank with(ResourceLocation gas, double n) {
        moles.merge(gas, n, Double::sum);
        return this;
    }

    double moles(ResourceLocation gas) {
        return moles.getOrDefault(gas, 0.0);
    }

    double total() {
        double total = 0;
        for (double n : moles.values()) {
            total += n;
        }
        return total;
    }

    double temperature() {
        return temperature;
    }

    @Override
    public GasMixture contents() {
        return new GasMixture(moles, temperature, 100);
    }

    @Override
    public GasParcel fill(GasParcel gas, Action action) {
        double room = capacity - total();
        double offered = gas.totalMoles();
        if (!(room > 0) || !(offered > 0)) {
            return GasParcel.EMPTY;
        }
        double part = Math.min(1, room / offered);
        if (action == Action.EXECUTE) {
            part *= 1 - fillShortfall;
        }
        TreeMap<ResourceLocation, Double> took = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> e : gas.moles().entrySet()) {
            took.put(e.getKey(), e.getValue() * part);
        }
        GasParcel taken = new GasParcel(took, gas.temperature());
        if (action == Action.EXECUTE) {
            GasParcel mixed = GasProperties.mix(new GasParcel(moles, temperature), taken);
            moles.clear();
            moles.putAll(mixed.moles());
            temperature = mixed.temperature();
        }
        return taken;
    }

    @Override
    public GasParcel drain(Map<ResourceLocation, Double> wanted, Action action) {
        TreeMap<ResourceLocation, Double> gave = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Double> e : wanted.entrySet()) {
            double have = moles(e.getKey());
            double gives = Math.min(have, Math.max(0, e.getValue()) + drainBonus);
            if (gives > 0) {
                gave.put(e.getKey(), gives);
            }
        }
        if (action == Action.EXECUTE) {
            gave.forEach((gas, n) -> {
                double left = moles(gas) - n;
                if (left > 0) {
                    moles.put(gas, left);
                } else {
                    moles.remove(gas);
                }
            });
        }
        return new GasParcel(gave, temperature);
    }
}
