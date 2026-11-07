package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The area air controller's GUI (CONT-041, CONT-047, CONT-049, CONT-074). It has no slots. The
 * server sends the screen a {@link ControllerSync} twice a second, and the screen's buttons come
 * back as menu button clicks, which the server checks here: an unknown button is refused, a menu
 * the player is too far from is refused by vanilla before it gets here, and every edit is clamped
 * so the preset's bounds stay in range and in order.
 *
 * <p>The same menu opens from a central atmospherics controller that has claimed this controller
 * (CONT-124). Its buttons are checked here in the same way, and it stays open while the player is at
 * the central controller, this controller is loaded and the central controller still has it.
 *
 * <p>Buttons:
 *
 * <ul>
 *   <li>{@code 0}–{@code 6}: set the mode, by {@link ControllerMode} ordinal;
 *   <li>{@code 10}, {@code 11}: load the shipped Human or Vacuum preset, discarding edits;
 *   <li>{@code 30}–{@code 35}: change the selected bound by −10, −1, −0.1, +0.1, +1 or +10;
 *   <li>{@code 36}: turn the selected bound off, or back on;
 *   <li>{@code 40}: opened from a central controller, go back to its GUI;
 *   <li>{@code 100} and up: select a bound for editing, {@code 100 + reading * 4 + bound}.
 * </ul>
 */
public class ControllerMenu extends AbstractContainerMenu {
    public static final int LOAD_PRESET = 10;
    public static final int ADJUST = 30;
    public static final int TOGGLE_BOUND = 36;
    public static final int BACK = 40;
    public static final int SELECT_BOUND = 100;

    /** The steps of buttons {@code 30} to {@code 35}. */
    public static final double[] STEPS = {-10, -1, -0.1, 0.1, 1, 10};

    private static final int SYNC_TICKS = 10;

    private final BlockPos pos;
    private final AreaAirControllerBlockEntity controller;
    private final BlockPos via;
    private final boolean remote;
    private final ContainerLevelAccess access;
    private final Player player;
    private int reading;
    private int bound = 2;
    private int sinceSync = SYNC_TICKS;
    private ControllerSync state;

    /** On the server, opened at the controller. */
    public ControllerMenu(int id, Inventory inventory, AreaAirControllerBlockEntity controller) {
        this(id, inventory, controller, null);
    }

    /**
     * On the server, opened at the controller or, with {@code via}, from the central controller there
     * that has claimed it (CONT-124).
     */
    public ControllerMenu(int id, Inventory inventory, AreaAirControllerBlockEntity controller, BlockPos via) {
        super(AtmosMenus.AREA_AIR_CONTROLLER.get(), id);
        this.pos = controller.getBlockPos();
        this.controller = controller;
        this.via = via == null ? null : via.immutable();
        this.remote = via != null;
        this.access = ContainerLevelAccess.create(controller.getLevel(), via == null ? pos : via);
        this.player = inventory.player;
    }

    /** On the client, from what {@link #writeOpening} sent. */
    public ControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(AtmosMenus.AREA_AIR_CONTROLLER.get(), id);
        this.pos = buf.readBlockPos();
        this.remote = buf.readBoolean();
        this.controller = null;
        this.via = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
    }

    /**
     * What the server sends the client as the menu opens.
     *
     * @param pos the controller's position
     * @param remote whether it opens from a central controller
     */
    public static void writeOpening(RegistryFriendlyByteBuf buf, BlockPos pos, boolean remote) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(remote);
    }

    public BlockPos pos() {
        return pos;
    }

    /** Whether it was opened from a central controller (CONT-124). */
    public boolean remote() {
        return remote;
    }

    /** The last state from the server, on the client; null before the first. */
    public ControllerSync state() {
        return state;
    }

    void receive(ControllerSync sync) {
        state = sync;
    }

    /** The reading selected for editing. */
    public int reading() {
        return reading;
    }

    /** The bound selected for editing, 0 to 3. */
    public int bound() {
        return bound;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || controller.isRemoved()) {
            return false;
        }
        ControllerPreset preset = controller.preset();
        if (id >= 0 && id < ControllerMode.values().length) {
            controller.setMode(ControllerMode.values()[id]);
        } else if (id >= LOAD_PRESET && id < LOAD_PRESET + ControllerPreset.SHIPPED.size()) {
            controller.setPreset(ControllerPreset.SHIPPED.get(id - LOAD_PRESET));
            reading = Math.min(reading, controller.preset().readings() - 1);
        } else if (id >= SELECT_BOUND && id < SELECT_BOUND + preset.readings() * 4) {
            reading = (id - SELECT_BOUND) / 4;
            bound = (id - SELECT_BOUND) % 4;
        } else if (id >= ADJUST && id < ADJUST + STEPS.length) {
            Thresholds t = preset.thresholds(reading);
            double now = t.bound(bound);
            if (now == Thresholds.OFF) {
                return false; // an off bound has no value to change; turn it on first
            }
            double next = Math.round((now + STEPS[id - ADJUST]) * 1000) / 1000.0;
            controller.setPreset(preset.with(reading, t.with(bound, Math.max(0, next))));
        } else if (id == TOGGLE_BOUND) {
            Thresholds t = preset.thresholds(reading);
            double now = t.bound(bound);
            double next = now == Thresholds.OFF ? defaultBound(t) : Thresholds.OFF;
            controller.setPreset(preset.with(reading, t.with(bound, next)));
        } else if (id == BACK) {
            return back(player);
        } else {
            return false;
        }
        sinceSync = SYNC_TICKS;
        return true;
    }

    /** Opened from a central controller, opens that central controller's GUI again. */
    private boolean back(Player player) {
        if (via == null
                || !(player instanceof ServerPlayer sp)
                || !(controller.getLevel() instanceof ServerLevel level)
                || !level.isLoaded(via)
                || !(level.getBlockEntity(via) instanceof CentralControllerBlockEntity central)) {
            return false;
        }
        sp.openMenu(central, buf -> buf.writeBlockPos(via));
        return true;
    }

    /** A bound turned back on starts next to its enabled neighbours, or at 0. */
    private double defaultBound(Thresholds t) {
        for (int j = bound - 1; j >= 0; j--) {
            if (t.bound(j) != Thresholds.OFF) {
                return t.bound(j);
            }
        }
        for (int j = bound + 1; j < 4; j++) {
            if (t.bound(j) != Thresholds.OFF) {
                return t.bound(j);
            }
        }
        return 0;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (controller != null && player instanceof ServerPlayer sp && ++sinceSync >= SYNC_TICKS) {
            sinceSync = 0;
            PacketDistributor.sendToPlayer(sp, sync());
        }
    }

    /** The state the screen shows now. */
    public ControllerSync sync() {
        ControllerPreset preset = controller.preset();
        List<String> labels = new ArrayList<>();
        double[] bounds = new double[preset.readings() * 4];
        for (int i = 0; i < preset.readings(); i++) {
            ResourceLocation gas = preset.gas(i);
            labels.add(i == 0 ? "pressure" : i == 1 ? "temperature" : gas == null ? "others" : gas.toString());
            for (int b = 0; b < 4; b++) {
                bounds[i * 4 + b] = preset.thresholds(i).bound(b);
            }
        }
        return new ControllerSync(
                containerId,
                controller.mode().ordinal(),
                preset.name(),
                preset.edited(),
                controller.status().ordinal(),
                controller.buffer().getEnergyStored(),
                controller.buffer().getMaxEnergyStored(),
                reading,
                bound,
                labels,
                controller.values(),
                controller.ratings(),
                bounds,
                controller.parts(),
                controller.lastPin() == null ? "" : controller.lastPin().name(),
                controller.breach(),
                controller.fire(),
                central());
    }

    /** The central controller it belongs to, or null (CONT-118). */
    private BlockPos central() {
        return controller.getLevel() instanceof ServerLevel level
                ? CentralClaims.of(level).centralOf(pos).orElse(null)
                : null;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * At the controller: the player is within reach of it. From a central controller: the player is
     * within reach of that, this controller is loaded, and that central controller still has it.
     */
    @Override
    public boolean stillValid(Player player) {
        if (via == null) {
            return stillValid(access, player, AtmosBlocks.AREA_AIR_CONTROLLER.get());
        }
        return controller != null
                && !controller.isRemoved()
                && controller.getLevel() instanceof ServerLevel level
                && level.isLoaded(pos)
                && via.equals(central())
                && stillValid(access, player, AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get());
    }
}
