package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
 * <p>Buttons:
 *
 * <ul>
 *   <li>{@code 0}–{@code 5}: set the mode, by {@link ControllerMode} ordinal;
 *   <li>{@code 10}, {@code 11}: load the shipped Human or Vacuum preset, discarding edits;
 *   <li>{@code 20}/{@code 21}: select the previous or next reading; {@code 22}/{@code 23}: the
 *       previous or next bound;
 *   <li>{@code 30}–{@code 35}: change the selected bound by −10, −1, −0.1, +0.1, +1 or +10;
 *   <li>{@code 36}: turn the selected bound off, or back on.
 * </ul>
 */
public class ControllerMenu extends AbstractContainerMenu {
    public static final int LOAD_PRESET = 10;
    public static final int PREV_READING = 20;
    public static final int NEXT_READING = 21;
    public static final int PREV_BOUND = 22;
    public static final int NEXT_BOUND = 23;
    public static final int ADJUST = 30;
    public static final int TOGGLE_BOUND = 36;

    /** The steps of buttons {@code 30} to {@code 35}. */
    public static final double[] STEPS = {-10, -1, -0.1, 0.1, 1, 10};

    private static final int SYNC_TICKS = 10;

    private final BlockPos pos;
    private final AreaAirControllerBlockEntity controller;
    private final ContainerLevelAccess access;
    private final Player player;
    private int reading;
    private int bound = 2;
    private int sinceSync = SYNC_TICKS;
    private ControllerSync state;

    /** On the server. */
    public ControllerMenu(int id, Inventory inventory, AreaAirControllerBlockEntity controller) {
        super(AtmosMenus.AREA_AIR_CONTROLLER.get(), id);
        this.pos = controller.getBlockPos();
        this.controller = controller;
        this.access = ContainerLevelAccess.create(controller.getLevel(), pos);
        this.player = inventory.player;
    }

    /** On the client, from the position the server sent. */
    public ControllerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(AtmosMenus.AREA_AIR_CONTROLLER.get(), id);
        this.pos = buf.readBlockPos();
        this.controller = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
    }

    public BlockPos pos() {
        return pos;
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
        } else if (id == PREV_READING || id == NEXT_READING) {
            int n = preset.readings();
            reading = Math.floorMod(reading + (id == NEXT_READING ? 1 : -1), n);
        } else if (id == PREV_BOUND || id == NEXT_BOUND) {
            bound = Math.floorMod(bound + (id == NEXT_BOUND ? 1 : -1), 4);
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
        } else {
            return false;
        }
        sinceSync = SYNC_TICKS;
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
                controller.lastPin() == null ? "" : controller.lastPin().name());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.AREA_AIR_CONTROLLER.get());
    }
}
