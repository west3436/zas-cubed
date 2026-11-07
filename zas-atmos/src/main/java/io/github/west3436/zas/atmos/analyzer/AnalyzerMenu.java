package io.github.west3436.zas.atmos.analyzer;

import io.github.west3436.zas.api.atmosphere.AtmosphereSample;
import io.github.west3436.zas.atmos.analyzer.AnalyzerSync.Kind;
import io.github.west3436.zas.atmos.gear.GasTankItem;
import io.github.west3436.zas.atmos.registry.AtmosMenus;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * An analyzer's window (CONT-034, CONT-074): what it was used on, read live, with a tab for a tank in
 * the other hand. It has no slots. The target stays on the server, which reads it and sends the reading
 * in an {@link AnalyzerSync}; the client sends only {@link #SHOW_TARGET} and {@link #SHOW_TANK}, which
 * the server checks. So a client can't name anything to read, near or far.
 *
 * <p>The window stays open while the hand holds an analyzer and, for a block, while the block is there
 * and within {@link AnalyzerTarget#REACH} of the player's reach, as a container's does. It reads at most
 * every {@link #SYNC_TICKS} game ticks, four times a second, and sends only when what it shows has
 * changed ({@link AnalyzerSync#showsSameAs}).
 */
public class AnalyzerMenu extends AbstractContainerMenu {
    /** Shows what it was used on. */
    public static final int SHOW_TARGET = 0;

    /** Shows the tank in the other hand; refused if the other hand holds none. */
    public static final int SHOW_TANK = 1;

    /** How often it reads, in game ticks: four times a second, as the HUD's own sync (DATA-014). */
    public static final int SYNC_TICKS = 5;

    private final InteractionHand hand;

    /** What it reads, on the server; null on the client. */
    @Nullable
    private final AnalyzerTarget target;

    /** Who it sends to, on the server; null on the client. */
    @Nullable
    private final ServerPlayer viewer;

    private boolean showingTank;
    private boolean pending;

    /**
     * The server tick it last read on. The game also calls {@link #broadcastChanges} after each button
     * a client sends, so counting calls would let a client read faster by sending buttons.
     */
    private long lastRead;

    /** On the server, the last reading sent; on the client, the last received. */
    @Nullable
    private AnalyzerSync last;

    /** The client's menu. */
    public AnalyzerMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(id, handOf(buf.readVarInt()), null, null);
    }

    private AnalyzerMenu(int id, InteractionHand hand, @Nullable AnalyzerTarget target, @Nullable ServerPlayer viewer) {
        super(AtmosMenus.ANALYZER.get(), id);
        this.hand = hand;
        this.target = target;
        this.viewer = viewer;
        // Opening reads at once (open); the game's calls while it opens wait for the next read.
        this.lastRead = viewer == null ? 0 : viewer.server.getTickCount();
    }

    private static InteractionHand handOf(int ordinal) {
        return ordinal == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static InteractionHand other(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    /**
     * Opens the window for the analyzer in a hand, on a target, and sends its first reading at once.
     *
     * @return false, opening nothing, if the player can't read the target: the block isn't there, or
     *     it's out of reach
     */
    public static boolean open(ServerPlayer player, InteractionHand hand, AnalyzerTarget target) {
        if (!(player.getItemInHand(hand).getItem() instanceof AnalyzerItem) || !target.readableBy(player)) {
            return false;
        }
        player.openMenu(
                new SimpleMenuProvider(
                        (id, inventory, p) -> new AnalyzerMenu(id, hand, target, player),
                        Component.translatable("item.zas_atmos.atmos_analyzer")),
                buf -> buf.writeVarInt(hand.ordinal()));
        if (player.containerMenu instanceof AnalyzerMenu menu && menu.viewer == player) {
            menu.read(true);
            return true;
        }
        return false;
    }

    /** The hand holding the analyzer. */
    public InteractionHand hand() {
        return hand;
    }

    /** What the window shows: on the server, the last reading sent; on the client, the last received. */
    public @Nullable AnalyzerSync reading() {
        return last;
    }

    void receive(AnalyzerSync sync) {
        last = sync;
    }

    /** The tank in the other hand, if it holds one. */
    private Optional<ItemStack> tank(Player player) {
        ItemStack stack = player.getItemInHand(other(hand));
        return stack.getItem() instanceof GasTankItem ? Optional.of(stack) : Optional.empty();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (viewer != null && viewer.server.getTickCount() - lastRead >= SYNC_TICKS) {
            read(false);
        }
    }

    /** Reads the target or the tank, and sends the reading if it shows something new or a tab was chosen. */
    private void read(boolean force) {
        if (target == null || viewer == null) {
            return;
        }
        lastRead = viewer.server.getTickCount();
        if (!stillValid(viewer)) {
            return; // the game closes it at the player's next tick
        }
        Optional<ItemStack> tank = tank(viewer);
        if (tank.isEmpty()) {
            showingTank = false;
        }
        AnalyzerTarget.Reading read = target.read(viewer);
        Optional<Component> tankName = tank.map(ItemStack::getHoverName);
        AnalyzerSync now;
        if (showingTank) {
            now = new AnalyzerSync(
                    containerId, Kind.TANK, read.name(), tankName, null, -1, GasTankItem.contents(tank.get()));
        } else {
            AtmosphereSample air = read.air();
            now = new AnalyzerSync(
                    containerId,
                    target.kind(),
                    read.name(),
                    tankName,
                    air == null ? null : air.state(),
                    air == null ? -1 : air.zone().map(z -> z.value()).orElse(-1L),
                    read.gas());
        }
        if (force || pending || last == null || !now.showsSameAs(last)) {
            last = now;
            pending = false;
            PacketDistributor.sendToPlayer(viewer, now);
        }
    }

    /**
     * Whether the window can stay open: the player is alive and playing, the hand still holds an
     * analyzer, and, on the server, the target can still be read.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.isAlive()
                && !player.isSpectator()
                && player.getItemInHand(hand).getItem() instanceof AnalyzerItem
                && (target == null || target.readableBy(player));
    }

    /** Applies a tab: the server's half of the window. Unknown ids are refused, and so is a stale window. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!stillValid(player)) {
            return false;
        }
        boolean tank;
        if (id == SHOW_TARGET) {
            tank = false;
        } else if (id == SHOW_TANK && tank(player).isPresent()) {
            tank = true;
        } else {
            return false;
        }
        if (tank != showingTank) {
            showingTank = tank;
            pending = true;
        }
        return true;
    }

    /** Whether the window is reading the tank in the other hand: the server's own record. */
    public boolean showingTank() {
        return showingTank;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
