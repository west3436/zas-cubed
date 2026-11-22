package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A gas turbine's own window (CONT-161, CONT-163): what its engine is doing and whether a controller
 * links it, its blades' speed, the gas it lets through, from the chamber's pressure to the exhaust's,
 * and what its generator makes. It has no buttons: the engine's controller sets it. The server sends
 * where its controller is as the window opens. Menu data syncs as shorts.
 */
public class TurbineMenu extends AbstractContainerMenu {
    public static final int DATA_STATUS = 0;

    /** 1 while a controller links it. */
    public static final int DATA_LINKED = 1;

    public static final int DATA_RPM = 2;

    /** The top speed, rpm, for the speed bar. */
    public static final int DATA_TOP_RPM = 3;

    /** The gas it let out at its last atmos tick, hundredths of a mol/s. */
    public static final int DATA_FLOW = 4;

    /** The chamber's pressure, kPa, rounded. */
    public static final int DATA_CHAMBER_PRESSURE = 5;

    /** The exhaust's pressure, kPa, rounded. */
    public static final int DATA_EXHAUST_PRESSURE = 6;

    /** What its generator makes, FE/t, rounded and capped at 32,767. */
    public static final int DATA_POWER = 7;

    public static final int DATA_COUNT = 8;

    private final ContainerData data;
    private final @Nullable TurbineBlockEntity turbine;
    private final ContainerLevelAccess access;
    private final Optional<BlockPos> controller;

    /** The client's, with where its controller was as it opened. */
    public TurbineMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(
                id,
                null,
                new SimpleContainerData(DATA_COUNT),
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf));
    }

    /** The server's, on a turbine. */
    public TurbineMenu(
            int id, @Nullable TurbineBlockEntity turbine, ContainerData data, Optional<BlockPos> controller) {
        super(AtmosMenus.TURBINE.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.turbine = turbine;
        this.controller = controller;
        this.access = turbine == null || turbine.getLevel() == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(turbine.getLevel(), turbine.getBlockPos());
        addDataSlots(data);
    }

    /** Writes where a turbine's controller is, for the client's window. */
    static void write(RegistryFriendlyByteBuf buf, Optional<BlockPos> controller) {
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, controller);
    }

    /** A value as a short carries it: clamped. */
    static int shortOf(long value) {
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    public TurbineStatus status() {
        return TurbineStatus.of(data.get(DATA_STATUS));
    }

    /** Whether a controller links it. */
    public boolean linked() {
        return data.get(DATA_LINKED) != 0;
    }

    /** Where its controller was as the window opened, if a controller linked it. */
    public Optional<BlockPos> controller() {
        return controller;
    }

    /** The gas it let out at its last atmos tick, mol/s. */
    public double flow() {
        return data.get(DATA_FLOW) / 100.0;
    }

    /** What its generator makes, FE/t. */
    public int power() {
        return data.get(DATA_POWER);
    }

    /** It has no buttons. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return turbine == null || stillValid(access, player, AtmosBlocks.TURBINE.get());
    }
}
