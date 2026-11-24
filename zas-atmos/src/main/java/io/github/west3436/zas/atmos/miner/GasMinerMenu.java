package io.github.west3436.zas.atmos.miner;

import io.github.west3436.ss13.device.RedstoneControl;
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
 * A gas miner's window (CONT-180, CONT-074): its switch, state and room, its redstone setting
 * (CONT-170), its target and temperature, and the gas it makes. The server fixes the gas list when the window opens and sends it, and a
 * button names a gas only by its place in that list, so a client can't name a gas the server doesn't
 * define. The server clamps the target and temperature.
 */
public class GasMinerMenu extends AbstractContainerMenu {
    /** Switches it on or off. */
    public static final int TOGGLE = 0;

    /** Changes the target by −100, −10, +10 and +100 kPa (ids 1–4). */
    public static final int TARGET_DOWN_100 = 1;

    /** Sets the target back to a new miner's. */
    public static final int TARGET_DEFAULT = 5;

    /** Changes the temperature by −100, −10, +10 and +100 K (ids 6–9). */
    public static final int TEMPERATURE_DOWN_100 = 6;

    /** Sets the temperature back to a new miner's. */
    public static final int TEMPERATURE_DEFAULT = 10;

    /**
     * Picks the list's first gas; the others follow in order, past the redstone setting's buttons
     * ({@link #gasButton}).
     */
    public static final int GAS = 11;

    /** How many ids the redstone setting's buttons take. */
    private static final int REDSTONE_BUTTONS = RedstoneControl.LAST_BUTTON - RedstoneControl.FIRST_BUTTON + 1;

    public static final int DATA_ON = 0;
    /** The place of its gas in the list, or −1. */
    public static final int DATA_GAS = 1;
    /** Its target, kPa, rounded. */
    public static final int DATA_TARGET = 2;
    /** Its gas's temperature, in tenths of a kelvin. */
    public static final int DATA_TEMPERATURE = 3;
    /** The room's pressure, kPa, rounded, or {@link #NO_ROOM}. */
    public static final int DATA_ROOM = 4;
    /** The {@link GasMinerStatus}'s ordinal. */
    public static final int DATA_STATUS = 5;

    /** Its {@linkplain RedstoneControl#data() redstone setting and signal} (CONT-170). */
    public static final int DATA_REDSTONE = 6;

    public static final int DATA_COUNT = 7;

    /** {@link #DATA_ROOM} with no reading. */
    public static final int NO_ROOM = -1;

    /** The gas list, over the network. */
    public static final StreamCodec<ByteBuf, List<ResourceLocation>> GASES =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static final double[] STEPS = {-100, -10, 10, 100};

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final List<ResourceLocation> gases;

    @Nullable
    private final GasMinerBlockEntity miner;

    /** The client's menu, with the list the server sent. */
    public GasMinerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, GASES.decode(buf), null);
    }

    /** The server's menu, on a miner. */
    public GasMinerMenu(int id, GasMinerBlockEntity miner, List<ResourceLocation> gases) {
        this(
                id,
                miner.data(),
                miner.getLevel() == null
                        ? ContainerLevelAccess.NULL
                        : ContainerLevelAccess.create(miner.getLevel(), miner.getBlockPos()),
                gases,
                miner);
    }

    private GasMinerMenu(
            int id,
            ContainerData data,
            ContainerLevelAccess access,
            List<ResourceLocation> gases,
            @Nullable GasMinerBlockEntity miner) {
        super(AtmosMenus.GAS_MINER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.gases = List.copyOf(gases);
        this.miner = miner;
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The gases it offers. */
    public List<ResourceLocation> gases() {
        return gases;
    }

    /** Its redstone setting and signal, as synced: read with {@link RedstoneControl#mode(int)} and the rest. */
    public int redstone() {
        return data.get(DATA_REDSTONE);
    }

    /** The button that picks the gas at a place in the list: they skip the redstone setting's. */
    public static int gasButton(int index) {
        int id = GAS + index;
        return id < RedstoneControl.FIRST_BUTTON ? id : id + REDSTONE_BUTTONS;
    }

    /** The place in the list of the gas a button picks, or -1 for a button that picks none. */
    static int gasIndex(int id) {
        if (id < GAS || RedstoneControl.isButton(id)) {
            return -1;
        }
        return id < RedstoneControl.FIRST_BUTTON ? id - GAS : id - GAS - REDSTONE_BUTTONS;
    }

    /** The synced status. */
    public GasMinerStatus status() {
        return GasMinerStatus.of(data.get(DATA_STATUS));
    }

    /** Applies a button: the server's half of the window. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (miner == null || miner.isRemoved() || player.isSpectator()) {
            return false;
        }
        if (RedstoneControl.isButton(id)) {
            return miner.redstone().button(id);
        }
        int gas = gasIndex(id);
        if (id == TOGGLE) {
            miner.setOn(!miner.isOn());
        } else if (id >= TARGET_DOWN_100 && id < TARGET_DEFAULT) {
            miner.setTarget(miner.target() + STEPS[id - TARGET_DOWN_100]);
        } else if (id == TARGET_DEFAULT) {
            miner.setTarget(GasMinerBlockEntity.DEFAULT_TARGET);
        } else if (id >= TEMPERATURE_DOWN_100 && id < TEMPERATURE_DEFAULT) {
            miner.setTemperature(miner.temperature() + STEPS[id - TEMPERATURE_DOWN_100]);
        } else if (id == TEMPERATURE_DEFAULT) {
            miner.setTemperature(GasMinerBlockEntity.DEFAULT_TEMPERATURE);
        } else if (gas >= 0 && gas < gases.size()) {
            return miner.setGas(gases.get(gas));
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
        return stillValid(access, player, AtmosBlocks.GAS_MINER.get());
    }
}
