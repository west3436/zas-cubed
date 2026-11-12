package io.github.west3436.zas.atmos.controller;

import io.github.west3436.zas.atmos.registry.AtmosBlocks;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The central atmospherics controller's GUI (CONT-117, CONT-121, CONT-123, CONT-124, CONT-074). It
 * has no slots. The server sends the screen a {@link CentralSync} twice a second, and the screen's
 * buttons come back as menu button clicks, which the server checks here. A row's button names the
 * row's place in the list the server last sent this player, so it can only reach an area air
 * controller this central controller has claimed.
 *
 * <p>Buttons:
 *
 * <ul>
 *   <li>{@code 0}, {@code 1}: lower or raise the range by a chunk, from 1 to the server's maximum;
 *   <li>{@code 1000 + row}: open that area air controller's own GUI, if it is in contact (CONT-124);
 *   <li>{@code 100000 + row * 3 + p}: set that area air controller's zone power priority to {@link
 *       ZonePriority} {@code p} (CONT-121).
 * </ul>
 */
public class CentralMenu extends AbstractContainerMenu {
    public static final int RANGE_DOWN = 0;
    public static final int RANGE_UP = 1;
    public static final int OPEN = 1_000;
    public static final int PRIORITY = 100_000;

    private static final int SYNC_TICKS = 10;

    private final BlockPos pos;
    private final CentralControllerBlockEntity central;
    private final ContainerLevelAccess access;
    private final Player player;
    private int sinceSync = SYNC_TICKS;
    private List<BlockPos> rows = List.of();
    private CentralSync state;

    /** On the server. */
    public CentralMenu(int id, Inventory inventory, CentralControllerBlockEntity central) {
        super(AtmosMenus.CENTRAL_ATMOS_CONTROLLER.get(), id);
        this.pos = central.getBlockPos();
        this.central = central;
        this.access = ContainerLevelAccess.create(central.getLevel(), pos);
        this.player = inventory.player;
    }

    /** On the client, from the position the server sent. */
    public CentralMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        super(AtmosMenus.CENTRAL_ATMOS_CONTROLLER.get(), id);
        this.pos = buf.readBlockPos();
        this.central = null;
        this.access = ContainerLevelAccess.NULL;
        this.player = inventory.player;
    }

    public BlockPos pos() {
        return pos;
    }

    /** The last state from the server, on the client; null before the first. */
    public CentralSync state() {
        return state;
    }

    void receive(CentralSync sync) {
        state = sync;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (central == null || central.isRemoved() || !(central.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        boolean done;
        if (id == RANGE_DOWN || id == RANGE_UP) {
            done = central.setRange(central.range() + (id == RANGE_UP ? 1 : -1));
        } else if (id >= OPEN && id < OPEN + rows.size()) {
            done = open(level, player, rows.get(id - OPEN));
        } else if (id >= PRIORITY && id < PRIORITY + rows.size() * ZonePriority.values().length) {
            int n = ZonePriority.values().length;
            done = central.setPriority(rows.get((id - PRIORITY) / n), ZonePriority.values()[(id - PRIORITY) % n]);
        } else {
            done = false;
        }
        if (done) {
            sinceSync = SYNC_TICKS;
        }
        return done;
    }

    private boolean mine(ServerLevel level, BlockPos controller) {
        return CentralClaims.of(level).centralOf(controller).filter(pos::equals).isPresent();
    }

    /**
     * Opens a claimed area air controller's own GUI for the player, as if they stood at it
     * (CONT-124): the same menu, whose every button the server checks as it does there. Refused if it
     * is out of contact: not loaded, or waiting in a chunk that doesn't tick.
     */
    private boolean open(ServerLevel level, Player player, BlockPos controller) {
        if (!(player instanceof ServerPlayer sp)
                || !mine(level, controller)
                || !level.isLoaded(controller)
                || !(level.getBlockEntity(controller) instanceof AreaAirControllerBlockEntity c)
                || !c.stepping()) {
            return false;
        }
        sp.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, p) -> new ControllerMenu(id, inventory, c, pos), c.getDisplayName()),
                buf -> ControllerMenu.writeOpening(buf, controller, true));
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (central != null && player instanceof ServerPlayer sp && ++sinceSync >= SYNC_TICKS) {
            sinceSync = 0;
            PacketDistributor.sendToPlayer(sp, sync());
        }
    }

    /** The state the screen shows now. The rows' buttons refer to this list until the next. */
    public CentralSync sync() {
        List<CentralSync.Row> out = central.rows();
        List<BlockPos> listed = new ArrayList<>();
        for (CentralSync.Row row : out) {
            listed.add(row.pos());
        }
        rows = List.copyOf(listed);
        return new CentralSync(
                containerId,
                central.status().ordinal(),
                central.buffer().getEnergyStored(),
                central.buffer().getMaxEnergyStored(),
                central.range(),
                CentralControllerBlockEntity.maxRange(),
                out);
    }

    /** The area air controllers the last sync listed, in its order: what a row's button refers to. */
    public List<BlockPos> rows() {
        return rows;
    }

    /** The row a claimed area air controller was last sent in, for tests. */
    public Optional<Integer> row(BlockPos controller) {
        int i = rows.indexOf(controller);
        return i < 0 ? Optional.empty() : Optional.of(i);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, AtmosBlocks.CENTRAL_ATMOS_CONTROLLER.get());
    }
}
