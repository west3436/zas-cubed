package io.github.west3436.zas.compat.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.mojang.logging.LogUtils;
import io.github.west3436.zas.api.ThirdPartyFaults;
import io.github.west3436.zas.api.capability.IGasHandler;
import io.github.west3436.zas.api.gas.GasMixture;
import io.github.west3436.zas.api.gas.GasParcel;
import io.github.west3436.zas.api.gas.GasProperties;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A block's {@link IGasHandler} as ME storage: what a storage bus on the block reaches, and what the
 * import and export buses move gas through (COMPAT-070, COMPAT-073). Each gas the block holds is its
 * own key, so a mixture shows, and goes in, as its gases (COMPAT-071).
 *
 * <p><b>Rounding</b> (COMPAT-074). ME storage counts whole millimoles ({@link GasUnits}), and a block
 * holds any amount, so neither side is ever told it got more than it did:
 *
 * <ul>
 *   <li>Out of the block, a bus asks for whole units and is given no more than the block gave. If the
 *       block gave more than that, or gases that weren't asked for, the rest goes back into it, and
 *       what it won't take back is let out into the air ({@link GasRelease}).
 *   <li>Into the block, ME storage offers whole units and gives up only those the block took. If the
 *       block took part of a unit less than it said it would, ME storage still gives up that unit, and
 *       the part the block didn't take is let out into the air.
 *   <li>What the block shows ME storage is the whole units of each gas, rounded down, so a bus never
 *       asks for gas that isn't there, and a fraction of a unit stays in the block.
 * </ul>
 *
 * <p><b>Temperature</b> (COMPAT-072). Gas leaving ME storage goes into the block at {@link
 * Ae2Settings#leavingTemperature}. Gas going back into the block it came from goes at the temperature
 * it left at.
 *
 * <p>The handler is looked up anew on every call, so the storage follows a block that comes or goes.
 * A handler that throws is skipped, as if it held and took nothing, and the fault is logged at most
 * once a minute (NFR-013). Server thread only, as the handler is.
 */
public final class GasHandlerStorage implements MEStorage {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Supplier<@Nullable IGasHandler> handler;
    private final ServerLevel level;
    private final BlockPos pos;
    private final boolean extractableOnly;
    private final @Nullable Runnable changed;

    /**
     * @param handler the block's handler now, or null if it has none
     * @param level the block's level
     * @param pos the block, where gas no one takes is let out
     * @param extractableOnly whether to show only the gases the block would give
     * @param changed called after gas has gone in or out, or null
     */
    public GasHandlerStorage(
            Supplier<@Nullable IGasHandler> handler,
            ServerLevel level,
            BlockPos pos,
            boolean extractableOnly,
            @Nullable Runnable changed) {
        this.handler = Objects.requireNonNull(handler, "handler");
        this.level = Objects.requireNonNull(level, "level");
        this.pos = pos.immutable();
        this.extractableOnly = extractableOnly;
        this.changed = changed;
    }

    /** Whether the block provides a handler now. */
    public boolean present() {
        return handler() != null;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return what instanceof GasKey key ? fill(key, amount, mode, Ae2Settings.leavingTemperature()) : 0;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        return what instanceof GasKey key ? drain(key, amount, mode) : 0;
    }

    /**
     * Puts whole units of a gas into the block, at a temperature.
     *
     * @return the units ME storage gives up: those the block took, and part of one it took less of
     */
    long fill(GasKey key, long amount, Actionable mode, double temperature) {
        IGasHandler h = handler();
        if (h == null || amount <= 0 || !GasProperties.isDefined(key.gas())) {
            return 0;
        }
        ResourceLocation gas = key.gas();
        GasParcel would =
                call(h, () -> h.fill(parcel(gas, GasUnits.moles(amount), temperature), IGasHandler.Action.SIMULATE));
        long units = would == null ? 0 : Math.min(amount, GasUnits.whole(would.moles(gas)));
        if (units <= 0 || mode == Actionable.SIMULATE) {
            return Math.max(0, units);
        }
        double offered = GasUnits.moles(units);
        GasParcel took = call(h, () -> h.fill(parcel(gas, offered, temperature), IGasHandler.Action.EXECUTE));
        double taken = took == null ? 0 : Math.min(offered, took.moles(gas));
        if (taken < offered) {
            // It took less than it said it would. ME storage gives up the units that cover what it
            // took, and the part of the last one it didn't take goes into the air.
            units = GasUnits.covering(taken);
            double spare = GasUnits.moles(units) - taken;
            if (spare > 0) {
                GasRelease.release(level, pos, parcel(gas, spare, temperature), "gas a block didn't take");
            }
        }
        if (units > 0) {
            changed();
        }
        return units;
    }

    /**
     * Takes whole units of a gas out of the block.
     *
     * @return the units it gave
     */
    long drain(GasKey key, long amount, Actionable mode) {
        IGasHandler h = handler();
        if (h == null || amount <= 0) {
            return 0;
        }
        ResourceLocation gas = key.gas();
        GasMixture held = call(h, h::contents);
        long can = held == null ? 0 : Math.min(amount, GasUnits.whole(held.moles(gas)));
        if (can <= 0) {
            return 0;
        }
        Map<ResourceLocation, Double> wanted = Map.of(gas, GasUnits.moles(can));
        IGasHandler.Action action =
                mode == Actionable.SIMULATE ? IGasHandler.Action.SIMULATE : IGasHandler.Action.EXECUTE;
        GasParcel gave = call(h, () -> h.drain(wanted, action));
        if (gave == null) {
            return 0;
        }
        long units = Math.min(can, GasUnits.whole(gave.moles(gas)));
        if (mode == Actionable.MODULATE) {
            // All it gave beyond those units goes back: a fraction of one, or another gas.
            GasParcel rest = minus(gave, parcel(gas, GasUnits.moles(units), gave.temperature()));
            if (!rest.isEmpty()) {
                giveBack(h, rest);
            }
            if (units > 0) {
                changed();
            }
        }
        return units;
    }

    /** Puts gas that came out of the block back into it, and lets out what it won't take. */
    void giveBack(IGasHandler h, GasParcel gas) {
        GasParcel took = call(h, () -> h.fill(gas, IGasHandler.Action.EXECUTE));
        GasParcel left = took == null ? gas : minus(gas, took);
        if (!left.isEmpty()) {
            GasRelease.release(level, pos, left, "gas a block wouldn't take back");
        }
    }

    /** The temperature of what the block holds, or ME storage's leaving temperature if it holds nothing. */
    double temperature() {
        IGasHandler h = handler();
        GasMixture held = h == null ? null : call(h, h::contents);
        return held == null || held.isEmpty() ? Ae2Settings.leavingTemperature() : held.temperature();
    }

    /** The whole units of each gas the block holds, rounded down. */
    @Override
    public void getAvailableStacks(KeyCounter out) {
        IGasHandler h = handler();
        GasMixture held = h == null ? null : call(h, h::contents);
        if (held == null) {
            return;
        }
        for (Map.Entry<ResourceLocation, Double> entry : held.moles().entrySet()) {
            long units = GasUnits.whole(entry.getValue());
            if (units > 0 && (!extractableOnly || drain(GasKey.of(entry.getKey()), units, Actionable.SIMULATE) > 0)) {
                out.add(GasKey.of(entry.getKey()), units);
            }
        }
    }

    @Override
    public Component getDescription() {
        return Component.translatable("zas.ae2.gas_storage");
    }

    private @Nullable IGasHandler handler() {
        try {
            return handler.get();
        } catch (RuntimeException | LinkageError e) {
            fault("A gas handler's lookup", e);
            return null;
        }
    }

    private void changed() {
        if (changed != null) {
            changed.run();
        }
    }

    /** Calls the handler; null if it throws, which is logged at most once a minute. */
    private <T> @Nullable T call(IGasHandler h, Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException | LinkageError e) {
            fault("Gas handler " + h.getClass().getName(), e);
            return null;
        }
    }

    private void fault(String source, Throwable e) {
        if (ThirdPartyFaults.shouldLog(source)) {
            LOGGER.error("{} at {} threw; AE2 skips it this time", source, pos, e);
        }
    }

    static GasParcel parcel(ResourceLocation gas, double moles, double temperature) {
        return new GasParcel(Map.of(gas, moles), temperature);
    }

    /** What is in one parcel and not in another, gas by gas, at the first's temperature. */
    static GasParcel minus(GasParcel a, GasParcel b) {
        TreeMap<ResourceLocation, Double> left = new TreeMap<>();
        a.moles().forEach((gas, n) -> {
            double rest = n - b.moles(gas);
            if (rest > 0) {
                left.put(gas, rest);
            }
        });
        return left.isEmpty() ? GasParcel.EMPTY : new GasParcel(left, a.temperature());
    }
}
