package io.github.west3436.zas.atmos.turbine;

import io.github.west3436.zas.atmos.device.RedstoneControl;
import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
 * A gas turbine controller's window (CONT-163): its switch and state, its links, the shaft's speed, the
 * chamber, the compressor's buffer, what the generator makes and what the controller has stored; and
 * its fuel, oxidizer, burn rate, igniter button and auto switch, and its redstone setting (CONT-170).
 * The server fixes the fuel and oxidizer
 * lists when the window opens and sends them, and a button names a gas only by its place in its list,
 * so a client can't name a gas the server doesn't burn. Menu data syncs as shorts, so the store's FE
 * comes in two halves.
 */
public class TurbineControllerMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    /** Switches auto relighting on or off. */
    public static final int AUTO = 1;

    /** Sparks its igniter. */
    public static final int IGNITE = 2;

    /** Changes the burn rate by −0.1, −0.01, +0.01 and +0.1 mol/s (ids 3–6). */
    public static final int RATE_DOWN_TENTH = 3;

    /** Picks the fuel list's first gas; the others follow in order. */
    public static final int FUEL = 16;

    /** Picks the oxidizer list's first gas; the others follow in order. */
    public static final int OXIDIZER = 48;

    /** The most gases each list offers a button. */
    public static final int MOST_GASES = 32;

    public static final int DATA_ON = 0;
    public static final int DATA_STATUS = 1;
    public static final int DATA_RPM = 2;
    public static final int DATA_CHAMBER_PRESSURE = 3;
    public static final int DATA_CHAMBER_TEMPERATURE = 4;

    /** What the generator makes, FE/t, rounded and capped at 32,767. */
    public static final int DATA_POWER = 5;

    public static final int DATA_STORED_HIGH = 6;
    public static final int DATA_STORED_LOW = 7;
    public static final int DATA_CAPACITY_HIGH = 8;
    public static final int DATA_CAPACITY_LOW = 9;

    /** The top speed, rpm, for the speed bar. */
    public static final int DATA_TOP_RPM = 10;

    /** The fuel's place in its list, or −1. */
    public static final int DATA_FUEL = 11;

    /** The oxidizer's place in its list, or −1. */
    public static final int DATA_OXIDIZER = 12;

    /** The burn rate, hundredths of a mol/s. */
    public static final int DATA_RATE = 13;

    public static final int DATA_AUTO = 14;

    /** Which parts are linked: 1 the turbine, 2 the compressor, 4 the igniter. */
    public static final int DATA_LINKS = 15;

    /** The compressor's buffer's pressure, kPa, or {@link #NONE}. */
    public static final int DATA_BUFFER_PRESSURE = 16;

    /** The fuel's partial pressure in the buffer, kPa. */
    public static final int DATA_BUFFER_FUEL = 17;

    /** The oxidizer's partial pressure in the buffer, kPa. */
    public static final int DATA_BUFFER_OXIDIZER = 18;

    /** Its {@linkplain RedstoneControl#data() redstone setting and signal} (CONT-170). */
    public static final int DATA_REDSTONE = 19;

    public static final int DATA_COUNT = 20;

    /** A reading there is none of. */
    public static final int NONE = -1;

    /** A gas list, over the network. */
    public static final StreamCodec<ByteBuf, List<ResourceLocation>> GASES =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static final double[] RATE_STEPS = {-0.1, -0.01, 0.01, 0.1};

    private final ContainerData data;
    private final @Nullable TurbineControllerBlockEntity controller;
    private final ContainerLevelAccess access;
    private final List<ResourceLocation> fuels;
    private final List<ResourceLocation> oxidizers;

    /** The client's, with the lists the server sent. */
    public TurbineControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, null, new SimpleContainerData(DATA_COUNT), GASES.decode(buf), GASES.decode(buf));
    }

    /** The server's, on a controller. */
    public TurbineControllerMenu(
            int id,
            @Nullable TurbineControllerBlockEntity controller,
            ContainerData data,
            List<ResourceLocation> fuels,
            List<ResourceLocation> oxidizers) {
        super(AtmosMenus.TURBINE_CONTROLLER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.controller = controller;
        this.fuels = List.copyOf(fuels);
        this.oxidizers = List.copyOf(oxidizers);
        this.access = controller == null || controller.getLevel() == null
                ? ContainerLevelAccess.NULL
                : ContainerLevelAccess.create(controller.getLevel(), controller.getBlockPos());
        addDataSlots(data);
    }

    /** A value as a short carries it: clamped. */
    static int shortOf(long value) {
        return (int) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The fuels it offers. */
    public List<ResourceLocation> fuels() {
        return fuels;
    }

    /** The oxidizers it offers. */
    public List<ResourceLocation> oxidizers() {
        return oxidizers;
    }

    public boolean on() {
        return data.get(DATA_ON) != 0;
    }

    public TurbineStatus status() {
        return TurbineStatus.of(data.get(DATA_STATUS));
    }

    /** Its redstone setting and signal, as synced: read with {@link RedstoneControl#mode(int)} and the rest. */
    public int redstone() {
        return data.get(DATA_REDSTONE);
    }

    /** Whether a part is linked. */
    public boolean linked(TurbineControllerBlockEntity.Part part) {
        return (data.get(DATA_LINKS) & 1 << part.ordinal()) != 0;
    }

    /** Its stored FE. */
    public int energy() {
        return join(DATA_STORED_HIGH);
    }

    /** Its store's size, FE. */
    public int capacity() {
        return join(DATA_CAPACITY_HIGH);
    }

    private int join(int high) {
        return ((data.get(high) & 0xFFFF) << 16) | (data.get(high + 1) & 0xFFFF);
    }

    /** Applies a button: the server's half of the window. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || controller.isRemoved() || player.isSpectator()) {
            return false;
        }
        if (RedstoneControl.isButton(id)) {
            return controller.redstone().button(id);
        }
        if (id == TOGGLE) {
            controller.setOn(!controller.on());
        } else if (id == AUTO) {
            controller.setAutoIgnite(!controller.autoIgnite());
        } else if (id == IGNITE) {
            return controller.ignite();
        } else if (id >= RATE_DOWN_TENTH && id < RATE_DOWN_TENTH + RATE_STEPS.length) {
            double next = controller.burnRate() + RATE_STEPS[id - RATE_DOWN_TENTH];
            controller.setBurnRate(Math.round(next * 100) / 100.0);
        } else if (id >= FUEL && id - FUEL < Math.min(fuels.size(), MOST_GASES)) {
            return controller.setFuel(fuels.get(id - FUEL));
        } else if (id >= OXIDIZER && id - OXIDIZER < Math.min(oxidizers.size(), MOST_GASES)) {
            return controller.setOxidizer(oxidizers.get(id - OXIDIZER));
        } else {
            return false;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return controller == null || stillValid(access, player, AtmosBlocks.TURBINE_CONTROLLER.get());
    }
}
