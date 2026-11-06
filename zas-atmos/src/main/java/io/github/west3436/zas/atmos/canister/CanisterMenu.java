package io.github.west3436.zas.atmos.canister;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A canister's GUI (CONT-074, CONT-084): its pressure and contents, its valve, and its release
 * pressure. The client sends only button ids; the server checks each id and clamps the release
 * pressure to 10–1,013 kPa. What each gas holds doesn't fit in menu data, so it comes in a {@link
 * CanisterSync} when it changes, at most twice a second.
 */
public class CanisterMenu extends AbstractContainerMenu {
    /** Opens or closes the valve. */
    public static final int TOGGLE_VALVE = 0;

    /** Change the release pressure by −100, −10, +10 and +100 kPa. */
    public static final int RELEASE_DOWN_100 = 1;

    public static final int RELEASE_DOWN_10 = 2;
    public static final int RELEASE_UP_10 = 3;
    public static final int RELEASE_UP_100 = 4;

    /** Sets the release pressure back to a new canister's. */
    public static final int RELEASE_DEFAULT = 5;

    /** The synced values: release pressure, valve, canister pressure (kPa, rounded), moles and port. */
    public static final int DATA_RELEASE = 0;

    public static final int DATA_OPEN = 1;
    public static final int DATA_PRESSURE = 2;
    public static final int DATA_MOLES = 3;

    /** 1 if it stands on a connector port. */
    public static final int DATA_PORT = 4;

    public static final int DATA_COUNT = 5;

    private static final int SYNC_TICKS = 10;

    private static final double[] STEPS = {0, -100, -10, 10, 100};

    private final ContainerData data;
    private final ContainerLevelAccess access;
    private final CanisterBlockEntity canister;
    private final Player viewer;
    private int sinceSync = SYNC_TICKS;
    private Map<ResourceLocation, Double> contents = Map.of();

    /** The client's menu. */
    public CanisterMenu(int id, Inventory inventory) {
        this(id, new SimpleContainerData(DATA_COUNT), ContainerLevelAccess.NULL, null, null);
    }

    /** The server's menu, on a canister, with no one to send its contents to. */
    public CanisterMenu(int id, ContainerData data, ContainerLevelAccess access, CanisterBlockEntity canister) {
        this(id, data, access, canister, null);
    }

    /** The server's menu, on a canister, for a player viewing it. */
    public CanisterMenu(
            int id, ContainerData data, ContainerLevelAccess access, CanisterBlockEntity canister, Player viewer) {
        super(AtmosMenus.CANISTER.get(), id);
        checkContainerDataCount(data, DATA_COUNT);
        this.data = data;
        this.access = access;
        this.canister = canister;
        this.viewer = viewer;
        addDataSlots(data);
    }

    /** A synced value, by its {@code DATA_} index. */
    public int value(int index) {
        return data.get(index);
    }

    /** The moles of each gas it holds: the last the server sent, on either side. */
    public Map<ResourceLocation, Double> contents() {
        return contents;
    }

    void receive(Map<ResourceLocation, Double> moles) {
        contents = moles;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (canister != null && viewer instanceof ServerPlayer player && ++sinceSync >= SYNC_TICKS) {
            sinceSync = 0;
            Map<ResourceLocation, Double> now = canister.share().moles();
            if (!now.equals(contents)) {
                contents = now;
                PacketDistributor.sendToPlayer(player, new CanisterSync(containerId, now));
            }
        }
    }

    /** Applies a button: the server's half of the GUI. Unknown ids are refused. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (canister == null || canister.isRemoved()) {
            return false;
        }
        if (id == TOGGLE_VALVE) {
            canister.setValveOpen(!canister.valveOpen());
            return true;
        }
        if (id >= RELEASE_DOWN_100 && id <= RELEASE_UP_100) {
            canister.setReleasePressure(canister.releasePressure() + STEPS[id]);
            return true;
        }
        if (id == RELEASE_DEFAULT) {
            canister.setReleasePressure(CanisterRelease.DEFAULT_KPA);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.CANISTER.get());
    }
}
