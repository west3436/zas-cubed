package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A compressor's own window (CONT-160, CONT-163): what its engine is doing and whether a controller
 * links it, its buffer's pressure with the fuel and the oxidizer it holds, and what it released of each
 * at its last atmos tick. It has no buttons: the engine's controller picks the gases and the rate. As
 * the window opens the server sends where its controller is and the lists of gases a turbine burns, and
 * the data names the fuel and the oxidizer by their place in those lists, as the controller's window
 * does. Menu data syncs as shorts.
 */
public class CompressorMenu extends AbstractContainerMenu {
    public static final int DATA_STATUS = 0;

    /** 1 while a controller links it. */
    public static final int DATA_LINKED = 1;

    /** Its buffer's pressure, kPa, rounded. */
    public static final int DATA_BUFFER_PRESSURE = 2;

    /** Its controller's fuel's place in the fuel list, or −1. */
    public static final int DATA_FUEL = 3;

    /** Its controller's oxidizer's place in the oxidizer list, or −1. */
    public static final int DATA_OXIDIZER = 4;

    /** The fuel's partial pressure in its buffer, kPa, rounded. */
    public static final int DATA_BUFFER_FUEL = 5;

    /** The oxidizer's partial pressure in its buffer, kPa, rounded. */
    public static final int DATA_BUFFER_OXIDIZER = 6;

    /** The fuel it released at its last atmos tick, hundredths of a mol/s. */
    public static final int DATA_FUEL_RATE = 7;

    /** The oxidizer it released at its last atmos tick, hundredths of a mol/s. */
    public static final int DATA_OXIDIZER_RATE = 8;

    public static final int DATA_COUNT = 9;

    private final ContainerData data;
    private final @Nullable CompressorBlockEntity compressor;
    private final ContainerLevelAccess access;
    private final Optional<BlockPos> controller;
    private final List<ResourceLocation> fuels;
    private final List<ResourceLocation> oxidizers;

    /** The client's, with what the server sent as it opened. */
    public CompressorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(
                id,
                null,
                new SimpleContainerData(DATA_COUNT),
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf),
                TurbineControllerMenu.GASES.decode(buf),
                TurbineControllerMenu.GASES.decode(buf));
    }

    /** The server's, on a compressor. */
    public CompressorMenu(
            int id,
            @Nullable CompressorBlockEntity compressor,
            ContainerData data,
            Optional<BlockPos> controller,
            List<ResourceLocation> fuels,
            List<ResourceLocation> oxidizers) {
        super(AtmosMenus.COMPRESSOR.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.compressor = compressor;
        this.controller = controller;
        this.fuels = List.copyOf(fuels);
        this.oxidizers = List.copyOf(oxidizers);
        this.access = compressor == null || compressor.getLevel() == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(compressor.getLevel(), compressor.getBlockPos());
        addDataSlots(data);
    }

    /** Writes what the client's window needs as it opens. */
    static void write(
            RegistryFriendlyByteBuf buf,
            Optional<BlockPos> controller,
            List<ResourceLocation> fuels,
            List<ResourceLocation> oxidizers) {
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, controller);
        TurbineControllerMenu.GASES.encode(buf, fuels);
        TurbineControllerMenu.GASES.encode(buf, oxidizers);
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

    /** Its controller's fuel, if it has one in the list. */
    public Optional<ResourceLocation> fuel() {
        return pick(fuels, data.get(DATA_FUEL));
    }

    /** Its controller's oxidizer, if it has one in the list. */
    public Optional<ResourceLocation> oxidizer() {
        return pick(oxidizers, data.get(DATA_OXIDIZER));
    }

    private static Optional<ResourceLocation> pick(List<ResourceLocation> gases, int index) {
        return index >= 0 && index < gases.size() ? Optional.of(gases.get(index)) : Optional.empty();
    }

    /** What it released at its last atmos tick, mol/s: of the fuel, or of the oxidizer. */
    public double released(boolean fuel) {
        return data.get(fuel ? DATA_FUEL_RATE : DATA_OXIDIZER_RATE) / 100.0;
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
        return compressor == null || stillValid(access, player, AtmosBlocks.COMPRESSOR.get());
    }
}
